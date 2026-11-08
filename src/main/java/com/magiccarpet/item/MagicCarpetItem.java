package com.magiccarpet.item;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.item.v1.EnchantingContext;

import com.magiccarpet.MagicCarpet;
import com.magiccarpet.entity.MagicCarpetEntity;

/**
 * Use on the ground (or water) to lay the carpet down. Use it while falling and it unrolls under you and
 * catches you.
 */
public class MagicCarpetItem extends Item {
	public MagicCarpetItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.ANY);

		boolean midAir = hit.getType() == HitResult.Type.MISS;
		if (midAir && (player.onGround() || player.isPassenger())) {
			return InteractionResult.PASS;
		}

		Vec3 position = midAir ? player.position().add(0.0, -0.4, 0.0) : hit.getLocation();

		MagicCarpetEntity carpet = new MagicCarpetEntity(MagicCarpet.MAGIC_CARPET_ENTITY, level);
		carpet.setPos(position.x, position.y, position.z);
		carpet.setYRot(player.getYRot());
		carpet.setCarpetStack(stack);
		carpet.setOwner(player.getUUID());

		if (!midAir && !level.noCollision(carpet, carpet.getBoundingBox())) {
			carpet.setPos(position.x, position.y + 0.5, position.z);
			if (!level.noCollision(carpet, carpet.getBoundingBox())) {
				return InteractionResult.FAIL;
			}
		}

		if (!level.isClientSide()) {
			level.addFreshEntity(carpet);
			if (midAir) {
				player.resetFallDistance();
				player.startRiding(carpet);
			}

			if (!player.hasInfiniteMaterials()) {
				stack.shrink(1);
			}
		}

		return InteractionResult.SUCCESS;
	}

	/** Every enchantment a carpet can have. Anything else is removed (creative-mode anvils allow anything). */
	public static boolean isAllowed(Holder<Enchantment> enchantment) {
		return enchantment.is(Enchantments.UNBREAKING) || enchantment.is(Enchantments.MENDING)
				|| enchantment.is(Enchantments.LOYALTY) || isProtection(enchantment);
	}

	/** Removes enchantments a carpet can't use. Returns true if anything was removed. */
	public static boolean stripUnsupported(ItemStack stack) {
		ItemEnchantments current = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
		ItemEnchantments.Mutable kept = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
		boolean removed = false;

		for (Holder<Enchantment> holder : current.keySet()) {
			if (isAllowed(holder)) {
				kept.set(holder, current.getLevel(holder));
			} else {
				removed = true;
			}
		}

		if (removed) {
			stack.set(DataComponents.ENCHANTMENTS, kept.toImmutable());
		}

		return removed;
	}

	/** The four Protection enchantments, which can all go on one carpet together. */
	public static boolean isProtection(Holder<Enchantment> enchantment) {
		return enchantment.is(Enchantments.PROTECTION) || enchantment.is(Enchantments.FIRE_PROTECTION)
				|| enchantment.is(Enchantments.BLAST_PROTECTION) || enchantment.is(Enchantments.PROJECTILE_PROTECTION);
	}

	/**
	 * Unbreaking and Mending as usual (Unbreaking from the enchanting table too), plus Loyalty from a book on an
	 * anvil, and all four Protections from books on an anvil. Nothing else fits a carpet.
	 */
	@Override
	public boolean canBeEnchantedWith(ItemStack stack, Holder<Enchantment> enchantment, EnchantingContext context) {
		if (enchantment.is(Enchantments.UNBREAKING)) {
			return true;
		}

		if (enchantment.is(Enchantments.MENDING) || enchantment.is(Enchantments.LOYALTY) || isProtection(enchantment)) {
			return context == EnchantingContext.ACCEPTABLE;
		}

		return false;
	}
}
