package dev.juiced.render;

import java.util.Arrays;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

import dev.juiced.core.Colors;

/**
 * Collects all 3D effect geometry for one frame into plain arrays, then hands it to the game
 * in a single draw call. Coordinates are relative to the camera, which keeps them precise far
 * from the world origin.
 *
 * <p>Every quad is turned to face the camera, so effects look the same from every side and
 * nothing is drawn twice.
 */
public final class Geo {
	private static final int MAX_VERTICES = 196_608;

	/** Maps a position along a shape (0..1) to an 0xRRGGBB color. */
	@FunctionalInterface
	public interface Tint {
		int rgb(float t);
	}

	private float[] xyz = new float[3 * 8192];
	private int[] color = new int[8192];
	private int count;
	private double camX;
	private double camY;
	private double camZ;
	private float partialTick;

	// Billboard basis of the last call to basis()
	private float rightX;
	private float rightY;
	private float rightZ;
	private float upX;
	private float upY;
	private float upZ;

	// Reused buffers for ribbons
	private float[] sideX = new float[64];
	private float[] sideY = new float[64];
	private float[] sideZ = new float[64];

	private static final float[][] SIN = new float[129][];
	private static final float[][] COS = new float[129][];

	public void begin(double camX, double camY, double camZ, float partialTick) {
		this.camX = camX;
		this.camY = camY;
		this.camZ = camZ;
		this.partialTick = partialTick;
		this.count = 0;
	}

	public boolean isEmpty() {
		return count == 0;
	}

	public float partialTick() {
		return partialTick;
	}

	public float rx(double x) {
		return (float) (x - camX);
	}

	public float ry(double y) {
		return (float) (y - camY);
	}

	public float rz(double z) {
		return (float) (z - camZ);
	}

	/** Squared distance from the camera to a world position. */
	public double distanceSq(double x, double y, double z) {
		double dx = x - camX;
		double dy = y - camY;
		double dz = z - camZ;
		return dx * dx + dy * dy + dz * dz;
	}

	public double x(Entity entity) {
		return Mth.lerp(partialTick, entity.xOld, entity.getX());
	}

	public double y(Entity entity) {
		return Mth.lerp(partialTick, entity.yOld, entity.getY());
	}

	public double z(Entity entity) {
		return Mth.lerp(partialTick, entity.zOld, entity.getZ());
	}

	/** Sends this frame's geometry to the game. Called by the renderer's custom geometry callback. */
	public void emit(PoseStack.Pose pose, VertexConsumer buffer) {
		for (int i = 0, p = 0; i < count; i++, p += 3) {
			buffer.addVertex(pose, xyz[p], xyz[p + 1], xyz[p + 2]).setColor(color[i]);
		}
	}

	// ---- primitives -----------------------------------------------------------------------

	private void put(float x, float y, float z, int c) {
		int p = count * 3;
		xyz[p] = x;
		xyz[p + 1] = y;
		xyz[p + 2] = z;
		color[count] = c;
		count++;
	}

	private boolean reserve(int vertices) {
		if (count + vertices > MAX_VERTICES) {
			return false;
		}

		if (count + vertices > color.length) {
			int size = Math.min(MAX_VERTICES, Math.max(color.length * 2, count + vertices));
			color = Arrays.copyOf(color, size);
			xyz = Arrays.copyOf(xyz, size * 3);
		}

		return true;
	}

