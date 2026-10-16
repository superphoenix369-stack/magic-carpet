package com.magiccarpet.entity;

/**
 * The flight model, kept free of Minecraft classes so it can be reasoned about (and tested) on its own.
 * Units are blocks per tick (20 ticks = 1 second).
 *
 * <ul>
 *   <li>Forward is the throttle: speed builds from a crawl to {@link #MAX_SPEED} (30 blocks/s) over about 6–7 s.</li>
 *   <li>Letting go coasts: speed fades by about half every 9 s.</li>
 *   <li>Back is the brake: a full stop from top speed in about 1.3 s; keep holding to drift backwards.</li>
 *   <li>The carpet turns toward where the pilot looks, but its heading lags. Sharp turns cost a lot of speed,
 *       gentle curves very little (the loss grows with the square of the turn rate).</li>
 *   <li>Diving gains speed (up to {@link #DIVE_MAX_SPEED}), climbing costs some.</li>
 *   <li>Jump/descend move straight up/down; strafing only works at low speed.</li>
 * </ul>
 */
public final class CarpetPhysics {
	public static final double MAX_SPEED = 1.5;          // 30 blocks/s, about elytra-with-rockets
	public static final double DIVE_MAX_SPEED = 1.9;     // 38 blocks/s in a dive
	public static final double REVERSE_SPEED = 0.15;     // 3 blocks/s
	public static final double FRAYED_MAX_SPEED = 0.25;

	static final double THROTTLE_BASE = 0.006;
	static final double THROTTLE_LOW_SPEED_BONUS = 0.014;
	static final double COAST_FACTOR = 0.9965;
	static final double COAST_LINEAR = 0.001;
	static final double BRAKE = 0.06;
	static final double REVERSE_ACCEL = 0.01;
	static final double OVERSPEED_DRAG = 0.02;
	static final double DIVE_GAIN = 0.022;
	static final double CLIMB_COST = 0.012;

	static final double TURN_RATE_SLOW_DEG = 20.0;
	static final double TURN_RATE_FAST_DEG = 4.5;
	static final double TURN_LOSS = 3.5;

	static final double STRAFE_MAX = 0.15;
	static final double LIFT_MAX = 0.32;                 // 6.4 blocks/s straight up or down
	static final double FRAYED_SINK = 0.08;

	public static final double CRASH_SPEED = 0.45;

	/** Signed speed along the heading. Negative means drifting backwards. */
	public double speed;
	/** Unit vector the carpet is travelling along. */
	public double hx, hy, hz = 1.0;
	public double strafe;
	public double lift;

	/** Velocity for this tick, filled in by {@link #step}. */
	public double vx, vy, vz;

	public void reset(double lookX, double lookY, double lookZ) {
		speed = 0;
		strafe = 0;
		lift = 0;
		setHeading(lookX, lookY, lookZ);
	}

	public void setHeading(double x, double y, double z) {
		double len = Math.sqrt(x * x + y * y + z * z);
		if (len < 1.0E-6) {
			hx = 0; hy = 0; hz = 1;
		} else {
			hx = x / len; hy = y / len; hz = z / len;
		}
	}

