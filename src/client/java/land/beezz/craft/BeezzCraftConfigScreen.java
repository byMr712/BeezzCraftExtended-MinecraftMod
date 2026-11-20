package land.beezz.craft;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Mod Menu config screen, laid out like the other Mr712 mods: a full-width master switch at the top, two
 * columns of toggle buttons, and Reset / Save / Cancel at the bottom.
 */
public final class BeezzCraftConfigScreen extends Screen {
    private final Screen parent;
    private final Config temp;
    private ConfigButton clickButton;
    private boolean listening;
    private InputConstants.Key pendingKey;

    public BeezzCraftConfigScreen(Screen lastScreen) {
        super(Tr.t("beezzcraft.config.title"));
        this.parent = lastScreen;
        this.temp = Config.get().copySettings();
        // The Mod Menu is not a container screen: do not keep the inventory wheel above it.
        BeezzCraft.wheel = null;
        Panel.close();
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int x = centerX - 160;
        int y = 34;
        int row = 24;
        int half = 156;
        int col2 = x + half + 8;

        // Master switch across the whole width.
        addRenderableWidget(new ConfigButton(x, y, 320, 20,
                toggle("beezzcraft.config.enabled", temp.enabled),
                () -> {
                    temp.enabled = !temp.enabled;
                    rebuildWidgets();
                },
                () -> lines("beezzcraft.config.enabled.tip1", "beezzcraft.config.enabled.tip2")));

        addRenderableWidget(new ConfigButton(x, y + row, half, 20,
                toggle("beezzcraft.config.key_only.short", temp.keyOnly),
                () -> {
                    temp.keyOnly = !temp.keyOnly;
                    rebuildWidgets();
                },
                () -> lines("beezzcraft.config.key_only.line1", "beezzcraft.config.key_only.line2",
                        "beezzcraft.config.key_only.line3")));
        clickButton = new ConfigButton(col2, y + row, half, 20,
                choice("beezzcraft.config.click.short",
                        temp.functionalClick == 0 ? "beezzcraft.set.click.left" : "beezzcraft.set.click.right"),
                () -> {
                    if (temp.keyOnly) return;
                    temp.functionalClick = temp.functionalClick == 0 ? 1 : 0;
                    rebuildWidgets();
                },
                () -> temp.keyOnly ? lines("beezzcraft.config.click.off")
                        : temp.functionalClick == 0
                        ? lines("beezzcraft.config.click.left.line1", "beezzcraft.config.click.left.line2")
                        : lines("beezzcraft.config.click.right.line1", "beezzcraft.config.click.right.line2"));
        clickButton.active = !temp.keyOnly;
        addRenderableWidget(clickButton);

        addRenderableWidget(new ConfigButton(x, y + row * 2, half, 20,
                choice("beezzcraft.config.position.short", Config.gearPositionKey(temp.gearPosition)),
                () -> {
                    temp.gearPosition = (temp.gearPosition + 1) & 3;
                    rebuildWidgets();
                },
                () -> lines("beezzcraft.config.position.tip")));
        addRenderableWidget(new ConfigButton(col2, y + row * 2, half, 20,
                keyLabel(),
                () -> {
                    listening = true;
                    rebuildWidgets();
                },
                () -> lines("beezzcraft.config.key.tip")));

        addRenderableWidget(new ConfigButton(x, y + row * 3, half, 20,
                toggle("beezzcraft.set.queue", temp.queue),
                () -> {
                    temp.queue = !temp.queue;
                    rebuildWidgets();
                },
                () -> lines("beezzcraft.set.queue.note1", "beezzcraft.set.queue.note2")));
        addRenderableWidget(new ConfigButton(col2, y + row * 3, half, 20,
                toggle("beezzcraft.set.missing", temp.showMissing),
                () -> {
                    temp.showMissing = !temp.showMissing;
                    rebuildWidgets();
                },
                () -> lines("beezzcraft.config.missing.tip1", "beezzcraft.config.missing.tip2",
                        "beezzcraft.config.missing.tip3")));

        addRenderableWidget(new ConfigButton(x, y + row * 4, half, 20,
                toggle("beezzcraft.set.needs", temp.showNeeds),
                () -> {
                    temp.showNeeds = !temp.showNeeds;
                    rebuildWidgets();
                },
                () -> lines("beezzcraft.config.needs.tip1", "beezzcraft.config.needs.tip2",
                        "beezzcraft.config.needs.tip3")));
        addRenderableWidget(new ConfigButton(col2, y + row * 4, half, 20,
                toggle("beezzcraft.set.sound", temp.sound),
                () -> {
                    temp.sound = !temp.sound;
                    rebuildWidgets();
                },
                () -> lines("beezzcraft.config.sound.tip1", "beezzcraft.config.sound.tip2")));

        int bottom = this.height - 28;
        addRenderableWidget(new ConfigButton(centerX - 155, bottom, 100, 20,
                Tr.t("beezzcraft.config.reset.button"),
                this::restoreDefaults,
                () -> lines("beezzcraft.config.reset.tip")));
        addRenderableWidget(Button.builder(Tr.t("beezzcraft.config.save"), b -> {
            this.save();
            this.onClose();
        }).bounds(centerX - 50, bottom, 100, 20).build());
        addRenderableWidget(Button.builder(Tr.t("beezzcraft.config.cancel"), b -> this.onClose())
                .bounds(centerX + 55, bottom, 100, 20).build());
    }

