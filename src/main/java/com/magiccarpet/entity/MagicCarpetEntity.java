package com.magiccarpet.entity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.LinearInterpolationHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.magiccarpet.MagicCarpet;

/**
 * The rideable carpet.
 *
 * <p>While a player pilots it, the pilot's game simulates the flight (like boats and the happy ghast) and the
 * server checks the result. The server handles everything else: durability, parking, drifting down, and the
 * Loyalty catch and call.
 */
public class MagicCarpetEntity extends VehicleEntity {
	private static final EntityDataAccessor<ItemStack> DATA_CARPET_ITEM =
			SynchedEntityData.defineId(MagicCarpetEntity.class, EntityDataSerializers.ITEM_STACK);

	public static final int DISMOUNT_HOLD_TICKS = 20;
	public static final float WIDTH = 1.75F;
	public static final float HEIGHT = 0.1875F;

	private static final int TICKS_PER_DURABILITY_MOVING = 20;   // 1 point per second of flight
	private static final int HOVER_DRAIN_DIVISOR = 3;            // hovering drains a third as fast
	private static final int CRASH_DURABILITY = 5;

	/** What the carpet does when nobody is flying it. */
	public enum State {
		PILOTED, PARKED, DRIFTING, RESTING, CATCHING, RETURNING
	}

	// --- Server state ---
	private State state = State.RESTING;
	private UUID owner;
	private UUID chaseTarget;
	private int chaseTicks;
	private Vec3 chaseLastPos;
	private double drainProgress;
	private double lastServerX = Double.NaN, lastServerY, lastServerZ;
	private double lastServerSpeed;
	private int crashCooldown;
	private final Map<UUID, Integer> shiftHeld = new HashMap<>();

	// --- Pilot (client) state ---
	public final CarpetPhysics physics = new CarpetPhysics();
	private boolean wasPiloted;
	private boolean inForward, inBack, inLeft, inRight, inUp, inDown;
	private int clientCrashCooldown;

	public MagicCarpetEntity(EntityType<? extends MagicCarpetEntity> type, Level level) {
		super(type, level);
	}