	/** Quad in camera-relative coordinates. Winding is flipped if needed so it faces the camera. */
	public void quad(float x0, float y0, float z0, int c0,
			float x1, float y1, float z1, int c1,
			float x2, float y2, float z2, int c2,
			float x3, float y3, float z3, int c3) {
		if (((c0 | c1 | c2 | c3) >>> 24) == 0 || !reserve(4)) {
			return;
		}

		float ax = x1 - x0;
		float ay = y1 - y0;
		float az = z1 - z0;
		float bx = x2 - x0;
		float by = y2 - y0;
		float bz = z2 - z0;
		float nx = ay * bz - az * by;
		float ny = az * bx - ax * bz;
		float nz = ax * by - ay * bx;

		if (nx * nx + ny * ny + nz * nz < 1.0e-14f) {
			bx = x3 - x0;
			by = y3 - y0;
			bz = z3 - z0;
			nx = ay * bz - az * by;
			ny = az * bx - ax * bz;
			nz = ax * by - ay * bx;
		}

		// The camera is at the origin: the quad faces it when the normal points towards -v0.
		if (-(nx * x0 + ny * y0 + nz * z0) >= 0f) {
			put(x0, y0, z0, c0);
			put(x1, y1, z1, c1);
			put(x2, y2, z2, c2);
			put(x3, y3, z3, c3);
		} else {
			put(x0, y0, z0, c0);
			put(x3, y3, z3, c3);
			put(x2, y2, z2, c2);
			put(x1, y1, z1, c1);
		}
	}

	public void tri(float x0, float y0, float z0, int c0, float x1, float y1, float z1, int c1, float x2, float y2, float z2, int c2) {
		quad(x0, y0, z0, c0, x1, y1, z1, c1, x2, y2, z2, c2, x2, y2, z2, c2);
	}

	/** Computes a camera-facing basis (right, up) at a camera-relative point. */
	public void basis(float px, float py, float pz) {
		float len = (float) Math.sqrt(px * px + py * py + pz * pz);
		float fx;
		float fy;
		float fz;

		if (len < 1.0e-4f) {
			fx = 0;
			fy = 0;
			fz = 1;
		} else {
			fx = px / len;
			fy = py / len;
			fz = pz / len;
		}

		// right = forward x worldUp
		float rx = -fz;
		float rz = fx;
		float rl = (float) Math.sqrt(rx * rx + rz * rz);

		if (rl < 1.0e-4f) {
			rx = 1;
			rz = 0;
		} else {
			rx /= rl;
			rz /= rl;
		}

		rightX = rx;
		rightY = 0;
		rightZ = rz;
		// up = right x forward
		upX = -rz * fy;
		upY = rz * fx - rx * fz;
		upZ = rx * fy;
	}

	private static float[] sin(int segments) {
		ensureTables(segments);
		return SIN[segments];
	}

	private static float[] cos(int segments) {
		ensureTables(segments);
		return COS[segments];
	}

	private static void ensureTables(int segments) {
		if (SIN[segments] == null) {
			float[] s = new float[segments + 1];
			float[] c = new float[segments + 1];

			for (int i = 0; i <= segments; i++) {
				double a = i * Math.PI * 2 / segments;
				s[i] = (float) Math.sin(a);
				c[i] = (float) Math.cos(a);
			}

			SIN[segments] = s;
			COS[segments] = c;
		}
	}

	private static int clampSegments(int segments) {
		return Math.max(3, Math.min(128, segments));
	}

	/** Soft glowing dot that always faces the camera. */
	public void glowDot(double wx, double wy, double wz, float radius, int rgb, float alpha) {
		if (alpha <= 0.004f) {
			return;
		}

		float px = rx(wx);
		float py = ry(wy);
		float pz = rz(wz);
		basis(px, py, pz);
		int segments = 8;
		float[] s = sin(segments);
		float[] c = cos(segments);
		int center = Colors.argb(alpha, Colors.shade(rgb, 0.25f));
		int rim = Colors.argb(0f, rgb);
		int coreColor = Colors.argb(Math.min(1f, alpha * 1.2f), Colors.shade(rgb, 0.7f));
		float core = radius * 0.32f;

		for (int i = 0; i < segments; i++) {
			float ax = (c[i] * rightX + s[i] * upX);
			float ay = (c[i] * rightY + s[i] * upY);
			float az = (c[i] * rightZ + s[i] * upZ);
			float bx = (c[i + 1] * rightX + s[i + 1] * upX);
			float by = (c[i + 1] * rightY + s[i + 1] * upY);
			float bz = (c[i + 1] * rightZ + s[i + 1] * upZ);
			tri(px, py, pz, center, px + ax * radius, py + ay * radius, pz + az * radius, rim, px + bx * radius, py + by * radius, pz + bz * radius, rim);
			tri(px, py, pz, coreColor, px + ax * core, py + ay * core, pz + az * core, center, px + bx * core, py + by * core, pz + bz * core, center);
		}
	}

