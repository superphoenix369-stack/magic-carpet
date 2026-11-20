package com.magiccarpet.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.core.Holder;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

import com.magiccarpet.MagicCarpet;
import com.magiccarpet.item.MagicCarpetItem;

/**
 * On armor the four Protections exclude each other. On a magic carpet they don't: the anvil treats them as
 * compatible when the left-hand item is a carpet. Everything else about the anvil is unchanged.
 */
@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin {
	@WrapOperation(
			method = "createResult",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/item/enchantment/Enchantment;areCompatible(Lnet/minecraft/core/Holder;Lnet/minecraft/core/Holder;)Z"
			),
			require = 0
	)
	private boolean magiccarpet$protectionsStack(Holder<Enchantment> first, Holder<Enchantment> second, Operation<Boolean> original) {
		ItemStack left = ((AnvilMenu) (Object) this).getSlot(0).getItem();
		if (left.is(MagicCarpet.MAGIC_CARPET_ITEM) && !first.equals(second)
				&& MagicCarpetItem.isProtection(first) && MagicCarpetItem.isProtection(second)) {
			return true;
		}

		return original.call(first, second);
	}
}
