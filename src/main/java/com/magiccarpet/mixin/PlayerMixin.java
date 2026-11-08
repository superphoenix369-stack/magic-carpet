package com.magiccarpet.mixin;

import net.minecraft.world.entity.player.Player;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.magiccarpet.entity.MagicCarpetEntity;

/** In mid-air, getting off a magic carpet needs Shift held for a second, so a tap can't drop you. */
@Mixin(Player.class)
public abstract class PlayerMixin {
	@Inject(method = "wantsToStopRiding", at = @At("HEAD"), cancellable = true)
	private void magiccarpet$holdToDismount(CallbackInfoReturnable<Boolean> cir) {
		Player self = (Player) (Object) this;
		if (self.getVehicle() instanceof MagicCarpetEntity carpet && !carpet.allowsDismount(self)) {
			cir.setReturnValue(false);
		}
	}
}
