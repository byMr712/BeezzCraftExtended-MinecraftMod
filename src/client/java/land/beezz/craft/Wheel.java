package land.beezz.craft;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The octagon wheel drawn over an inventory or crafting table screen. Ring 1 holds 8 recipes, rings 2 and 3 the next
 * 8 each; more than 24 go to pages (mouse wheel). Craftable recipes come first, grey ones lack resources.
 */
final class Wheel {
    // geometry in GUI pixels, must match tools/make_textures.py
    static final int CENTER = 20;
    static final int[][] RINGS = {{23, 51}, {54, 82}, {85, 113}};
    static final int PER_RING = 8, PER_PAGE = PER_RING * RINGS.length;
    private static final double HALF = Math.toRadians(22.5);

    private static final Identifier[] FILL = new Identifier[3], EDGE = new Identifier[3];
    private static final Identifier CENTER_FILL = tex("center_fill"), CENTER_EDGE = tex("center_edge"), WHITE = tex("white");

    static {
        for (int i = 0; i < 3; i++) {
            FILL[i] = tex("ring" + (i + 1) + "_fill");
            EDGE[i] = tex("ring" + (i + 1) + "_edge");
        }
    }

    // colours (ARGB)
    private static final int BG_DIM = 0xA0000000;
    private static final int FILL_OK = 0xFF181C26, FILL_OK_HOVER = 0xFF4A3D10, EDGE_OK = 0xFFFACC15, EDGE_OK_HOVER = 0xFFFFE680;
    private static final int FILL_NO = 0xFF111217, FILL_NO_HOVER = 0xFF23252D, EDGE_NO = 0xFF4B5060, EDGE_NO_HOVER = 0xFF8A90A0;
    private static final int EDGE_QUEUED = 0xFF60A5FA, CENTER_FILL_C = 0xFF0D0F14;

    final AbstractContainerScreen<?> screen;
    final ItemStack source;
    final List<Recipes.Option> all;
    private final boolean table;
    List<Recipes.Option> shown = new ArrayList<>();
    int page;
    private float cx, cy, scale = 1;
    private int hover = -1;
    private boolean settings;
    private int refreshIn;

    private static Identifier tex(String name) {
        return Identifier.fromNamespaceAndPath("beezzcraft", "textures/gui/" + name + ".png");
    }

    static int canvas(int apothem) {
        return (int) Math.ceil(apothem / Math.cos(HALF)) * 2 + 4;
    }

    Wheel(AbstractContainerScreen<?> screen, ItemStack source, List<Recipes.Option> all, double mx, double my) {
        this.screen = screen;
        this.source = source.copyWithCount(1);
        this.all = all;
        this.table = Recipes.gridSize(screen.getMenu()) >= 3;
        refresh();
        place(mx, my);
    }

    private AbstractContainerMenu menu() {
        return screen.getMenu();
    }

    void refresh() {
        Minecraft mc = Minecraft.getInstance();
        Recipes.refresh(mc.player, menu(), all);
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
        float r = RINGS[ringsUsed() - 1][1] / (float) Math.cos(HALF) + 14;
        scale = Math.min(1f, Math.min(screen.width, screen.height) / (2 * r + 8));
        float rs = r * scale;
        cx = (float) Math.max(rs + 2, Math.min(screen.width - rs - 2, mx));
        cy = (float) Math.max(rs + 2, Math.min(screen.height - rs - 2, my));
    }

