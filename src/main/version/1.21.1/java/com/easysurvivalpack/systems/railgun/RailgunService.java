package com.easysurvivalpack.systems.railgun;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.easysurvivalpack.display.StatusDisplayService;
import com.easysurvivalpack.mixin.EntityInvulnerabilityAccessor;

public final class RailgunService {
	private static final String RAILGUN_MARKER = "easy_survival_pack:railgun_bow";
	private static final String RAILGUN_ARROW_TAG = "easy_survival_pack:railgun_arrow";
	private static final String PREPARED_ARROW_TAG = "easy_survival_pack:railgun_arrow_prepared";
	private static final String IMPACT_PROCESSED_TAG = "easy_survival_pack:railgun_impact_processed";
	private static final double MAX_CRAFTING_RADIUS = 2.0;
	private static final double MAX_CRAFTING_DISTANCE_SQUARED = MAX_CRAFTING_RADIUS * MAX_CRAFTING_RADIUS;
	private static final int MAX_INGREDIENT_AGE_DIFFERENCE_TICKS = 5 * 20;
	private static final double PROJECTILE_SPEED = 20.0;
	private static final double AIM_ASSIST_RADIUS = 1.5;
	private static final double EXPLOSION_RADIUS = 9.0;
	private static final double EXPLOSION_OFFSET = 2.25;
	private static final float EXPLOSION_DAMAGE_MULTIPLIER = 0.8F;
	private static final int BOW_DRAW_TICKS = 20;
	private static final int CHARGE_DURATION_TICKS = 2 * 20;
	private static final float MAX_CHARGE_DAMAGE_MULTIPLIER = 5.0F;
	private static final Map<UUID, Integer> CHARGING_TICKS = new HashMap<>();
	private static final List<Vec3> EXPLOSION_OFFSETS = List.of(
			Vec3.ZERO,
			new Vec3(-EXPLOSION_OFFSET, 0.0, 0.0),
			new Vec3(EXPLOSION_OFFSET, 0.0, 0.0),
			new Vec3(0.0, -EXPLOSION_OFFSET, 0.0),
			new Vec3(0.0, EXPLOSION_OFFSET, 0.0),
			new Vec3(0.0, 0.0, -EXPLOSION_OFFSET),
			new Vec3(0.0, 0.0, EXPLOSION_OFFSET)
	);

