package land.beezz.craft;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Temporary automated screenshot run used while checking every build. It is dormant unless the system property
 * {@code beezzcraft.autotest} is set, and it is removed from the release branches after the check.
 */
public final class AutoTest {
    private static int ticks;
    private static int phase;
    private static boolean wheelShot;
    private static boolean craftedShot;

    private AutoTest() {}

    public static void tick(Minecraft mc) {
        if (!Boolean.getBoolean("beezzcraft.autotest")) return;
        if (mc.player == null || mc.level == null) {
            ticks = 0;
            return;
        }
        ticks++;
        if (phase == 0 && ticks == 10) {
            mc.player.connection.sendCommand("gamemode survival @s");
        }
        if (phase == 0 && ticks == 20) {
            if (mc.player.getInventory().countItem(Items.OAK_PLANKS) == 0) {
                mc.player.connection.sendCommand("give @s oak_planks 12");
            }
            if (mc.player.getInventory().countItem(Items.STICK) == 0) {
                mc.player.connection.sendCommand("give @s stick 2");
            }
        }
        if (phase == 0 && ticks == 30 && mc.player.getInventory().countItem(Items.OAK_PLANKS) == 0) {
            mc.player.getInventory().add(new ItemStack(Items.OAK_PLANKS, 12));
            mc.player.getInventory().add(new ItemStack(Items.STICK, 2));
        }
        if (phase == 0 && ticks >= 40 && BeezzCraft.screen(mc) == null) {
            setScreen(mc, new InventoryScreen(mc.player));
            phase = 1;
            ticks = 0;
            return;
        }
        if (phase == 1 && ticks >= 10 && BeezzCraft.screen(mc) instanceof AbstractContainerScreen<?> screen) {
            ItemStack source = find(screen, Items.OAK_PLANKS);
            if (source == null) source = find(screen, Items.STICK);
            if (source == null) {
                BeezzCraft.LOG.warn("autotest: no planks or sticks in the inventory");
                mc.stop();
                return;
            }
            double[] p = slot(screen, source);
            BeezzCraft.open(screen, source, p[0], p[1]);
            phase = 2;
            ticks = 0;
            return;
        }
        if (phase == 2 && ticks >= 10) {
            shot(mc, "01_wheel");
            wheelShot = true;
            phase = 3;
            ticks = 0;
            return;
        }
        if (phase == 3 && ticks >= 5) {
            if (BeezzCraft.screen(mc) instanceof AbstractContainerScreen<?> screen && BeezzCraft.wheel != null) {
                Wheel w = BeezzCraft.wheel;
                for (int i = 0; i < w.shown.size(); i++) {
                    Groups.Group g = w.shown.get(i);
                    for (int k = 0; k < g.members.size(); k++) {
                        if (g.members.get(k).result.is(Items.STICK)) {
                            g.sel = k;
                            w.page = i / Wheel.PER_PAGE;
                            float[] c = w.centre(i);
                            BeezzCraft.mouse(screen, c[0], c[1], InputConstants.MOUSE_BUTTON_LEFT, false, false, false);
                            phase = 4;
                            ticks = 0;
                            return;
                        }
                    }
                }
            }
            phase = 4;
            ticks = 0;
            return;
        }
        if (phase == 4 && ticks >= 25) {
            BeezzCraft.wheel = null;
            phase = 5;
            ticks = 0;
            return;
        }
        if (phase == 5 && ticks >= 5) {
            shot(mc, "02_crafted");
            craftedShot = true;
            phase = 6;
            ticks = 0;
            return;
        }
        if (phase == 6 && ticks >= 5) {
            BeezzCraft.LOG.info("autotest: done wheel={} crafted={}", wheelShot, craftedShot);
            mc.stop();
            phase = 7;
        }
    }

    private static ItemStack find(AbstractContainerScreen<?> screen, Item item) {
        for (Slot slot : screen.getMenu().slots) {
            if (slot.container instanceof Inventory && slot.getItem().is(item)) return slot.getItem();
        }
        return null;
    }

    private static double[] slot(AbstractContainerScreen<?> screen, ItemStack stack) {
        try {
            Field left = AbstractContainerScreen.class.getDeclaredField("leftPos");
            Field top = AbstractContainerScreen.class.getDeclaredField("topPos");
            left.setAccessible(true);
            top.setAccessible(true);
            int l = left.getInt(screen), t = top.getInt(screen);
            for (Slot slot : screen.getMenu().slots) {
                if (slot.getItem() == stack) return new double[] {l + slot.x + 8, t + slot.y + 8};
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        throw new IllegalStateException("slot not found");
    }

    private static void setScreen(Minecraft mc, Screen screen) {
        try {
            Minecraft.class.getMethod("setScreen", Screen.class).invoke(mc, screen);
            return;
        } catch (NoSuchMethodException ignored) {
            // 26.2+
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        try {
            Minecraft.class.getMethod("setScreenAndShow", Screen.class).invoke(mc, screen);
            return;
        } catch (NoSuchMethodException ignored) {
            // older 26.x
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        try {
            Object gui = Minecraft.class.getField("gui").get(mc);
            gui.getClass().getMethod("setScreen", Screen.class).invoke(gui, screen);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void shot(Minecraft mc, String suffix) {
        try {
            Path dir = Path.of(System.getProperty("beezzcraft.autotest.out", "."));
            Files.createDirectories(dir);
            String prefix = System.getProperty("beezzcraft.autotest.name", "beezzcraft");
            Path file = dir.resolve(prefix + "_" + suffix + ".png");
            Object target;
            try {
                target = Minecraft.class.getMethod("getMainRenderTarget").invoke(mc);
            } catch (NoSuchMethodException e) {
                Object renderer = Minecraft.class.getField("gameRenderer").get(mc);
                target = renderer.getClass().getMethod("mainRenderTarget").invoke(renderer);
            }

            // 1.21.5+ / 26.x: the image is handed to a consumer.
            try {
                Method take = Screenshot.class.getMethod("takeScreenshot", RenderTargetClass(), Consumer.class);
                take.invoke(null, target, (Consumer<NativeImage>) image -> write(image, file));
                BeezzCraft.LOG.info("autotest: saved {}", file);
                return;
            } catch (NoSuchMethodException ignored) {
                // 1.21 - 1.21.4 returns the image directly.
            }
            try {
                Method take = Screenshot.class.getMethod("takeScreenshot", RenderTargetClass());
                Object image = take.invoke(null, target);
                write((NativeImage) image, file);
                BeezzCraft.LOG.info("autotest: saved {}", file);
                return;
            } catch (NoSuchMethodException ignored) {
                // very old fallback below
            }
            Method grab = Screenshot.class.getMethod("grab", File.class, String.class, RenderTargetClass(), Consumer.class);
            File gameDir = dir.toFile();
            grab.invoke(null, gameDir, file.getFileName().toString(), target, (Consumer<?>) c -> {});
            BeezzCraft.LOG.info("autotest: grabbed {}", file);
        } catch (ReflectiveOperationException | java.io.IOException e) {
            BeezzCraft.LOG.warn("autotest: screenshot failed: {}", e.toString());
        }
    }

    private static Class<?> RenderTargetClass() {
        try {
            return Class.forName("com.mojang.blaze3d.pipeline.RenderTarget");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void write(NativeImage image, Path file) {
        try {
            image.writeToFile(new File(file.toString()));
        } catch (java.io.IOException e) {
            BeezzCraft.LOG.warn("autotest: cannot write {}: {}", file, e.toString());
        } finally {
            image.close();
        }
    }
}
