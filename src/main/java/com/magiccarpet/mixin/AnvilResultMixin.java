package com.magiccarpet.mixin;

import java.util.Objects;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import com.magiccarpet.MagicCarpet;
import com.magiccarpet.item.MagicCarpetItem;

/**
 * In creative mode the anvil lets any book onto any item. For a magic carpet, take back anything it can't use, and if
 * that leaves the book adding nothing, show no result at all.
 */
@Mixin(AnvilMenu.class)
public abstract class AnvilResultMixin {
	@Inject(method = "createResult", at = @At("RETURN"))
	private void magiccarpet$onlySupportedEnchantments(CallbackInfo ci) {
		AnvilMenu menu = (AnvilMenu) (Object) this;
		ItemStack result = menu.getSlot(2).getItem();
		if (result.isEmpty() || !result.is(MagicCarpet.MAGIC_CARPET_ITEM)) {
			return;
		}

		if (!MagicCarpetItem.stripUnsupported(result)) {
			return;
		}

		ItemStack left = menu.getSlot(0).getItem();
		ItemEnchantments before = left.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
		ItemEnchantments after = result.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
		boolean onlyEnchanting = left.getDamageValue() == result.getDamageValue()
				&& Objects.equals(left.get(DataComponents.CUSTOM_NAME), result.get(DataComponents.CUSTOM_NAME));

		if (onlyEnchanting && before.equals(after)) {
			menu.getSlot(2).set(ItemStack.EMPTY);
		}
	}
}
