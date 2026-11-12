package com.easysurvivalpack.systems.inftrident;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import com.easysurvivalpack.EasySurvivalPack;

public final class InfiniteTridentCraftingService {
	private static final int CHANNELING_TARGET_RADIUS = 4;
	private static final String INFINITE_MARKER = "easy_survival_pack:infinite_trident";
	private static final String THUNDER_MARKER = "easy_survival_pack:thunder_sky";
	private static final String THROW_CYCLE = "easy_survival_pack:thunder_sky_throw_cycle";
	private static final String STRIKE_SEQUENCE_TAG = "easy_survival_pack:thunder_sky_sequence_started";
	private static final String LIGHTNING_TAG = "easy_survival_pack:thunder_sky_lightning";
	private static final String RECOVERED_OVERFLOW_TAG = "easy_survival_pack:lightning_drop_overflow";
	private static final double MAX_RADIUS = 2.0;
	private static final double MAX_DISTANCE_SQUARED = MAX_RADIUS * MAX_RADIUS;
	private static final int MAX_DROP_AGE_DIFFERENCE_TICKS = 5 * 20;
	private static final long DROP_RECOVERY_DURATION_TICKS = 5 * 20L;
	private static final int STRIKE_INTERVAL_TICKS = 4;
	private static final Map<ServerLevel, List<LightningDropArea>> ACTIVE_DROP_RECOVERY_AREAS = new HashMap<>();
	private static final Map<ServerLevel, List<ThunderSequence>> ACTIVE_THUNDER_SEQUENCES = new HashMap<>();

