package dev.juiced.gui.weltprobe;

import java.util.concurrent.ThreadLocalRandom;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

import dev.juiced.combat.CombatEvents;
import dev.juiced.core.Clock;
import dev.juiced.render.Fx;
import dev.juiced.render.Sparks;

/**
 * The straw dummy the Kampf channels hit: an armor stand with a hay bale for a head, standing next to you on the
 * stage. It only exists on your client (a negative entity id the server never uses) and combat tracking ignores it,
 * so nothing reaches the server or other modules; the menu calls the tuned module's hooks with it directly.
 *
 * <p>Its moves (rising out of the ground, the knockback hop and the slide home) are played inside the entity's own
 * tick, right after the game stored the previous position, so everything that draws it or follows it (the knockback
 * comet) sees a smooth path. Its turn and the hit wobble are set every frame. Its health never changes.
 *
 * <p>Taking it away waits while effects still follow it: until a knockback flight has landed, and for 0.8 s after a
 * totem pop (the totem ring sits on the entity). It also takes itself away as soon as no screen is open, so it can
 * never stay behind in the world when the menu goes without cleaning up.
 */
public final class StrawDummy {
	private static final int BASE_ID = -742_000;
	private static final int[] STRAW = { 0xD9B35B, 0xE8D7A8, 0xB98A3E, 0x8A5A2B };
	private static final int RISE_TICKS = 5;
	private static final int REGROW_RISE_TICKS = 4;
	private static final int REGROW_DELAY_TICKS = 16;
	private static final int LANDED_TICKS = 20;
	private static final int RETURN_TICKS = 12;
	/** How long the dummy has to stay after a totem pop, because the totem ring follows it. */
	private static final int POP_HOLD_TICKS = 16;
	/** A knockback flight shorter than this isn't worth showing. */
	private static final double MIN_FLIGHT = 0.8;
	private static final double MAX_FLIGHT = 2.6;

	private enum Phase {
		RISE,
		IDLE,
		FLY,
		LANDED,
		RETURN
	}

	private final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();

	private @Nullable Stand stand;
	private @Nullable ClientLevel level;
	private int serial;

	/** A spot was found the last time the dummy was placed. */
	private boolean hasHome;
	/** The world that spot is in. */
	private @Nullable ClientLevel homeWorld;
	private double homeX;
	private double homeY;
	private double homeZ;
	private float faceYaw;
	/** Direction and free length of the knockback flight. */
	private double knockX;
	private double knockZ;
	private double knockRoom;

	private Phase phase = Phase.IDLE;
	private int phaseTick;
	private int riseTicks = RISE_TICKS;
	private double flight;
	private int flightTicks;
	private double hop;
	private double landX;
	private double landZ;
	/** Ticks until the dummy grows back after a kill, or 0. */
	private int regrow;
	/** Ticks until a dummy that was asked to go actually goes (see {@link #remove}), or 0. */
	private int leaving;
	private boolean leavingPuff;
	private int ticksSincePop = POP_HOLD_TICKS;

	// Hit wobble: a damped spring on the yaw, in degrees
	private float wobble;
	private float wobbleSpeed;
	private int wobbleSign = 1;

	// Notices when the game stops ticking the entity, so it still moves
	private int seenTickCount = -1;
	private int stalled;

	/**
	 * Finds a spot and puts the dummy there: 1.8 blocks to the left of the stage camera's view of you and 0.4 towards
	 * it, else the same on the right, else 1.6 in front of you. With {@code inFront} (the stage camera has no room,
	 * so the menu shows your own view) the spot in front comes first. Returns false if there is no room anywhere.
	 */
	public boolean place(float stageYaw, float viewYaw, boolean inFront) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		ClientLevel world = minecraft.level;

		if (leaving > 0 && stand != null && level == world) {
			// Asked to go a moment ago and wanted again: it simply stays
			leaving = 0;
			return true;
		}

		regrow = 0;
		discard();
		hasHome = false;

		if (player == null || world == null) {
			return false;
		}

