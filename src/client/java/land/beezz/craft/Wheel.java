package land.beezz.craft;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The octagon wheel drawn over an inventory or crafting table screen. Ring 1 holds 8 recipes, rings 2 and 3 the next
 * 8 each; more than 24 go to pages (mouse wheel). Craftable recipes come first and glow, grey ones lack resources.
 */
final class Wheel {
    // geometry in GUI pixels, must match tools/make_textures.py
    static final int CENTER = 20, GLOW = 5;
    static final int[][] RINGS = {{23, 51}, {54, 82}, {85, 113}};
    static final int PER_RING = 8, PER_PAGE = PER_RING * RINGS.length;
    private static final double HALF = Math.toRadians(22.5);
    private static final long OPEN_MS = 160;

    private static final Identifier[] FILL = new Identifier[3], EDGE = new Identifier[3], HALO = new Identifier[3];
    private static final Identifier CENTER_FILL = Ui.tex("center_fill"), CENTER_EDGE = Ui.tex("center_edge"),
            CENTER_GLOW = Ui.tex("center_glow"), SHADOW = Ui.tex("shadow");

    static {
        for (int i = 0; i < 3; i++) {
            FILL[i] = Ui.tex("ring" + (i + 1) + "_fill");
            EDGE[i] = Ui.tex("ring" + (i + 1) + "_edge");
            HALO[i] = Ui.tex("ring" + (i + 1) + "_glow");
        }
    }

    // colours (ARGB): warm amber for what can be crafted, cool slate for what can't
    private static final int BG_DIM = 0x90000000;
    private static final int FILL_OK = 0xFF2A2416, FILL_OK_HOVER = 0xFF5A4714, EDGE_OK = 0xCCE8B923, EDGE_OK_HOVER = 0xFFFDE68A;
    private static final int GLOW_OK = 0x26FACC15, GLOW_OK_HOVER = 0x90FACC15;
    private static final int FILL_NO = 0xFF1A1C23, FILL_NO_HOVER = 0xFF2B2E38, EDGE_NO = 0x704B5060, EDGE_NO_HOVER = 0xD08A90A0;
    private static final int EDGE_QUEUED = 0xFF60A5FA, GLOW_QUEUED = 0x5060A5FA, CENTER_FILL_C = 0xFF14161C;

    final AbstractContainerScreen<?> screen;
    final ItemStack source;
    final List<Recipes.Option> all;
    List<Recipes.Option> shown = new ArrayList<>();
    int page;
    private float cx, cy, scale = 1;
    private int hover = -1;
    private int refreshIn;
    private final long opened = System.currentTimeMillis();
    /** 0..1 per visible sector: how far it is lifted by the mouse (smoothed). */
    private final float[] lift = new float[PER_PAGE];

    static int canvas(int apothem) {
        return (int) Math.ceil(apothem / Math.cos(HALF)) * 2 + 4 + 2 * GLOW;
    }

    Wheel(AbstractContainerScreen<?> screen, ItemStack source, List<Recipes.Option> all, double mx, double my) {
        this.screen = screen;
        this.source = source.copyWithCount(1);
        this.all = all;
        refresh();
        place(mx, my);
    }

    void refresh() {
        Minecraft mc = Minecraft.getInstance();
        Recipes.refresh(mc.player, screen.getMenu(), all);
        Recipes.sort(all);
        shown = new ArrayList<>();
        for (Recipes.Option o : all) if (o.craftable || Config.get().showMissing) shown.add(o);
        page = Math.min(page, Math.max(0, pages() - 1));
        refreshIn = 5;
    }

    int pages() {
        return Math.max(1, (shown.size() + PER_PAGE - 1) / PER_PAGE);
    }

    private int ringsUsed() {
        int onPage = Math.min(PER_PAGE, shown.size() - page * PER_PAGE);
        return Math.max(1, (onPage + PER_RING - 1) / PER_RING);
    }

    /** Centre on the mouse but keep the whole wheel on screen; shrink it on very small windows. */
    private void place(double mx, double my) {
        float r = RINGS[ringsUsed() - 1][1] / (float) Math.cos(HALF) + 16;
        scale = Math.min(1f, Math.min(screen.width, screen.height) / (2 * r + 8));
        float rs = r * scale;
        cx = (float) Math.max(rs + 2, Math.min(screen.width - rs - 2, mx));
        cy = (float) Math.max(rs + 2, Math.min(screen.height - rs - 2, my));
    }

    void tick() {
        if (--refreshIn <= 0) refresh();
    }

    /** 0..1 eased opening animation. */
    private float open() {
        float p = Math.min(1f, (System.currentTimeMillis() - opened) / (float) OPEN_MS);
        return 1 - (1 - p) * (1 - p) * (1 - p);
    }

    private float zoom() {
        return scale * (0.86f + 0.14f * open());
    }