	/** Four-pointed star that faces the camera. */
	public void star(double wx, double wy, double wz, float size, float rotation, int rgb, float alpha) {
		if (alpha <= 0.004f) {
			return;
		}

		float px = rx(wx);
		float py = ry(wy);
		float pz = rz(wz);
		basis(px, py, pz);
		int center = Colors.argb(alpha, Colors.shade(rgb, 0.6f));
		int tip = Colors.argb(alpha * 0.85f, rgb);

		for (int i = 0; i < 8; i++) {
			double a0 = rotation + i * Math.PI / 4;
			double a1 = rotation + (i + 1) * Math.PI / 4;
			float r0 = (i % 2 == 0) ? size : size * 0.28f;
			float r1 = (i % 2 == 0) ? size * 0.28f : size;
			float c0 = (float) Math.cos(a0) * r0;
			float s0 = (float) Math.sin(a0) * r0;
			float c1 = (float) Math.cos(a1) * r1;
			float s1 = (float) Math.sin(a1) * r1;
			tri(px, py, pz, center,
					px + c0 * rightX + s0 * upX, py + c0 * rightY + s0 * upY, pz + c0 * rightZ + s0 * upZ, i % 2 == 0 ? tip : center,
					px + c1 * rightX + s1 * upX, py + c1 * rightY + s1 * upY, pz + c1 * rightZ + s1 * upZ, i % 2 == 0 ? center : tip);
		}

		glowDot(wx, wy, wz, size * 1.6f, rgb, alpha * 0.25f);
	}

	/**
	 * Flat band between two radii in the plane spanned by (u, v) around a camera-relative center.
	 * Colors go from {@code inner} to {@code outer} across the band.
	 */
	public void band(float px, float py, float pz,
			float ux, float uy, float uz, float vx, float vy, float vz,
			float r0, float r1, int segments, float start, float arc,
			Tint tint, float innerAlpha, float outerAlpha, float shadeAmount) {
		segments = clampSegments(segments);
		r0 = Math.max(0f, r0);

		if (r1 <= r0 || (innerAlpha <= 0.004f && outerAlpha <= 0.004f)) {
			return;
		}

		float step = arc / segments;
		float cosA = (float) Math.cos(start);
		float sinA = (float) Math.sin(start);

		for (int i = 0; i < segments; i++) {
			float a1 = start + (i + 1) * step;
			float cosB = (float) Math.cos(a1);
			float sinB = (float) Math.sin(a1);
			int rgb = Colors.shade(tint.rgb((i + 0.5f) / segments), shadeAmount);
			int ci = Colors.argb(innerAlpha, rgb);
			int co = Colors.argb(outerAlpha, rgb);
			float dax = cosA * ux + sinA * vx;
			float day = cosA * uy + sinA * vy;
			float daz = cosA * uz + sinA * vz;
			float dbx = cosB * ux + sinB * vx;
			float dby = cosB * uy + sinB * vy;
			float dbz = cosB * uz + sinB * vz;
			quad(px + dax * r0, py + day * r0, pz + daz * r0, ci,
					px + dax * r1, py + day * r1, pz + daz * r1, co,
					px + dbx * r1, py + dby * r1, pz + dbz * r1, co,
					px + dbx * r0, py + dby * r0, pz + dbz * r0, ci);
			cosA = cosB;
			sinA = sinB;
		}
	}

