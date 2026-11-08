package com.magiccarpet.client;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

public class MagicCarpetRenderState extends EntityRenderState {
	public float yRot;
	public float hurtTime;
	public int hurtDir;
	public float damageTime;
	/** 0 at rest, 1 at top speed: drives how hard the carpet ripples. */
	public float speedFraction;
	public int color = 0xB02E26;
	public boolean frayed;
}
