package com.kaleblangley.lockscontinue.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;

/** Plays puzzle-only UI sounds without creating a world sound source. */
public final class LockSoundHelper {
    private LockSoundHelper() {
    }

    public static void playUi(SoundEvent sound, float volume, float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }
}
