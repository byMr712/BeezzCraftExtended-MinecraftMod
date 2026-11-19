package land.beezz.craft;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

/** Small drawing helpers shared by the wheel, the recipe card and the panel: soft panels, badges, key caps, icons. */
final class Ui {
    static final ResourceLocation WHITE = tex("white"), GEAR = tex("icon_gear"), QUEUE = tex("icon_queue");
    static final ResourceLocation MOUSE_LEFT = tex("mouse_left"), MOUSE_RIGHT = tex("mouse_right"), MOUSE_WHEEL = tex("mouse_wheel");

    // palette (ARGB)
    static final int HONEY = 0xFFFACC15, HONEY_SOFT = 0xFFFDE68A, BLUE = 0xFF93C5FD, GREEN = 0xFF86EFAC, RED = 0xFFFCA5A5;
    static final int TEXT = 0xFFE5E7EB, MUTED = 0xFF9CA3AF, DIM = 0xFF6B7280;
    static final int PANEL = 0xF2141720, PANEL_EDGE = 0xFF3A3F4D, PANEL_HI = 0xFF2A2F3B;

    private Ui() {}

    static ResourceLocation tex(String name) {
        return ResourceLocation.fromNamespaceAndPath("beezzcraft", "textures/gui/" + name + ".png");
    }

    /** A filled rectangle. */
    static void rect(GuiGraphics g, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) return;
        g.fill(x, y, x + w, y + h, color);
    }

    /**
     * A texture tinted by color. 1.21/1.21.1 blits have no colour argument and the shader colour does not apply
     * its alpha, which made the soft glow textures look like wide solid lines; draw the quad with a vertex colour.
     */
    static void quad(GuiGraphics g, ResourceLocation tex, int x, int y, int w, int h, int tw, int th, int color) {
        if (w <= 0 || h <= 0) return;
        ModResources.texture(tex);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, tex);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        Matrix4f matrix = g.pose().last().pose();
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        buffer.addVertex(matrix, x, y + h, 0).setUv(0f, 1f).setColor(color);
        buffer.addVertex(matrix, x + w, y + h, 0).setUv(1f, 1f).setColor(color);
        buffer.addVertex(matrix, x + w, y, 0).setUv(1f, 0f).setColor(color);
        buffer.addVertex(matrix, x, y, 0).setUv(0f, 0f).setColor(color);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    /** Rectangle with 2 px rounded corners. */
    static void round(GuiGraphics g, int x, int y, int w, int h, int color) {
        rect(g, x + 2, y, w - 4, h, color);
        rect(g, x, y + 2, 2, h - 4, color);
        rect(g, x + w - 2, y + 2, 2, h - 4, color);
        rect(g, x + 1, y + 1, 1, 1, color);
        rect(g, x + w - 2, y + 1, 1, 1, color);
        rect(g, x + 1, y + h - 2, 1, 1, color);
        rect(g, x + w - 2, y + h - 2, 1, 1, color);
    }

    /** A soft panel: rounded, with a 1 px border and a faint shadow. */
    static void panel(GuiGraphics g, int x, int y, int w, int h) {
        round(g, x + 1, y + 2, w, h, 0x50000000);
        round(g, x - 1, y - 1, w + 2, h + 2, PANEL_EDGE);
        round(g, x, y, w, h, PANEL);
    }

    static void icon(GuiGraphics g, ResourceLocation tex, int x, int y, int size, int color) {
        quad(g, tex, x, y, size, size, 32, 32, color);
    }

    /** A keyboard key cap with its name, e.g. Ctrl. @return its width */
    static int key(GuiGraphics g, Font font, String name, int x, int y) {
        int w = font.width(name) + 6;
        round(g, x, y, w, 11, 0xFF5A6070);
        round(g, x, y, w, 10, 0xFF2C313C);
        g.drawString(font, name, x + 3, y + 1, TEXT, false);
        return w;
    }

    /** Toggle switch, 16x8. */
    static void toggle(GuiGraphics g, int x, int y, boolean on) {
        round(g, x, y, 16, 8, on ? 0xFFB98F10 : 0xFF3A3F4D);
        rect(g, on ? x + 9 : x + 1, y + 1, 6, 6, on ? HONEY_SOFT : 0xFF9CA3AF);
    }

    /** An item with its count in the corner, optionally scaled around its centre. */
    static void item(GuiGraphics g, Font font, ItemStack stack, float cx, float cy, float scale, boolean decorations) {
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().scale(scale, scale, 1);
        g.renderItem(stack, -8, -8);
        if (decorations) g.renderItemDecorations(font, stack, -8, -8);
        g.pose().popPose();
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
