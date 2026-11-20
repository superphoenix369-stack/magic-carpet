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

	/** -1 draws the whole carpet; 0-7 draws only that strip (used when strips fade at different strengths). */
	public int onlySegment = -1;
	/** Opacity of each strip when you look down through the carpet you're riding, or null when fully solid. */
	public float[] segmentAlpha;
	/** One reusable state per strip, so each strip can be drawn with its own opacity. */
	public MagicCarpetRenderState[] segmentViews;
}