    private Component keyLabel() {
        Component key = pendingKey != null ? pendingKey.getDisplayName()
                : BeezzCraftKeys.MODIFIER.getTranslatedKeyMessage();
        return Tr.t("beezzcraft.config.key.short").copy().append(": ")
                .append(listening ? Tr.t("beezzcraft.config.press") : key);
    }

    private static Component toggle(String key, boolean on) {
        return Tr.t(key).copy().append(": ")
                .append(Tr.t(on ? "beezzcraft.config.on" : "beezzcraft.config.off")
                        .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED, ChatFormatting.BOLD));
    }

    private static Component choice(String key, String valueKey) {
        return Tr.t(key).copy().append(": ").append(Tr.t(valueKey).withStyle(ChatFormatting.YELLOW));
    }

    private static List<FormattedCharSequence> lines(String... keys) {
        List<FormattedCharSequence> lines = new ArrayList<>(keys.length);
        for (String key : keys) lines.add(Tr.t(key).getVisualOrderText());
        return lines;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (listening) {
            if (!event.isEscape()) pendingKey = InputConstants.getKey(event);
            listening = false;
            rebuildWidgets();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (listening) {
            pendingKey = InputConstants.Type.MOUSE.getOrCreate(event.button());
            listening = false;
            rebuildWidgets();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void restoreDefaults() {
        temp.reset();
        pendingKey = BeezzCraftKeys.MODIFIER.getDefaultKey();
        listening = false;
        rebuildWidgets();
    }

    private void save() {
        Config live = Config.get();
        live.copySettingsFrom(temp);
        live.save();
        if (pendingKey != null) {
            BeezzCraftKeys.MODIFIER.setKey(pendingKey);
            Minecraft.getInstance().options.save();
        }
        BeezzCraft.onConfigChanged();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(this.font, this.title, this.width / 2, 14, 0xFFFFFF);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) this.minecraft.gui.setScreen(this.parent);
    }

    private static final class ConfigButton extends Button {
        private final Supplier<List<FormattedCharSequence>> tooltip;

        private ConfigButton(int x, int y, int width, int height, Component label, Runnable onPress,
                             Supplier<List<FormattedCharSequence>> tooltip) {
            super(x, y, width, height, label, b -> onPress.run(), DEFAULT_NARRATION);
            this.tooltip = tooltip;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            extractDefaultSprite(graphics);
            Font font = Minecraft.getInstance().font;
            graphics.centeredText(font, getMessage(), getX() + getWidth() / 2,
                    getY() + (getHeight() - 8) / 2, 0xFFFFFF);
            List<FormattedCharSequence> lines = tooltip.get();
            if (isHovered() && !lines.isEmpty()) {
                graphics.setTooltipForNextFrame(font, lines, mouseX, mouseY);
            }
        }
    }
}
