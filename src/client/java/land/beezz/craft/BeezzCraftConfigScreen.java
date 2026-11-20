package land.beezz.craft;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Mod Menu config screen: key selection, key-only mode and the functional mouse button. */
public final class BeezzCraftConfigScreen extends OptionsSubScreen {
    private MultiLineButton keyButton;
    private MultiLineButton keyOnlyButton;
    private MultiLineButton clickButton;
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
        resetButton = new MultiLineButton(this::restoreDefaults, () -> lines("beezzcraft.config.reset.tip"));
        list.addSmall(List.of(keyButton));
        list.addSmall(List.of(keyOnlyButton));
        list.addSmall(List.of(clickButton));
        list.addSmall(List.of(resetButton));
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
    }

    private List<FormattedCharSequence> clickTooltip() {
        if (Config.get().keyOnly) return lines("beezzcraft.config.click.off");
        return Config.get().functionalClick == 0
                ? lines("beezzcraft.config.click.left.line1", "beezzcraft.config.click.left.line2")
                : lines("beezzcraft.config.click.right.line1", "beezzcraft.config.click.right.line2");
    }

    private static List<FormattedCharSequence> lines(String... keys) {
        List<FormattedCharSequence> lines = new ArrayList<>(keys.length);
        for (String key : keys) lines.add(Tr.t(key).getVisualOrderText());
        return lines;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (listening) {
            if (keyCode != InputConstants.KEY_ESCAPE) {
                BeezzCraftKeys.MODIFIER.setKey(InputConstants.getKey(keyCode, scanCode));
                Minecraft.getInstance().options.save();
            }
            listening = false;
            updateLabels();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (listening) {
            BeezzCraftKeys.MODIFIER.setKey(InputConstants.Type.MOUSE.getOrCreate(button));
            Minecraft.getInstance().options.save();
            listening = false;
            updateLabels();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void restoreDefaults(Button button) {
        Config.get().reset();
        Config.get().save();
        BeezzCraftKeys.MODIFIER.setKey(InputConstants.getKey("key.keyboard.left.control"));
        Minecraft.getInstance().options.save();
        listening = false;
        updateLabels();
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
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
            if (isHovered()) {
                Font font = Minecraft.getInstance().font;
                graphics.renderTooltip(font, tooltip.get(), mouseX, mouseY);
            }
        }
    }
}
