package dev.juiced.combat;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import dev.juiced.core.ModuleManager;

/**
 * Turns what the server already sends every client into combat events:
 * landed hits, crits, kills, totem pops, jumps and your current target.
 * Nothing is sent to the server and no input is automated.
 */
public final class CombatEvents {
	private static final int HIT_WINDOW_TICKS = 6;
	private static final int KILL_CREDIT_TICKS = 60;
	private static final int TARGET_TIMEOUT_TICKS = 160;
	private static final double TRACK_RANGE_SQR = 64 * 64;
	private static final double JUMP_RANGE_SQR = 32 * 32;

	private static final Map<Integer, Integer> LAST_HURT_TIME = new HashMap<>();
	private static final Map<Integer, Swing> PENDING_SWINGS = new HashMap<>();
	private static final Map<Integer, Long> RECENTLY_HIT = new HashMap<>();
	private static final Map<Integer, Integer> TOTEM_POPS = new HashMap<>();
	private static final Map<Integer, AirState> AIR = new HashMap<>();
	private static final Map<Integer, Float> LAST_HEALTH = new HashMap<>();
	private static final Map<Integer, Boolean> LAST_HIT_CRIT = new HashMap<>();
	private static final Set<Integer> KILLED = new HashSet<>();
	private static final Set<Integer> SEEN = new HashSet<>();

	/** Set each tick by modules that want jump events for other players too. */
	public static boolean trackOtherJumps;

	private static long tick;
	private static @Nullable LivingEntity target;
	private static long targetTick;
	private static long lastHitTick = -1000;
	private static int lastHitEntity = -1;
	private static boolean wasDead;
	private static float lastReach = -1f;
	private static long lastReachTick = -1000;
	private static boolean lastHitSprint;

	private CombatEvents() {
	}

	// ---- public state ---------------------------------------------------------------------

	/** The entity you're fighting: the last one you hit, or the last one that hit you. */
	public static @Nullable LivingEntity target() {
		return target;
	}

	/** Ticks since you last landed a hit on this entity (large if never). */
	public static long ticksSinceHit(Entity entity) {
		return entity.getId() == lastHitEntity ? tick - lastHitTick : Long.MAX_VALUE;
	}

	public static int totemPops(Entity entity) {
		return TOTEM_POPS.getOrDefault(entity.getId(), 0);
	}

	/** Distance of your last landed hit in blocks (eye to hitbox), or -1. */
	public static float lastReach() {
		return lastReach;
	}

	/** Ticks since your last landed hit. */
	public static long ticksSinceLastHit() {
		return tick - lastReachTick;
	}

	/**
	 * True when your last landed hit was a sprint hit at full strength, which vanilla rewards with extra
	 * knockback. Read it inside {@code Module.onHit}; it describes your client at the moment you swung.
	 */
	public static boolean lastHitSprint() {
		return lastHitSprint;
	}

	// ---- hooks ----------------------------------------------------------------------------

	/** Fabric's AttackEntityCallback; fires on your client right before the attack is sent. */
	public static InteractionResult onAttack(Player player, Level level, InteractionHand hand, Entity entity, @Nullable EntityHitResult hitResult) {
		if (!level.isClientSide() || player != Minecraft.getInstance().player || !(entity instanceof LivingEntity)) {
			return InteractionResult.PASS;
		}

		boolean fullStrength = player.getAttackStrengthScale(0.5F) > 0.9F;
		// Sampled here because vanilla's attack clears sprinting right after a sprint hit
		boolean sprintHit = fullStrength && player.isSprinting();
		PENDING_SWINGS.put(entity.getId(), new Swing(tick, fullStrength && canCrit(player, entity), sprintHit, reach(player, entity)));
		return InteractionResult.PASS;
	}

	/** Called from LivingEntityMixin for every entity event the server sends (35 = totem used). */
	public static void onEntityEvent(LivingEntity entity, byte event) {
		if (event != 35 || Minecraft.getInstance().level == null) {
			return;
		}

		int pops = TOTEM_POPS.merge(entity.getId(), 1, Integer::sum);
		ModuleManager.totemPop(entity, pops);
	}

