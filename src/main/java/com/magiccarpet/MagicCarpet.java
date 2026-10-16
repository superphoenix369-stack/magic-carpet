package com.magiccarpet;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.DyedItemColor;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import com.magiccarpet.entity.MagicCarpetEntity;
import com.magiccarpet.item.MagicCarpetItem;
import com.magiccarpet.net.CallCarpetPayload;

public class MagicCarpet implements ModInitializer {
	public static final String MOD_ID = "magiccarpet";
	public static final Logger LOGGER = LoggerFactory.getLogger("Magic Carpet");

	public static final int DURABILITY = 1250;
	/** Carpet colors in dye order, using Minecraft's own dye colors. */
	public static final int[] COLORS = {
			0xF9FFFE, 0xF9801D, 0xC74EBD, 0x3AB3DA, 0xFED83D, 0x80C71F, 0xF38BAA, 0x474F52,
			0x9D9D97, 0x169C9C, 0x8932B8, 0x3C44AA, 0x835432, 0x5E7C16, 0xB02E26, 0x1D1D21
	};
	public static final int DEFAULT_COLOR = 0xB02E26;

	public static final ResourceKey<EntityType<?>> CARPET_ENTITY_KEY = ResourceKey.create(Registries.ENTITY_TYPE, id("magic_carpet"));
	public static final EntityType<MagicCarpetEntity> MAGIC_CARPET_ENTITY = EntityType.Builder
			.<MagicCarpetEntity>of(MagicCarpetEntity::new, MobCategory.MISC)
			.sized(MagicCarpetEntity.WIDTH, MagicCarpetEntity.HEIGHT)
			.clientTrackingRange(10)
			.build(CARPET_ENTITY_KEY);

	public static final ResourceKey<Item> CARPET_ITEM_KEY = ResourceKey.create(Registries.ITEM, id("magic_carpet"));
	public static final Item MAGIC_CARPET_ITEM = new MagicCarpetItem(new Item.Properties()
			.setId(CARPET_ITEM_KEY)
			.stacksTo(1)
			.durability(DURABILITY)
			.repairable(Items.PHANTOM_MEMBRANE)
			.enchantable(15)
			.rarity(Rarity.UNCOMMON));

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		Registry.register(BuiltInRegistries.ENTITY_TYPE, CARPET_ENTITY_KEY, MAGIC_CARPET_ENTITY);
		Registry.register(BuiltInRegistries.ITEM, CARPET_ITEM_KEY, MAGIC_CARPET_ITEM);

		CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.TOOLS_AND_UTILITIES).register(output -> {
			for (int color : COLORS) {
				ItemStack stack = new ItemStack(MAGIC_CARPET_ITEM);
				stack.set(DataComponents.DYED_COLOR, new DyedItemColor(color));
				output.accept(stack);
			}
		});

		PayloadTypeRegistry.serverboundPlay().register(CallCarpetPayload.TYPE, CallCarpetPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(CallCarpetPayload.TYPE, (payload, context) -> callCarpet(context.player()));

		LOGGER.info("Magic Carpet ready");
	}

	/** The C key: bring the nearest parked carpet with Loyalty back to its owner. */
	static void callCarpet(ServerPlayer player) {
		if (player.isPassenger()) {
			return;
		}

		List<MagicCarpetEntity> carpets = player.level().getEntitiesOfClass(MagicCarpetEntity.class,
				player.getBoundingBox().inflate(MagicCarpetEntity.callRangeFor(3)),
				carpet -> carpet.isOwnedBy(player) && carpet.getPassengers().isEmpty());

		if (carpets.isEmpty()) {
			player.sendOverlayMessage(Component.translatable("message.magiccarpet.call.none").withStyle(ChatFormatting.GRAY));
			return;
		}

		MagicCarpetEntity best = null;
		double bestDistance = Double.MAX_VALUE;
		boolean anyLoyal = false;

		for (MagicCarpetEntity carpet : carpets) {
			int loyalty = carpet.getLoyaltyLevel();
			if (loyalty <= 0) {
				continue;
			}

			anyLoyal = true;
			double distance = carpet.distanceTo(player);
			if (distance <= MagicCarpetEntity.callRangeFor(loyalty) && distance < bestDistance) {
				best = carpet;
				bestDistance = distance;
			}
		}

		if (best != null) {
			best.startReturning(player);
			player.sendOverlayMessage(Component.translatable("message.magiccarpet.call.coming").withStyle(ChatFormatting.AQUA));
		} else if (anyLoyal) {
			player.sendOverlayMessage(Component.translatable("message.magiccarpet.call.too_far").withStyle(ChatFormatting.GRAY));
		} else {
			player.sendOverlayMessage(Component.translatable("message.magiccarpet.call.no_loyalty").withStyle(ChatFormatting.GRAY));
		}
	}
}
