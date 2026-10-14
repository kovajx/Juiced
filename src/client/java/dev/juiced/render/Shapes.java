package dev.juiced.render;

import dev.juiced.core.Colors;

/** Small solid shapes built from triangles, with back faces skipped and simple light from above. */
public final class Shapes {
	private Shapes() {
	}

	/**
	 * A gem (octahedron) standing on its tip: four corners around the middle, a point on top and one below.
	 * {@code rotation} turns it around the vertical axis (radians).
	 */
	public static void gem(Geo geo, double x, double y, double z, float radius, float halfHeight, float rotation, int rgb, float alpha) {
		float cx = geo.rx(x);
		float cy = geo.ry(y);
		float cz = geo.rz(z);
		float[] ex = new float[4];
		float[] ez = new float[4];

		for (int k = 0; k < 4; k++) {
			double a = rotation + k * Math.PI / 2;
			ex[k] = cx + (float) Math.cos(a) * radius;
			ez[k] = cz + (float) Math.sin(a) * radius;
		}

		for (int k = 0; k < 4; k++) {
			int j = (k + 1) % 4;
			face(geo, cx, cy + halfHeight, cz, ex[j], cy, ez[j], ex[k], cy, ez[k], cx, cy, cz, rgb, alpha);
			face(geo, cx, cy - halfHeight, cz, ex[k], cy, ez[k], ex[j], cy, ez[j], cx, cy, cz, rgb, alpha);
		}
	}

	/** One triangle of a closed shape; skipped when it faces away from the camera. */
	private static void face(Geo geo, float ax, float ay, float az, float bx, float by, float bz, float qx, float qy, float qz,
			float centerX, float centerY, float centerZ, int rgb, float alpha) {
		float ux = bx - ax;
		float uy = by - ay;
		float uz = bz - az;
		float vx = qx - ax;
		float vy = qy - ay;
		float vz = qz - az;
		float nx = uy * vz - uz * vy;
		float ny = uz * vx - ux * vz;
		float nz = ux * vy - uy * vx;
		float mx = (ax + bx + qx) / 3f;
		float my = (ay + by + qy) / 3f;
		float mz = (az + bz + qz) / 3f;

		// Make the normal point outwards
		if (nx * (mx - centerX) + ny * (my - centerY) + nz * (mz - centerZ) < 0f) {
			nx = -nx;
			ny = -ny;
			nz = -nz;
		}

		if (nx * mx + ny * my + nz * mz >= 0f) {
			return;
		}

		float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
		float up = length < 1.0e-6f ? 0f : ny / length;
		float shade = up >= 0 ? 0.72f + 0.38f * up : 0.72f + 0.2f * up;
		int r = Math.min(255, Math.round(((rgb >> 16) & 0xFF) * shade));
		int g = Math.min(255, Math.round(((rgb >> 8) & 0xFF) * shade));
		int b = Math.min(255, Math.round((rgb & 0xFF) * shade));
		int top = Colors.argb(alpha, (r << 16) | (g << 8) | b);
		int light = Colors.argb(alpha, Colors.mixRgb((r << 16) | (g << 8) | b, 0xFFFFFF, 0.35f));
		geo.tri(ax, ay, az, light, bx, by, bz, top, qx, qy, qz, top);
	}
}