	/** Glowing ring: a bright core with soft glow on both sides, in the plane (u, v). */
	public void glowRing(float px, float py, float pz,
			float ux, float uy, float uz, float vx, float vy, float vz,
			float radius, float width, float glow, int segments, float start, float arc, Tint tint, float alpha) {
		float half = width / 2f;
		band(px, py, pz, ux, uy, uz, vx, vy, vz, radius - half, radius + half, segments, start, arc, tint, alpha, alpha, 0.2f);

		if (glow > 0f) {
			band(px, py, pz, ux, uy, uz, vx, vy, vz, radius + half, radius + half + glow, segments, start, arc, tint, alpha * 0.45f, 0f, 0f);
			band(px, py, pz, ux, uy, uz, vx, vy, vz, radius - half - glow, radius - half, segments, start, arc, tint, 0f, alpha * 0.45f, 0f);
		}
	}

	/** Horizontal glowing ring at a world position. */
	public void groundRing(double wx, double wy, double wz, float radius, float width, float glow, int segments, float start, float arc, Tint tint, float alpha) {
		glowRing(rx(wx), ry(wy), rz(wz), 1, 0, 0, 0, 0, 1, radius, width, glow, segments, start, arc, tint, alpha);
	}

	/** Camera-facing glowing ring at a world position. */
	public void billboardRing(double wx, double wy, double wz, float radius, float width, float glow, int segments, Tint tint, float alpha) {
		float px = rx(wx);
		float py = ry(wy);
		float pz = rz(wz);
		basis(px, py, pz);
		glowRing(px, py, pz, rightX, rightY, rightZ, upX, upY, upZ, radius, width, glow, segments, 0f, (float) (Math.PI * 2), tint, alpha);
	}

	/** Filled horizontal disc that fades towards the rim. */
	public void disc(double wx, double wy, double wz, float radius, int segments, Tint tint, float centerAlpha, float rimAlpha) {
		band(rx(wx), ry(wy), rz(wz), 1, 0, 0, 0, 0, 1, 0f, radius, segments, 0f, (float) (Math.PI * 2), tint, centerAlpha, rimAlpha, 0f);
	}

	/** Vertical cylinder wall, colors fade from bottom to top. */
	public void wall(double wx, double wy, double wz, float radius, float height, int segments, float start, Tint tint, float bottomAlpha, float topAlpha) {
		segments = clampSegments(segments);
		float px = rx(wx);
		float py = ry(wy);
		float pz = rz(wz);
		float step = (float) (Math.PI * 2 / segments);

		for (int i = 0; i < segments; i++) {
			float a0 = start + i * step;
			float a1 = a0 + step;
			float x0 = (float) Math.cos(a0) * radius;
			float z0 = (float) Math.sin(a0) * radius;
			float x1 = (float) Math.cos(a1) * radius;
			float z1 = (float) Math.sin(a1) * radius;
			int rgb = tint.rgb((i + 0.5f) / segments);
			int bottom = Colors.argb(bottomAlpha, rgb);
			int top = Colors.argb(topAlpha, rgb);
			quad(px + x0, py, pz + z0, bottom, px + x1, py, pz + z1, bottom, px + x1, py + height, pz + z1, top, px + x0, py + height, pz + z0, top);
		}
	}

	/**
	 * Camera-facing ribbon through camera-relative points. {@code halfWidth} and {@code colors}
	 * (ARGB) are per point. Used for trails and beams.
	 */
	public void ribbon(float[] px, float[] py, float[] pz, float[] halfWidth, int[] colors, int n) {
		if (n < 2) {
			return;
		}

		if (sideX.length < n) {
			sideX = new float[n * 2];
			sideY = new float[n * 2];
			sideZ = new float[n * 2];
		}

		float lastX = 0;
		float lastY = 1;
		float lastZ = 0;

		for (int i = 0; i < n; i++) {
			int a = Math.max(0, i - 1);
			int b = Math.min(n - 1, i + 1);
			float tx = px[b] - px[a];
			float ty = py[b] - py[a];
			float tz = pz[b] - pz[a];
			// side = tangent x toCamera (toCamera = -p)
			float cx = -(ty * pz[i] - tz * py[i]);
			float cy = -(tz * px[i] - tx * pz[i]);
			float cz = -(tx * py[i] - ty * px[i]);
			float len = (float) Math.sqrt(cx * cx + cy * cy + cz * cz);

			if (len > 1.0e-6f) {
				lastX = cx / len;
				lastY = cy / len;
				lastZ = cz / len;
			}

			sideX[i] = lastX * halfWidth[i];
			sideY[i] = lastY * halfWidth[i];
			sideZ[i] = lastZ * halfWidth[i];
		}

		for (int i = 0; i < n - 1; i++) {
			int j = i + 1;
			quad(px[i] - sideX[i], py[i] - sideY[i], pz[i] - sideZ[i], colors[i],
					px[i] + sideX[i], py[i] + sideY[i], pz[i] + sideZ[i], colors[i],
					px[j] + sideX[j], py[j] + sideY[j], pz[j] + sideZ[j], colors[j],
					px[j] - sideX[j], py[j] - sideY[j], pz[j] - sideZ[j], colors[j]);
		}
	}

