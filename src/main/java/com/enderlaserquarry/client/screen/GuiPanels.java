package com.enderlaserquarry.client.screen;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Draws vanilla-style GUI panels and slots in code, so screens need no background textures.
 */
public final class GuiPanels {
    private static final int BORDER = 0xFF000000;
    private static final int FACE = 0xFFC6C6C6;
    private static final int LIGHT = 0xFFFFFFFF;
    private static final int SHADOW = 0xFF555555;
    private static final int SLOT_DARK = 0xFF373737;
    private static final int SLOT_FACE = 0xFF8B8B8B;

    public static void panel(GuiGraphics g, int x, int y, int w, int h) {
        // Black outline with clipped corners
        g.fill(x + 2, y, x + w - 3, y + 1, BORDER);
        g.fill(x + 2, y + h - 1, x + w - 3, y + h, BORDER);
        g.fill(x, y + 2, x + 1, y + h - 3, BORDER);
        g.fill(x + w - 1, y + 2, x + w, y + h - 3, BORDER);
        g.fill(x + 1, y + 1, x + 2, y + 2, BORDER);
        g.fill(x + w - 3, y + 1, x + w - 1, y + 2, BORDER);
        g.fill(x + 1, y + h - 3, x + 2, y + h - 1, BORDER);
        g.fill(x + w - 2, y + h - 3, x + w - 1, y + h - 2, BORDER);
        g.fill(x + w - 3, y + h - 2, x + w - 2, y + h - 1, BORDER);

        g.fill(x + 1, y + 2, x + w - 1, y + h - 3, FACE);
        g.fill(x + 2, y + 1, x + w - 3, y + h - 1, FACE);

        // Bevel
        g.fill(x + 2, y + 1, x + w - 3, y + 3, LIGHT);
        g.fill(x + 1, y + 2, x + 3, y + h - 3, LIGHT);
        g.fill(x + 3, y + h - 3, x + w - 2, y + h - 1, SHADOW);
        g.fill(x + w - 3, y + 3, x + w - 1, y + h - 3, SHADOW);
    }

    /** An inset well (e.g. behind the minimap). */
    public static void inset(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, SLOT_DARK);
        g.fill(x + 1, y + 1, x + w, y + h, LIGHT);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, SLOT_FACE);
    }

    /** Background for an item slot whose item renders at (x, y). */
    public static void slot(GuiGraphics g, int x, int y) {
        inset(g, x - 1, y - 1, 18, 18);
    }

    public static void playerInventory(GuiGraphics g, int x, int y) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                slot(g, x + col * 18, y + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            slot(g, x + col * 18, y + 58);
        }
    }

    private GuiPanels() {
    }
}
