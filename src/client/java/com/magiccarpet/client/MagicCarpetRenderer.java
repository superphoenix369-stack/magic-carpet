package com.magiccarpet.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;

import com.magiccarpet.MagicCarpet;
import com.magiccarpet.entity.CarpetPhysics;
import com.magiccarpet.entity.MagicCarpetEntity;

/** Draws the carpet twice: the dyed weave, then the gold trim and tassels on top (undyed). */
public class MagicCarpetRenderer extends EntityRenderer<MagicCarpetEntity, MagicCarpetRenderState> {
	private static final Identifier BASE_TEXTURE = MagicCarpet.id("textures/entity/magic_carpet.png");
	private static final Identifier TRIM_TEXTURE = MagicCarpet.id("textures/entity/magic_carpet_trim.png");

	private final MagicCarpetModel model;

	public MagicCarpetRenderer(EntityRendererProvider.Context context) {
		super(context);
		this.model = new MagicCarpetModel(context.bakeLayer(MagicCarpetModel.LAYER));
		this.shadowRadius = 0.7F;
	}

	@Override
	public MagicCarpetRenderState createRenderState() {
		return new MagicCarpetRenderState();
	}

	@Override
	public void extractRenderState(MagicCarpetEntity entity, MagicCarpetRenderState state, float partialTicks) {
		super.extractRenderState(entity, state, partialTicks);
		state.yRot = entity.getYRot(partialTicks);
		state.hurtTime = entity.getHurtTime() - partialTicks;
		state.hurtDir = entity.getHurtDir();
		state.damageTime = Math.max(entity.getDamage() - partialTicks, 0.0F);

		double dx = entity.getX() - entity.xo;
		double dy = entity.getY() - entity.yo;
		double dz = entity.getZ() - entity.zo;
		double speed = Math.sqrt(dx * dx + dy * dy + dz * dz);
		state.speedFraction = (float) Math.min(1.0, speed / CarpetPhysics.MAX_SPEED);

		ItemStack stack = entity.getCarpetStack();
		state.color = DyedItemColor.getOrDefault(stack, MagicCarpet.DEFAULT_COLOR);
		state.frayed = entity.isFrayed();
	}

	@Override
	public void submit(MagicCarpetRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.translate(0.0F, 0.125F, 0.0F);
		poseStack.rotate(Axis.YP.rotationDegrees(180.0F - state.yRot));

		if (state.hurtTime > 0.0F) {
			float wobble = Mth.sin(state.hurtTime) * state.hurtTime * state.damageTime / 10.0F * state.hurtDir;
			poseStack.rotate(Axis.XP.rotationDegrees(wobble));
		}

		poseStack.scale(-1.0F, -1.0F, 1.0F);
		this.model.setupAnim(state);

		int color = 0xFF000000 | (state.color & 0xFFFFFF);
		if (state.frayed) {
			color = 0xFF000000 | darken(state.color, 0.65F);
		}

		collector.submitModel(this.model, state, poseStack, RenderTypes.entityCutout(BASE_TEXTURE),
				state.lightCoords, OverlayTexture.NO_OVERLAY, color, null, state.outlineColor);
		collector.submitModel(this.model, state, poseStack, RenderTypes.entityCutout(TRIM_TEXTURE),
				state.lightCoords, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF, null, state.outlineColor);

		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}

	private static int darken(int rgb, float factor) {
		int r = (int) (((rgb >> 16) & 0xFF) * factor);
		int g = (int) (((rgb >> 8) & 0xFF) * factor);
		int b = (int) ((rgb & 0xFF) * factor);
		return (r << 16) | (g << 8) | b;
	}
}
