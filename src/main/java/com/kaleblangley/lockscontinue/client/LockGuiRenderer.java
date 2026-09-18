package com.kaleblangley.lockscontinue.client;

import net.minecraft.client.gui.GuiGraphics;

/** Draws the pixel-art lock GUI without scaling its corner pixels. */
public final class LockGuiRenderer {
    private static final int SOURCE_WIDTH = 48;
    private static final int SOURCE_TEXTURE_HEIGHT = 66;
    private static final int SOURCE_GUI_HEIGHT = 48;
    private static final int CORNER_SIZE = 6;

    private LockGuiRenderer() {
    }

    public static int headerHeight(int panelHeight) {
        return Math.max(1, Math.round(panelHeight * 15.0F / SOURCE_GUI_HEIGHT));
    }

    /** Renders the 48x48 GUI region as a nine-slice. */
    public static void render(GuiGraphics graphics, int left, int top, int width, int height) {
        blitNineSlice(graphics, left, top, width, height,
                0, 0, SOURCE_WIDTH, SOURCE_GUI_HEIGHT);
    }

    private static void blitNineSlice(GuiGraphics graphics, int left, int top, int width, int height,
                                      int sourceLeft, int sourceTop, int sourceWidth, int sourceHeight) {
        if (width <= 0 || height <= 0) {
            return;
        }

        int sourceCorner = Math.min(CORNER_SIZE, Math.min(sourceWidth, sourceHeight) / 2);
        int leftCorner = Math.min(sourceCorner, width / 2);
        int rightCorner = Math.min(sourceCorner, width - leftCorner);
        int topCorner = Math.min(sourceCorner, height / 2);
        int bottomCorner = Math.min(sourceCorner, height - topCorner);
        int centerWidth = Math.max(0, width - leftCorner - rightCorner);
        int centerHeight = Math.max(0, height - topCorner - bottomCorner);
        int sourceCenterWidth = sourceWidth - sourceCorner * 2;
        int sourceCenterHeight = sourceHeight - sourceCorner * 2;

        blit(graphics, left, top, leftCorner, topCorner,
                sourceLeft, sourceTop, sourceCorner, sourceCorner);
        blit(graphics, left + width - rightCorner, top, rightCorner, topCorner,
                sourceLeft + sourceWidth - sourceCorner, sourceTop,
                sourceCorner, sourceCorner);
        blit(graphics, left, top + height - bottomCorner, leftCorner, bottomCorner,
                sourceLeft, sourceTop + sourceHeight - sourceCorner,
                sourceCorner, sourceCorner);
        blit(graphics, left + width - rightCorner, top + height - bottomCorner,
                rightCorner, bottomCorner,
                sourceLeft + sourceWidth - sourceCorner,
                sourceTop + sourceHeight - sourceCorner,
                sourceCorner, sourceCorner);

        if (centerWidth > 0) {
            blit(graphics, left + leftCorner, top, centerWidth, topCorner,
                    sourceLeft + sourceCorner, sourceTop, sourceCenterWidth, sourceCorner);
            blit(graphics, left + leftCorner, top + height - bottomCorner,
                    centerWidth, bottomCorner,
                    sourceLeft + sourceCorner, sourceTop + sourceHeight - sourceCorner,
                    sourceCenterWidth, sourceCorner);
        }
        if (centerHeight > 0) {
            blit(graphics, left, top + topCorner, leftCorner, centerHeight,
                    sourceLeft, sourceTop + sourceCorner, sourceCorner, sourceCenterHeight);
            blit(graphics, left + width - rightCorner, top + topCorner,
                    rightCorner, centerHeight,
                    sourceLeft + sourceWidth - sourceCorner,
                    sourceTop + sourceCorner,
                    sourceCorner, sourceCenterHeight);
        }
        if (centerWidth > 0 && centerHeight > 0) {
            blit(graphics, left + leftCorner, top + topCorner,
                    centerWidth, centerHeight,
                    sourceLeft + sourceCorner, sourceTop + sourceCorner,
                    sourceCenterWidth, sourceCenterHeight);
        }
    }

    private static void blit(GuiGraphics graphics, int left, int top, int width, int height,
                             int sourceLeft, int sourceTop, int sourceWidth, int sourceHeight) {
        if (width <= 0 || height <= 0 || sourceWidth <= 0 || sourceHeight <= 0) {
            return;
        }
        graphics.blit(LockScreen.GUI_TEXTURE, left, top, width, height,
                sourceLeft, sourceTop, sourceWidth, sourceHeight,
                SOURCE_WIDTH, SOURCE_TEXTURE_HEIGHT);
    }
}