		double rad = Math.toRadians(stageYaw);
		// The stage camera's left on screen, and the way back towards it
		double leftX = Math.cos(rad);
		double leftZ = Math.sin(rad);
		double backX = Math.sin(rad);
		double backZ = -Math.cos(rad);
		double viewRad = Math.toRadians(viewYaw);
		double frontX = -Math.sin(viewRad);
		double frontZ = Math.cos(viewRad);
		double[][] spots = {
				{ leftX * 1.8 + backX * 0.4, leftZ * 1.8 + backZ * 0.4 },
				{ -leftX * 1.8 + backX * 0.4, -leftZ * 1.8 + backZ * 0.4 },
				{ frontX * 1.6, frontZ * 1.6 } };

		if (inFront) {
			spots = new double[][] { { frontX * 2.2, frontZ * 2.2 }, spots[2], spots[0], spots[1] };
		}

		for (double[] spot : spots) {
			double x = player.getX() + spot[0];
			double z = player.getZ() + spot[1];
			double ground = ground(world, x, z, player.getY(), 2);

			if (!Double.isNaN(ground)) {
				homeX = Math.floor(x) + 0.5;
				homeY = ground;
				homeZ = Math.floor(z) + 0.5;
				hasHome = true;
				break;
			}
		}

		if (!hasHome) {
			return false;
		}

