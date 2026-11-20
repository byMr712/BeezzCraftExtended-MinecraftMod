package land.beezz.craft;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** The Mod Menu config screen: key selection, key-only mode and the functional mouse button. */
public final class BeezzCraftConfigScreen extends OptionsSubScreen {
    private MultiLineButton keyButton;
    private MultiLineButton keyOnlyButton;
    private MultiLineButton clickButton;
    private MultiLineButton queueButton;
    private MultiLineButton missingButton;
    private MultiLineButton needsButton;
    private MultiLineButton soundButton;
    private MultiLineButton resetButton;
    private boolean listening;

    public BeezzCraftConfigScreen(Screen lastScreen) {
        super(lastScreen, Minecraft.getInstance().options, Tr.t("beezzcraft.config.title"));
    }

    @Override
    protected void addOptions() {
        keyButton = new MultiLineButton(this::pressKey, () -> lines("beezzcraft.config.key.tip"));
        keyOnlyButton = new MultiLineButton(this::toggleKeyOnly, () -> lines(
                "beezzcraft.config.key_only.line1",
                "beezzcraft.config.key_only.line2",
                "beezzcraft.config.key_only.line3"));
        clickButton = new MultiLineButton(this::toggleClick, this::clickTooltip);
        queueButton = new MultiLineButton(this::toggleQueue,
                () -> lines("beezzcraft.set.queue.note1", "beezzcraft.set.queue.note2"));
        missingButton = new MultiLineButton(this::toggleMissing, () -> lines("beezzcraft.config.toggle.tip"));
        needsButton = new MultiLineButton(this::toggleNeeds, () -> lines("beezzcraft.config.toggle.tip"));
        soundButton = new MultiLineButton(this::toggleSound, () -> lines("beezzcraft.config.toggle.tip"));
        resetButton = new MultiLineButton(this::restoreDefaults, () -> lines("beezzcraft.config.reset.tip"));
        list.addBig(keyButton);
        list.addBig(keyOnlyButton);
        list.addBig(clickButton);
        list.addHeader(Tr.t("beezzcraft.config.section.crafting"));
        list.addBig(queueButton);
        list.addBig(missingButton);
        list.addBig(needsButton);
        list.addBig(soundButton);
        list.addBig(resetButton);
        updateLabels();
    }

    private void pressKey(Button button) {
        listening = true;
        button.setMessage(Tr.t("beezzcraft.config.press"));
    }

    private void toggleKeyOnly(Button button) {
        Config config = Config.get();
        config.keyOnly = !config.keyOnly;
        config.save();
        updateLabels();
    }

    private void toggleClick(Button button) {
        Config config = Config.get();
        if (config.keyOnly) return;
        config.functionalClick = config.functionalClick == 0 ? 1 : 0;
        config.save();
        updateLabels();
    }

    private void toggleQueue(Button button) {
        Config config = Config.get();
        config.queue = !config.queue;
        if (!config.queue) Crafter.jobs().removeIf(j -> j.queued);
        config.save();
        updateLabels();
    }

    private void toggleMissing(Button button) {
        Config config = Config.get();
        config.showMissing = !config.showMissing;
        if (BeezzCraft.wheel != null) BeezzCraft.wheel.refresh();
        config.save();
        updateLabels();
    }

    private void toggleNeeds(Button button) {
        Config config = Config.get();
        config.showNeeds = !config.showNeeds;
        config.save();
        updateLabels();
    }

    private void toggleSound(Button button) {
        Config config = Config.get();
        config.sound = !config.sound;
        config.save();
        updateLabels();
    }

    private void updateLabels() {
        Config config = Config.get();
        keyButton.setMessage(Tr.t("beezzcraft.config.key",
                BeezzCraftKeys.MODIFIER.getTranslatedKeyMessage().getString()));
        keyOnlyButton.setMessage(Tr.t("beezzcraft.config.key_only",
                Tr.t(config.keyOnly ? "beezzcraft.config.on" : "beezzcraft.config.off")));
        clickButton.setMessage(Tr.t("beezzcraft.config.click",
                Tr.t(config.functionalClick == 0
                        ? "beezzcraft.config.left" : "beezzcraft.config.right")));
        clickButton.active = !config.keyOnly;
        queueButton.setMessage(toggle("beezzcraft.set.queue", config.queue));
        missingButton.setMessage(toggle("beezzcraft.set.missing", config.showMissing));
        needsButton.setMessage(toggle("beezzcraft.set.needs", config.showNeeds));
        soundButton.setMessage(toggle("beezzcraft.set.sound", config.sound));
        resetButton.setMessage(Tr.t("beezzcraft.config.reset"));
    }

    private static Component toggle(String key, boolean on) {
        return Tr.t("beezzcraft.config.toggle", Tr.t(key),
                Tr.t(on ? "beezzcraft.config.on" : "beezzcraft.config.off"));
    }

    private List<FormattedCharSequence> clickTooltip() {
        if (Config.get().keyOnly) return lines("beezzcraft.config.click.off");
        return Config.get().functionalClick == 0
                ? lines("beezzcraft.config.click.left.line1", "beezzcraft.config.click.left.line2")
                : lines("beezzcraft.config.click.right.line1", "beezzcraft.config.click.right.line2");
    }

    private void restoreDefaults(Button button) {
        Config.get().reset();
        Config.get().save();
        BeezzCraftKeys.MODIFIER.setKey(InputConstants.getKey("key.keyboard.left.control"));
        Minecraft.getInstance().options.save();
        listening = false;
        updateLabels();
    }

    private static List<FormattedCharSequence> lines(String... keys) {
        List<FormattedCharSequence> lines = new ArrayList<>(keys.length);
        for (String key : keys) lines.add(Tr.t(key).getVisualOrderText());
        return lines;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (listening) {
            if (!event.isEscape()) {
                BeezzCraftKeys.MODIFIER.setKey(InputConstants.getKey(event));
                Minecraft.getInstance().options.save();
            }
            listening = false;
            updateLabels();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (listening) {
            BeezzCraftKeys.MODIFIER.setKey(InputConstants.Type.MOUSE.getOrCreate(event.button()));
            Minecraft.getInstance().options.save();
            listening = false;
            updateLabels();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void removed() {
        Config.get().save();
        Minecraft.getInstance().options.save();
        super.removed();
    }

    private static final class MultiLineButton extends Button {
        private final Supplier<List<FormattedCharSequence>> tooltip;

        private MultiLineButton(OnPress onPress, Supplier<List<FormattedCharSequence>> tooltip) {
            super(0, 0, 200, 20, Component.empty(), onPress, DEFAULT_NARRATION);
            this.tooltip = tooltip;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
            extractDefaultSprite(graphics);
            Font font = Minecraft.getInstance().font;
            graphics.centeredText(font, getMessage(), getX() + getWidth() / 2,
                    getY() + (getHeight() - 8) / 2, 0xFFFFFFFF);
        }

        @Override
        protected void extractTooltipForNextRenderPass(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
            if (isHovered()) {
                Font font = Minecraft.getInstance().font;
                graphics.setTooltipForNextFrame(font, tooltip.get(), mouseX, mouseY);
            }
        }
    }
}
