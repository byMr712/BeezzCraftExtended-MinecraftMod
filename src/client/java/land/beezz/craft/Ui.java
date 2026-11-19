package land.beezz.craft;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** Small drawing helpers shared by the wheel, the recipe card and the panel: soft panels, badges, key caps, icons. */
final class Ui {
    static final Identifier WHITE = tex("white"), GEAR = tex("icon_gear"), QUEUE = tex("icon_queue");
    static final Identifier MOUSE_LEFT = tex("mouse_left"), MOUSE_RIGHT = tex("mouse_right"), MOUSE_WHEEL = tex("mouse_wheel");

    // palette (ARGB)
    static final int HONEY = 0xFFFACC15, HONEY_SOFT = 0xFFFDE68A, BLUE = 0xFF93C5FD, GREEN = 0xFF86EFAC, RED = 0xFFFCA5A5;
    static final int TEXT = 0xFFE5E7EB, MUTED = 0xFF9CA3AF, DIM = 0xFF6B7280;
    static final int PANEL = 0xF2141720, PANEL_EDGE = 0xFF3A3F4D, PANEL_HI = 0xFF2A2F3B;

    private Ui() {}

    static Identifier tex(String name) {
        return Identifier.fromNamespaceAndPath("beezzcraft", "textures/gui/" + name + ".png");
    }

    /** A filled rectangle drawn as a textured quad (plain fills can land under the screen picture). */
    static void rect(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) return;
        ModResources.texture(WHITE);
        ModResources.texture(WHITE);
        g.blit(RenderPipelines.GUI_TEXTURED, WHITE, x, y, 0, 0, w, h, 16, 16, 16, 16, color);
    }

    /** Rectangle with 2 px rounded corners. */
    static void round(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        rect(g, x + 2, y, w - 4, h, color);
        rect(g, x, y + 2, 2, h - 4, color);
        rect(g, x + w - 2, y + 2, 2, h - 4, color);
        rect(g, x + 1, y + 1, 1, 1, color);
        rect(g, x + w - 2, y + 1, 1, 1, color);
        rect(g, x + 1, y + h - 2, 1, 1, color);
        rect(g, x + w - 2, y + h - 2, 1, 1, color);
    }

    /** A soft panel: rounded, with a 1 px border and a faint shadow. */
    static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        round(g, x + 1, y + 2, w, h, 0x50000000);
        round(g, x - 1, y - 1, w + 2, h + 2, PANEL_EDGE);
        round(g, x, y, w, h, PANEL);
    }

    static void icon(GuiGraphicsExtractor g, Identifier tex, int x, int y, int size, int color) {
        ModResources.texture(tex);
        ModResources.texture(tex);
        g.blit(RenderPipelines.GUI_TEXTURED, tex, x, y, 0, 0, size, size, 32, 32, 32, 32, color);
    }

    /** A keyboard key cap with its name, e.g. Ctrl. @return its width */
    static int key(GuiGraphicsExtractor g, Font font, String name, int x, int y) {
        int w = font.width(name) + 6;
        round(g, x, y, w, 11, 0xFF5A6070);
        round(g, x, y, w, 10, 0xFF2C313C);
        g.text(font, name, x + 3, y + 1, TEXT, false);
        return w;
    }

    /** Toggle switch, 16x8. */
    static void toggle(GuiGraphicsExtractor g, int x, int y, boolean on) {
        round(g, x, y, 16, 8, on ? 0xFFB98F10 : 0xFF3A3F4D);
        rect(g, on ? x + 9 : x + 1, y + 1, 6, 6, on ? HONEY_SOFT : 0xFF9CA3AF);
    }

    /** An item with its count in the corner, optionally scaled around its centre. */
    static void item(GuiGraphicsExtractor g, Font font, ItemStack stack, float cx, float cy, float scale, boolean decorations) {
        g.pose().pushMatrix();
        g.pose().translate(cx, cy);
        g.pose().scale(scale, scale);
        g.item(stack, -8, -8);
        if (decorations) g.itemDecorations(font, stack, -8, -8);
        g.pose().popMatrix();
    }

    static int width(Font font, Component c) {
        return font.width(c);
    }

    /** Multiplies the alpha of a colour (fade in). */
    static int fade(int color, float a) {
        int alpha = Math.round(((color >>> 24) & 0xFF) * Math.max(0, Math.min(1, a)));
        return (alpha << 24) | (color & 0xFFFFFF);
    }
}
