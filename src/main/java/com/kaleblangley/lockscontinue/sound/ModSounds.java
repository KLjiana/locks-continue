package com.kaleblangley.lockscontinue.sound;

import com.kaleblangley.lockscontinue.LocksContinue;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, LocksContinue.MODID);

    public static final RegistryObject<SoundEvent> PIN_FAIL = register("pin_fail");
    public static final RegistryObject<SoundEvent> PIN_FINISH = register("pin_finish");
    public static final RegistryObject<SoundEvent> ROTARY_FAIL = register("lock_cut");
    public static final RegistryObject<SoundEvent> LOCK_RATTLE = register("lock_rattle");
    public static final RegistryObject<SoundEvent> LOCK_OPEN = register("lock_open");
    public static final RegistryObject<SoundEvent> CARD_FAIL = register("card_fail");
    public static final RegistryObject<SoundEvent> CARD_FLIP = register("card_flip");
    public static final RegistryObject<SoundEvent> CARD_WIN = register("card_win");

    private ModSounds() {
    }

    private static RegistryObject<SoundEvent> register(String name) {
        ResourceLocation id = new ResourceLocation(LocksContinue.MODID, name);
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(id));
    }

    public static void register(IEventBus eventBus) {
        SOUNDS.register(eventBus);
    }
}