	/**
	 * Advances one tick.
	 *
	 * @param look the pilot's look direction (any length)
	 * @param frayed the carpet is worn out: slow, and it can only sink
	 * @param airborne false while resting on the ground
	 */
	public void step(boolean forward, boolean back, boolean left, boolean right, boolean up, boolean down,
			double lookX, double lookY, double lookZ, boolean frayed, boolean airborne) {
		// --- Steering: turn the heading toward the look direction, limited by speed. ---
		double lx = lookX, ly = lookY, lz = lookZ;
		double llen = Math.sqrt(lx * lx + ly * ly + lz * lz);
		if (llen > 1.0E-6) {
			lx /= llen; ly /= llen; lz /= llen;
			double speedFraction = clamp(Math.abs(speed) / MAX_SPEED, 0, 1);
			double maxTurn = Math.toRadians(lerp(speedFraction, TURN_RATE_SLOW_DEG, TURN_RATE_FAST_DEG));
			double turned = rotateHeadingToward(lx, ly, lz, maxTurn);

			if (speed > 0) {
				speed *= Math.max(0.0, 1.0 - TURN_LOSS * turned * turned);
			}
		}

		// --- Throttle, coasting, brake and reverse. ---
		double cap = frayed ? FRAYED_MAX_SPEED : MAX_SPEED;

		if (forward && !back) {
			if (speed < 0) {
				speed = Math.min(0, speed + BRAKE);
			} else if (speed < cap) {
				double accel = THROTTLE_BASE + THROTTLE_LOW_SPEED_BONUS * (1.0 - speed / MAX_SPEED);
				speed = Math.min(cap, speed + accel);
			}
		} else if (back && !forward) {
			if (speed > 0) {
				speed = Math.max(0, speed - BRAKE);
			} else {
				speed = Math.max(-REVERSE_SPEED, speed - REVERSE_ACCEL);
			}
		} else {
			if (speed > 0) {
				speed = speed * COAST_FACTOR - COAST_LINEAR;
				if (speed < 0.01) speed = 0;
			} else if (speed < 0) {
				speed *= 0.85;
				if (speed > -0.005) speed = 0;
			}
		}

		// --- Dive and climb. ---
		if (speed > 0.05 && !frayed) {
			if (hy < -0.05) {
				speed += -hy * DIVE_GAIN;
			} else if (hy > 0.05) {
				speed -= hy * CLIMB_COST;
				if (speed < 0) speed = 0;
			}
		}

		if (speed > MAX_SPEED) {
			speed -= (speed - MAX_SPEED) * OVERSPEED_DRAG;
		}

		speed = Math.min(speed, frayed ? FRAYED_MAX_SPEED : DIVE_MAX_SPEED);

		// --- Strafe (only useful when slow) and straight up/down. ---
		double strafeInput = (right ? 1 : 0) - (left ? 1 : 0);
		double strafeTarget = strafeInput * STRAFE_MAX * (1.0 - clamp(Math.abs(speed) / MAX_SPEED, 0, 1));
		strafe += (strafeTarget - strafe) * 0.25;

		double liftTarget;
		if (frayed) {
			liftTarget = down ? -LIFT_MAX : (airborne ? -FRAYED_SINK : 0);
		} else {
			liftTarget = ((up ? 1 : 0) - (down ? 1 : 0)) * LIFT_MAX;
		}

		lift += (liftTarget - lift) * 0.2;
		if (Math.abs(lift) < 0.002 && liftTarget == 0) lift = 0;

		// --- Combine. Right-hand vector from the heading's horizontal direction. ---
		double fx = hx, fz = hz;
		double flen = Math.sqrt(fx * fx + fz * fz);
		double rx = 0, rz = 0;
		if (flen > 1.0E-4) {
			rx = -fz / flen;
			rz = fx / flen;
		}

		vx = hx * speed + rx * strafe;
		vy = hy * speed + lift;
		vz = hz * speed + rz * strafe;
	}

	/**
	 * Reacts to bumping into something after moving.
	 *
	 * @return true if the impact was hard enough to count as a crash
	 */
	public boolean onCollision(boolean horizontal, boolean vertical, double attemptedVy) {
		boolean crash = false;

		if (horizontal) {
			if (Math.abs(speed) > CRASH_SPEED) {
				crash = true;
				speed *= 0.3;
			} else {
				speed *= 0.7;
			}
		}

		if (vertical && attemptedVy < -0.4 && speed > CRASH_SPEED) {
			crash = true;
			speed *= 0.3;
		}

		return crash;
	}

	/** Rotates the heading toward a unit vector by at most {@code maxAngle} radians; returns the angle actually turned. */
	double rotateHeadingToward(double lx, double ly, double lz, double maxAngle) {
		double dot = clamp(hx * lx + hy * ly + hz * lz, -1, 1);
		double angle = Math.acos(dot);

		if (angle < 1.0E-5) {
			return 0;
		}

		if (angle <= maxAngle) {
			hx = lx; hy = ly; hz = lz;
			return angle;
		}

		if (angle > Math.PI - 1.0E-3) {
			// Looking straight back: nudge the target sideways so the turn has a direction.
			lx += -hz * 0.01;
			lz += hx * 0.01;
			double len = Math.sqrt(lx * lx + ly * ly + lz * lz);
			lx /= len; ly /= len; lz /= len;
			dot = clamp(hx * lx + hy * ly + hz * lz, -1, 1);
			angle = Math.acos(dot);
		}

		double t = maxAngle / angle;
		double sinA = Math.sin(angle);
		double a = Math.sin((1 - t) * angle) / sinA;
		double b = Math.sin(t * angle) / sinA;
		setHeading(hx * a + lx * b, hy * a + ly * b, hz * a + lz * b);
		return maxAngle;
	}

	/** Horizontal yaw (Minecraft degrees) of the heading, or {@code fallback} when heading straight up/down. */
	public float headingYaw(float fallback) {
		if (hx * hx + hz * hz < 1.0E-4) {
			return fallback;
		}

		return (float) Math.toDegrees(Math.atan2(-hx, hz));
	}

	public double velocityLength() {
		return Math.sqrt(vx * vx + vy * vy + vz * vz);
	}

	static double clamp(double v, double lo, double hi) {
		return v < lo ? lo : (v > hi ? hi : v);
	}

	static double lerp(double t, double a, double b) {
		return a + (b - a) * t;
	}
}
