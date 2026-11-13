package land.beezz.craft;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.lang.reflect.Field;

/**
 * Plays the mod in a real client: the wheel in the inventory, a hover tooltip, craft once / Shift / Ctrl, the craft
 * queue (a grey torch made by itself once coal arrives), the gear, and the wheel at a crafting table.
 * Every step saves a screenshot to build/run/clientGameTest/screenshots.
 */
public class WheelTest implements FabricClientGameTest {
    private static final int LEFT = InputConstants.MOUSE_BUTTON_LEFT, RIGHT = InputConstants.MOUSE_BUTTON_RIGHT;
    private static final String LANG = System.getenv().getOrDefault("BEEZZCRAFT_LANG", "ru_ru");
    private ClientGameTestContext ctx;

    private static Object field(Class<?> type, Object o, String name) {
        try {
            Field f = type.getDeclaredField(name);
            f.setAccessible(true);
            return f.get(o);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private void cursor(double x, double y) {
        double scale = ctx.computeOnClient(mc -> (double) mc.getWindow().getWidth() / mc.gui.screen().width);
        ctx.getInput().setCursorPos(x * scale, y * scale);
        ctx.waitTicks(3);
    }

    private void click(double x, double y, int button) {
        cursor(x, y);
        ctx.getInput().pressMouse(button);
        ctx.waitTicks(4);
    }

    private void shot(String name) {
        ctx.waitTicks(6);
        ctx.takeScreenshot(name);
    }

    /** Screen point of the first inventory slot holding this item. */
    private double[] slotOf(Item item) {
        return ctx.computeOnClient(mc -> {
            AbstractContainerScreen<?> s = (AbstractContainerScreen<?>) mc.gui.screen();
            int left = (int) field(AbstractContainerScreen.class, s, "leftPos"), top = (int) field(AbstractContainerScreen.class, s, "topPos");
            for (Slot slot : s.getMenu().slots) {
                if (slot.container instanceof Inventory && slot.getItem().is(item)) return new double[] {left + slot.x + 8, top + slot.y + 8};
            }
            throw new IllegalStateException("no slot with " + item);
        });
    }

    /** Opens the wheel on an item: Alt + right click. */
    private void wheelOn(Item item) {
        double[] p = slotOf(item);
        cursor(p[0], p[1]);
        ctx.getInput().holdAlt();
        ctx.getInput().pressMouse(RIGHT);
        ctx.getInput().releaseAlt();
        ctx.waitTicks(5);
        if (ctx.computeOnClient(mc -> BeezzCraft.wheel == null)) throw new IllegalStateException("the wheel did not open on " + item);
    }

    /** Screen point of the sector showing this result. */
    private double[] sectorOf(Item result) {
        return ctx.computeOnClient(mc -> {
            Wheel w = BeezzCraft.wheel;
            float cx = (float) field(Wheel.class, w, "cx"), cy = (float) field(Wheel.class, w, "cy"), sc = (float) field(Wheel.class, w, "scale");
            for (int i = 0; i < w.shown.size(); i++) {
                if (!w.shown.get(i).result.is(result)) continue;
                int slot = i % Wheel.PER_PAGE, ring = slot / Wheel.PER_RING, k = slot % Wheel.PER_RING;
                double mid = (Wheel.RINGS[ring][0] + Wheel.RINGS[ring][1]) / 2.0, a = Math.toRadians(-90 + 45 * k);
                return new double[] {cx + Math.cos(a) * mid * sc, cy + Math.sin(a) * mid * sc};
            }
            throw new IllegalStateException("no sector for " + result + " among " + w.shown.size());
        });
    }

    private int count(Item item) {
        return ctx.computeOnClient(mc -> mc.player.getInventory().countItem(item));
    }

    private void check(String what, boolean ok) {
        BeezzCraft.LOG.info("BeezzCraft test: {} {}", ok ? "PASS" : "FAIL", what);
        if (!ok) throw new AssertionError(what);
    }

    private void waitCount(Item item, int atLeast) {
        ctx.waitFor(mc -> mc.player.getInventory().countItem(item) >= atLeast, 100);
    }

    @Override
    public void runTest(ClientGameTestContext ctx) {
        this.ctx = ctx;
        System.setProperty("beezzcraft.debug", "true");
        ctx.getInput().resizeWindow(1920, 1080);
        ctx.runOnClient(mc -> {
            mc.options.languageCode = LANG;
            mc.getLanguageManager().setSelected(LANG);
            mc.options.guiScale().set(3);
            mc.resizeGui();
        });
        ctx.runOnClient(Minecraft::reloadResourcePacks);
        ctx.waitFor(mc -> mc.gui.overlay() == null, 20 * 120);
        Config.get().queue = false;

        try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
            var server = world.getServer();
            ctx.waitTicks(60);
            for (String c : new String[] {"gamemode survival @a", "clear @a", "recipe give @a *", "time set day", "weather clear",
                    "give @a oak_planks 12", "give @a stick 2", "give @a iron_ingot 3", "give @a cobblestone 8"}) {
                server.runCommand(c);
            }
            ctx.waitTicks(20);

            // 1) the wheel in the inventory: 2x2 recipes from planks
            ctx.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
            ctx.waitForScreen(InventoryScreen.class);
            wheelOn(Items.OAK_PLANKS);
            shot("craft_01_inventory_wheel");
            double[] table = sectorOf(Items.CRAFTING_TABLE);
            cursor(table[0], table[1]);
            shot("craft_02_hover_tooltip");

            // 2) click crafts once, Ctrl ten times, Shift as much as possible
            int sticks = count(Items.STICK);
            double[] stick = sectorOf(Items.STICK);
            click(stick[0], stick[1], LEFT);
            waitCount(Items.STICK, sticks + 4);
            check("click crafts sticks once: " + sticks + " -> " + count(Items.STICK), count(Items.STICK) == sticks + 4);
            ctx.waitTicks(8); // the wheel re-sorts after a craft
            table = sectorOf(Items.CRAFTING_TABLE);
            click(table[0], table[1], LEFT);
            waitCount(Items.CRAFTING_TABLE, 1);
            check("crafting table crafted in the inventory", count(Items.CRAFTING_TABLE) == 1);
            int planks = count(Items.OAK_PLANKS);
            ctx.waitTicks(8);
            stick = sectorOf(Items.STICK);
            cursor(stick[0], stick[1]);
            ctx.getInput().holdShift();
            ctx.getInput().pressMouse(LEFT);
            ctx.getInput().releaseShift();
            ctx.waitFor(mc -> mc.player.getInventory().countItem(Items.OAK_PLANKS) <= 1, 100);
            check("Shift + click uses up the planks: " + planks + " -> " + count(Items.OAK_PLANKS), count(Items.OAK_PLANKS) <= 1);
            shot("craft_03_after_crafting");

            // 3) the gear and the craft queue: a torch needs coal we don't have yet
            ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
            ctx.waitTicks(3);
            check("Esc closes only the wheel", ctx.computeOnClient(mc -> BeezzCraft.wheel == null && mc.gui.screen() instanceof InventoryScreen));
            wheelOn(Items.STICK);
            double[] gear = ctx.computeOnClient(mc -> {
                try {
                    var m = Wheel.class.getDeclaredMethod("gear");
                    m.setAccessible(true);
                    int[] g = (int[]) m.invoke(BeezzCraft.wheel);
                    return new double[] {g[0] + 7, g[1] + 7};
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
            });
            click(gear[0], gear[1], LEFT);
            shot("craft_04_settings");
            ctx.runOnClient(mc -> {
                Config.get().queue = true;
                BeezzCraft.wheel.refresh();
            });
            click(gear[0], gear[1], LEFT); // close the settings
            ctx.waitTicks(8);
            double[] torch = sectorOf(Items.TORCH);
            cursor(torch[0], torch[1]);
            shot("craft_05_grey_torch");
            ctx.getInput().holdControl();
            ctx.getInput().pressMouse(LEFT);
            ctx.getInput().releaseControl();
            ctx.waitTicks(5);
            check("Ctrl + click on a grey recipe queues 10", ctx.computeOnClient(mc -> Crafter.jobs().stream().anyMatch(j -> j.queued && j.left == 10)));
            shot("craft_06_queued");
            ctx.setScreen(() -> null);
            ctx.waitTicks(10);
            server.runCommand("give @a coal 2");
            waitCount(Items.TORCH, 8);
            check("queued torches crafted by themselves once coal came: " + count(Items.TORCH), count(Items.TORCH) == 8);
            ctx.waitTicks(10);
            shot("craft_07_queue_hud");
            ctx.runOnClient(mc -> Crafter.clear());

            // 4) at a crafting table: 3x3 recipes from iron
            server.runCommand("give @a stick 4");
            BlockPos pos = ctx.computeOnClient(mc -> mc.player.blockPosition().north(2));
            server.runCommand("setblock " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " crafting_table");
            ctx.waitTicks(10);
            ctx.getInput().lookAt(pos);
            ctx.waitTicks(5);
            ctx.getInput().pressKey(mc -> mc.keyUse);
            ctx.waitForScreen(CraftingScreen.class);
            wheelOn(Items.IRON_INGOT);
            double[] pick = sectorOf(Items.IRON_PICKAXE);
            cursor(pick[0], pick[1]);
            shot("craft_08_table_wheel");
            click(pick[0], pick[1], LEFT);
            waitCount(Items.IRON_PICKAXE, 1);
            check("iron pickaxe crafted at the table", count(Items.IRON_PICKAXE) == 1);
            shot("craft_09_table_after");
            ctx.setScreen(() -> null);
        }
        Config.get().queue = false;
        Config.get().recent.clear();
        Config.get().save();
        BeezzCraft.LOG.info("BeezzCraft test: all steps done");
    }
}
