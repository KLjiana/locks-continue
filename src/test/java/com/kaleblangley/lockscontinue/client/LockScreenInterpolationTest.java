package com.kaleblangley.lockscontinue.client;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LockScreenInterpolationTest {
    @Test
    void minigameRenderReceivesTickInterpolationInsteadOfTheScreenFrameDuration() throws Exception {
        // Check the real render entry without creating an OpenGL window. Formula-only
        // tests cannot catch Forge supplying frame duration through Screen.render.
        ClassNode screen = new ClassNode();
        try (InputStream input = getClass().getResourceAsStream(
                "/com/kaleblangley/lockscontinue/client/LockScreen.class")) {
            assertNotNull(input);
            new ClassReader(input).accept(screen, 0);
        }
        MethodNode render = screen.methods.stream()
                .filter(method -> method.name.equals("render")
                        && method.desc.equals("(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"))
                .findFirst().orElseThrow();

        SourceInterpreter interpreter = new SourceInterpreter(Opcodes.ASM9) {
            @Override
            public SourceValue copyOperation(AbstractInsnNode instruction, SourceValue value) {
                // Keep the originating expression through local variable loads/stores.
                return value;
            }
        };
        Frame<SourceValue>[] frames = new Analyzer<>(interpreter).analyze(screen.name, render);
        int checkedCalls = 0;
        for (int index = 0; index < render.instructions.size(); index++) {
            AbstractInsnNode instruction = render.instructions.get(index);
            if (!(instruction instanceof MethodInsnNode call)
                    || !call.owner.equals("com/kaleblangley/lockscontinue/client/game/LockGame")
                    || !call.name.equals("render")) {
                continue;
            }
            Frame<SourceValue> frame = frames[index];
            assertNotNull(frame);
            int argumentCount = Type.getArgumentTypes(call.desc).length;
            SourceValue partialTick = frame.getStack(frame.getStackSize() - argumentCount + 3);
            assertTrue(partialTick.insns.stream().anyMatch(source -> source instanceof MethodInsnNode getter
                            && getter.owner.equals("net/minecraft/client/Minecraft")
                            && getter.name.equals("getFrameTime") && getter.desc.equals("()F")),
                    "Minigame interpolation must use Minecraft.getFrameTime(), not the Screen.render argument");
            checkedCalls++;
        }
        assertEquals(1, checkedCalls);
    }
}
