package land.beezz.craft;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
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

/** Mod Menu config screen: key selection, key-only mode and the functional mouse button. */
public final class BeezzCraftConfigScreen extends OptionsSubScreen {
    private MultiLineButton keyButton;
    private MultiLineButton keyOnlyButton;
    private MultiLineButton clickButton;
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
        list.addSmall(List.of(keyButton));
        list.addSmall(List.of(keyOnlyButton));
        list.addSmall(List.of(clickButton));
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
    }

    private List<FormattedCharSequence> clickTooltip() {
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
    public boolean keyPressed(KeyEvent event) {
        if (listening) {
            if (event.input() != InputConstants.KEY_ESCAPE) {
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
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            super.renderWidget(graphics, mouseX, mouseY, partialTick);
            if (isHovered()) {
                Font font = Minecraft.getInstance().font;
                graphics.setTooltipForNextFrame(font, tooltip.get(), mouseX, mouseY);
            }
        }
    }
}