	private RailgunService() {
	}

	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof AbstractArrow arrow) {
				prepareProjectile(arrow, level);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(RailgunService::tickCharging);
	}

	public static boolean isRailgunBow(ItemStack stack) {
		return stack.is(Items.BOW)
				&& stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
						.copyTag()
						.getBoolean(RAILGUN_MARKER);
	}

	public static void markProjectile(AbstractArrow arrow, ItemStack weapon, LivingEntity shooter) {
		if (isRailgunBow(weapon)) {
			arrow.addTag(RAILGUN_ARROW_TAG);
			arrow.setBaseDamage(
					arrow.getBaseDamage() * MAX_CHARGE_DAMAGE_MULTIPLIER * getChargeProgress(shooter)
			);
		}
	}

	public static void tryCraft(ItemEntity bowEntity) {
		if (bowEntity.level().isClientSide()
				|| bowEntity.isRemoved()
				|| !bowEntity.getItem().is(Items.BOW)
				|| !(bowEntity.getOwner() instanceof ServerPlayer owner)
				|| isRailgunBow(bowEntity.getItem())) {
			return;
		}

		List<ItemEntity> nearbyItems = bowEntity.level().getEntitiesOfClass(
				ItemEntity.class,
				bowEntity.getBoundingBox().inflate(MAX_CRAFTING_RADIUS),
				item -> !item.isRemoved()
						&& item.getOwner() != null
						&& item.getOwner().getUUID().equals(owner.getUUID())
						&& item.position().distanceToSqr(bowEntity.position()) <= MAX_CRAFTING_DISTANCE_SQUARED
						&& Math.abs(item.getAge() - bowEntity.getAge()) <= MAX_INGREDIENT_AGE_DIFFERENCE_TICKS
		);
		Map<ItemEntity, Integer> allocation = new IdentityHashMap<>();
		allocation.put(bowEntity, 1);
		if (!allocate(nearbyItems, Items.NETHER_STAR, 1, allocation)) {
			return;
		}

		ItemStack result = bowEntity.getItem().copyWithCount(1);
		CustomData.update(DataComponents.CUSTOM_DATA, result, tag -> tag.putBoolean(RAILGUN_MARKER, true));
		result.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		ItemLore currentLore = result.getOrDefault(DataComponents.LORE, ItemLore.EMPTY);
		List<Component> lore = new ArrayList<>(currentLore.lines());
		if (lore.stream().noneMatch(line -> line.getString().equals("Railgun"))) {
			lore.add(Component.literal("Railgun"));
		}
		result.set(DataComponents.LORE, new ItemLore(lore));

		ItemEntity resultEntity = new ItemEntity(
				bowEntity.level(),
				bowEntity.getX(),
				bowEntity.getY(),
				bowEntity.getZ(),
				result
		);
		resultEntity.setThrower(owner);
		if (!bowEntity.level().addFreshEntity(resultEntity)) {
			return;
		}

		for (Map.Entry<ItemEntity, Integer> entry : allocation.entrySet()) {
			ItemEntity item = entry.getKey();
			item.getItem().shrink(entry.getValue());
			if (item.getItem().isEmpty()) {
				item.discard();
			}
		}
		bowEntity.level().playSound(
				null,
				bowEntity.blockPosition(),
				SoundEvents.LIGHTNING_BOLT_IMPACT,
				SoundSource.PLAYERS,
				1.0F,
				1.0F
		);
	}

	public static void handleImpact(AbstractArrow arrow, HitResult hit) {
		if (!(arrow.level() instanceof ServerLevel level)
				|| !arrow.getTags().contains(RAILGUN_ARROW_TAG)
				|| arrow.getTags().contains(IMPACT_PROCESSED_TAG)) {
			return;
		}
		arrow.addTag(IMPACT_PROCESSED_TAG);

		Entity owner = arrow.getOwner();
		if (owner == null) {
			return;
		}

		Vec3 impact = hit.getLocation();
		double damage = arrow.getBaseDamage()
				* arrow.getDeltaMovement().length()
				* EXPLOSION_DAMAGE_MULTIPLIER;
		DamageSource damageSource = level.damageSources().explosion(owner, owner);
		Map<java.util.UUID, LivingEntity> damagedEntities = new java.util.LinkedHashMap<>();
		LivingEntity directTarget = hit instanceof EntityHitResult entityHit
				&& entityHit.getEntity() instanceof LivingEntity living
						? living
						: null;

		for (Vec3 offset : EXPLOSION_OFFSETS) {
			Vec3 center = impact.add(offset);
			level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y, center.z, 1, 0.0, 0.0, 0.0, 0.0);
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, center.x, center.y, center.z, 8, 0.25, 0.25, 0.25, 0.04);
			level.playSound(
					null,
					center.x,
					center.y,
					center.z,
					SoundEvents.GENERIC_EXPLODE,
					SoundSource.PLAYERS,
					0.7F,
					0.85F + level.getRandom().nextFloat() * 0.2F
			);

			AABB area = AABB.ofSize(center, EXPLOSION_RADIUS * 2.0, EXPLOSION_RADIUS * 2.0, EXPLOSION_RADIUS * 2.0);
			for (LivingEntity target : level.getEntitiesOfClass(
					LivingEntity.class,
					area,
					entity -> entity.isAlive()
							&& entity.position().distanceToSqr(center) <= EXPLOSION_RADIUS * EXPLOSION_RADIUS
			)) {
				damagedEntities.putIfAbsent(target.getUUID(), target);
			}
		}

		if (directTarget != null && directTarget.isAlive()) {
			damagedEntities.putIfAbsent(directTarget.getUUID(), directTarget);
		}
		for (LivingEntity target : damagedEntities.values()) {
			EntityInvulnerabilityAccessor accessor =
					(EntityInvulnerabilityAccessor) (Object) target;
			accessor.easySurvivalPack$setInvulnerableTime(0);
			float targetMultiplier = target instanceof ServerPlayer ? 1.0F : 2.0F;
			target.hurt(damageSource, (float) (damage * targetMultiplier));
			if (target == directTarget) {
				accessor.easySurvivalPack$setInvulnerableTime(0);
			}
		}

		if (directTarget != null && !(directTarget instanceof ServerPlayer)) {
			arrow.setBaseDamage(arrow.getBaseDamage() * 2.0F);
		}

		level.playSound(
				null,
				impact.x,
				impact.y,
				impact.z,
				SoundEvents.LIGHTNING_BOLT_THUNDER,
				SoundSource.PLAYERS,
				0.8F,
				1.0F
		);
	}

	private static boolean allocate(
			List<ItemEntity> items,
			Item requiredItem,
			int requiredCount,
			Map<ItemEntity, Integer> allocation
	) {
		int remaining = requiredCount;
		for (ItemEntity item : items) {
			if (!item.getItem().is(requiredItem)) {
				continue;
			}

			int alreadyAllocated = allocation.getOrDefault(item, 0);
			int available = item.getItem().getCount() - alreadyAllocated;
			int amount = Math.min(available, remaining);
			if (amount > 0) {
				allocation.put(item, alreadyAllocated + amount);
				remaining -= amount;
			}
			if (remaining == 0) {
				return true;
			}
		}
		return false;
	}

	private static void prepareProjectile(AbstractArrow arrow, ServerLevel level) {
		if (!arrow.getTags().contains(RAILGUN_ARROW_TAG)
				|| arrow.getTags().contains(PREPARED_ARROW_TAG)) {
			return;
		}

		Vec3 velocity = arrow.getDeltaMovement();
		double originalSpeed = velocity.length();
		if (originalSpeed <= 1.0E-6) {
			return;
		}

		arrow.setBaseDamage(arrow.getBaseDamage() * originalSpeed / PROJECTILE_SPEED);
		arrow.setNoGravity(true);
		Entity owner = arrow.getOwner();
		Vec3 direction = owner == null ? velocity.normalize() : owner.getLookAngle();
		arrow.setDeltaMovement(direction.normalize().scale(PROJECTILE_SPEED));
		arrow.addTag(PREPARED_ARROW_TAG);

		if (owner != null) {
			level.playSound(
					null,
					owner.blockPosition(),
					SoundEvents.LIGHTNING_BOLT_IMPACT,
					SoundSource.PLAYERS,
					1.0F,
					1.5F
			);
		}
	}

	public static void applyAimAssist(AbstractArrow arrow) {
		if (!(arrow.level() instanceof ServerLevel level)
				|| !arrow.getTags().contains(RAILGUN_ARROW_TAG)
				|| arrow.getTags().contains(IMPACT_PROCESSED_TAG)) {
			return;
		}

		Vec3 movement = arrow.getDeltaMovement();
		if (movement.lengthSqr() < 1.0E-8) {
			return;
		}
		Vec3 start = arrow.position();
		Vec3 end = start.add(movement);
		var blockHit = level.clip(new ClipContext(
				start,
				end,
				ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE,
				arrow
		));
		if (blockHit.getType() == HitResult.Type.BLOCK) {
			end = blockHit.getLocation();
		}
		if (start.distanceToSqr(end) < 1.0E-8) {
			return;
		}

		AABB searchArea = new AABB(start, end).inflate(AIM_ASSIST_RADIUS);
		Vec3 selectedAimPoint = null;
		double selectedDistance = Double.MAX_VALUE;
		for (LivingEntity target : level.getEntitiesOfClass(
				LivingEntity.class,
				searchArea,
				entity -> entity.isAlive() && !entity.isSpectator() && entity != arrow.getOwner()
		)) {
			Vec3 aimPoint = findAimPointOnTarget(target.getBoundingBox(), start, end);
			if (aimPoint == null) {
				continue;
			}
			double distance = aimPoint.distanceToSqr(start);
			if (distance < selectedDistance) {
				selectedDistance = distance;
				selectedAimPoint = aimPoint;
			}
		}

		if (selectedAimPoint != null) {
			Vec3 direction = selectedAimPoint.subtract(start);
			if (direction.lengthSqr() > 1.0E-8) {
				arrow.setDeltaMovement(direction.normalize().scale(movement.length()));
			}
		}
	}

	public static void restoreArrowPhysicsAfterImpact(AbstractArrow arrow, HitResult hit) {
		if (arrow.getTags().contains(RAILGUN_ARROW_TAG)
				&& arrow.getTags().contains(IMPACT_PROCESSED_TAG)
				&& hit.getType() == HitResult.Type.ENTITY) {
			if (hit instanceof EntityHitResult entityHit
					&& entityHit.getEntity() instanceof LivingEntity living
					&& !(living instanceof ServerPlayer)) {
				arrow.setBaseDamage(arrow.getBaseDamage() / 2.0F);
			}
			arrow.setNoGravity(false);
		}
	}

	private static void tickCharging(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			UUID playerId = player.getUUID();
			if (!player.isUsingItem() || !isRailgunBow(player.getUseItem())) {
				CHARGING_TICKS.remove(playerId);
				continue;
			}

			int ticksUsingBow = player.getTicksUsingItem();
			CHARGING_TICKS.put(playerId, ticksUsingBow);
			if (ticksUsingBow >= BOW_DRAW_TICKS) {
				int chargePercent = Math.min(100, ticksUsingBow - BOW_DRAW_TICKS);
				StatusDisplayService.publish(
						player,
						"railgun_charge",
						Component.literal("RailGun: " + chargePercent + "%")
				);
			}
		}
		CHARGING_TICKS.keySet().removeIf(playerId -> server.getPlayerList().getPlayer(playerId) == null);
	}

	private static float getChargeProgress(LivingEntity shooter) {
		int ticksUsingBow = CHARGING_TICKS.getOrDefault(shooter.getUUID(), 0);
		if (shooter.isUsingItem() && isRailgunBow(shooter.getUseItem())) {
			ticksUsingBow = shooter.getTicksUsingItem();
		}

		return Math.max(
				0.0F,
				Math.min(1.0F, (ticksUsingBow - BOW_DRAW_TICKS) / (float) CHARGE_DURATION_TICKS)
		);
	}

	private static Vec3 findAimPointOnTarget(AABB targetBounds, Vec3 start, Vec3 end) {
		Vec3 segment = end.subtract(start);
		double low = 0.0;
		double high = 1.0;
		for (int iteration = 0; iteration < 24; iteration++) {
			double first = low + (high - low) / 3.0;
			double second = high - (high - low) / 3.0;
			double firstDistance = distanceSquaredToBounds(start.add(segment.scale(first)), targetBounds);
			double secondDistance = distanceSquaredToBounds(start.add(segment.scale(second)), targetBounds);
			if (firstDistance <= secondDistance) {
				high = second;
			} else {
				low = first;
			}
		}

		Vec3 closestOnSegment = start.add(segment.scale((low + high) / 2.0));
		if (distanceSquaredToBounds(closestOnSegment, targetBounds) > AIM_ASSIST_RADIUS * AIM_ASSIST_RADIUS) {
			return null;
		}
		return closestPointOnBounds(closestOnSegment, targetBounds);
	}

	private static double distanceSquaredToBounds(Vec3 point, AABB bounds) {
		return point.distanceToSqr(closestPointOnBounds(point, bounds));
	}

	private static Vec3 closestPointOnBounds(Vec3 point, AABB bounds) {
		return new Vec3(
				Math.max(bounds.minX, Math.min(bounds.maxX, point.x)),
				Math.max(bounds.minY, Math.min(bounds.maxY, point.y)),
				Math.max(bounds.minZ, Math.min(bounds.maxZ, point.z))
		);
	}
}
