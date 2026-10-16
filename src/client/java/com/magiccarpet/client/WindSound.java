package com.magiccarpet.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;

import com.magiccarpet.entity.CarpetPhysics;
import com.magiccarpet.entity.MagicCarpetEntity;

/** Rushing wind (the elytra's sound) that swells with speed while you fly the carpet. */
public class WindSound extends AbstractSoundInstance implements TickableSoundInstance {
	private final MagicCarpetEntity carpet;
	private boolean stopped;

	public WindSound(MagicCarpetEntity carpet) {
		super(Identifier.withDefaultNamespace("item.elytra.flying"), SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
		this.carpet = carpet;
		this.looping = true;
		this.delay = 0;
		this.volume = 0.01F;
		this.pitch = 1.0F;
		this.relative = true;
		this.attenuation = SoundInstance.Attenuation.NONE;
	}

	@Override
	public void tick() {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null || carpet.isRemoved() || player.getVehicle() != carpet) {
			stopped = true;
			return;
		}

		float fraction = (float) Math.min(1.0, carpet.physics.velocityLength() / CarpetPhysics.MAX_SPEED);
		this.volume = fraction < 0.1F ? 0.0F : fraction * fraction * 0.8F;
		this.pitch = 0.7F + fraction * 0.5F;
	}

	@Override
	public boolean isStopped() {
		return stopped;
	}
}
