package com.kaleblangley.lockscontinue.client;

import com.kaleblangley.lockscontinue.network.LockNetwork;
import net.minecraft.client.Minecraft;

/** Applies server lock-session packets on the client thread. */
public final class LockClientPacketHandler {
    private LockClientPacketHandler() {
    }

    public static void open(LockNetwork.OpenLockPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new LockScreen(packet));
    }

    public static void openSelection(LockNetwork.OpenLockSelectionPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.setScreen(new LockSelectionScreen(packet));
    }

    public static void result(LockNetwork.LockResultPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof LockScreen screen) {
            screen.handleServerResult(packet);
        }
    }

    public static void selectionResult(LockNetwork.LockSelectionResultPacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof LockSelectionScreen screen) {
            screen.handleServerResult(packet);
        }
    }
}