		homeWorld = world;
		faceYaw = (float) Math.toDegrees(Math.atan2(-(player.getX() - homeX), player.getZ() - homeZ));
		measureKnockback(world, player, stageYaw);
		spawn(world, RISE_TICKS);
		return true;
	}

	/** True while the dummy stands in the world (and isn't about to go). */
	/** True if the dummy's spot is more than a few blocks away from where you are now (a teleport, a respawn). */
	public boolean leftBehind(Entity player) {
		double dx = player.getX() - homeX;
		double dz = player.getZ() - homeZ;
		return dx * dx + dz * dz > 6 * 6 || Math.abs(player.getY() - homeY) > 3;
	}

	public boolean placed() {
		return stand != null && leaving == 0;
	}

	/** True while the dummy is away after a kill and will grow back by itself. */
	public boolean regrowing() {
		return regrow > 0;
	}

	/** True if the last placement found a spot (also while the dummy grows back after a kill). */
	public boolean hasRoom() {
		return hasHome;
	}

	/** True if there is enough level ground behind the dummy to fly back from a hit. */
	public boolean canFly() {
		return hasHome && knockRoom >= MIN_FLIGHT;
	}

	/** The dummy entity, also while it is about to go (so the combat probe state can be cleared for it). */
	public @Nullable LivingEntity entity() {
		return stand;
	}

	/** Standing at its spot and ready for the next hit. */
	public boolean ready() {
		return placed() && phase == Phase.IDLE;
	}

	/** A totem popped on the dummy: it stays for the ring that follows it, even if it is taken away now. */
	public void totemPopped() {
		ticksSincePop = 0;
	}

	/** A hit: the dummy wobbles, flashes red and makes the armor stand's own hit sound. */
	public void hit(boolean crit) {
		Stand target = stand;

		if (target == null) {
			return;
		}

		wobbleSign = -wobbleSign;
		// About 14 degrees at the first swing, a little more on a crit
		wobbleSpeed += wobbleSign * (crit ? 470f : 390f);
		target.handleEntityEvent((byte) 32);
		target.animateHurt(0f);
	}

	/** The dummy hops away from you (far for a sprint hit), lands, waits a second and slides back to its spot. */
	public void knock(boolean sprint) {
		if (!ready() || !canFly()) {
			return;
		}

		flight = Math.min(sprint ? MAX_FLIGHT : 1.2, knockRoom);
		flightTicks = sprint ? 9 : 6;
		hop = sprint ? 0.45 : 0.3;
		phase = Phase.FLY;
		phaseTick = 0;
	}

	/** A lethal hit: the dummy bursts into straw and grows back after 0.8 s. */
	public void kill() {
		Stand target = stand;

		if (target == null || !placed()) {
			return;
		}

		puff(target.getX(), target.getY(), target.getZ(), true);
		discard();
		regrow = REGROW_DELAY_TICKS;
	}

	/**
	 * Takes the dummy away, with a straw puff if {@code puff}. While a knockback flight or a totem ring still
	 * follows it, it goes a moment later by itself.
	 */
	public void remove(boolean puff) {
		Stand target = stand;
		regrow = 0;

		if (target == null) {
			return;
		}

		int hold = holdTicks();

		if (hold > 0 && !target.isRemoved() && level == Minecraft.getInstance().level) {
			leaving = Math.max(leaving, hold);
			leavingPuff |= puff;
			return;
		}

		if (puff) {
			puff(target.getX(), target.getY(), target.getZ(), false);
		}

		discard();
	}

	/** Every client tick while the menu is open (before the game ticks the entities). */
	public void tick() {
		Minecraft minecraft = Minecraft.getInstance();

		if (homeWorld != null && minecraft.level != homeWorld) {
			// Another world: the old one took the entity with it, and the spot is gone too
			forget();
			hasHome = false;
			homeWorld = null;
			return;
		}

		if (regrow > 0 && --regrow == 0 && minecraft.level != null && hasHome) {
			puff(homeX, homeY, homeZ, false);
			spawn(minecraft.level, REGROW_RISE_TICKS);
		}

		Stand target = stand;

		if (target == null) {
			return;
		}

		if (target.isRemoved()) {
			forget();
			return;
		}

		// The game ticks client-made entities like any other; if it ever doesn't, move it from here
		if (target.tickCount == seenTickCount) {
			if (++stalled >= 2) {
				step(target);
			}
		} else {
			stalled = 0;
		}

		seenTickCount = target.tickCount;
	}

	/** Every frame: the hit wobble and the turn towards you. */
	public void frame() {
		Stand target = stand;
		float dt = Math.min(0.1f, Clock.dt());

		// Small steps keep the stiff spring stable at any frame rate
		for (float left = dt; left > 0f; left -= 0.005f) {
			float h = Math.min(0.005f, left);
			// About 3 swings a second, calm again after 0.6 s
			wobbleSpeed += (-390f * wobble - 9.5f * wobbleSpeed) * h;
			wobble += wobbleSpeed * h;
		}

		if (Math.abs(wobble) < 0.01f && Math.abs(wobbleSpeed) < 0.1f) {
			wobble = 0f;
			wobbleSpeed = 0f;
		}

		if (target != null) {
			turn(target, faceYaw + wobble);
		}
	}

	// ---- placing -------------------------------------------------------------------------------

	/**
	 * Height the dummy's feet would stand at in the column (x, z), searching {@code range} blocks up and down from
	 * {@code nearY}, or NaN if there is no solid ground with two free blocks above it (and no water at the feet).
	 */
	private double ground(ClientLevel world, double x, double z, double nearY, int range) {
		int bx = (int) Math.floor(x);
		int bz = (int) Math.floor(z);
		int base = (int) Math.floor(nearY);

		for (int y = base + range; y >= base - range; y--) {
			probe.set(bx, y, bz);
			BlockState state = world.getBlockState(probe);
			VoxelShape shape = state.getCollisionShape(world, probe);

			if (shape.isEmpty()) {
				continue;
			}

			double surface = y + shape.max(Direction.Axis.Y);

			if (surface > nearY + range + 0.5) {
				continue;
			}

			return free(world, bx, y, bz, surface) ? surface : Double.NaN;
		}

		return Double.NaN;
	}

	/** Two blocks of air (no collision, no liquid) above a surface. */
	private boolean free(ClientLevel world, int bx, int groundBlock, int bz, double surface) {
		int top = (int) Math.floor(surface + 1.9);

		for (int y = (int) Math.floor(surface + 0.01); y <= top; y++) {
			if (y == groundBlock) {
				// A slab or a path block: its own upper part is free
				continue;
			}

			probe.set(bx, y, bz);
			BlockState state = world.getBlockState(probe);

			if (state.liquid() || !state.getCollisionShape(world, probe).isEmpty()) {
				return false;
			}
		}

		return true;
	}

	/**
	 * The flight goes away from you and a little away from the stage camera, so it stays in the picture. It needs
	 * level ground with room above; the flight is cut to what is free.
	 */
	private void measureKnockback(ClientLevel world, LocalPlayer player, float stageYaw) {
		double awayX = homeX - player.getX();
		double awayZ = homeZ - player.getZ();
		double length = Math.sqrt(awayX * awayX + awayZ * awayZ);

		if (length < 1.0e-3) {
			awayX = 1;
			awayZ = 0;
			length = 1;
		}

		double rad = Math.toRadians(stageYaw);
		double x = awayX / length - Math.sin(rad) * 0.7;
		double z = awayZ / length + Math.cos(rad) * 0.7;
		double norm = Math.sqrt(x * x + z * z);
		knockX = norm < 1.0e-3 ? awayX / length : x / norm;
		knockZ = norm < 1.0e-3 ? awayZ / length : z / norm;
		knockRoom = 0;

		for (double d = 0.25; d <= MAX_FLIGHT + 0.3; d += 0.25) {
			double ground = ground(world, homeX + knockX * d, homeZ + knockZ * d, homeY, 1);

			if (Double.isNaN(ground) || Math.abs(ground - homeY) > 0.07) {
				break;
			}

			knockRoom = Math.max(0, d - 0.3);
		}
	}

	private void spawn(ClientLevel world, int rise) {
		Stand created = new Stand(world, this);
		int id = freeId(world);
		created.setId(id);
		created.setNoGravity(true);
		created.setShowArms(true);
		created.setNoBasePlate(true);
		created.setCustomName(Component.literal("Strohpuppe"));
		created.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.HAY_BLOCK));
		double startY = homeY - 1.6;
		created.snapTo(homeX, startY, homeZ, faceYaw, 0f);
		turn(created, faceYaw);
		created.setOnGround(true);

		// Never tracked: no hits, kills or health changes reach other modules through it
		CombatEvents.ignore(id, true);
		world.addEntity(created);
		stand = created;
		level = world;
		phase = Phase.RISE;
		phaseTick = 0;
		riseTicks = rise;
		seenTickCount = -1;
		stalled = 0;
		wobble = 0f;
		wobbleSpeed = 0f;
	}

	private int freeId(ClientLevel world) {
		for (int i = 0; i < 64; i++) {
			int id = BASE_ID - (serial++ & 63);

			if (world.getEntity(id) == null) {
				return id;
			}
		}

		return BASE_ID - 64 - ThreadLocalRandom.current().nextInt(1 << 20);
	}

	private void discard() {
		Stand target = stand;
		ClientLevel world = level;

		if (target != null) {
			// A world that was left took the entity with it
			if (world != null && world == Minecraft.getInstance().level && !target.isRemoved()) {
				world.removeEntity(target.getId(), Entity.RemovalReason.DISCARDED);
			}

			CombatEvents.ignore(target.getId(), false);
		}

		stand = null;
		level = null;
		phase = Phase.IDLE;
		leaving = 0;
		leavingPuff = false;
	}

	/** Drops the reference without touching the world (it is gone already). */
	private void forget() {
		Stand target = stand;

		if (target != null) {
			CombatEvents.ignore(target.getId(), false);
		}

		stand = null;
		level = null;
		regrow = 0;
		phase = Phase.IDLE;
		leaving = 0;
		leavingPuff = false;
	}

	/** Ticks the dummy still has to stay for the effects that follow it. */
	private int holdTicks() {
		int hold = Math.max(0, POP_HOLD_TICKS - ticksSincePop);

		if (phase == Phase.FLY) {
			// Land first; the comet must never lose its target in the air
			hold = Math.max(hold, flightTicks - phaseTick + 2);
		}

		return hold;
	}

	// ---- moving --------------------------------------------------------------------------------

	/** One tick of the current move. Runs inside the entity's tick, after the game kept the old position. */
	private void step(Stand target) {
		if (target != stand) {
			// Not ours any more (never expected): don't leave it standing around
			target.discard();
			return;
		}

		if (ticksSincePop < POP_HOLD_TICKS) {
			ticksSincePop++;
		}

		if (leaving == 0 && Minecraft.getInstance().gui.screen() == null) {
			// The menu went without taking the dummy away
			remove(true);
		}

		if (leaving > 0 && --leaving == 0) {
			if (leavingPuff) {
				puff(target.getX(), target.getY(), target.getZ(), false);
			}

			discard();
			return;
		}

		if (stand != target) {
			return;
		}

		double x = homeX;
		double y = homeY;
		double z = homeZ;
		boolean onGround = true;

		switch (phase) {
			case RISE -> {
				phaseTick++;
				float t = Math.min(1f, phaseTick / (float) riseTicks);
				float inv = 1f - t;
				y = homeY - 1.6 * inv * inv * inv;

				if (t >= 1f) {
					phase = Phase.IDLE;
				}
			}
			case IDLE -> {
			}
			case FLY -> {
				phaseTick++;
				double t = Math.min(1.0, phaseTick / (double) flightTicks);
				double along = flight * (1 - (1 - t) * (1 - t));
				x = homeX + knockX * along;
				z = homeZ + knockZ * along;
				y = homeY + hop * 4 * t * (1 - t);
				onGround = t >= 1.0;

				if (onGround) {
					landX = x;
					landZ = z;
					phase = Phase.LANDED;
					phaseTick = 0;
				}
			}
			case LANDED -> {
				x = landX;
				z = landZ;

				if (++phaseTick >= LANDED_TICKS) {
					phase = Phase.RETURN;
					phaseTick = 0;
				}
			}
			case RETURN -> {
				phaseTick++;
				double t = Math.min(1.0, phaseTick / (double) RETURN_TICKS);
				double e = t * t * (3 - 2 * t);
				x = landX + (homeX - landX) * e;
				z = landZ + (homeZ - landZ) * e;

				if (t >= 1.0) {
					phase = Phase.IDLE;
				}
			}
		}

		target.setPos(x, y, z);
		target.setOnGround(onGround);
		target.setDeltaMovement(0, 0, 0);
	}

	/** Body, head and the old values all the same, so the drawn turn is exactly this. */
	private static void turn(Stand target, float yaw) {
		target.setYRot(yaw);
		target.yRotO = yaw;
		target.yBodyRot = yaw;
		target.yBodyRotO = yaw;
		target.yHeadRot = yaw;
		target.yHeadRotO = yaw;
	}

	/** A burst of straw where the dummy stands (bigger when it bursts from a kill). */
	private static void puff(double x, double y, double z, boolean burst) {
		Sparks.burst(x, y + 1.0, z, burst ? 30 : 16, burst ? 3.2f : 2.0f, 0.07f, 0.7f,
				t -> STRAW[Math.min(STRAW.length - 1, (int) (t * STRAW.length))], Sparks.DOT, 3.5f, 0.25f);
		Fx.particles(ParticleTypes.POOF, x, y + 0.6, z, burst ? 6 : 3, 0.3, 0.03);
		Fx.sound("block.grass.break", burst ? 0.5f : 0.3f, burst ? 0.8f : 1f);
	}

	/** The armor stand, moved by the dummy from inside its own tick. Not pickable, so your crosshair never lands on it. */
	private static final class Stand extends ArmorStand {
		private final StrawDummy owner;

		Stand(Level level, StrawDummy owner) {
			// The position constructor picks the armor stand entity type itself
			super(level, 0, 0, 0);
			this.owner = owner;
		}

		@Override
		public void tick() {
			super.tick();

			if (owner != null) {
				owner.step(this);
			}
		}

		@Override
		public boolean isPickable() {
			return false;
		}
	}
}
