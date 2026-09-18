package com.kaleblangley.lockscontinue.item;

import com.kaleblangley.lockscontinue.LocksContinue;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.eventbus.api.IEventBus;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, LocksContinue.MODID);

    public static final RegistryObject<Item> CORNER_PIPE = register("corner_pipe");
    public static final RegistryObject<Item> CROSS_PIPE = register("cross_pipe");
    public static final RegistryObject<Item> DIAMOND_WRENCH = registerTool("diamond_wrench", 128);
    public static final RegistryObject<Item> IRON_WRENCH = registerTool("iron_wrench", 64);
    public static final RegistryObject<Item> LOCK = ITEMS.register("lock",
            () -> new ToolboxItem(new Item.Properties()));
    public static final RegistryObject<Item> MEMORY_LOCK_OPEN = register("memory_lock_open");
    public static final RegistryObject<Item> MEMORY_LOCK = register("memory_lock");
    public static final RegistryObject<Item> NETHERITE_PICK_GUN = registerTool("netherite_pick_gun", 256);
    public static final RegistryObject<Item> PIN_LOCK_OPEN = register("pin_lock_open");
    public static final RegistryObject<Item> PIN_LOCK = register("pin_lock");
    public static final RegistryObject<Item> PIPE_LOCK_OPEN = register("pipe_lock_open");
    public static final RegistryObject<Item> PIPE_LOCK = register("pipe_lock");
    public static final RegistryObject<Item> ROTARY_LOCK_OPEN = register("rotary_lock_open");
    public static final RegistryObject<Item> ROTARY_LOCK = register("rotary_lock");
    /** Internal model-only item used to render the rotary lock's screwdriver in the GUI. */
    public static final RegistryObject<Item> SCREWDRIVER_RENDER = register("screwdriver_render");
    public static final RegistryObject<Item> STRAIGHT_PIPE = register("straight_pipe");
    public static final RegistryObject<Item> T_PIPE = register("t_pipe");
    public static final RegistryObject<Item> WOOD_COMB = registerTool("wood_comb", 32);

    private ModItems() {
    }

    private static RegistryObject<Item> register(String name) {
        return ITEMS.register(name, () -> new Item(new Item.Properties()));
    }

    private static RegistryObject<Item> registerTool(String name, int durability) {
        return ITEMS.register(name, () -> new Item(new Item.Properties().durability(durability)));
    }

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
