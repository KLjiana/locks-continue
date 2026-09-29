package com.kaleblangley.lockscontinue.lock;

import com.kaleblangley.lockscontinue.LocksContinue;
import com.kaleblangley.lockscontinue.lock.definition.LockDefinitionManager;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** The four lock kinds. Every registered lockpick can operate every kind. */
public enum LockType {
    PIN_LOCK(1, "pin_lock"),
    PIPE_LOCK(2, "pipe_lock"),
    ROTARY_LOCK(3, "rotary_lock"),
    MEMORY_LOCK(0, "memory_lock");

    private final int variant;
    private final String id;
    private final ResourceLocation resourceId;

    LockType(int variant, String id) {
        this.variant = variant;
        this.id = id;
        this.resourceId = new ResourceLocation(LocksContinue.MODID, id.replace("_lock", ""));
    }

    public int variant() {
        return variant;
    }

    public String id() {
        return id;
    }

    /** Stable namespaced ID used by LockInstance and data packs. */
    public ResourceLocation resourceId() {
        return resourceId;
    }

    public Component displayName() {
        return Component.translatable("lock.lockscontinue." + id);
    }

    /**
     * Returns the quality level of a registered lockpick. The quality is kept
     * available for durability and future puzzle difficulty, but does not
     * restrict which lock kind the tool can open.
     */
    public static int toolStrength(ItemStack stack) {
        return LockDefinitionManager.INSTANCE.lockpickStats(stack).strength();
    }

    public boolean canOpen(ItemStack stack) {
        return LockDefinitionManager.INSTANCE.isLockpick(stack);
    }

    public static LockType fromVariant(int variant) {
        return switch (Math.floorMod(variant, 4)) {
            case 0 -> MEMORY_LOCK;
            case 1 -> PIN_LOCK;
            case 2 -> PIPE_LOCK;
            default -> ROTARY_LOCK;
        };
    }

    /** Accepts the new ID and the old *_lock path for migration compatibility. */
    public static LockType fromResourceId(ResourceLocation id) {
        if (id != null) {
            for (LockType type : values()) {
                if (type.resourceId.equals(id)
                        || (LocksContinue.MODID.equals(id.getNamespace())
                        && type.id.equals(id.getPath()))) {
                    return type;
                }
            }
        }
        return MEMORY_LOCK;
    }
}