	public static void tick(Minecraft minecraft) {
		tick++;
		LocalPlayer player = minecraft.player;
		ClientLevel level = minecraft.level;

		if (player == null || level == null) {
			reset();
			return;
		}

		SEEN.clear();

		for (Entity entity : level.entitiesForRendering()) {
			if (!(entity instanceof LivingEntity living)) {
				continue;
			}

			boolean self = entity == player;
			double distanceSq = entity.position().distanceToSqr(player.position());

			if (!self && distanceSq > TRACK_RANGE_SQR) {
				continue;
			}

			int id = entity.getId();
			SEEN.add(id);

			Integer previousHurtTime = LAST_HURT_TIME.put(id, living.hurtTime);
			boolean justHurt = previousHurtTime != null && living.hurtTime > previousHurtTime;

			if (self) {
				if (justHurt && !living.isDeadOrDying()) {
					onSelfHurt(player);
				}

				boolean dead = living.isDeadOrDying();

				if (dead && !wasDead) {
					ModuleManager.death();
				}

				wasDead = dead;
				trackHealth(living, id);
				trackAir(entity, true);
				continue;
			}

			if (justHurt) {
				Swing swing = PENDING_SWINGS.remove(id);

				if (swing != null && tick - swing.tick <= HIT_WINDOW_TICKS) {
					RECENTLY_HIT.put(id, tick);
					LAST_HIT_CRIT.put(id, swing.crit);
					lastHitTick = tick;
					lastHitEntity = id;
					lastReach = swing.reach;
					lastReachTick = tick;
					lastHitSprint = swing.sprint;
					setTarget(living);
					ModuleManager.hit(living, swing.crit);
				}
			}

			trackHealth(living, id);

			Long hitAt = RECENTLY_HIT.get(id);

			if (hitAt != null && tick - hitAt <= KILL_CREDIT_TICKS && living.isDeadOrDying() && KILLED.add(id)) {
				ModuleManager.kill(living);
			}

			if (living.isDeadOrDying()) {
				TOTEM_POPS.remove(id);
			}

			if (trackOtherJumps && entity instanceof Player && distanceSq < JUMP_RANGE_SQR) {
				trackAir(entity, false);
			}
		}

		LAST_HURT_TIME.keySet().retainAll(SEEN);
		KILLED.retainAll(SEEN);
		AIR.keySet().retainAll(SEEN);
		TOTEM_POPS.keySet().retainAll(SEEN);
		LAST_HEALTH.keySet().retainAll(SEEN);
		LAST_HIT_CRIT.keySet().retainAll(RECENTLY_HIT.keySet());
		PENDING_SWINGS.values().removeIf(swing -> tick - swing.tick > HIT_WINDOW_TICKS);
		RECENTLY_HIT.values().removeIf(hitTick -> tick - hitTick > KILL_CREDIT_TICKS);
		updateTarget(player);
	}

	// ---- internals ------------------------------------------------------------------------

	/** Fires health change events when an entity's health plus absorption changes. */
	private static void trackHealth(LivingEntity living, int id) {
		float now = living.getHealth() + living.getAbsorptionAmount();
		Float before = LAST_HEALTH.put(id, now);

		if (before == null || Math.abs(before - now) < 0.05f) {
			return;
		}

		Long hitAt = RECENTLY_HIT.get(id);
		boolean byMe = hitAt != null && tick - hitAt <= 10;
		boolean crit = byMe && LAST_HIT_CRIT.getOrDefault(id, false);
		ModuleManager.healthChange(living, before - now, byMe, crit);
	}

	/** Distance from the player's eyes to the closest point of the target's hitbox. */
	private static float reach(Player player, Entity target) {
		Vec3 eye = player.getEyePosition();
		AABB box = target.getBoundingBox();
		double dx = Math.max(Math.max(box.minX - eye.x, 0), eye.x - box.maxX);
		double dy = Math.max(Math.max(box.minY - eye.y, 0), eye.y - box.maxY);
		double dz = Math.max(Math.max(box.minZ - eye.z, 0), eye.z - box.maxZ);
		return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	private static void onSelfHurt(LocalPlayer player) {
		DamageSource source = player.getLastDamageSource();

		if (source != null && source.getEntity() instanceof LivingEntity attacker && attacker != player) {
			setTarget(attacker);
		}
	}

	private static void setTarget(LivingEntity entity) {
		target = entity;
		targetTick = tick;
	}

	private static void updateTarget(LocalPlayer player) {
		if (target == null) {
			return;
		}

		boolean gone = target.isRemoved()
				|| tick - targetTick > TARGET_TIMEOUT_TICKS
				|| target.position().distanceToSqr(player.position()) > 48 * 48
				|| (target.isDeadOrDying() && target.deathTime > 15);

		if (gone) {
			target = null;
		}
	}

	private static void trackAir(Entity entity, boolean self) {
		AirState state = AIR.computeIfAbsent(entity.getId(), id -> new AirState());
		boolean onGround = entity.onGround();
		double y = entity.getY();

		if (state.initialized) {
			double rising = self ? entity.getDeltaMovement().y : y - entity.yOld;

			if (state.onGround && !onGround && rising > (self ? 0.2 : 0.05)) {
				ModuleManager.jump(entity);
			}

			if (!state.onGround && onGround) {
				ModuleManager.land(entity, (float) Math.max(0, state.peakY - y));
			}
		}

		if (onGround) {
			state.peakY = y;
		} else {
			state.peakY = Math.max(state.peakY, y);
		}

		state.onGround = onGround;
		state.initialized = true;
	}

	private static void reset() {
		LAST_HURT_TIME.clear();
		PENDING_SWINGS.clear();
		RECENTLY_HIT.clear();
		TOTEM_POPS.clear();
		AIR.clear();
		KILLED.clear();
		LAST_HEALTH.clear();
		LAST_HIT_CRIT.clear();
		target = null;
		wasDead = false;
	}

	/** Same rules vanilla uses for a critical hit. */
	private static boolean canCrit(Player player, Entity target) {
		return player.fallDistance > 0
				&& !player.onGround()
				&& !player.onClimbable()
				&& !player.isInWater()
				&& !player.hasEffect(MobEffects.BLINDNESS)
				&& !player.isPassenger()
				&& !player.isSprinting()
				&& target instanceof LivingEntity;
	}

	private record Swing(long tick, boolean crit, boolean sprint, float reach) {
	}

	private static final class AirState {
		boolean initialized;
		boolean onGround = true;
		double peakY;
	}
}