    void tick() {
        if (--refreshIn <= 0) refresh();
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

    private int[] gear() {
        float r = (RINGS[ringsUsed() - 1][1] + 6) * scale;
        return new int[] {(int) (cx + r * 0.72f) - 7, (int) (cy - r * 0.72f) - 7, 14, 14};
    }

    private static boolean in(int[] box, double mx, double my) {
        return mx >= box[0] && mx < box[0] + box[2] && my >= box[1] && my < box[1] + box[3];
    }

    // ------------------------------------------------------------------ drawing

    void render(GuiGraphicsExtractor g, Font font, int mx, int my) {
        g.nextStratum();
        // a textured quad: plain fills can end up under the inventory picture in some screens
        g.blit(RenderPipelines.GUI_TEXTURED, WHITE, 0, 0, 0, 0, screen.width, screen.height, 16, 16, 16, 16, BG_DIM);
        hover = settings ? -1 : hit(mx, my);
        int start = page * PER_PAGE, end = Math.min(shown.size(), start + PER_PAGE);

        // sectors
        for (int i = start; i < end; i++) {
            int slot = i - start, ring = slot / PER_RING, k = slot % PER_RING;
            Recipes.Option o = shown.get(i);
            boolean h = i == hover;
            int fill = o.craftable ? (h ? FILL_OK_HOVER : FILL_OK) : (h ? FILL_NO_HOVER : FILL_NO);
            int edge = Crafter.queued(o.entry) > 0 ? EDGE_QUEUED : o.craftable ? (h ? EDGE_OK_HOVER : EDGE_OK) : (h ? EDGE_NO_HOVER : EDGE_NO);
            sector(g, FILL[ring], RINGS[ring][1], k, fill);
            sector(g, EDGE[ring], RINGS[ring][1], k, edge);
        }
        sector(g, CENTER_FILL, CENTER, 0, CENTER_FILL_C);
        sector(g, CENTER_EDGE, CENTER, 0, hover == -2 ? EDGE_OK_HOVER : EDGE_OK);

        // items on top
        g.nextStratum();
        for (int i = start; i < end; i++) {
            int slot = i - start, ring = slot / PER_RING, k = slot % PER_RING;
            Recipes.Option o = shown.get(i);
            double mid = (RINGS[ring][0] + RINGS[ring][1]) / 2.0, a = angle(k);
            int x = Math.round(cx + (float) (Math.cos(a) * mid) * scale) - 8, y = Math.round(cy + (float) (Math.sin(a) * mid) * scale) - 8;
            g.item(o.result, x, y);
            g.itemDecorations(font, o.result, x, y);
            int q = Crafter.queued(o.entry);
            if (q > 0) g.text(font, "⌛", x + 10, y - 4, 0xFF93C5FD, true);
        }
        g.item(source, Math.round(cx) - 8, Math.round(cy) - 8);

        // grey items get a dark veil over them
        g.nextStratum();
        for (int i = start; i < end; i++) {
            Recipes.Option o = shown.get(i);
            if (o.craftable) continue;
            int slot = i - start, ring = slot / PER_RING, k = slot % PER_RING;
            double mid = (RINGS[ring][0] + RINGS[ring][1]) / 2.0, a = angle(k);
            int x = Math.round(cx + (float) (Math.cos(a) * mid) * scale) - 8, y = Math.round(cy + (float) (Math.sin(a) * mid) * scale) - 8;
            g.fill(x, y, x + 16, y + 16, 0x9A101014);
        }

        // texts: page, hints, gear
        float rOut = RINGS[ringsUsed() - 1][1] / (float) Math.cos(HALF) * scale;
        if (shown.isEmpty()) {
            g.centeredText(font, Component.translatable("beezzcraft.none"), Math.round(cx), Math.round(cy + rOut * 0.5f), 0xFFAAAAAA);
        }
        Component hint = pages() > 1
                ? Component.translatable("beezzcraft.hint.pages", page + 1, pages())
                : Component.translatable("beezzcraft.hint");
        g.centeredText(font, hint, Math.round(cx), Math.min(screen.height - 10, Math.round(cy + rOut) + 4), 0xFFB0B4C0);
        int[] gb = gear();
        boolean gh = in(gb, mx, my) || settings;
        g.fill(gb[0], gb[1], gb[0] + gb[2], gb[1] + gb[3], gh ? 0xF0453A12 : 0xE6181C26);
        g.outline(gb[0], gb[1], gb[2], gb[3], gh ? EDGE_OK_HOVER : EDGE_OK);
        g.centeredText(font, Component.literal("⚙"), gb[0] + 7, gb[1] + 3, 0xFFFACC15);

        if (settings) renderSettings(g, font, mx, my);
        else if (hover >= 0) tooltip(g, font, shown.get(hover), mx, my);
        else if (hover == -2) g.setTooltipForNextFrame(font, Component.translatable("beezzcraft.center"), mx, my);
        else if (in(gb, mx, my)) g.setTooltipForNextFrame(font, Component.translatable("beezzcraft.gear"), mx, my);
    }

    /** One texture rotated into sector k around the wheel centre. */
    private void sector(GuiGraphicsExtractor g, Identifier tex, int apothem, int k, int color) {
        int size = canvas(apothem);
        g.pose().pushMatrix();
        g.pose().translate(cx, cy);
        g.pose().scale(scale, scale);
        g.pose().rotate((float) Math.toRadians(45 * k));
        g.blit(RenderPipelines.GUI_TEXTURED, tex, -size / 2, -size / 2, 0, 0, size, size, size * 2, size * 2, size * 2, size * 2, color);
        g.pose().popMatrix();
    }

    private void tooltip(GuiGraphicsExtractor g, Font font, Recipes.Option o, int mx, int my) {
        List<Component> lines = new ArrayList<>();
        MutableComponent title = o.result.getHoverName().copy().withStyle(o.craftable ? ChatFormatting.YELLOW : ChatFormatting.GRAY);
        if (o.result.getCount() > 1) title.append(Component.literal(" ×" + o.result.getCount()).withStyle(ChatFormatting.WHITE));
        lines.add(title);
        lines.add(Component.translatable("beezzcraft.needs").withStyle(ChatFormatting.DARK_GRAY));
        for (Recipes.Need n : o.needs) {
            lines.add(Component.literal("  " + n.need() + "× ").append(n.icon().getHoverName())
                    .append(Component.translatable("beezzcraft.have", n.have()).withStyle(ChatFormatting.DARK_GRAY))
                    .withStyle(n.missing() ? ChatFormatting.RED : ChatFormatting.GRAY));
        }
        int q = Crafter.queued(o.entry);
        if (q > 0) lines.add(Component.translatable("beezzcraft.queued", q).withStyle(ChatFormatting.AQUA));
        if (o.craftable) {
            lines.add(Component.translatable("beezzcraft.tip.craft", Config.get().ctrlCount).withStyle(ChatFormatting.GREEN));
        } else if (Config.get().queue) {
            lines.add(Component.translatable("beezzcraft.tip.queue", Config.get().ctrlCount).withStyle(ChatFormatting.AQUA));
        } else {
            lines.add(Component.translatable("beezzcraft.tip.noqueue").withStyle(ChatFormatting.DARK_GRAY));
        }
        g.setComponentTooltipForNextFrame(font, lines, mx, my);
    }

    // ------------------------------------------------------------------ settings (the gear)

    private record Row(Component label, Boolean on, Runnable action) {}

    private List<Row> rows() {
        Config c = Config.get();
        List<Row> rows = new ArrayList<>();
        rows.add(new Row(Component.translatable("beezzcraft.set.queue"), c.queue, () -> {
            c.queue = !c.queue;
            if (!c.queue) Crafter.jobs().removeIf(j -> j.queued);
            c.save();
        }));
        rows.add(new Row(Component.translatable("beezzcraft.set.queue.note1").withStyle(ChatFormatting.DARK_GRAY), null, null));
        rows.add(new Row(Component.translatable("beezzcraft.set.queue.note2").withStyle(ChatFormatting.DARK_GRAY), null, null));
        rows.add(new Row(Component.translatable("beezzcraft.set.missing"), c.showMissing, () -> {
            c.showMissing = !c.showMissing;
            c.save();
            refresh();
        }));
        rows.add(new Row(Component.translatable("beezzcraft.set.sound"), c.sound, () -> {
            c.sound = !c.sound;
            c.save();
        }));
        long queued = Crafter.jobs().stream().filter(j -> j.queued).count();
        rows.add(new Row(Component.translatable("beezzcraft.set.clear", queued), null, queued > 0 ? () -> Crafter.jobs().removeIf(j -> j.queued) : null));
        return rows;
    }

    private int[] settingsBox(Font font) {
        int w = 0;
        for (Row r : rows()) w = Math.max(w, font.width(r.label) + (r.on != null ? 16 : 0));
        w += 12;
        int h = rows().size() * 13 + 6;
        // beside the wheel, not over it: right if there is room, else left
        float rOut = RINGS[ringsUsed() - 1][1] / (float) Math.cos(HALF) * scale;
        int x = Math.round(cx + rOut) + 6;
        if (x + w > screen.width - 4) x = Math.max(4, Math.round(cx - rOut) - 6 - w);
        int y = Math.max(4, Math.min(screen.height - h - 4, Math.round(cy) - h / 2));
        return new int[] {x, y, w, h};
    }

    private void renderSettings(GuiGraphicsExtractor g, Font font, int mx, int my) {
        g.nextStratum();
        int[] b = settingsBox(font);
        g.fill(b[0], b[1], b[0] + b[2], b[1] + b[3], 0xF2101218);
        g.outline(b[0], b[1], b[2], b[3], EDGE_OK);
        List<Row> rows = rows();
        for (int i = 0; i < rows.size(); i++) {
            Row r = rows.get(i);
            int y = b[1] + 4 + i * 13;
            boolean h = r.action != null && mx >= b[0] && mx < b[0] + b[2] && my >= y - 1 && my < y + 12;
            if (h) g.fill(b[0] + 1, y - 1, b[0] + b[2] - 1, y + 11, 0x40FACC15);
            int tx = b[0] + 6;
            if (r.on != null) {
                g.outline(tx, y + 1, 9, 9, 0xFFB0B4C0);
                if (r.on) g.fill(tx + 2, y + 3, tx + 7, y + 8, 0xFFFACC15);
                tx += 14;
            }
            g.text(font, r.label, tx, y + 2, r.action == null && r.on == null ? 0xFF808590 : 0xFFE5E7EB, false);
        }
    }

    private boolean clickSettings(Font font, double mx, double my) {
        int[] b = settingsBox(font);
        if (!(mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3])) return false;
        List<Row> rows = rows();
        int i = (int) ((my - b[1] - 3) / 13);
        if (i >= 0 && i < rows.size() && rows.get(i).action != null) rows.get(i).action.run();
        return true;
    }