	/** Short camera-facing line segment between two world points (sparks, lock-on brackets). */
	public void line(double x0, double y0, double z0, double x1, double y1, double z1, float halfWidth, int c0, int c1) {
		float ax = rx(x0);
		float ay = ry(y0);
		float az = rz(z0);
		float bx = rx(x1);
		float by = ry(y1);
		float bz = rz(z1);
		float tx = bx - ax;
		float ty = by - ay;
		float tz = bz - az;
		float mx = (ax + bx) / 2f;
		float my = (ay + by) / 2f;
		float mz = (az + bz) / 2f;
		float cx = -(ty * mz - tz * my);
		float cy = -(tz * mx - tx * mz);
		float cz = -(tx * my - ty * mx);
		float len = (float) Math.sqrt(cx * cx + cy * cy + cz * cz);

		if (len < 1.0e-6f) {
			return;
		}

		cx = cx / len * halfWidth;
		cy = cy / len * halfWidth;
		cz = cz / len * halfWidth;
		quad(ax - cx, ay - cy, az - cz, c0, ax + cx, ay + cy, az + cz, c0, bx + cx, by + cy, bz + cz, c1, bx - cx, by - cy, bz - cz, c1);
	}

	// ---- boxes (small 3D models) --------------------------------------------------------------

	private static final class Part {
		float cx;
		float cy;
		float cz;
		float rx;
		float ry;
		float rz;
		float ux;
		float uy;
		float uz;
		float fx;
		float fy;
		float fz;
		float hx;
		float hy;
		float hz;
		int rgb;
		float alpha;
		float distance;
	}

	private final java.util.ArrayList<Part> parts = new java.util.ArrayList<>();
	private int partCount;

	/** Starts collecting boxes for one model. Boxes are drawn back to front in {@link #endModel()}. */
	public void beginModel() {
		partCount = 0;
	}

	/** Adds a box centered on the pose, with half sizes along the pose's right, up and forward axes. */
	public void box(Pose3 pose, float hx, float hy, float hz, int rgb, float alpha) {
		if (alpha <= 0.004f) {
			return;
		}

		if (partCount == parts.size()) {
			parts.add(new Part());
		}

		Part part = parts.get(partCount++);
		part.cx = rx(pose.x);
		part.cy = ry(pose.y);
		part.cz = rz(pose.z);
		part.rx = pose.rx;
		part.ry = pose.ry;
		part.rz = pose.rz;
		part.ux = pose.ux;
		part.uy = pose.uy;
		part.uz = pose.uz;
		part.fx = pose.fx;
		part.fy = pose.fy;
		part.fz = pose.fz;
		part.hx = hx;
		part.hy = hy;
		part.hz = hz;
		part.rgb = rgb;
		part.alpha = alpha;
		part.distance = part.cx * part.cx + part.cy * part.cy + part.cz * part.cz;
	}

	public void endModel() {
		if (partCount == 0) {
			return;
		}

		java.util.List<Part> active = parts.subList(0, partCount);
		active.sort((a, b) -> Float.compare(b.distance, a.distance));

		for (Part part : active) {
			drawBox(part);
		}

		partCount = 0;
	}