    // ------------------------------------------------------------------ geometry

    /** Direction of sector k (0 = up, clockwise). */
    private static double angle(int k) {
        return Math.toRadians(-90 + 45 * k);
    }

    /** Index into shown of the sector under the mouse, -1 for none; -2 = the centre. */
    private int hit(double mx, double my) {
        double dx = (mx - cx) / scale, dy = (my - cy) / scale;
        int k = Math.floorMod((int) Math.round((Math.toDegrees(Math.atan2(dy, dx)) + 90) / 45), 8);
        double a = angle(k), proj = dx * Math.cos(a) + dy * Math.sin(a);
        if (proj < CENTER) return Math.hypot(dx, dy) < CENTER + 2 ? -2 : -1;
        for (int ring = 0; ring < ringsUsed(); ring++) {
            if (proj >= RINGS[ring][0] - 1 && proj <= RINGS[ring][1] + 1) {
                int index = page * PER_PAGE + ring * PER_RING + k;
                return index < shown.size() ? index : -1;
            }
        }
        return -1;
    }

    /** Screen point of the middle of sector slot (0..23 on the page), with its lift. */
    private float[] point(int slot, float extra) {
        int ring = slot / PER_RING, k = slot % PER_RING;
        double mid = (RINGS[ring][0] + RINGS[ring][1]) / 2.0 + extra, a = angle(k);
        float z = zoom();
        return new float[] {cx + (float) (Math.cos(a) * mid) * z, cy + (float) (Math.sin(a) * mid) * z};
    }

    // ------------------------------------------------------------------ drawing

    void render(GuiGraphicsExtractor g, Font font, int mx, int my) {
        float a = open();
        g.nextStratum();
        g.nextStratum(); // the inventory screen leaves its own picture in the stratum we would otherwise share
        Ui.rect(g, 0, 0, screen.width, screen.height, Ui.fade(BG_DIM, a));
        hover = hit(mx, my);
        int start = page * PER_PAGE, end = Math.min(shown.size(), start + PER_PAGE);
        for (int s = 0; s < PER_PAGE; s++) {
            float target = start + s == hover ? 1 : 0;
            lift[s] += (target - lift[s]) * 0.35f;
        }

        // shadow, glows, then the sectors
        layer(g, SHADOW, RINGS[ringsUsed() - 1][1], 0, 0, Ui.fade(0xB0000000, a));
        for (int i = start; i < end; i++) {
            Recipes.Option o = shown.get(i);
            int slot = i - start, ring = slot / PER_RING, k = slot % PER_RING;
            boolean queued = Crafter.queued(o.entry) > 0;
            int glow = queued ? GLOW_QUEUED : o.craftable ? (lift[slot] > 0.5f ? GLOW_OK_HOVER : GLOW_OK) : 0;
            if (glow != 0) layer(g, HALO[ring], RINGS[ring][1], k, lift[slot] * 2, Ui.fade(glow, a));
        }
        for (int i = start; i < end; i++) {
            Recipes.Option o = shown.get(i);
            int slot = i - start, ring = slot / PER_RING, k = slot % PER_RING;
            boolean h = lift[slot] > 0.5f;
            int fill = o.craftable ? (h ? FILL_OK_HOVER : FILL_OK) : (h ? FILL_NO_HOVER : FILL_NO);
            int edge = Crafter.queued(o.entry) > 0 ? EDGE_QUEUED : o.craftable ? (h ? EDGE_OK_HOVER : EDGE_OK) : (h ? EDGE_NO_HOVER : EDGE_NO);
            float push = lift[slot] * 2;
            layer(g, FILL[ring], RINGS[ring][1], k, push, Ui.fade(fill, a));
            layer(g, EDGE[ring], RINGS[ring][1], k, push, Ui.fade(edge, a));
        }
        layer(g, CENTER_GLOW, CENTER, 0, 0, Ui.fade(hover == -2 ? 0x80FACC15 : 0x30FACC15, a));
        layer(g, CENTER_FILL, CENTER, 0, 0, Ui.fade(CENTER_FILL_C, a));
        layer(g, CENTER_EDGE, CENTER, 0, 0, Ui.fade(hover == -2 ? EDGE_OK_HOVER : EDGE_OK, a));

        // items on top: the hovered one grows
        g.nextStratum();
        for (int i = start; i < end; i++) {
            int slot = i - start;
            Recipes.Option o = shown.get(i);
            float[] p = point(slot, lift[slot] * 2);
            Ui.item(g, font, o.result, p[0], p[1], zoom() * (1 + 0.25f * lift[slot]), true);
        }
        Ui.item(g, font, source, cx, cy, zoom() * (hover == -2 ? 1.1f : 1f), false);

        // grey items get a veil, queued ones a little hourglass
        g.nextStratum();
        for (int i = start; i < end; i++) {
            int slot = i - start;
            Recipes.Option o = shown.get(i);
            float[] p = point(slot, lift[slot] * 2);
            if (!o.craftable) {
                int half = Math.round(8 * zoom() * (1 + 0.25f * lift[slot]));
                Ui.rect(g, Math.round(p[0]) - half, Math.round(p[1]) - half, half * 2, half * 2, 0x8C14161C);
            }
            if (Crafter.queued(o.entry) > 0) Ui.icon(g, Ui.QUEUE, Math.round(p[0]) + 4, Math.round(p[1]) - 12, 9, Ui.BLUE);
        }

        if (shown.isEmpty()) {
            g.centeredText(font, Component.translatable("beezzcraft.none"), Math.round(cx), Math.round(cy + 30), Ui.MUTED);
        }
        hints(g, font);

        if (hover >= 0) Card.recipe(g, font, shown.get(hover), mx, my, screen.width, screen.height);
        else if (hover == -2) Card.text(g, font, Component.translatable("beezzcraft.center"), mx, my, screen.width, screen.height);
    }

