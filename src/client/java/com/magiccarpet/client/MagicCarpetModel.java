package com.magiccarpet.client;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

import com.magiccarpet.MagicCarpet;

/**
 * A 24 x 32 pixel rug made of 8 strips so it can ripple. Strip 0 is the front.
 * Tassels hang off both ends.
 */
public class MagicCarpetModel extends EntityModel<MagicCarpetRenderState> {
	public static final ModelLayerLocation LAYER = new ModelLayerLocation(MagicCarpet.id("magic_carpet"), "main");
	public static final int SEGMENTS = 8;
	public static final int SEGMENT_LENGTH = 4;
	public static final int WIDTH = 24;
	public static final int TEXTURE_WIDTH = 64;
	public static final int TEXTURE_HEIGHT = 64;
	/** Top-left of the shared tassel UV block. */
	public static final int TASSEL_U = 0;
	public static final int TASSEL_V = 41;

	private final ModelPart[] segments = new ModelPart[SEGMENTS];

	public MagicCarpetModel(ModelPart root) {
		super(root);
		for (int i = 0; i < SEGMENTS; i++) {
			segments[i] = root.getChild("segment_" + i);
		}
	}

	public static LayerDefinition createLayer() {
		MeshDefinition mesh = new MeshDefinition();
		PartDefinition root = mesh.getRoot();
		float halfLength = SEGMENTS * SEGMENT_LENGTH / 2.0F;

		for (int i = 0; i < SEGMENTS; i++) {
			float z = -halfLength + i * SEGMENT_LENGTH;
			CubeListBuilder cubes = CubeListBuilder.create()
					.texOffs(0, i * (SEGMENT_LENGTH + 1))
					.addBox(-WIDTH / 2.0F, -1.0F, 0.0F, WIDTH, 1, SEGMENT_LENGTH);

			if (i == 0) {
				addTassels(cubes, -2.0F);
			} else if (i == SEGMENTS - 1) {
				addTassels(cubes, SEGMENT_LENGTH);
			}

			root.addOrReplaceChild("segment_" + i, cubes, PartPose.offset(0.0F, 0.0F, z));
		}

		return LayerDefinition.create(mesh, TEXTURE_WIDTH, TEXTURE_HEIGHT);
	}

	private static void addTassels(CubeListBuilder cubes, float z) {
		for (float x : new float[] {-11.0F, -6.0F, -1.0F, 4.0F, 9.0F}) {
			cubes.texOffs(TASSEL_U, TASSEL_V).addBox(x, -0.5F, z, 2, 1, 2);
		}
	}

	@Override
	public void setupAnim(MagicCarpetRenderState state) {
		this.resetPose();

		float idle = 0.35F;
		float amplitude = idle + state.speedFraction * 0.9F;
		float waveSpeed = 0.12F + state.speedFraction * 0.45F;
		float phase = state.ageInTicks * waveSpeed;

		for (int i = 0; i < SEGMENTS; i++) {
			float wave = (float) Math.sin(phase - i * 0.8F);
			float slope = (float) Math.cos(phase - i * 0.8F);
			segments[i].y += wave * amplitude;
			segments[i].xRot += slope * amplitude * 0.12F;
		}
	}
}