	private InfiniteTridentCraftingService() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerLevel level : server.getAllLevels()) {
				processThunderSequences(level);

				List<LightningDropArea> areas = ACTIVE_DROP_RECOVERY_AREAS.get(level);
				if (areas == null) {
					continue;
				}

				Iterator<LightningDropArea> iterator = areas.iterator();
				while (iterator.hasNext()) {
					LightningDropArea area = iterator.next();
					if (area.expiryTick() < level.getGameTime()) {
						iterator.remove();
						continue;
					}

					for (ItemEntity itemEntity : level.getEntitiesOfClass(
							ItemEntity.class,
							area.fireArea(),
							InfiniteTridentCraftingService::shouldRecoverDrop
					)) {
						recoverItemIntoInventoryOrAtFeet(itemEntity, area.owner());
					}
				}
				if (areas.isEmpty()) {
					ACTIVE_DROP_RECOVERY_AREAS.remove(level);
				}
			}
		});
	}

	public static void tryCraft(ItemEntity tridentEntity) {
		if (tridentEntity.level().isClientSide()
				|| tridentEntity.isRemoved()
				|| !tridentEntity.getItem().is(Items.TRIDENT)
				|| !(tridentEntity.getOwner() instanceof ServerPlayer owner)) {
			return;
		}

		tryCraftThunderSky(tridentEntity, owner);
		tryCraftInfiniteTrident(tridentEntity, owner);
	}

	private static void tryCraftThunderSky(ItemEntity tridentEntity, ServerPlayer owner) {
		if (isThunderSkyTrident(tridentEntity.getItem())) {
			return;
		}

		List<ItemEntity> nearbyItems = tridentEntity.level().getEntitiesOfClass(
				ItemEntity.class,
				tridentEntity.getBoundingBox().inflate(MAX_RADIUS),
				item -> !item.isRemoved()
						&& item.getOwner() != null
						&& item.getOwner().getUUID().equals(owner.getUUID())
						&& item.position().distanceToSqr(tridentEntity.position()) <= MAX_DISTANCE_SQUARED
						&& Math.abs(item.getAge() - tridentEntity.getAge()) <= MAX_DROP_AGE_DIFFERENCE_TICKS
		);
		ItemEntity starEntity = nearbyItems.stream()
				.filter(item -> item != tridentEntity && item.getItem().is(Items.NETHER_STAR))
				.findFirst()
				.orElse(null);
		if (starEntity == null) {
			return;
		}

		ItemStack result = tridentEntity.getItem().copyWithCount(1);
		result.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		CustomData.update(DataComponents.CUSTOM_DATA, result, tag -> tag.putBoolean(THUNDER_MARKER, true));

		ItemEntity resultEntity = new ItemEntity(
				tridentEntity.level(),
				tridentEntity.getX(),
				tridentEntity.getY(),
				tridentEntity.getZ(),
				result
		);
		resultEntity.setThrower(owner);
		if (!tridentEntity.level().addFreshEntity(resultEntity)) {
			return;
		}

		consumeOne(tridentEntity);
		consumeOne(starEntity);
	}

	private static void tryCraftInfiniteTrident(ItemEntity tridentEntity, ServerPlayer owner) {
		if (tridentEntity.isRemoved() || isInfiniteTrident(tridentEntity.getItem())) {
			return;
		}

		List<ItemEntity> nearbyItems = tridentEntity.level().getEntitiesOfClass(
				ItemEntity.class,
				tridentEntity.getBoundingBox().inflate(MAX_RADIUS),
				item -> !item.isRemoved()
						&& item.getOwner() != null
						&& item.getOwner().getUUID().equals(owner.getUUID())
						&& item.position().distanceToSqr(tridentEntity.position()) <= MAX_DISTANCE_SQUARED
						&& Math.abs(item.getAge() - tridentEntity.getAge()) <= MAX_DROP_AGE_DIFFERENCE_TICKS
		);
		List<Consumption> ingredients = new ArrayList<>();
		if (!allocate(nearbyItems, Items.AMETHYST_SHARD, 5, tridentEntity, ingredients)
				|| !allocate(nearbyItems, Items.REDSTONE, 5, tridentEntity, ingredients)
				|| !allocate(nearbyItems, Items.GLOWSTONE_DUST, 5, tridentEntity, ingredients)) {
			return;
		}

		ItemStack result = tridentEntity.getItem().copyWithCount(1);
		result.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		CustomData.update(DataComponents.CUSTOM_DATA, result, tag -> tag.putBoolean(INFINITE_MARKER, true));

		ItemEntity resultEntity = new ItemEntity(
				tridentEntity.level(),
				tridentEntity.getX(),
				tridentEntity.getY(),
				tridentEntity.getZ(),
				result
		);
		resultEntity.setThrower(owner);
		if (!tridentEntity.level().addFreshEntity(resultEntity)) {
			return;
		}

		consumeOne(tridentEntity);
		for (Consumption ingredient : ingredients) {
			consume(ingredient.entity(), ingredient.count());
		}
	}

	public static boolean isInfiniteTrident(ItemStack stack) {
		if (!stack.is(Items.TRIDENT)) {
			return false;
		}
		CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
		return data.copyTag().getBoolean(INFINITE_MARKER);
	}

	public static boolean isThunderSkyTrident(ItemStack stack) {
		if (!stack.is(Items.TRIDENT)) {
			return false;
		}
		CustomData data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
		if (data.copyTag().getBoolean(THUNDER_MARKER)) {
			return true;
		}

		Component customName = stack.get(DataComponents.CUSTOM_NAME);
		return data.copyTag().getBoolean(INFINITE_MARKER)
				&& customName != null
				&& customName.getString().equals("Infinite Trident");
	}

	public static ItemStack recordThrow(ItemStack stack, int amount) {
		if (isThunderSkyTrident(stack)) {
			CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
				int cycle = Math.floorMod(tag.getInt(THROW_CYCLE), 3);
				tag.putInt(THROW_CYCLE, (cycle + 1) % 3);
			});
		}
		return stack.copyWithCount(amount);
	}

	public static void startThunderSequence(
			ThrownTrident trident,
			Vec3 hitPosition,
			LivingEntity initialHit
	) {
		if (!(trident.level() instanceof ServerLevel level)
				|| !isThunderSkyTrident(trident.getPickupItemStackOrigin())
				|| trident.getTags().contains(STRIKE_SEQUENCE_TAG)
				|| !(trident.getOwner() instanceof ServerPlayer owner)) {
			return;
		}
		trident.addTag(STRIKE_SEQUENCE_TAG);

		ItemStack projectileStack = trident.getPickupItemStackOrigin();
		var channeling = level.registryAccess()
				.lookupOrThrow(Registries.ENCHANTMENT)
				.getOrThrow(Enchantments.CHANNELING);
		boolean hasChanneling = EnchantmentHelper.getItemEnchantmentLevel(channeling, projectileStack) > 0;
		if (hasChanneling) {
			ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(
					projectileStack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY)
			);
			enchantments.set(channeling, 0);
			projectileStack.set(DataComponents.ENCHANTMENTS, enchantments.toImmutable());
		}

		CustomData data = projectileStack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
		int throwCycle = data.copyTag().contains(THROW_CYCLE)
				? data.copyTag().getInt(THROW_CYCLE)
				: 1;
		int strikeCount = throwCycle == 0 ? 5 : 3;
		int explosionCount = 2 + level.getRandom().nextInt(2);
		UUID initialTarget = initialHit == null ? null : initialHit.getUUID();
		ACTIVE_THUNDER_SEQUENCES.computeIfAbsent(level, ignored -> new ArrayList<>())
				.add(new ThunderSequence(
						owner,
						hitPosition,
						hasChanneling,
						initialTarget,
						strikeCount,
						explosionCount,
						level.getGameTime() + 1
				));
	}

	public static boolean isThunderSkyLightning(LightningBolt lightning) {
		return lightning.getTags().contains(LIGHTNING_TAG);
	}

	public static boolean shouldProtectThunderSkyOwner(LightningBolt lightning, Entity entity) {
		return isThunderSkyLightning(lightning) && lightning.getCause() == entity;
	}

	private static boolean shouldRecoverDrop(ItemEntity itemEntity) {
		return !itemEntity.isRemoved()
				&& !itemEntity.getTags().contains(RECOVERED_OVERFLOW_TAG)
				&& !(itemEntity.getOwner() instanceof ServerPlayer);
	}

	private static void processThunderSequences(ServerLevel level) {
		List<ThunderSequence> sequences = ACTIVE_THUNDER_SEQUENCES.get(level);
		if (sequences == null) {
			return;
		}

		Iterator<ThunderSequence> iterator = sequences.iterator();
		while (iterator.hasNext()) {
			ThunderSequence sequence = iterator.next();
			if (sequence.nextStrikeTick() > level.getGameTime()) {
				continue;
			}

			Vec3 strikePosition = sequence.positionForNextStrike(level);
			LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(level);
			if (lightning == null) {
				EasySurvivalPack.LOGGER.error("Could not create a Thunder Sky lightning bolt.");
				iterator.remove();
				continue;
			}
			lightning.setPos(strikePosition);
			lightning.addTag(LIGHTNING_TAG);
			lightning.setCause(sequence.owner());
			if (!level.addFreshEntity(lightning)) {
				EasySurvivalPack.LOGGER.error("Could not add a Thunder Sky lightning bolt to the level.");
				iterator.remove();
				continue;
			}
			registerDropRecoveryArea(level, sequence.owner(), BlockPos.containing(
					lightning.getX(),
					lightning.getY() - 1.0E-6,
					lightning.getZ()
			));
			if (sequence.shouldExplodeThisStrike()) {
				createNonDestructiveExplosion(level, sequence.owner(), strikePosition);
			}

			sequence.advance(level.getGameTime());
			if (sequence.isComplete()) {
				iterator.remove();
			}
		}

		if (sequences.isEmpty()) {
			ACTIVE_THUNDER_SEQUENCES.remove(level);
		}
	}

	private static void createNonDestructiveExplosion(ServerLevel level, ServerPlayer owner, Vec3 position) {
		DamageSource damageSource = level.damageSources().explosion(owner, owner);
		ExplosionDamageCalculator calculator = new ExplosionDamageCalculator() {
			@Override
			public boolean shouldDamageEntity(net.minecraft.world.level.Explosion explosion, Entity entity) {
				return entity != owner
						&& !(entity instanceof ItemEntity)
						&& super.shouldDamageEntity(explosion, entity);
			}

			@Override
			public float getKnockbackMultiplier(Entity entity) {
				return entity instanceof ItemEntity ? 0.0F : super.getKnockbackMultiplier(entity);
			}
		};
		level.explode(
				owner,
				damageSource,
				calculator,
				position,
				4.0F,
				false,
				Level.ExplosionInteraction.NONE
		);
	}

	private static LivingEntity findChannelingTarget(ThunderSequence sequence, ServerLevel level) {
		if (sequence.targetId() != null) {
			Entity tracked = level.getEntity(sequence.targetId());
			if (tracked instanceof LivingEntity living && living.isAlive() && living != sequence.owner()) {
				return living;
			}
		}

		Vec3 center = sequence.impactPosition();
		BlockPos centerBlock = BlockPos.containing(center);
		AABB searchArea = new AABB(
				centerBlock.getX() - CHANNELING_TARGET_RADIUS,
				centerBlock.getY() - CHANNELING_TARGET_RADIUS,
				centerBlock.getZ() - CHANNELING_TARGET_RADIUS,
				centerBlock.getX() + CHANNELING_TARGET_RADIUS + 1.0,
				centerBlock.getY() + CHANNELING_TARGET_RADIUS + 1.0,
				centerBlock.getZ() + CHANNELING_TARGET_RADIUS + 1.0
		);
		return level.getEntitiesOfClass(
				LivingEntity.class,
				searchArea,
				entity -> entity.isAlive() && entity != sequence.owner()
		).stream().min(Comparator.comparingDouble(entity -> entity.distanceToSqr(center))).orElse(null);
	}

	public static boolean recoverDroppedItem(ItemEntity itemEntity) {
		if (!(itemEntity.level() instanceof ServerLevel level)
				|| !shouldRecoverDrop(itemEntity)) {
			return false;
		}

		List<LightningDropArea> areas = ACTIVE_DROP_RECOVERY_AREAS.get(level);
		if (areas == null) {
			return false;
		}
		for (LightningDropArea area : areas) {
			if (area.expiryTick() >= level.getGameTime() && area.fireArea().contains(itemEntity.position())) {
				recoverItemIntoInventoryOrAtFeet(itemEntity, area.owner());
				return true;
			}
		}
		return false;
	}

	private static void registerDropRecoveryArea(
			ServerLevel level,
			ServerPlayer owner,
			BlockPos strikePosition
	) {
		AABB fireArea = new AABB(
				strikePosition.getX() - 1.5,
				strikePosition.getY() - 1.5,
				strikePosition.getZ() - 1.5,
				strikePosition.getX() + 2.5,
				strikePosition.getY() + 2.5,
				strikePosition.getZ() + 2.5
		);
		ACTIVE_DROP_RECOVERY_AREAS.computeIfAbsent(level, ignored -> new ArrayList<>())
				.add(new LightningDropArea(owner, fireArea, level.getGameTime() + DROP_RECOVERY_DURATION_TICKS));
		for (ItemEntity itemEntity : level.getEntitiesOfClass(
				ItemEntity.class,
				fireArea,
				InfiniteTridentCraftingService::shouldRecoverDrop
		)) {
			recoverItemIntoInventoryOrAtFeet(itemEntity, owner);
		}
	}

	private static void recoverItemIntoInventoryOrAtFeet(ItemEntity itemEntity, ServerPlayer owner) {
		ItemStack recovered = cookRawMeat(itemEntity.getItem());
		itemEntity.setItem(recovered);
		ItemStack remainder = recovered.copy();
		owner.getInventory().add(remainder);
		if (!remainder.isEmpty()) {
			ItemEntity overflow = owner.drop(remainder, false);
			if (overflow == null) {
				itemEntity.setItem(remainder);
				return;
			}
			overflow.addTag(RECOVERED_OVERFLOW_TAG);
		}
		itemEntity.discard();
	}

	private static ItemStack cookRawMeat(ItemStack rawStack) {
		Item cookedItem = switch (rawStack.getItem()) {
			case Item item when item == Items.BEEF -> Items.COOKED_BEEF;
			case Item item when item == Items.PORKCHOP -> Items.COOKED_PORKCHOP;
			case Item item when item == Items.CHICKEN -> Items.COOKED_CHICKEN;
			case Item item when item == Items.MUTTON -> Items.COOKED_MUTTON;
			case Item item when item == Items.RABBIT -> Items.COOKED_RABBIT;
			case Item item when item == Items.COD -> Items.COOKED_COD;
			case Item item when item == Items.SALMON -> Items.COOKED_SALMON;
			default -> null;
		};
		return cookedItem == null ? rawStack.copy() : new ItemStack(cookedItem, rawStack.getCount());
	}

	private static final class ThunderSequence {
		private final ServerPlayer owner;
		private final Vec3 impactPosition;
		private final boolean channeling;
		private final int strikeCount;
		private final int explosionCount;
		private UUID targetId;
		private int strikeIndex;
		private long nextStrikeTick;

		private ThunderSequence(
				ServerPlayer owner,
				Vec3 impactPosition,
				boolean channeling,
				UUID targetId,
				int strikeCount,
				int explosionCount,
				long nextStrikeTick
		) {
			this.owner = owner;
			this.impactPosition = impactPosition;
			this.channeling = channeling;
			this.targetId = targetId;
			this.strikeCount = strikeCount;
			this.explosionCount = explosionCount;
			this.nextStrikeTick = nextStrikeTick;
		}

		private Vec3 positionForNextStrike(ServerLevel level) {
			if (!channeling) {
				return impactPosition;
			}
			LivingEntity target = findChannelingTarget(this, level);
			if (target == null) {
				return impactPosition;
			}
			targetId = target.getUUID();
			return target.position();
		}

		private boolean shouldExplodeThisStrike() {
			for (int explosion = 0; explosion < explosionCount; explosion++) {
				int explosionStrike = (int) Math.round(
						(double) explosion * (strikeCount - 1) / (explosionCount - 1)
				);
				if (explosionStrike == strikeIndex) {
					return true;
				}
			}
			return false;
		}

		private void advance(long currentTick) {
			strikeIndex++;
			nextStrikeTick = currentTick + STRIKE_INTERVAL_TICKS;
		}

		private boolean isComplete() {
			return strikeIndex >= strikeCount;
		}

		private ServerPlayer owner() {
			return owner;
		}

		private Vec3 impactPosition() {
			return impactPosition;
		}

		private UUID targetId() {
			return targetId;
		}

		private long nextStrikeTick() {
			return nextStrikeTick;
		}
	}

	private record LightningDropArea(ServerPlayer owner, AABB fireArea, long expiryTick) {
	}

	private static void consumeOne(ItemEntity itemEntity) {
		consume(itemEntity, 1);
	}

	private static boolean allocate(
			List<ItemEntity> nearbyItems,
			Item requiredItem,
			int requiredCount,
			ItemEntity tridentEntity,
			List<Consumption> ingredients
	) {
		int remaining = requiredCount;
		for (ItemEntity item : nearbyItems) {
			if (item == tridentEntity || !item.getItem().is(requiredItem)) {
				continue;
			}

			int consumed = Math.min(remaining, item.getItem().getCount());
			if (consumed > 0) {
				ingredients.add(new Consumption(item, consumed));
				remaining -= consumed;
			}
			if (remaining == 0) {
				return true;
			}
		}
		return false;
	}

	private static void consume(ItemEntity itemEntity, int count) {
		ItemStack remaining = itemEntity.getItem().copy();
		remaining.shrink(count);
		if (remaining.isEmpty()) {
			itemEntity.discard();
		} else {
			itemEntity.setItem(remaining);
		}
	}

	private record Consumption(ItemEntity entity, int count) {
	}
}
