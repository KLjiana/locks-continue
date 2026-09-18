package com.kaleblangley.lockscontinue.item;

import com.kaleblangley.lockscontinue.LocksContinue;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModCreativeModeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, LocksContinue.MODID);

    public static final RegistryObject<CreativeModeTab> LOCKS_CONTINUE_TAB = CREATIVE_MODE_TABS.register(
            "locks_continue",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.lockscontinue"))
                    .icon(() -> new ItemStack(ModItems.IRON_WRENCH.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.WOOD_COMB.get());
                        output.accept(ModItems.IRON_WRENCH.get());
                        output.accept(ModItems.DIAMOND_WRENCH.get());
                        output.accept(ModItems.NETHERITE_PICK_GUN.get());
                        output.accept(ModItems.LOCK.get());
                        output.accept(ModItems.MEMORY_LOCK.get());
                        output.accept(ModItems.PIN_LOCK.get());
                        output.accept(ModItems.PIPE_LOCK.get());
                        output.accept(ModItems.ROTARY_LOCK.get());
//                        output.accept(ModItems.STRAIGHT_PIPE.get());
//                        output.accept(ModItems.T_PIPE.get());
//                        output.accept(ModItems.CORNER_PIPE.get());
//                        output.accept(ModItems.CROSS_PIPE.get());
                    })
                    .build());

    private ModCreativeModeTabs() {
    }

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }
}