	private void drawBox(Part p) {
		float ax = p.rx * p.hx;
		float ay = p.ry * p.hx;
		float az = p.rz * p.hx;
		float bx = p.ux * p.hy;
		float by = p.uy * p.hy;
		float bz = p.uz * p.hy;
		float cx = p.fx * p.hz;
		float cy = p.fy * p.hz;
		float cz = p.fz * p.hz;
		face(p, p.rx, p.ry, p.rz, ax, ay, az, bx, by, bz, cx, cy, cz);
		face(p, -p.rx, -p.ry, -p.rz, -ax, -ay, -az, bx, by, bz, -cx, -cy, -cz);
		face(p, p.ux, p.uy, p.uz, bx, by, bz, cx, cy, cz, ax, ay, az);
		face(p, -p.ux, -p.uy, -p.uz, -bx, -by, -bz, cx, cy, cz, -ax, -ay, -az);
		face(p, p.fx, p.fy, p.fz, cx, cy, cz, ax, ay, az, bx, by, bz);
		face(p, -p.fx, -p.fy, -p.fz, -cx, -cy, -cz, ax, ay, az, -bx, -by, -bz);
	}

	/**
	 * One box face. (ox, oy, oz) points from the center to the face, (sx..) and (tx..) span it.
	 * Faces turned away from the camera are skipped; the rest get simple light-from-above shading.
	 */
	private void face(Part p, float nx, float ny, float nz, float ox, float oy, float oz,
			float sx, float sy, float sz, float tx, float ty, float tz) {
		float centerX = p.cx + ox;
		float centerY = p.cy + oy;
		float centerZ = p.cz + oz;

		if (nx * centerX + ny * centerY + nz * centerZ >= 0f) {
			return;
		}

		float shade = ny >= 0 ? 0.74f + 0.26f * ny : 0.74f + 0.2f * ny;
		shade += 0.05f * nx;
		int r = Math.min(255, Math.round(((p.rgb >> 16) & 0xFF) * shade));
		int g = Math.min(255, Math.round(((p.rgb >> 8) & 0xFF) * shade));
		int b = Math.min(255, Math.round((p.rgb & 0xFF) * shade));
		int c = Colors.argb(p.alpha, (r << 16) | (g << 8) | b);
		quad(centerX - sx - tx, centerY - sy - ty, centerZ - sz - tz, c,
				centerX + sx - tx, centerY + sy - ty, centerZ + sz - tz, c,
				centerX + sx + tx, centerY + sy + ty, centerZ + sz + tz, c,
				centerX - sx + tx, centerY - sy + ty, centerZ - sz + tz, c);
	}

