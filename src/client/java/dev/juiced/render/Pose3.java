package dev.juiced.render;

/**
 * A position plus its own right/up/forward axes in the world, for building small 3D models
 * (ears, blades, pets) out of boxes. Rotations are in degrees around the pose's own axes.
 */
public final class Pose3 {
	public double x;
	public double y;
	public double z;
	// right
	public float rx = 1;
	public float ry;
	public float rz;
	// up
	public float ux;
	public float uy = 1;
	public float uz;
	// forward
	public float fx;
	public float fy;
	public float fz = 1;

	/** Pose at a world position, facing the Minecraft yaw (0 = south, 90 = west). */
	public static Pose3 at(double x, double y, double z, float yawDegrees) {
		Pose3 pose = new Pose3();
		pose.x = x;
		pose.y = y;
		pose.z = z;
		double yaw = Math.toRadians(yawDegrees);
		float sin = (float) Math.sin(yaw);
		float cos = (float) Math.cos(yaw);
		pose.fx = -sin;
		pose.fy = 0;
		pose.fz = cos;
		pose.rx = -cos;
		pose.ry = 0;
		pose.rz = -sin;
		pose.ux = 0;
		pose.uy = 1;
		pose.uz = 0;
		return pose;
	}

	public Pose3 copy() {
		Pose3 pose = new Pose3();
		pose.x = x;
		pose.y = y;
		pose.z = z;
		pose.rx = rx;
		pose.ry = ry;
		pose.rz = rz;
		pose.ux = ux;
		pose.uy = uy;
		pose.uz = uz;
		pose.fx = fx;
		pose.fy = fy;
		pose.fz = fz;
		return pose;
	}

	/** Moves along the pose's own axes. */
	public Pose3 move(double right, double up, double forward) {
		x += rx * right + ux * up + fx * forward;
		y += ry * right + uy * up + fy * forward;
		z += rz * right + uz * up + fz * forward;
		return this;
	}

	/** Turns around the up axis; positive turns to the right. */
	public Pose3 yaw(float degrees) {
		double a = Math.toRadians(degrees);
		float c = (float) Math.cos(a);
		float s = (float) Math.sin(a);
		float nfx = fx * c + rx * s;
		float nfy = fy * c + ry * s;
		float nfz = fz * c + rz * s;
		float nrx = rx * c - fx * s;
		float nry = ry * c - fy * s;
		float nrz = rz * c - fz * s;
		fx = nfx;
		fy = nfy;
		fz = nfz;
		rx = nrx;
		ry = nry;
		rz = nrz;
		return this;
	}

	/** Tilts around the right axis; positive tips the front down. */
	public Pose3 pitch(float degrees) {
		double a = Math.toRadians(degrees);
		float c = (float) Math.cos(a);
		float s = (float) Math.sin(a);
		float nfx = fx * c - ux * s;
		float nfy = fy * c - uy * s;
		float nfz = fz * c - uz * s;
		float nux = ux * c + fx * s;
		float nuy = uy * c + fy * s;
		float nuz = uz * c + fz * s;
		fx = nfx;
		fy = nfy;
		fz = nfz;
		ux = nux;
		uy = nuy;
		uz = nuz;
		return this;
	}

	/** Rolls around the forward axis; positive tips the top to the right. */
	public Pose3 roll(float degrees) {
		double a = Math.toRadians(degrees);
		float c = (float) Math.cos(a);
		float s = (float) Math.sin(a);
		float nux = ux * c + rx * s;
		float nuy = uy * c + ry * s;
		float nuz = uz * c + rz * s;
		float nrx = rx * c - ux * s;
		float nry = ry * c - uy * s;
		float nrz = rz * c - uz * s;
		ux = nux;
		uy = nuy;
		uz = nuz;
		rx = nrx;
		ry = nry;
		rz = nrz;
		return this;
	}

	/** World x of a point given in the pose's own coordinates. */
	public double wx(double right, double up, double forward) {
		return x + rx * right + ux * up + fx * forward;
	}

	public double wy(double right, double up, double forward) {
		return y + ry * right + uy * up + fy * forward;
	}

	public double wz(double right, double up, double forward) {
		return z + rz * right + uz * up + fz * forward;
	}
}
