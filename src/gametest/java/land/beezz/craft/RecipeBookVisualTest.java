package land.beezz.craft;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.item.Items;

/** Temporary visual reproduction: the vanilla recipe book open behind the wheel and its card. */
public class RecipeBookVisualTest implements FabricClientGameTest {
    private ClientGameTestContext ctx;

    private void shot(String name) {
        ctx.waitTicks(8);
        ctx.takeScreenshot(name);
    }

    private void cursor(double x, double y) {
        double scale = ctx.computeOnClient(mc -> (double) mc.getWindow().getWidth() / BeezzCraft.screen(mc).width);
        ctx.getInput().setCursorPos(x * scale, y * scale);
        ctx.waitTicks(3);
    }

    @Override
    public void runTest(ClientGameTestContext ctx) {
        this.ctx = ctx;
        ctx.getInput().resizeWindow(1280, 720);
        ctx.runOnClient(mc -> {
            mc.options.languageCode = "ru_ru";
            mc.getLanguageManager().setSelected("ru_ru");
            mc.options.guiScale().set(2);
        });
        try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
            ctx.waitTicks(60);
            for (String c : new String[] {"gamemode survival @a", "clear @a", "recipe give @a *",
                    "give @a oak_planks 12", "give @a stick 2", "give @a crafting_table 1"}) {
                world.getServer().runCommand(c);
            }
            ctx.waitTicks(20);
            ctx.runOnClient(mc -> mc.player.getRecipeBook().setOpen(RecipeBookType.CRAFTING, true));
            ctx.setScreen(() -> new InventoryScreen(Minecraft.getInstance().player));
            ctx.waitForScreen(InventoryScreen.class);
            ctx.waitTicks(10);
            shot("book_00_open");

            ctx.runOnClient(mc -> {
                InventoryScreen s = (InventoryScreen) mc.screen;
                var slot = s.getMenu().slots.stream()
                        .filter(x -> x.hasItem() && x.getItem().is(Items.OAK_PLANKS)).findFirst().orElseThrow();
                BeezzCraft.open(s, slot.getItem(), 150, 200);
            });
            ctx.waitTicks(10);
            shot("book_01_wheel_sticks");

            cursor(200, 220);
            shot("book_02_wheel_hover_sticks");

            ctx.runOnClient(mc -> {
                Wheel w = BeezzCraft.wheel;
                for (int i = 0; i < w.shown.size(); i++) {
                    Groups.Group group = w.shown.get(i);
                    for (int k = 0; k < group.members.size(); k++) {
                        if (group.members.get(k).result.is(Items.STICK)) group.sel = k;
                    }
                }
            });
            ctx.waitTicks(5);
            shot("book_03_wheel_stick_selected");

            ctx.runOnClient(mc -> {
                Wheel w = BeezzCraft.wheel;
                for (int i = 0; i < w.shown.size(); i++) {
                    Groups.Group group = w.shown.get(i);
                    for (int k = 0; k < group.members.size(); k++) {
                        if (group.members.get(k).result.is(Items.STICK)) {
                            group.sel = k;
                            w.page = i / Wheel.PER_PAGE;
                            float[] p = w.centre(i);
                            BeezzCraft.LOG.info("BeezzCraft test: stick sector at {} {}", p[0], p[1]);
                        }
                    }
                }
            });
            ctx.waitTicks(5);
            double[] p = ctx.computeOnClient(mc -> {
                Wheel w = BeezzCraft.wheel;
                for (int i = 0; i < w.shown.size(); i++) {
                    Groups.Group group = w.shown.get(i);
                    for (int k = 0; k < group.members.size(); k++) {
                        if (group.members.get(k).result.is(Items.STICK)) return new double[] {w.centre(i)[0], w.centre(i)[1]};
                    }
                }
                throw new IllegalStateException("no stick sector");
            });
            cursor(p[0], p[1]);
            shot("book_04_card_over_book");

            // Move the cursor so the card is placed over the recipe book panel, not only the wheel.
            cursor(420, 200);
            shot("book_05_card_on_book");
        }
    }
}
