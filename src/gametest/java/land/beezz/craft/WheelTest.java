package land.beezz.craft;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

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
        double scale = ctx.computeOnClient(mc -> (double) mc.getWindow().getWidth() / BeezzCraft.screen(mc).width);
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
            AbstractContainerScreen<?> s = (AbstractContainerScreen<?>) BeezzCraft.screen(mc);
            int left = (int) field(AbstractContainerScreen.class, s, "leftPos"), top = (int) field(AbstractContainerScreen.class, s, "topPos");
            for (Slot slot : s.getMenu().slots) {
                if (slot.container instanceof Inventory && slot.getItem().is(item)) return new double[] {left + slot.x + 8, top + slot.y + 8};
            }
            throw new IllegalStateException("no slot with " + item);
        });
    }

    /** Screen point of any slot that has an item (the creative picker has no player Inventory slots). */
    private double[] anySlot() {
        return ctx.computeOnClient(mc -> {
            AbstractContainerScreen<?> s = (AbstractContainerScreen<?>) BeezzCraft.screen(mc);
            int left = (int) field(AbstractContainerScreen.class, s, "leftPos"), top = (int) field(AbstractContainerScreen.class, s, "topPos");
            for (Slot slot : s.getMenu().slots) {
                if (slot.hasItem()) return new double[] {left + slot.x + 8, top + slot.y + 8};
            }
            throw new IllegalStateException("no slot with an item");
        });
    }

    /** Opens the wheel on an item: the functional key (Left Ctrl by default) + right click. */
    private void wheelOn(Item item) {
        double[] p = slotOf(item);
        cursor(p[0], p[1]);
        ctx.getInput().holdControl();
        ctx.getInput().pressMouse(RIGHT);
        ctx.getInput().releaseControl();
        ctx.waitTicks(5);
        if (ctx.computeOnClient(mc -> BeezzCraft.wheel == null)) throw new IllegalStateException("the wheel did not open on " + item);
    }

    /** Screen point of the sector showing this result. */
    private double[] sectorOf(Item result) {
        return ctx.computeOnClient(mc -> {
            Wheel w = BeezzCraft.wheel;
            for (int i = 0; i < w.shown.size(); i++) {
                var group = w.shown.get(i);
                int m = -1;
                for (int k = 0; k < group.members.size(); k++) if (group.members.get(k).result.is(result)) m = k;
                if (m < 0) continue;
                group.sel = m;
                w.page = i / Wheel.PER_PAGE;
                float[] p = w.centre(i);
                return new double[] {p[0], p[1]};
            }
            throw new IllegalStateException("no sector for " + result + " among " + w.shown.size());
        });
    }

    /** The recipes of the open wheel in their order. */
    private String order() {
        return ctx.computeOnClient(mc -> BeezzCraft.wheel.shown.stream().map(g -> g.current().itemId()).toList().toString());
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
            resizeGui(mc);
        });
        ctx.runOnClient(Minecraft::reloadResourcePacks);
        ctx.waitFor(mc -> overlay(mc) == null, 20 * 120);
        Config.get().queue = false;
        Config.get().recent.clear();
        Config.get().counts.clear();

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
            String before = order();
            int sticks = count(Items.STICK);
            double[] stick = sectorOf(Items.STICK);
            click(stick[0], stick[1], LEFT);
            waitCount(Items.STICK, sticks + 4);
            check("click crafts sticks once: " + sticks + " -> " + count(Items.STICK), count(Items.STICK) == sticks + 4);
            ctx.waitTicks(12); // the wheel has refreshed by now
            check("crafting does not move the recipes around", order().equals(before));
            ctx.waitTicks(8); // the wheel re-sorts after a craft
            table = sectorOf(Items.CRAFTING_TABLE);
            click(table[0], table[1], LEFT);
            waitCount(Items.CRAFTING_TABLE, 1);
            check("crafting table crafted in the inventory", count(Items.CRAFTING_TABLE) == 1);
            // Ctrl: ten at once (here the planks allow three), all in one go, not one by one
            server.runCommand("give @a oak_planks 6");
            ctx.waitTicks(5);
            int before10 = count(Items.STICK), planks6 = count(Items.OAK_PLANKS);
            stick = sectorOf(Items.STICK);
            cursor(stick[0], stick[1]);
            ctx.getInput().holdControl();
            ctx.getInput().pressMouse(LEFT);
            ctx.getInput().releaseControl();
            int ticks = ctx.waitFor(mc -> mc.player.getInventory().countItem(Items.STICK) >= before10 + planks6 / 2 * 4, 40);
            check("Ctrl + click crafts all it can at once (" + planks6 / 2 + " crafts in " + ticks + " ticks)", ticks <= 4);
            server.runCommand("give @a oak_planks 8");
            ctx.waitTicks(5);
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
            check("Esc closes only the wheel", ctx.computeOnClient(mc -> BeezzCraft.wheel == null && BeezzCraft.screen(mc) instanceof InventoryScreen));
            wheelOn(Items.STICK);
            // the gear is hidden while the wheel is open: close the wheel, then open the settings
            ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
            ctx.waitTicks(3);
            click(16, 16, LEFT);
            shot("craft_04_settings");
            click(90, 6 + 20 + 6 + 4 + 9, LEFT);
            check("the switch turns the craft queue on", Config.get().queue);
            click(16, 16, LEFT); // close the settings
            ctx.waitTicks(8);
            wheelOn(Items.STICK);
            double[] torch = sectorOf(Items.TORCH);
            cursor(torch[0], torch[1]);
            shot("craft_05_grey_torch");
            ctx.getInput().holdControl();
            ctx.getInput().pressMouse(LEFT);
            ctx.getInput().releaseControl();
            ctx.waitTicks(5);
            ctx.getInput().pressMouse(LEFT);
            ctx.waitTicks(3);
            ctx.getInput().pressMouse(LEFT);
            ctx.waitTicks(5);
            check("the same recipe queued again adds up on one line: Ctrl + 2 clicks = ×12",
                    ctx.computeOnClient(mc -> Crafter.jobs().stream().filter(j -> j.queued).count() == 1
                            && Crafter.jobs().stream().anyMatch(j -> j.queued && j.left == 12)));
            shot("craft_06_queued");
            int coalMissing = ctx.computeOnClient(mc -> Needs.of(mc.player, mc.player.inventoryMenu).stream()
                    .filter(n -> n.icon().is(Items.COAL) || n.icon().is(Items.CHARCOAL)).mapToInt(Needs.Item::missing).sum());
            check("what to get: 12 coal for the 12 queued torches (" + coalMissing + ")", coalMissing == 12);
            ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
            ctx.waitTicks(3);
            shot("craft_06b_needs_in_inventory");
            ctx.setScreen(() -> null);
            ctx.waitTicks(10);
            shot("craft_06c_needs_while_playing");
            server.runCommand("give @a coal 2");
            waitCount(Items.TORCH, 8);
            check("queued torches crafted by themselves once coal came: " + count(Items.TORCH), count(Items.TORCH) == 8);
            check("what the coal could not cover stays queued: ×10 left",
                    ctx.computeOnClient(mc -> Crafter.jobs().stream().anyMatch(j -> j.queued && j.left == 10)));
            ctx.waitTicks(10);
            shot("craft_07_queue_hud");
            ctx.runOnClient(mc -> Crafter.clear());

            // a recipe the recipe book does not have (a torch before any stone pickaxe): laid out by hand clicks
            server.runCommand("recipe take @a minecraft:torch");
            server.runCommand("give @a coal 1");
            ctx.waitTicks(10);
            ctx.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
            ctx.waitForScreen(InventoryScreen.class);
            int torches = count(Items.TORCH);
            wheelOn(Items.COAL);
            check("a torch shows up although the recipe book does not know it", ctx.computeOnClient(mc ->
                    BeezzCraft.wheel.shown.stream().anyMatch(g -> g.members.stream().anyMatch(o -> o.result.is(Items.TORCH) && o.entry == null))));
            double[] t2 = sectorOf(Items.TORCH);
            click(t2[0], t2[1], LEFT);
            waitCount(Items.TORCH, torches + 4);
            check("the unknown torch recipe is crafted by hand: " + torches + " -> " + count(Items.TORCH), count(Items.TORCH) == torches + 4);
            ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
            ctx.waitTicks(3);
            ctx.setScreen(() -> null);
            ctx.waitTicks(5);

            // 4) at a crafting table: 3x3 recipes from iron
            server.runCommand("give @a stick 4");
            BlockPos pos = ctx.computeOnClient(mc -> mc.player.blockPosition().north(2));
            server.runCommand("setblock " + pos.getX() + " " + pos.getY() + " " + pos.getZ() + " crafting_table");
            ctx.waitTicks(10);
            ctx.runOnClient(mc -> lookAt(mc, pos));
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

            // sticks at the table: more recipes than one page holds
            ctx.getInput().pressKey(InputConstants.KEY_ESCAPE);
            ctx.waitTicks(3);
            wheelOn(Items.STICK);
            int[] info = ctx.computeOnClient(mc -> new int[] {BeezzCraft.wheel.shown.size(), BeezzCraft.wheel.pages(),
                    (int) BeezzCraft.wheel.shown.stream().filter(g -> g.current().result.is(Items.TORCH)).count()});
            int torchAt = ctx.computeOnClient(mc -> {
                for (int i = 0; i < BeezzCraft.wheel.shown.size(); i++) if (BeezzCraft.wheel.shown.get(i).current().result.is(Items.TORCH)) return i;
                return -1;
            });
            BeezzCraft.LOG.info("BeezzCraft test: stick at the table: {} recipes, {} pages, torch at {}", info[0], info[1], torchAt);
            check("a torch short of only coal is on the first page, not lost among wood variants", torchAt >= 0 && torchAt < Wheel.PER_PAGE);
            check("sticks at the table fit on one page (" + info[0] + " buttons)", info[1] == 1);
            String pickaxeShown = ctx.computeOnClient(mc -> BeezzCraft.wheel.shown.stream().filter(g -> g.family.equals("pickaxe"))
                    .map(g -> g.current().itemId()).findFirst().orElse("none"));
            check("the pickaxe button opens on the iron pickaxe crafted last: " + pickaxeShown, pickaxeShown.equals("minecraft:iron_pickaxe"));
            String pickaxes = ctx.computeOnClient(mc -> {
                StringBuilder sb = new StringBuilder();
                int last = -2;
                boolean together = true;
                for (int i = 0; i < BeezzCraft.wheel.shown.size(); i++) {
                    var grp = BeezzCraft.wheel.shown.get(i);
                    if (!grp.family.equals("pickaxe")) continue;
                    if (last >= 0) together = false; // one button
                    last = i;
                    for (var m : grp.members) sb.append(m.itemId().substring(10)).append(' ');
                }
                return (together ? "together: " : "SPLIT: ") + sb.toString().trim();
            });
            BeezzCraft.LOG.info("BeezzCraft test: pickaxes {}", pickaxes);
            check("all pickaxes are one button, tiers in order from wooden to diamond", pickaxes.startsWith("together: wooden_pickaxe stone_pickaxe")
                    && pickaxes.indexOf("iron_pickaxe") < pickaxes.indexOf("golden_pickaxe") && pickaxes.indexOf("golden_pickaxe") < pickaxes.indexOf("diamond_pickaxe"));
            int fences = ctx.computeOnClient(mc -> BeezzCraft.wheel.shown.stream().filter(g -> g.family.equals("fence")).mapToInt(g -> g.members.size()).sum());
            int fenceButtons = ctx.computeOnClient(mc -> (int) BeezzCraft.wheel.shown.stream().filter(g -> g.family.equals("fence")).count());
            check("all " + fences + " wooden fences are one button", fences > 5 && fenceButtons == 1);
            java.util.List<java.util.List<String>> torchButtons = ctx.computeOnClient(mc -> BeezzCraft.wheel.shown.stream()
                    .filter(g -> g.members.stream().anyMatch(o -> o.result.is(Items.TORCH) || o.result.is(Items.SOUL_TORCH)))
                    .map(g -> g.members.stream().map(o -> o.itemId().substring(10)).toList()).toList());
            check("the torch and the soul torch are one button: " + torchButtons, torchButtons.size() == 1
                    && torchButtons.getFirst().contains("torch") && torchButtons.getFirst().contains("soul_torch"));

            // multiplayer: the recipes come from the game's own data instead of the server
            ctx.runOnClient(mc -> {
                System.setProperty("beezzcraft.vanillaData", "true");
                Recipes.forget();
            });
            int known = ctx.computeOnClient(mc -> Recipes.catalog(mc).size());
            ctx.runOnClient(mc -> {
                System.clearProperty("beezzcraft.vanillaData");
                Recipes.forget();
            });
            check("the game's own recipe data reads (multiplayer): " + known + " crafting recipes", known > 400);
            customRecipeTest();
            shot("craft_10_table_sticks_page1");
            ctx.getInput().scroll(-1);
            ctx.waitTicks(5);
            shot("craft_11_table_sticks_page2");
            ctx.setScreen(() -> new Screen(Component.literal("overlay")) {});
            ctx.waitTicks(5);
            check("the wheel stays open over another screen", ctx.computeOnClient(mc -> BeezzCraft.wheel != null));
            shot("craft_overlay_wheel");
            ctx.setScreen(() -> null);

            // creative inventory: the gear and the wheel must show there too
            server.runCommand("gamemode creative @a");
            ctx.waitTicks(5);
            ctx.setScreen(() -> new CreativeModeInventoryScreen(Minecraft.getInstance().player,
                    Minecraft.getInstance().player.connection.enabledFeatures(), true));
            ctx.waitForScreen(CreativeModeInventoryScreen.class);
            double[] creativeSlot = anySlot();
            cursor(creativeSlot[0], creativeSlot[1]);
            ctx.getInput().holdControl();
            ctx.getInput().pressMouse(RIGHT);
            ctx.getInput().releaseControl();
            ctx.waitTicks(5);
            check("the wheel opens in the creative inventory", ctx.computeOnClient(mc -> BeezzCraft.wheel != null));

            // the gear panel over the creative tabs: its top row must still be clickable
            ctx.runOnClient(mc -> {
                mc.options.guiScale().set(4);
                resizeGui(mc);
            });
            ctx.setScreen(() -> new CreativeModeInventoryScreen(Minecraft.getInstance().player,
                    Minecraft.getInstance().player.connection.enabledFeatures(), true));
            ctx.waitForScreen(CreativeModeInventoryScreen.class);
            ctx.waitTicks(3);
            ctx.runOnClient(mc -> Config.get().queue = false);
            click(16, 16, LEFT); // the gear
            shot("craft_12_creative_settings");
            click(164, 45, LEFT); // the queue row, over the first creative tab at this scale
            check("the settings row over the creative tabs still toggles", ctx.computeOnClient(mc -> Config.get().queue));
            click(16, 16, LEFT); // close the settings
            ctx.runOnClient(mc -> {
                mc.options.guiScale().set(3);
                resizeGui(mc);
            });
            ctx.setScreen(() -> null);
        }
        configScreenTest();
        ctx.runOnClient(mc -> {
            Config.get().queue = false;
            Config.get().recent.clear();
            Config.get().save();
        });
        BeezzCraft.LOG.info("BeezzCraft test: all steps done");
    }

    /** Mod Menu screen: key-only mode disables the click button, and the reset button restores defaults. */
    /** Mod Menu screen: key-only mode disables the click button, and reset/save applies the defaults. */
    private void configScreenTest() {
        ctx.setScreen(() -> new BeezzCraftConfigScreen(BeezzCraft.screen(Minecraft.getInstance())));
        ctx.waitForScreen(BeezzCraftConfigScreen.class);
        ctx.runOnClient(mc -> {
            BeezzCraftConfigScreen screen = (BeezzCraftConfigScreen) BeezzCraft.screen(mc);
            try {
                Field tempField = BeezzCraftConfigScreen.class.getDeclaredField("temp");
                tempField.setAccessible(true);
                Config temp = (Config) tempField.get(screen);
                Field clickField = BeezzCraftConfigScreen.class.getDeclaredField("clickButton");
                clickField.setAccessible(true);
                Method rebuild = Screen.class.getDeclaredMethod("rebuildWidgets");
                rebuild.setAccessible(true);
                Method restore = BeezzCraftConfigScreen.class.getDeclaredMethod("restoreDefaults");
                restore.setAccessible(true);
                Method save = BeezzCraftConfigScreen.class.getDeclaredMethod("save");
                save.setAccessible(true);

                temp.keyOnly = true;
                rebuild.invoke(screen);
                Button click = (Button) clickField.get(screen);
                check("functional click is disabled in key-only mode", !click.active);

                temp.functionalClick = 0;
                temp.queue = true;
                temp.showMissing = false;
                temp.showNeeds = false;
                temp.sound = false;
                temp.ctrlCount = 3;
                restore.invoke(screen);
                check("restore defaults resets key-only mode", !temp.keyOnly);
                check("restore defaults resets the functional click", temp.functionalClick == 1);
                check("restore defaults resets the other settings", !temp.queue && temp.showMissing
                        && temp.showNeeds && temp.sound && temp.ctrlCount == 10);
                save.invoke(screen);
                Config live = Config.get();
                check("save applies the reset settings", !live.queue && live.showMissing && live.showNeeds
                        && live.sound && live.ctrlCount == 10 && !live.keyOnly && live.functionalClick == 1);
                check("restore defaults resets the functional key",
                        BeezzCraftKeys.MODIFIER.saveString().equals("key.keyboard.left.control"));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        });
        ctx.setScreen(() -> null);
    }

    /** A custom CraftingRecipe (not Shaped/Shapeless) must still be read by the generic branch. */
    private void customRecipeTest() {
        ctx.runOnClient(mc -> {
            var all = mc.getSingleplayerServer().getRecipeManager().getRecipes().stream()
                    .map(h -> h.value()).toList();
            ShapelessRecipe shapeless = all.stream().filter(r -> r instanceof ShapelessRecipe)
                    .map(r -> (ShapelessRecipe) r).findFirst().orElse(null);
            ShapedRecipe shaped = all.stream().filter(r -> r instanceof ShapedRecipe)
                    .map(r -> (ShapedRecipe) r).findFirst().orElse(null);
            if (shapeless == null || shaped == null) throw new IllegalStateException("no base recipes");
            try {
                Method add = Recipes.class.getDeclaredMethod("add", java.util.List.class, Recipe.class, ContextMap.class);
                add.setAccessible(true);
                java.util.List<Recipes.Option> out = new java.util.ArrayList<>();

                add.invoke(null, out, custom(shapeless), Recipes.context(mc));
                check("custom CraftingRecipe (shapeless) is read",
                        out.stream().anyMatch(o -> o.width == 0 && !o.reqs.isEmpty()));

                out.clear();
                add.invoke(null, out, custom(shaped), Recipes.context(mc));
                check("custom CraftingRecipe (shaped) is read", out.stream().anyMatch(o -> o.width == shaped.getWidth()
                        && o.height == shaped.getHeight() && !o.cells.isEmpty()));
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    /** A proxy that is a CraftingRecipe but neither ShapedRecipe nor ShapelessRecipe. */
    private static CraftingRecipe custom(CraftingRecipe base) {
        return (CraftingRecipe) Proxy.newProxyInstance(CraftingRecipe.class.getClassLoader(),
                new Class<?>[] {CraftingRecipe.class}, (proxy, method, args) -> {
                    try {
                        return method.invoke(base, args);
                    } catch (java.lang.reflect.InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }

    /** mc.resizeGui() since 26.x, mc.resizeDisplay() in 1.21.x. */
    private static void resizeGui(Minecraft mc) {
        try {
            Minecraft.class.getMethod("resizeGui").invoke(mc);
        } catch (NoSuchMethodException e) {
            try {
                Minecraft.class.getMethod("resizeDisplay").invoke(mc);
            } catch (ReflectiveOperationException e2) {
                throw new IllegalStateException(e2);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Looks at the centre of a block; TestInput.lookAt(BlockPos) exists only in newer Fabric API. */
    private static void lookAt(Minecraft mc, BlockPos pos) {
        var eye = mc.player.getEyePosition();
        var target = net.minecraft.world.phys.Vec3.atCenterOf(pos);
        double dx = target.x - eye.x, dy = target.y - eye.y, dz = target.z - eye.z;
        double flat = Math.sqrt(dx * dx + dz * dz);
        mc.player.setYRot((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0));
        mc.player.setXRot((float) -Math.toDegrees(Math.atan2(dy, flat)));
    }

    /** The loading overlay: mc.gui.overlay() since 26.2, mc.getOverlay() in 26.1. */
    private static Object overlay(Minecraft mc) {
        try {
            Object gui = Minecraft.class.getField("gui").get(mc);
            return gui.getClass().getMethod("overlay").invoke(gui);
        } catch (ReflectiveOperationException e) {
            try {
                return Minecraft.class.getMethod("getOverlay").invoke(mc);
            } catch (ReflectiveOperationException e2) {
                throw new IllegalStateException(e2);
            }
        }
    }
}
