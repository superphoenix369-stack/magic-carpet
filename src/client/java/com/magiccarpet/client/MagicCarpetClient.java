package com.magiccarpet.client;

import org.lwjgl.sdl.SDLScancode;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import java.util.List;

import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.DyedItemColor;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;

import com.magiccarpet.MagicCarpet;
import com.magiccarpet.entity.MagicCarpetEntity;
import com.magiccarpet.net.CallCarpetPayload;

public class MagicCarpetClient implements ClientModInitializer {
	private static final int SPEED_BARS = 20;

	private static KeyMapping callKey;
	private static MagicCarpetEntity soundCarpet;
	private static int shiftHeldTicks;

	@Override
	public void onInitializeClient() {
		ModelLayerRegistry.registerModelLayer(MagicCarpetModel.LAYER, MagicCarpetModel::createLayer);
		EntityRendererRegistry.register(MagicCarpet.MAGIC_CARPET_ENTITY, MagicCarpetRenderer::new);

		KeyMapping.Category category = KeyMapping.Category.register(MagicCarpet.id("magic_carpet"));
		callKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.magiccarpet.call",
				InputConstants.Type.KEYBOARD, SDLScancode.SDL_SCANCODE_C, category));

		ItemTooltipCallback.EVENT.register(MagicCarpetClient::colorTooltip);
		ClientTickEvents.START_CLIENT_TICK.register(MagicCarpetClient::feedInput);
		ClientTickEvents.END_CLIENT_TICK.register(MagicCarpetClient::afterTick);
	}

	/** Replace Minecraft's "Dyed" / "Color: #B02E26" line with the color's name, e.g. "Color: Red". */
	private static void colorTooltip(ItemStack stack, Item.TooltipContext context, TooltipFlag flag, List<Component> lines) {
		if (!stack.is(MagicCarpet.MAGIC_CARPET_ITEM)) {
			return;
		}

		int index = -1;
		for (int i = 0; i < lines.size(); i++) {
			if (lines.get(i).getContents() instanceof TranslatableContents translatable
					&& ("item.color".equals(translatable.getKey()) || "item.dyed".equals(translatable.getKey()))) {
				index = i;
				break;
			}
		}

		lines.removeIf(line -> line.getContents() instanceof TranslatableContents translatable
				&& ("item.color".equals(translatable.getKey()) || "item.dyed".equals(translatable.getKey())));

		int rgb = DyedItemColor.getOrDefault(stack, MagicCarpet.DEFAULT_COLOR) & 0xFFFFFF;
		Component name = Component.translatable("color.minecraft." + MagicCarpet.closestColorName(rgb));
		Component line = MagicCarpet.isExactDyeColor(rgb)
				? Component.translatable("tooltip.magiccarpet.color", name).withStyle(ChatFormatting.GRAY)
				: Component.translatable("tooltip.magiccarpet.color_mixed", name).withStyle(ChatFormatting.GRAY);

		if (index < 0 || index > lines.size()) {
			index = Math.min(1, lines.size());
		}

		lines.add(index, line);
	}

	/** Hand the movement keys to the carpet before the world ticks, so it flies with this tick's input. */
	private static void feedInput(Minecraft client) {
		LocalPlayer player = client.player;
		if (player == null || !(player.getVehicle() instanceof MagicCarpetEntity carpet) || carpet.getControllingPassenger() != player) {
			return;
		}

		boolean typing = client.gui.screen() != null;
		Options o = client.options;
		carpet.setPilotInput(
				!typing && o.keyUp.isDown(),
				!typing && o.keyDown.isDown(),
				!typing && o.keyLeft.isDown(),
				!typing && o.keyRight.isDown(),
				!typing && o.keyJump.isDown(),
				!typing && isHeld(o.keySprint));
	}

	/** Sprint may be set to "Toggle"; descending should still only happen while the key is physically held. */
	private static boolean isHeld(KeyMapping mapping) {
		InputConstants.Key key = KeyMappingHelper.getBoundKeyOf(mapping);
		if (key.getType() == InputConstants.Type.KEYBOARD) {
			return InputConstants.isKeyDown(key.getValue());
		}

		return mapping.isDown();
	}

	private static void afterTick(Minecraft client) {
		LocalPlayer player = client.player;

		while (callKey.consumeClick()) {
			if (player != null && client.getConnection() != null) {
				ClientPlayNetworking.send(CallCarpetPayload.INSTANCE);
			}
		}

		if (player == null || !(player.getVehicle() instanceof MagicCarpetEntity carpet)) {
			soundCarpet = null;
			shiftHeldTicks = 0;
			return;
		}

		if (soundCarpet != carpet && carpet.getControllingPassenger() == player) {
			soundCarpet = carpet;
			client.getSoundManager().play(new WindSound(carpet));
		}

		shiftHeldTicks = client.options.keyShift.isDown() ? shiftHeldTicks + 1 : 0;
		player.sendOverlayMessage(hud(carpet, player));
	}

	private static Component hud(MagicCarpetEntity carpet, LocalPlayer player) {
		MutableComponent line = Component.empty();

		boolean pilot = carpet.getControllingPassenger() == player;
		boolean airborne = !carpet.onGround();

		if (shiftHeldTicks > 0 && airborne) {
			int filled = Math.min(10, shiftHeldTicks * 10 / MagicCarpetEntity.DISMOUNT_HOLD_TICKS);
			line.append(Component.translatable("hud.magiccarpet.hold_shift").withStyle(ChatFormatting.YELLOW))
					.append(bar(filled, 10, ChatFormatting.YELLOW));
			return line;
		}

		if (pilot) {
			double blocksPerSecond = carpet.physics.velocityLength() * 20.0;
			int filled = (int) Math.round(Math.min(1.0, carpet.physics.speed / com.magiccarpet.entity.CarpetPhysics.MAX_SPEED) * SPEED_BARS);
			ChatFormatting color = carpet.physics.speed > com.magiccarpet.entity.CarpetPhysics.MAX_SPEED ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.AQUA;
			line.append(bar(Math.max(0, filled), SPEED_BARS, color))
					.append(Component.literal(String.format(" %d b/s", Math.round(blocksPerSecond))).withStyle(ChatFormatting.WHITE));
		}

		ItemStack stack = carpet.getCarpetStack();
		if (!stack.isEmpty() && stack.isDamageableItem()) {
			int left = stack.getMaxDamage() - 1 - stack.getDamageValue();
			int percent = (int) Math.round(100.0 * left / (stack.getMaxDamage() - 1));
			if (carpet.isFrayed()) {
				line.append(Component.literal("  ")).append(Component.translatable("hud.magiccarpet.frayed").withStyle(ChatFormatting.RED));
			} else if (percent <= 15) {
				line.append(Component.literal(String.format("  %d%%", percent)).withStyle(ChatFormatting.RED));
			}
		}

		return line;
	}

	private static Component bar(int filled, int total, ChatFormatting color) {
		return Component.literal("|".repeat(filled)).withStyle(color)
				.append(Component.literal("|".repeat(Math.max(0, total - filled))).withStyle(ChatFormatting.DARK_GRAY));
	}
}