	/**
	 * A flat convex shape with some thickness: the polygon (xs, ys) lies in the pose's right/up plane,
	 * listed counter-clockwise, and is extruded by {@code halfThickness} along the pose's forward axis.
	 * Faces turned away from the camera are skipped.
	 */
	public void prism(Pose3 pose, float[] xs, float[] ys, float halfThickness, int rgb, float alpha) {
		int n = xs.length;

		if (n < 3 || alpha <= 0.004f) {
			return;
		}

		float[] fx = new float[n];
		float[] fy = new float[n];
		float[] fz = new float[n];
		float[] bx = new float[n];
		float[] by = new float[n];
		float[] bz = new float[n];
		float ox = rx(pose.x);
		float oy = ry(pose.y);
		float oz = rz(pose.z);

		for (int i = 0; i < n; i++) {
			float px = ox + pose.rx * xs[i] + pose.ux * ys[i];
			float py = oy + pose.ry * xs[i] + pose.uy * ys[i];
			float pz = oz + pose.rz * xs[i] + pose.uz * ys[i];
			fx[i] = px + pose.fx * halfThickness;
			fy[i] = py + pose.fy * halfThickness;
			fz[i] = pz + pose.fz * halfThickness;
			bx[i] = px - pose.fx * halfThickness;
			by[i] = py - pose.fy * halfThickness;
			bz[i] = pz - pose.fz * halfThickness;
		}

		// Sides first, then front/back caps on top
		for (int i = 0; i < n; i++) {
			int j = (i + 1) % n;
			float ex = xs[j] - xs[i];
			float ey = ys[j] - ys[i];
			// Outward normal in the plane for a counter-clockwise polygon
			float nx = pose.rx * ey - pose.ux * ex;
			float ny = pose.ry * ey - pose.uy * ex;
			float nz = pose.rz * ey - pose.uz * ex;
			float cx = (fx[i] + fx[j] + bx[i] + bx[j]) / 4f;
			float cy = (fy[i] + fy[j] + by[i] + by[j]) / 4f;
			float cz = (fz[i] + fz[j] + bz[i] + bz[j]) / 4f;

			if (nx * cx + ny * cy + nz * cz < 0f) {
				float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
				int c = shaded(rgb, alpha, len < 1.0e-6f ? 0f : ny / len, 0.9f);
				quad(fx[i], fy[i], fz[i], c, fx[j], fy[j], fz[j], c, bx[j], by[j], bz[j], c, bx[i], by[i], bz[i], c);
			}
		}

		float centerX = 0;
		float centerY = 0;
		float centerZ = 0;

		for (int i = 0; i < n; i++) {
			centerX += fx[i];
			centerY += fy[i];
			centerZ += fz[i];
		}

		boolean frontVisible = pose.fx * centerX + pose.fy * centerY + pose.fz * centerZ < 0f;
		int c = shaded(rgb, alpha, frontVisible ? pose.fy : -pose.fy, 1f);

		for (int i = 1; i < n - 1; i++) {
			if (frontVisible) {
				tri(fx[0], fy[0], fz[0], c, fx[i], fy[i], fz[i], c, fx[i + 1], fy[i + 1], fz[i + 1], c);
			} else {
				tri(bx[0], by[0], bz[0], c, bx[i], by[i], bz[i], c, bx[i + 1], by[i + 1], bz[i + 1], c);
			}
		}
	}

	private static int shaded(int rgb, float alpha, float normalY, float extra) {
		float shade = (normalY >= 0 ? 0.74f + 0.26f * normalY : 0.74f + 0.2f * normalY) * extra;
		int r = Math.min(255, Math.round(((rgb >> 16) & 0xFF) * shade));
		int g = Math.min(255, Math.round(((rgb >> 8) & 0xFF) * shade));
		int b = Math.min(255, Math.round((rgb & 0xFF) * shade));
		return Colors.argb(alpha, (r << 16) | (g << 8) | b);
	}

	/** Triangle with world coordinates. */
	public void triWorld(double x0, double y0, double z0, int c0, double x1, double y1, double z1, int c1, double x2, double y2, double z2, int c2) {
		tri(rx(x0), ry(y0), rz(z0), c0, rx(x1), ry(y1), rz(z1), c1, rx(x2), ry(y2), rz(z2), c2);
	}

	/** Quad with world coordinates. */
	public void quadWorld(double x0, double y0, double z0, int c0, double x1, double y1, double z1, int c1,
			double x2, double y2, double z2, int c2, double x3, double y3, double z3, int c3) {
		quad(rx(x0), ry(y0), rz(z0), c0, rx(x1), ry(y1), rz(z1), c1, rx(x2), ry(y2), rz(z2), c2, rx(x3), ry(y3), rz(z3), c3);
	}

	// ---- pixel text ------------------------------------------------------------------------

	private static final java.util.Map<Character, String[]> GLYPHS = new java.util.HashMap<>();