    /** The line of hints under the wheel, with mouse pictures instead of words. */
    private void hints(GuiGraphicsExtractor g, Font font) {
        Component craft = Component.translatable("beezzcraft.hint.craft"), close = Component.translatable("beezzcraft.hint.close");
        Component pages = Component.translatable("beezzcraft.hint.pages", page + 1, pages());
        boolean multi = pages() > 1;
        int w = 12 + font.width(craft) + 10 + 12 + font.width(close) + (multi ? 10 + 12 + font.width(pages) : 0) + 12;
        float rOut = RINGS[ringsUsed() - 1][1] / (float) Math.cos(HALF) * zoom();
        int x = Math.round(cx) - w / 2, y = Math.min(screen.height - 16, Math.round(cy + rOut) + 6);
        x = Math.max(4, Math.min(screen.width - w - 4, x));
        Ui.panel(g, x, y, w, 14);
        int tx = x + 6;
        Ui.icon(g, Ui.MOUSE_LEFT, tx, y + 1, 12, 0xFFFFFFFF);
        g.text(font, craft, tx + 12, y + 3, Ui.TEXT, false);
        tx += 12 + font.width(craft) + 10;
        Ui.icon(g, Ui.MOUSE_RIGHT, tx, y + 1, 12, 0xFFFFFFFF);
        g.text(font, close, tx + 12, y + 3, Ui.TEXT, false);
        tx += 12 + font.width(close) + 10;
        if (multi) {
            Ui.icon(g, Ui.MOUSE_WHEEL, tx, y + 1, 12, 0xFFFFFFFF);
            g.text(font, pages, tx + 12, y + 3, Ui.HONEY_SOFT, false);
        }
    }

    /** One wheel texture rotated into sector k around the centre, pushed outwards by push GUI px. */
    private void layer(GuiGraphicsExtractor g, Identifier tex, int apothem, int k, float push, int color) {
        int size = canvas(apothem);
        float z = zoom();
        double a = angle(k);
        g.pose().pushMatrix();
        g.pose().translate(cx + (float) Math.cos(a) * push * z, cy + (float) Math.sin(a) * push * z);
        g.pose().scale(z, z);
        g.pose().rotate((float) Math.toRadians(45 * k));
        g.blit(RenderPipelines.GUI_TEXTURED, tex, -size / 2, -size / 2, 0, 0, size, size, size * 2, size * 2, size * 2, size * 2, color);
        g.pose().popMatrix();
    }

    // ------------------------------------------------------------------ input

    /** @return false when the wheel should close */
    boolean click(double mx, double my, int button, boolean shift, boolean ctrl) {
        boolean left = button == InputConstants.MOUSE_BUTTON_LEFT, right = button == InputConstants.MOUSE_BUTTON_RIGHT;
        int h = hit(mx, my);
        if (h == -2 || h == -1) return false; // the centre or outside: close
        Recipes.Option o = shown.get(h);
        if (right) return Crafter.unqueue(o.entry);
        if (!left) return true;
        int count = ctrl ? Config.get().ctrlCount : 1;
        if (o.craftable) {
            Crafter.craft(o, count, shift);
        } else if (Config.get().queue) {
            Crafter.enqueue(o, count, shift);
            Minecraft.getInstance().player.sendOverlayMessage(Component.translatable("beezzcraft.added", o.result.getHoverName()).withStyle(ChatFormatting.AQUA));
        } else {
            Minecraft.getInstance().player.sendOverlayMessage(Component.translatable("beezzcraft.noqueue").withStyle(ChatFormatting.GRAY));
            Panel.openSettings(); // show where the queue is switched on
        }
        refreshIn = 3;
        return true;
    }

    void scroll(double amount) {
        if (pages() <= 1) return;
        page = Math.floorMod(page + (amount < 0 ? 1 : -1), pages());
    }
}
