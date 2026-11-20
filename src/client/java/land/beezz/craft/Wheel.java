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
    static final int[][] RINGS = {{23, 51}, {54, 82}, {85, 113}, {116, 144}};
    /** Pieces per octagon side: the outer rings are longer, so they hold 16 and 24 buttons instead of 8. */
    static final int[] SPLIT = {1, 2, 3, 4};
    static final int PER_PAGE = 8 * (1 + 2 + 3 + 4);
    private static final double HALF = Math.toRadians(22.5);
    private static final long OPEN_MS = 160;

    private static final Identifier[][] FILL = new Identifier[4][], EDGE = new Identifier[4][], HALO = new Identifier[4][];
    private static final Identifier CENTER_FILL = Ui.tex("center_fill"), CENTER_EDGE = Ui.tex("center_edge"),
            CENTER_GLOW = Ui.tex("center_glow"), SHADOW = Ui.tex("shadow");

    static {
        for (int i = 0; i < RINGS.length; i++) {
            FILL[i] = new Identifier[SPLIT[i]];
            EDGE[i] = new Identifier[SPLIT[i]];
            HALO[i] = new Identifier[SPLIT[i]];
            for (int t = 0; t < SPLIT[i]; t++) {
                String name = "ring" + (i + 1) + "_" + t;
                FILL[i][t] = Ui.tex(name + "_fill");
                EDGE[i][t] = Ui.tex(name + "_edge");
                HALO[i][t] = Ui.tex(name + "_glow");
            }
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
    /** The buttons: one per thing, its variants (woods, colours) inside. */
    List<Groups.Group> shown = new ArrayList<>();
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

    /** Sorts and lays out the recipes: only when the wheel opens (or the shown set changes), so nothing jumps around. */
    void refresh() {
        Minecraft mc = Minecraft.getInstance();
        Recipes.refresh(mc.player, screen.getMenu(), all);
        shown = new ArrayList<>();
        for (Groups.Group g : Groups.build(all)) if (g.craftable() || Config.get().showMissing) shown.add(g);
        page = Math.min(page, Math.max(0, pages() - 1));
        refreshIn = 5;
    }

    /** After a craft or when the inventory changes: only the colours (craftable or grey) and counts, same places. */
    private void update() {
        Recipes.refresh(Minecraft.getInstance().player, screen.getMenu(), all);
        refreshIn = 5;
    }

    int pages() {
        return Math.max(1, (shown.size() + PER_PAGE - 1) / PER_PAGE);
    }

    private int ringsUsed() {
        int onPage = Math.min(PER_PAGE, shown.size() - page * PER_PAGE), rings = 0, cap = 0;
        while (rings < RINGS.length && cap < onPage) cap += 8 * SPLIT[rings++];
        return Math.max(1, rings);
    }

    /** Where slot s of a page sits: ring, octagon side (0 = up, clockwise), piece of that side, pieces per side. */
    record Pos(int ring, int side, int piece, int split) {}

    static Pos pos(int slot) {
        int ring = 0;
        while (slot >= 8 * SPLIT[ring]) slot -= 8 * SPLIT[ring++];
        return new Pos(ring, slot / SPLIT[ring], slot % SPLIT[ring], SPLIT[ring]);
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
        if (--refreshIn <= 0) update();
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
        int side = Math.floorMod((int) Math.round((Math.toDegrees(Math.atan2(dy, dx)) + 90) / 45), 8);
        double a = angle(side), proj = dx * Math.cos(a) + dy * Math.sin(a);
        if (proj < CENTER) return Math.hypot(dx, dy) < CENTER + 2 ? -2 : -1;
        // the lines between pieces go through the centre: the piece follows from the sideways share of the side
        double lat = dx * Math.cos(a + Math.PI / 2) + dy * Math.sin(a + Math.PI / 2), u = lat / (proj * Math.tan(HALF));
        int offset = 0;
        for (int ring = 0; ring < ringsUsed(); ring++) {
            int m = SPLIT[ring];
            if (proj >= RINGS[ring][0] - 1 && proj <= RINGS[ring][1] + 1) {
                int piece = Math.max(0, Math.min(m - 1, (int) Math.floor((u + 1) / 2 * m)));
                int index = page * PER_PAGE + offset + side * m + piece;
                return index < shown.size() ? index : -1;
            }
            offset += 8 * m;
        }
        return -1;
    }

    /** Unit vector from the centre through the middle of a piece. */
    private static double[] dir(Pos p) {
        double a = angle(p.side()), mid = (RINGS[p.ring()][0] + RINGS[p.ring()][1]) / 2.0;
        double u = -1 + (2 * p.piece() + 1.0) / p.split(), lat = u * mid * Math.tan(HALF);
        double x = Math.cos(a) * mid + Math.cos(a + Math.PI / 2) * lat, y = Math.sin(a) * mid + Math.sin(a + Math.PI / 2) * lat;
        double len = Math.hypot(x, y);
        return new double[] {x, y, len};
    }

    /** Screen point of the middle of a slot on the page, pushed outwards by extra GUI px. */
    float[] point(int slot, float extra) {
        double[] d = dir(pos(slot));
        float z = zoom(), k = (float) ((d[2] + extra) / d[2]);
        return new float[] {cx + (float) d[0] * k * z, cy + (float) d[1] * k * z};
    }

    /** Screen point of the sector of shown.get(index) (on the current page). */
    float[] centre(int index) {
        return point(index - page * PER_PAGE, 0);
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
        layer(g, SHADOW, RINGS[ringsUsed() - 1][1], 0, 0, 0, Ui.fade(0xB0000000, a));
        for (int i = start; i < end; i++) {
            Recipes.Option o = shown.get(i).current();
            int slot = i - start;
            Pos p = pos(slot);
            boolean queued = Crafter.queued(o) > 0;
            int glow = queued ? GLOW_QUEUED : o.craftable ? (lift[slot] > 0.5f ? GLOW_OK_HOVER : GLOW_OK) : 0;
            if (glow != 0) piece(g, HALO, p, lift[slot] * 2, Ui.fade(glow, a));
        }
        for (int i = start; i < end; i++) {
            Recipes.Option o = shown.get(i).current();
            int slot = i - start;
            Pos p = pos(slot);
            boolean h = lift[slot] > 0.5f;
            int fill = o.craftable ? (h ? FILL_OK_HOVER : FILL_OK) : (h ? FILL_NO_HOVER : FILL_NO);
            int edge = Crafter.queued(o) > 0 ? EDGE_QUEUED : o.craftable ? (h ? EDGE_OK_HOVER : EDGE_OK) : (h ? EDGE_NO_HOVER : EDGE_NO);
            float push = lift[slot] * 2;
            piece(g, FILL, p, push, Ui.fade(fill, a));
            piece(g, EDGE, p, push, Ui.fade(edge, a));
        }
        layer(g, CENTER_GLOW, CENTER, 0, 0, 0, Ui.fade(hover == -2 ? 0x80FACC15 : 0x30FACC15, a));
        layer(g, CENTER_FILL, CENTER, 0, 0, 0, Ui.fade(CENTER_FILL_C, a));
        layer(g, CENTER_EDGE, CENTER, 0, 0, 0, Ui.fade(hover == -2 ? EDGE_OK_HOVER : EDGE_OK, a));

        // items on top: the hovered one grows
        g.nextStratum();
        for (int i = start; i < end; i++) {
            int slot = i - start;
            Recipes.Option o = shown.get(i).current();
            float[] p = point(slot, lift[slot] * 2);
            Ui.item(g, font, o.result, p[0], p[1], zoom() * (1 + 0.25f * lift[slot]), true);
        }
        Ui.item(g, font, source, cx, cy, zoom() * (hover == -2 ? 1.1f : 1f), false);

        // queued ones get a little hourglass
        g.nextStratum();
        for (int i = start; i < end; i++) {
            int slot = i - start;
            Recipes.Option o = shown.get(i).current();
            float[] p = point(slot, lift[slot] * 2);
            if (Crafter.queued(o) > 0) Ui.icon(g, Ui.QUEUE, Math.round(p[0]) + 4, Math.round(p[1]) - 12, 9, Ui.BLUE);
            if (shown.get(i).members.size() > 1) {
                // three little dots: there are more variants inside (mouse wheel)
                int dy = Math.round(p[1] + 10 * zoom() * (1 + 0.25f * lift[slot]));
                for (int d = -1; d <= 1; d++) Ui.rect(g, Math.round(p[0]) + d * 3 - 1, dy, 2, 2, o.craftable ? Ui.HONEY_SOFT : Ui.MUTED);
            }
        }

        if (shown.isEmpty()) {
            g.centeredText(font, Tr.t("beezzcraft.none"), Math.round(cx), Math.round(cy + 30), Ui.MUTED);
        }
        hints(g, font);
        if (pages() > 1) pager(g, font, mx, my);

        if (hover >= 0) Card.recipe(g, font, shown.get(hover), mx, my, screen.width, screen.height);
        else if (hover == -2) Card.text(g, font, Tr.t("beezzcraft.center"), mx, my, screen.width, screen.height);
    }

    // ------------------------------------------------------------------ pages

    /** The page switcher «◀ 1/3 ▶» right above the wheel: [x, y, w, h] of the whole pill. */
    private int[] pagerBox(Font font) {
        String label = (page + 1) + " / " + pages();
        int w = 14 + 6 + font.width(label) + 6 + 14, h = 14;
        float rOut = RINGS[ringsUsed() - 1][1] / (float) Math.cos(HALF) * zoom();
        int x = Math.round(cx) - w / 2, y = Math.max(2, Math.round(cy - rOut) - h - 4);
        return new int[] {x, y, w, h};
    }

    private void pager(GuiGraphicsExtractor g, Font font, int mx, int my) {
        int[] b = pagerBox(font);
        String label = (page + 1) + " / " + pages();
        g.nextStratum();
        Ui.panel(g, b[0], b[1], b[2], b[3]);
        boolean lh = mx >= b[0] && mx < b[0] + 14 && my >= b[1] && my < b[1] + b[3];
        boolean rh = mx >= b[0] + b[2] - 14 && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3];
        if (lh) Ui.round(g, b[0], b[1], 14, b[3], 0xFF3A3214);
        if (rh) Ui.round(g, b[0] + b[2] - 14, b[1], 14, b[3], 0xFF3A3214);
        g.text(font, "◀", b[0] + 4, b[1] + 3, lh ? Ui.HONEY_SOFT : Ui.HONEY, false);
        g.text(font, label, b[0] + 20, b[1] + 3, Ui.TEXT, false);
        g.text(font, "▶", b[0] + b[2] - 10, b[1] + 3, rh ? Ui.HONEY_SOFT : Ui.HONEY, false);
    }

    /** @return true when the click turned a page */
    private boolean clickPager(double mx, double my) {
        if (pages() <= 1) return false;
        int[] b = pagerBox(Minecraft.getInstance().font);
        if (my < b[1] || my >= b[1] + b[3]) return false;
        if (mx >= b[0] && mx < b[0] + 14) {
            page = Math.floorMod(page - 1, pages());
            return true;
        }
        if (mx >= b[0] + b[2] - 14 && mx < b[0] + b[2]) {
            page = Math.floorMod(page + 1, pages());
            return true;
        }
        return mx >= b[0] && mx < b[0] + b[2]; // the middle of the pill: nothing, but don't close
    }

    /** The line of hints under the wheel, with mouse pictures instead of words. */
    private void hints(GuiGraphicsExtractor g, Font font) {
        Component craft = Tr.t("beezzcraft.hint.craft"), close = Tr.t("beezzcraft.hint.close");
        Component pages = Tr.t("beezzcraft.hint.pages", page + 1, pages());
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

    /** One piece of a ring, pushed outwards (through its own middle) by push GUI px. */
    private void piece(GuiGraphicsExtractor g, Identifier[][] tex, Pos p, float push, int color) {
        double[] d = dir(p);
        layer(g, tex[p.ring()][p.piece()], RINGS[p.ring()][1], p.side(), (float) (d[0] / d[2] * push), (float) (d[1] / d[2] * push), color);
    }

    /** One wheel texture rotated onto octagon side k around the centre, shifted by (px, py) GUI px. */
    private void layer(GuiGraphicsExtractor g, Identifier tex, int apothem, int k, float px, float py, int color) {
        int size = canvas(apothem);
        float z = zoom();
        g.pose().pushMatrix();
        g.pose().translate(cx + px * z, cy + py * z);
        g.pose().scale(z, z);
        g.pose().rotate((float) Math.toRadians(45 * k));
        ModResources.texture(tex);
        ModResources.texture(tex);
        g.blit(RenderPipelines.GUI_TEXTURED, tex, -size / 2, -size / 2, 0, 0, size, size, size * 2, size * 2, size * 2, size * 2, color);
        g.pose().popMatrix();
    }

    // ------------------------------------------------------------------ input

    /** @return false when the wheel should close */
    boolean click(double mx, double my, int button, boolean shift, boolean ctrl) {
        boolean left = button == InputConstants.MOUSE_BUTTON_LEFT, right = button == InputConstants.MOUSE_BUTTON_RIGHT;
        if (left && clickPager(mx, my)) return true;
        int h = hit(mx, my);
        if (button == InputConstants.MOUSE_BUTTON_MIDDLE && h >= 0) {
            if (shown.get(h).members.size() > 1) shown.get(h).cycle(1);
            return true;
        }
        if (h == -2 || h == -1) return false; // the centre or outside: close
        Recipes.Option o = shown.get(h).current();
        if (right) return Crafter.unqueue(o);
        if (!left) return true;
        int count = ctrl ? Config.get().ctrlCount : 1;
        if (o.craftable) {
            Crafter.craft(o, count, shift);
        } else if (Config.get().queue) {
            Crafter.enqueue(o, count, shift);
            Minecraft.getInstance().player.sendOverlayMessage(Tr.t("beezzcraft.added", o.result.getHoverName()).withStyle(ChatFormatting.AQUA));
        } else {
            Minecraft.getInstance().player.sendOverlayMessage(Tr.t("beezzcraft.noqueue").withStyle(ChatFormatting.GRAY));
            Panel.openSettings(); // show where the queue is switched on
        }
        refreshIn = 3;
        return true;
    }

    /** The mouse wheel: over a button with variants it shows the next variant, elsewhere it turns pages. */
    void scroll(double amount) {
        if (hover >= 0 && shown.get(hover).members.size() > 1) {
            shown.get(hover).cycle(amount < 0 ? 1 : -1);
            return;
        }
        if (pages() <= 1) return;
        page = Math.floorMod(page + (amount < 0 ? 1 : -1), pages());
    }
}