	// ------------------------------------------------------------------
	// Data
	// ------------------------------------------------------------------

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_CARPET_ITEM, ItemStack.EMPTY);
	}

	public ItemStack getCarpetStack() {
		return this.entityData.get(DATA_CARPET_ITEM);
	}

	public void setCarpetStack(ItemStack stack) {
		this.entityData.set(DATA_CARPET_ITEM, stack.copyWithCount(1));
	}

	public void setOwner(UUID owner) {
		this.owner = owner;
	}

	public boolean isOwnedBy(Player player) {
		return owner != null && owner.equals(player.getUUID());
	}

	public State getState() {
		return state;
	}

	/** True once the carpet is down to its last durability point. */
	public boolean isFrayed() {
		ItemStack stack = getCarpetStack();
		return !stack.isEmpty() && stack.isDamageableItem() && stack.getDamageValue() >= stack.getMaxDamage() - 1;
	}

	public int getLoyaltyLevel() {
		ItemEnchantments enchantments = getCarpetStack().getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
		for (Holder<net.minecraft.world.item.enchantment.Enchantment> holder : enchantments.keySet()) {
			if (holder.is(Enchantments.LOYALTY)) {
				return enchantments.getLevel(holder);
			}
		}

		return 0;
	}

	/** How far a fall Loyalty will catch: 20 / 40 / any. */
	public static double catchHeightFor(int loyalty) {
		return loyalty >= 3 ? Double.MAX_VALUE : 20.0 * loyalty;
	}

	/** How far away a called carpet can be: 32 / 64 / 96. */
	public static double callRangeFor(int loyalty) {
		return 32.0 * loyalty;
	}

	@Override
	protected void addAdditionalSaveData(ValueOutput output) {
		ItemStack stack = getCarpetStack();
		if (!stack.isEmpty()) {
			output.store("Carpet", ItemStack.CODEC, stack);
		}

		if (owner != null) {
			output.store("Owner", UUIDUtil.CODEC, owner);
		}

		State saved = switch (state) {
			case PILOTED, CATCHING, RETURNING -> onGround() ? State.RESTING : State.PARKED;
			default -> state;
		};
		output.putString("State", saved.name());
	}

	@Override
	protected void readAdditionalSaveData(ValueInput input) {
		input.read("Carpet", ItemStack.CODEC).ifPresent(this::setCarpetStack);
		input.read("Owner", UUIDUtil.CODEC).ifPresent(id -> this.owner = id);
		try {
			this.state = State.valueOf(input.getStringOr("State", State.RESTING.name()));
		} catch (IllegalArgumentException e) {
			this.state = State.RESTING;
		}
	}

	@Override
	protected Item getDropItem() {
		return MagicCarpet.MAGIC_CARPET_ITEM;
	}

	@Override
	public ItemStack getPickResult() {
		ItemStack stack = getCarpetStack();
		return stack.isEmpty() ? new ItemStack(MagicCarpet.MAGIC_CARPET_ITEM) : stack.copy();
	}

	/** Breaking the carpet drops the exact item back, with its color, durability and enchantments. */
	@Override
	protected void destroy(ServerLevel level, DamageSource source) {
		ItemStack drop = getCarpetStack().isEmpty() ? new ItemStack(MagicCarpet.MAGIC_CARPET_ITEM) : getCarpetStack().copy();
		this.discard();
		this.spawnAtLocation(level, drop);
	}

	// ------------------------------------------------------------------
	// Riding
	// ------------------------------------------------------------------

	@Override
	protected InterpolationHandler createInterpolationHandler() {
		return LinearInterpolationHandler.create(this, 3);
	}

	@Override
	public LivingEntity getControllingPassenger() {
		Entity first = this.getFirstPassenger();
		return first instanceof Player player ? player : null;
	}

	@Override
	protected boolean canAddPassenger(Entity passenger) {
		return this.getPassengers().size() < 2;
	}

	@Override
	protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float scale) {
		List<Entity> passengers = this.getPassengers();
		double forward = 0.0;
		if (passengers.size() > 1) {
			forward = passengers.indexOf(passenger) == 0 ? 0.35 : -0.55;
		}

		return new Vec3(0.0, HEIGHT - 0.05, forward).yRot(-this.getYRot() * ((float) Math.PI / 180F));
	}

	@Override
	public InteractionResult interact(Player player, InteractionHand hand, Vec3 location) {
		if (player.isShiftKeyDown()) {
			// Sneak + use picks the carpet up (but not out from under you while it's parked in the air).
			if (!this.getPassengers().isEmpty() || (isStandingOn(player) && !isNearGround())) {
				return InteractionResult.PASS;
			}

			if (this.level() instanceof ServerLevel serverLevel) {
				ItemStack stack = getPickResult();
				if (!player.getInventory().add(stack)) {
					player.spawnAtLocation(serverLevel, stack);
				}

				playSound("item.armor.equip_leather", 1.0F, 1.0F);
				this.discard();
			}

			return InteractionResult.SUCCESS;
		}

		if (!this.canAddPassenger(player) || player.isPassenger()) {
			return InteractionResult.PASS;
		}

		if (!this.level().isClientSide()) {
			return player.startRiding(this) ? InteractionResult.SUCCESS : InteractionResult.PASS;
		}

		return InteractionResult.SUCCESS;
	}

	@Override
	protected void removePassenger(Entity passenger) {
		super.removePassenger(passenger);

		if (this.level().isClientSide() || !this.getPassengers().isEmpty()) {
			return;
		}

		Integer held = shiftHeld.remove(passenger.getUUID());
		boolean intentional = (held != null && held >= DISMOUNT_HOLD_TICKS) || onGround() || isNearGround();

		if (onGround()) {
			state = State.RESTING;
		} else if (intentional) {
			state = State.PARKED;
		} else if (passenger instanceof Player player && player.isAlive() && !player.isRemoved()
				&& this.distanceTo(player) < 8.0 && getLoyaltyLevel() > 0 && fallHeightBelow(player) <= catchHeightFor(getLoyaltyLevel())) {
			state = State.CATCHING;
			chaseTarget = player.getUUID();
			chaseTicks = 0;
			chaseLastPos = player.position();
		} else {
			state = State.DRIFTING;
		}
	}

	/** Called by the dismount mixin: mid-air, Shift must be held for a second. */
	public boolean allowsDismount(Player player) {
		if (onGround() || isNearGround()) {
			return true;
		}

		return shiftHeld.getOrDefault(player.getUUID(), 0) >= DISMOUNT_HOLD_TICKS;
	}

	private boolean isStandingOn(Player player) {
		double dx = player.getX() - this.getX();
		double dz = player.getZ() - this.getZ();
		return Math.abs(player.getY() - (this.getY() + HEIGHT)) < 0.2 && dx * dx + dz * dz < 1.2 * 1.2;
	}

	private boolean isNearGround() {
		AABB below = this.getBoundingBox().move(0.0, -1.0, 0.0);
		return !this.level().noCollision(this, below);
	}

	@Override
	public boolean canBeCollidedWith(Entity other) {
		return true;
	}

	@Override
	public boolean isPickable() {
		return !this.isRemoved();
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean causeFallDamage(double fallDistance, float damageModifier, DamageSource damageSource) {
		return false;
	}

	@Override
	protected void checkFallDamage(double ya, boolean onGround, BlockState onState, BlockPos pos) {
		this.resetFallDistance();
	}

	// ------------------------------------------------------------------
	// Ticking
	// ------------------------------------------------------------------

	/** Set every tick by the pilot's game from their movement keys. */
	public void setPilotInput(boolean forward, boolean back, boolean left, boolean right, boolean up, boolean down) {
		this.inForward = forward;
		this.inBack = back;
		this.inLeft = left;
		this.inRight = right;
		this.inUp = up;
		this.inDown = down;
	}

	@Override
	public void tick() {
		if (this.getHurtTime() > 0) {
			this.setHurtTime(this.getHurtTime() - 1);
		}

		if (this.getDamage() > 0.0F) {
			this.setDamage(this.getDamage() - 1.0F);
		}

		super.tick();
		this.setNoGravity(true);

		LivingEntity pilot = this.getControllingPassenger();

		if (this.isLocalInstanceAuthoritative()) {
			if (pilot instanceof Player player && this.level().isClientSide()) {
				pilotTick(player);
			} else if (!this.level().isClientSide()) {
				unpilotedServerTick();
			} else {
				this.setDeltaMovement(Vec3.ZERO);
			}
		} else {
			this.getInterpolation().interpolate();
			wasPiloted = false;
		}

		if (!this.level().isClientSide()) {
			serverBookkeeping(pilot);
		} else {
			clientEffects();
		}
	}

	/** Runs in the pilot's game: turns key presses into movement. */
	private void pilotTick(Player pilot) {
		Vec3 look = pilot.getViewVector(1.0F);

		if (!wasPiloted) {
			physics.reset(look.x, look.y, look.z);
			wasPiloted = true;
		}

		boolean airborne = !this.onGround();
		physics.step(inForward, inBack, inLeft, inRight, inUp, inDown, look.x, look.y, look.z, isFrayed(), airborne);

		Vec3 velocity = new Vec3(physics.vx, physics.vy, physics.vz);
		this.setDeltaMovement(velocity);
		this.move(MoverType.SELF, velocity);

		if (clientCrashCooldown > 0) {
			clientCrashCooldown--;
		}

		boolean crashed = physics.onCollision(this.horizontalCollision, this.verticalCollision, velocity.y);
		if (crashed && clientCrashCooldown == 0) {
			clientCrashCooldown = 20;
			this.level().playLocalSound(this.getX(), this.getY(), this.getZ(), sound("block.wool.break"), SoundSource.NEUTRAL, 1.0F, 0.8F, false);
		}

		// Face the direction of travel.
		float targetYaw = physics.headingYaw(pilot.getYRot());
		float yaw = this.getYRot();
		float diff = wrapDegrees(targetYaw - yaw);
		this.setYRot(yaw + diff * 0.35F);
	}

	private void unpilotedServerTick() {
		wasPiloted = false;

		switch (state) {
			case PILOTED -> state = onGround() ? State.RESTING : State.PARKED;
			case PARKED -> {
				this.setDeltaMovement(Vec3.ZERO);
				if (onGround()) {
					state = State.RESTING;
				} else {
					watchForFallingOwner();
				}
			}
			case RESTING -> {
				this.setDeltaMovement(Vec3.ZERO);
				if (!onGround() && !isNearGround()) state = State.DRIFTING;
			}
			case DRIFTING -> {
				double vy = this.isInWater() ? 0.04 : -0.1;
				Vec3 v = new Vec3(0.0, vy, 0.0);
				this.setDeltaMovement(v);
				this.move(MoverType.SELF, v);
				if (onGround()) state = State.RESTING;
			}
			case CATCHING -> catchTick();
			case RETURNING -> returnTick();
		}
	}

	/** A parked carpet with Loyalty dives after its owner if they slip off and fall. */
	private void watchForFallingOwner() {
		int loyalty = getLoyaltyLevel();
		if (loyalty <= 0 || owner == null || this.tickCount % 2 != 0) {
			return;
		}

		Player player = this.level().getPlayerByUUID(owner);
		if (player == null || !player.isAlive() || player.isPassenger() || player.onGround() || player.isFallFlying()
				|| player.getAbilities().flying || player.isInWater()) {
			return;
		}

		double dx = player.getX() - this.getX();
		double dz = player.getZ() - this.getZ();
		boolean nearby = dx * dx + dz * dz < 12.0 * 12.0 && player.getY() < this.getY() && this.getY() - player.getY() < 24.0;
		double groundBelow = fallHeightBelow(player);

		// A real fall (not a hop down to a ledge): already dropping, and a long way still to go.
		if (nearby && player.fallDistance > 3.0 && groundBelow > 5.0 && groundBelow + player.fallDistance <= catchHeightFor(loyalty)) {
			state = State.CATCHING;
			chaseTarget = player.getUUID();
			chaseTicks = 0;
			chaseLastPos = player.position();
		}
	}

	private void catchTick() {
		Player target = chaseTarget == null ? null : this.level().getPlayerByUUID(chaseTarget);
		if (target == null || !target.isAlive() || target.isPassenger() || target.onGround() || ++chaseTicks > 300) {
			state = State.DRIFTING;
			return;
		}

		Vec3 now = target.position();
		Vec3 motion = chaseLastPos == null ? Vec3.ZERO : now.subtract(chaseLastPos);
		chaseLastPos = now;

		Vec3 aim = now.add(motion.scale(2.0)).add(0.0, -0.4, 0.0);
		Vec3 delta = aim.subtract(this.position());
		double distance = delta.length();

		if (distance < 1.8) {
			this.setPos(now.x, now.y - 0.3, now.z);
			if (target.startRiding(this, true, true)) {
				target.resetFallDistance();
				state = State.PILOTED;
				playSound("item.trident.return", 1.0F, 1.2F);
				if (target instanceof ServerPlayer serverPlayer) {
					serverPlayer.sendOverlayMessage(Component.translatable("message.magiccarpet.caught").withStyle(ChatFormatting.AQUA));
				}
			} else {
				state = State.DRIFTING;
			}

			return;
		}

		Vec3 step = delta.normalize().scale(Math.min(distance, 4.5));
		this.setPos(this.getX() + step.x, this.getY() + step.y, this.getZ() + step.z);
		faceTowards(step);
	}

	/** Starts flying back to its owner (the C key). */
	public void startReturning(Player player) {
		this.owner = player.getUUID();
		this.chaseTarget = player.getUUID();
		this.chaseTicks = 0;
		this.state = State.RETURNING;
		playSound("block.amethyst_block.chime", 1.0F, 1.2F);
	}

	private void returnTick() {
		Player target = chaseTarget == null ? null : this.level().getPlayerByUUID(chaseTarget);
		if (target == null || target.isPassenger() || ++chaseTicks > 600) {
			state = onGround() ? State.RESTING : State.PARKED;
			return;
		}

		Vec3 lookFlat = new Vec3(target.getLookAngle().x, 0.0, target.getLookAngle().z);
		if (lookFlat.lengthSqr() < 1.0E-4) {
			lookFlat = new Vec3(0.0, 0.0, 1.0);
		}

		Vec3 aim = target.position().add(lookFlat.normalize().scale(2.2)).add(0.0, 0.1, 0.0);
		Vec3 delta = aim.subtract(this.position());
		double distance = delta.length();

		if (distance < 0.5) {
			this.setPos(aim.x, aim.y, aim.z);
			for (int i = 0; i < 6 && !this.level().noCollision(this); i++) {
				this.setPos(this.getX(), this.getY() + 0.5, this.getZ());
			}

			this.setYRot(target.getYRot());
			state = State.PARKED;
			playSound("block.amethyst_block.chime", 1.0F, 1.6F);
			return;
		}

		Vec3 step = delta.normalize().scale(Math.min(distance, 1.4));
		this.setPos(this.getX() + step.x, this.getY() + step.y, this.getZ() + step.z);
		faceTowards(step);
	}

	private void serverBookkeeping(LivingEntity pilot) {
		if (crashCooldown > 0) {
			crashCooldown--;
		}

		// Track how long each rider has held Shift (for the mid-air dismount hold).
		for (Entity passenger : this.getPassengers()) {
			passenger.resetFallDistance();
			if (passenger instanceof Player player && player.isShiftKeyDown()) {
				shiftHeld.merge(player.getUUID(), 1, Integer::sum);
			} else {
				shiftHeld.remove(passenger.getUUID());
			}
		}

		double speedNow = 0.0;
		if (!Double.isNaN(lastServerX)) {
			double dx = this.getX() - lastServerX;
			double dy = this.getY() - lastServerY;
			double dz = this.getZ() - lastServerZ;
			speedNow = Math.sqrt(dx * dx + dy * dy + dz * dz);
		}

		if (pilot instanceof Player player) {
			state = State.PILOTED;
			owner = player.getUUID();

			if (!player.hasInfiniteMaterials() && !this.onGround()) {
				drainProgress += speedNow > 0.05 ? 1.0 : 1.0 / HOVER_DRAIN_DIVISOR;
				while (drainProgress >= TICKS_PER_DURABILITY_MOVING) {
					drainProgress -= TICKS_PER_DURABILITY_MOVING;
					wearOut(1, player);
				}
			}

			// A sudden stop from high speed means the pilot flew into something.
			if (lastServerSpeed > 0.75 && speedNow < lastServerSpeed * 0.35 && crashCooldown == 0) {
				crashCooldown = 20;
				if (!player.hasInfiniteMaterials()) {
					wearOut(CRASH_DURABILITY, player);
				}

				this.level().playSound(player, this.getX(), this.getY(), this.getZ(), sound("block.wool.break"), SoundSource.NEUTRAL, 1.0F, 0.8F);
			}
		}

		lastServerSpeed = pilot != null ? speedNow : 0.0;
		lastServerX = this.getX();
		lastServerY = this.getY();
		lastServerZ = this.getZ();
	}

	private void wearOut(int amount, Player pilot) {
		ItemStack stack = getCarpetStack().copy();
		if (stack.isEmpty() || !stack.isDamageableItem()) {
			return;
		}

		boolean wasFrayed = isFrayed();
		int applied = this.level() instanceof ServerLevel serverLevel
				? EnchantmentHelper.processDurabilityChange(serverLevel, stack, amount)
				: amount;

		if (applied <= 0) {
			return;
		}

		int newDamage = Math.min(stack.getMaxDamage() - 1, stack.getDamageValue() + applied);
		if (newDamage == stack.getDamageValue()) {
			return;
		}

		stack.setDamageValue(newDamage);
		setCarpetStack(stack);

		if (!wasFrayed && isFrayed()) {
			playSound("entity.item.break", 0.8F, 0.6F);
			for (Entity passenger : this.getPassengers()) {
				if (passenger instanceof ServerPlayer serverPlayer) {
					serverPlayer.sendOverlayMessage(Component.translatable("message.magiccarpet.frayed").withStyle(ChatFormatting.RED));
				}
			}
		}
	}

	private void clientEffects() {
		double dx = this.getX() - this.xo;
		double dz = this.getZ() - this.zo;
		double speed = Math.sqrt(dx * dx + dz * dz);

		if (speed > 0.9 && this.tickCount % 3 == 0) {
			double side = (this.random.nextBoolean() ? 1 : -1) * 0.7;
			double yawRad = Math.toRadians(this.getYRot());
			double bx = this.getX() + Math.sin(yawRad) * 1.0 + Math.cos(yawRad) * side;
			double bz = this.getZ() - Math.cos(yawRad) * 1.0 + Math.sin(yawRad) * side;
			this.level().addParticle(ParticleTypes.END_ROD, bx, this.getY() + 0.1, bz, 0.0, 0.0, 0.0);
		}
	}

	// ------------------------------------------------------------------
	// Helpers
	// ------------------------------------------------------------------

	/** Distance from the player's feet down to the first solid block or liquid below them. */
	private double fallHeightBelow(Player player) {
		BlockPos.MutableBlockPos pos = player.blockPosition().mutable();
		int minY = this.level().getMinY();
		int start = pos.getY();

		while (pos.getY() > minY) {
			BlockState blockState = this.level().getBlockState(pos);
			if (!blockState.getCollisionShape(this.level(), pos).isEmpty() || !blockState.getFluidState().isEmpty()) {
				return player.getY() - (pos.getY() + 1);
			}

			pos.move(0, -1, 0);
			if (start - pos.getY() > 400) {
				break;
			}
		}

		return Double.MAX_VALUE;
	}

	private void faceTowards(Vec3 direction) {
		if (direction.x * direction.x + direction.z * direction.z > 1.0E-4) {
			this.setYRot((float) Math.toDegrees(Math.atan2(-direction.x, direction.z)));
		}
	}

	private void playSound(String id, float volume, float pitch) {
		this.level().playSound(null, this.getX(), this.getY(), this.getZ(), sound(id), SoundSource.NEUTRAL, volume, pitch);
	}

	static SoundEvent sound(String id) {
		return SoundEvent.createVariableRangeEvent(Identifier.withDefaultNamespace(id));
	}

	private static float wrapDegrees(float degrees) {
		float d = degrees % 360.0F;
		if (d >= 180.0F) d -= 360.0F;
		if (d < -180.0F) d += 360.0F;
		return d;
	}

	public static Optional<MagicCarpetEntity> ridden(Entity entity) {
		return entity != null && entity.getVehicle() instanceof MagicCarpetEntity carpet ? Optional.of(carpet) : Optional.empty();
	}
}