    // ------------------------------------------------------------------ input

    /** @return false when the wheel should close */
    boolean click(Font font, double mx, double my, int button, boolean shift, boolean ctrl) {
        boolean left = button == InputConstants.MOUSE_BUTTON_LEFT, right = button == InputConstants.MOUSE_BUTTON_RIGHT;
        if (in(gear(), mx, my) && left) {
            settings = !settings;
            return true;
        }
        if (settings) {
            if (!clickSettings(font, mx, my)) settings = false;
            return true;
        }
        int h = hit(mx, my);
        if (h == -2 || h == -1) return false; // the centre or outside: close
        Recipes.Option o = shown.get(h);
        if (right) {
            if (Crafter.unqueue(o.entry)) return true;
            return false;
        }
        if (!left) return true;
        int count = ctrl ? Config.get().ctrlCount : 1;
        if (o.craftable) {
            Crafter.craft(o, count, shift);
        } else if (Config.get().queue) {
            Crafter.enqueue(o, count, shift);
            Minecraft.getInstance().player.sendOverlayMessage(Component.translatable("beezzcraft.added", o.result.getHoverName()).withStyle(ChatFormatting.AQUA));
        } else {
            settings = true; // show where the queue is switched on
        }
        refreshIn = 3;
        return true;
    }

    void scroll(double amount) {
        if (pages() <= 1) return;
        page = Math.floorMod(page + (amount < 0 ? 1 : -1), pages());
    }

    boolean table() {
        return table;
    }
}
