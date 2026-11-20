package com.magiccarpet.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
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
		state.segmentAlpha = seeThroughAlphas(entity, state, partialTicks);
	}

	/**
	 * When you ride the carpet in first person, the strips your line of sight passes through fade, so you can see the
	 * ground below. Strips away from where you're looking stay solid. Returns null when nothing needs to fade.
	 */
	private static float[] seeThroughAlphas(MagicCarpetEntity entity, MagicCarpetRenderState state, float partialTicks) {
		Minecraft minecraft = Minecraft.getInstance();
		Entity camera = minecraft.getCameraEntity();
		if (camera == null || camera.getVehicle() != entity || !minecraft.options.getCameraType().isFirstPerson()) {
			return null;
		}

		Vec3 eye = camera.getEyePosition(partialTicks);
		Vec3 view = camera.getViewVector(partialTicks);

		double cx = Mth.lerp(partialTicks, entity.xo, entity.getX());
		double cy = Mth.lerp(partialTicks, entity.yo, entity.getY()) + 0.16;
		double cz = Mth.lerp(partialTicks, entity.zo, entity.getZ());
		double yaw = Math.toRadians(state.yRot);
		double forwardX = -Math.sin(yaw);
		double forwardZ = Math.cos(yaw);
		double acrossX = Math.cos(yaw);
		double acrossZ = Math.sin(yaw);

		float[] alphas = state.segmentAlpha != null && state.segmentAlpha.length == MagicCarpetModel.SEGMENTS
				? state.segmentAlpha : new float[MagicCarpetModel.SEGMENTS];
		boolean anyFaded = false;

		for (int i = 0; i < MagicCarpetModel.SEGMENTS; i++) {
			// Strip centre along the carpet, in blocks (the model's +Z points backwards).
			double along = (-16.0 + i * MagicCarpetModel.SEGMENT_LENGTH + MagicCarpetModel.SEGMENT_LENGTH / 2.0) / 16.0;
			double bestAngle = Math.PI;

			for (double across = -0.7; across <= 0.71; across += 0.35) {
				double px = cx - forwardX * along + acrossX * across - eye.x;
				double py = cy - eye.y;
				double pz = cz - forwardZ * along + acrossZ * across - eye.z;
				double length = Math.sqrt(px * px + py * py + pz * pz);
				if (length < 1.0E-4) {
					continue;
				}

				double cos = (px * view.x + py * view.y + pz * view.z) / length;
				bestAngle = Math.min(bestAngle, Math.acos(Math.max(-1.0, Math.min(1.0, cos))));
			}

			// Within 12 degrees of your line of sight: mostly see-through. Beyond 32 degrees: solid.
			double t = (Math.toDegrees(bestAngle) - 12.0) / (32.0 - 12.0);
			t = Math.max(0.0, Math.min(1.0, t));
			double smooth = t * t * (3.0 - 2.0 * t);
			alphas[i] = (float) (0.2 + 0.8 * smooth);
			anyFaded |= alphas[i] < 0.99F;
		}

		return anyFaded ? alphas : null;
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

		if (state.segmentAlpha == null) {
			state.onlySegment = -1;
			collector.submitModel(this.model, state, poseStack, RenderTypes.entityCutout(BASE_TEXTURE),
					state.lightCoords, OverlayTexture.NO_OVERLAY, color, null, state.outlineColor);
			collector.submitModel(this.model, state, poseStack, RenderTypes.entityCutout(TRIM_TEXTURE),
					state.lightCoords, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF, null, state.outlineColor);
		} else {
			submitStripByStrip(state, poseStack, collector, color);
		}

		poseStack.popPose();
		super.submit(state, poseStack, collector, camera);
	}

	/** Draws each strip on its own, so the ones under your gaze can be see-through. */
	private void submitStripByStrip(MagicCarpetRenderState state, PoseStack poseStack, SubmitNodeCollector collector, int color) {
		if (state.segmentViews == null) {
			state.segmentViews = new MagicCarpetRenderState[MagicCarpetModel.SEGMENTS];
			for (int i = 0; i < MagicCarpetModel.SEGMENTS; i++) {
				state.segmentViews[i] = new MagicCarpetRenderState();
			}
		}

		for (int i = 0; i < MagicCarpetModel.SEGMENTS; i++) {
			MagicCarpetRenderState strip = state.segmentViews[i];
			strip.ageInTicks = state.ageInTicks;
			strip.speedFraction = state.speedFraction;
			strip.onlySegment = i;

			float alpha = state.segmentAlpha[i];
			if (alpha >= 0.99F) {
				collector.submitModel(this.model, strip, poseStack, RenderTypes.entityCutout(BASE_TEXTURE),
						state.lightCoords, OverlayTexture.NO_OVERLAY, color, null, state.outlineColor);
				collector.submitModel(this.model, strip, poseStack, RenderTypes.entityCutout(TRIM_TEXTURE),
						state.lightCoords, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF, null, state.outlineColor);
			} else {
				int alphaBits = Math.round(alpha * 255.0F) << 24;
				collector.submitModel(this.model, strip, poseStack, RenderTypes.entityTranslucent(BASE_TEXTURE),
						state.lightCoords, OverlayTexture.NO_OVERLAY, alphaBits | (color & 0xFFFFFF), null, state.outlineColor);
				collector.submitModel(this.model, strip, poseStack, RenderTypes.entityTranslucent(TRIM_TEXTURE),
						state.lightCoords, OverlayTexture.NO_OVERLAY, alphaBits | 0xFFFFFF, null, state.outlineColor);
			}
		}
	}

	private static int darken(int rgb, float factor) {
		int r = (int) (((rgb >> 16) & 0xFF) * factor);
		int g = (int) (((rgb >> 8) & 0xFF) * factor);
		int b = (int) ((rgb & 0xFF) * factor);
		return (r << 16) | (g << 8) | b;
	}
}