	static {
		GLYPHS.put('0', new String[] {"111", "101", "101", "101", "111"});
		GLYPHS.put('1', new String[] {"010", "110", "010", "010", "111"});
		GLYPHS.put('2', new String[] {"111", "001", "111", "100", "111"});
		GLYPHS.put('3', new String[] {"111", "001", "111", "001", "111"});
		GLYPHS.put('4', new String[] {"101", "101", "111", "001", "001"});
		GLYPHS.put('5', new String[] {"111", "100", "111", "001", "111"});
		GLYPHS.put('6', new String[] {"111", "100", "111", "101", "111"});
		GLYPHS.put('7', new String[] {"111", "001", "010", "010", "010"});
		GLYPHS.put('8', new String[] {"111", "101", "111", "101", "111"});
		GLYPHS.put('9', new String[] {"111", "101", "111", "001", "111"});
		GLYPHS.put('.', new String[] {"0", "0", "0", "0", "1"});
		GLYPHS.put('-', new String[] {"000", "000", "111", "000", "000"});
		GLYPHS.put('+', new String[] {"000", "010", "111", "010", "000"});
		GLYPHS.put('!', new String[] {"1", "1", "1", "0", "1"});
		GLYPHS.put('h', new String[] {"01010", "11111", "11111", "01110", "00100"});
		GLYPHS.put(' ', new String[] {"0", "0", "0", "0", "0"});
	}

	/** Width in pixels of text drawn with {@link #pixelText}. Use 'h' for a heart. */
	public static int pixelWidth(String text) {
		int width = 0;

		for (int i = 0; i < text.length(); i++) {
			String[] glyph = GLYPHS.get(text.charAt(i));
			width += (glyph == null ? 3 : glyph[0].length()) + (i < text.length() - 1 ? 1 : 0);
		}

		return width;
	}

	/**
	 * Draws text with a chunky 3x5 pixel font that always faces the camera, centered on the
	 * world position. {@code pixel} is the size of one font pixel in blocks.
	 */
	public void pixelText(double wx, double wy, double wz, String text, float pixel, int rgb, float alpha, boolean outline) {
		if (alpha <= 0.004f || text.isEmpty()) {
			return;
		}

		float cx = rx(wx);
		float cy = ry(wy);
		float cz = rz(wz);
		basis(cx, cy, cz);
		float rx = rightX;
		float ry = rightY;
		float rz = rightZ;
		float ux = upX;
		float uy = upY;
		float uz = upZ;
		float startX = -pixelWidth(text) / 2f;
		int fill = Colors.argb(alpha, rgb);
		int shadow = Colors.argb(alpha * 0.85f, Colors.shade(rgb, -0.8f));

		for (int pass = outline ? 0 : 1; pass < 2; pass++) {
			// The outline is drawn first and slightly bigger; the fill sits a hair closer to the camera
			float half = pass == 0 ? pixel * 0.85f : pixel * 0.5f;
			float pull = pass == 0 ? 1f : 0.996f;
			int color = pass == 0 ? shadow : fill;
			float cursor = startX;

			for (int i = 0; i < text.length(); i++) {
				String[] glyph = GLYPHS.get(text.charAt(i));

				if (glyph == null) {
					cursor += 4;
					continue;
				}

				for (int row = 0; row < glyph.length; row++) {
					for (int col = 0; col < glyph[row].length(); col++) {
						if (glyph[row].charAt(col) != '1') {
							continue;
						}

						float ox = (cursor + col + 0.5f) * pixel;
						float oy = (2f - row) * pixel;
						float qx = (cx + rx * ox + ux * oy) * pull;
						float qy = (cy + ry * ox + uy * oy) * pull;
						float qz = (cz + rz * ox + uz * oy) * pull;
						quad(qx - rx * half - ux * half, qy - ry * half - uy * half, qz - rz * half - uz * half, color,
								qx + rx * half - ux * half, qy + ry * half - uy * half, qz + rz * half - uz * half, color,
								qx + rx * half + ux * half, qy + ry * half + uy * half, qz + rz * half + uz * half, color,
								qx - rx * half + ux * half, qy - ry * half + uy * half, qz - rz * half + uz * half, color);
					}
				}

				cursor += glyph[0].length() + 1;
			}
		}
	}

	/** Camera-facing right/up vectors at a world position, for shapes built in screen space. */
	public float[] billboardBasis(double wx, double wy, double wz) {
		basis(rx(wx), ry(wy), rz(wz));
		return new float[] {rightX, rightY, rightZ, upX, upY, upZ};
	}
}
