/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.gui;

import meteordevelopment.meteorclient.settings.Setting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import xyz.thm.addon.shaders.ShaderManager;
import xyz.thm.addon.system.THMSystem;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT;

// Plain vanilla Screen - no Meteor GuiTheme/WidgetScreen dependency - styled with the same
// BleachHack window chrome as the title screen (see MainMenuFx). Reads/writes THMSystem's
// settings directly instead of going through Meteor's generic settings-widget factory.
public class MainMenuSettingsScreen extends Screen {
    private static final int MAX_WINDOW_WIDTH = 560;
    private static final int MIN_COLUMN_WIDTH = 100;
    private static final int ROW_HEIGHT = 22;

    private final Screen parent;
    private int x1, y1, x2, y2;

    // Both computed per-init from the actual screen size: the window stretches to the sides (up to
    // MAX_WINDOW_WIDTH) and the shader grid grows columns until the whole thing fits vertically -
    // 30 shaders at a fixed 3 columns overflowed the screen at larger GUI scales.
    private int windowWidth;
    private int columns;

    // Bounds of the shader-choice CyclingButtonWidget, so mouseClicked can detect a right-click
    // on it (vanilla CyclingButtonWidget only supports left-click-forward / shift+left-click-back,
    // no right-click). -1 width means "not currently shown" (random mode shows the pool grid
    // instead).
    private int shaderChoiceX, shaderChoiceY, shaderChoiceWidth = -1, shaderChoiceHeight;

    public MainMenuSettingsScreen(Screen parent) {
        super(Component.literal("THM Menu"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        THMSystem system = THMSystem.get();
        boolean random = system.shaderRandom.get();
        shaderChoiceWidth = -1;

        windowWidth = Math.min(this.width - 8, MAX_WINDOW_WIDTH);
        columns = Math.max(1, (windowWidth - 20) / MIN_COLUMN_WIDTH);
        int shaderCount = ShaderManager.availableShaders().size();
        while (random && columns < shaderCount && computeContentHeight(true) > this.height - 8) columns++;

        int windowHeight = computeContentHeight(random);

        x1 = Math.max(4, (this.width - windowWidth) / 2);
        y1 = Math.max(4, (this.height - windowHeight) / 2);
        x2 = x1 + windowWidth;
        y2 = y1 + windowHeight;

        int contentX = x1 + 10;
        int contentWidth = windowWidth - 20;
        int y = y1 + 16;

        y = addCheckbox("Styled Window", system.mainMenuWindow.get(), contentX, y, contentWidth,
            v -> system.mainMenuWindow.set(v));
        y = addCheckbox("Particle Trail", system.mainMenuParticles.get(), contentX, y, contentWidth,
            v -> system.mainMenuParticles.set(v));
        y = addCheckbox("Random Shader", random, contentX, y, contentWidth, v -> {
            system.shaderRandom.set(v);
            this.rebuildWidgets();
        });

        if (random) {
            y = addShaderPoolGrid(system, contentX, y, contentWidth);
        } else {
            y = addShaderChoice(system, contentX, y, contentWidth);
        }

        int sliderWidth = (contentWidth - 4) / 2;
        addSlider("Resolution (%)", system.shaderResolution, 25, 100, contentX, y, sliderWidth);
        addSlider("Shader FPS", system.shaderFps, 0, 120, contentX + sliderWidth + 4, y, contentWidth - sliderWidth - 4);
        y += ROW_HEIGHT;
        y = addSlider("Blur", system.mainMenuBlur, 0, 100, contentX, y, contentWidth);

        // Enters shader-only preview mode and drops back to the title screen (which strips its own
        // UI while previewMode is on). The exit ("Show UI") lives on the title screen since this
        // screen is hidden meanwhile - see TitleScreenMenuMixin.
        Button preview = this.addRenderableWidget(Button.builder(Component.literal("Preview Shader"), b -> {
                MainMenuFx.previewMode = true;
                this.minecraft.gui.setScreen(parent);
            })
            .bounds(contentX, y, contentWidth, 20)
            .build());
        ThmStyledButtons.mark(preview);
        y += ROW_HEIGHT;

        Button close = this.addRenderableWidget(Button.builder(Component.literal("Close"), b -> this.onClose())
            .bounds(contentX, y, contentWidth, 20)
            .build());
        ThmStyledButtons.mark(close);
    }

    private int addCheckbox(String label, boolean checked, int x, int y, int width, java.util.function.Consumer<Boolean> onChange) {
        Checkbox checkbox = Checkbox.builder(Component.literal(label), this.font)
            .pos(x, y)
            .selected(checked)
            .maxWidth(width)
            .onValueChange((cb, value) -> onChange.accept(value))
            .build();
        this.addRenderableWidget(checkbox);
        return y + ROW_HEIGHT;
    }

    private int addShaderChoice(THMSystem system, int x, int y, int width) {
        List<String> options = new ArrayList<>();
        options.add("None");
        options.addAll(ShaderManager.availableShaders());
        String current = options.contains(system.shaderChoice.get()) ? system.shaderChoice.get() : "None";

        // CyclingButtonWidget already composes "<optionText>: <value>" itself (build()'s
        // optionText param below) - a valueToText that also prepends "Shader: " doubles it up.
        CycleButton<String> widget = CycleButton.builder(Component::literal, current)
            .withValues(options)
            .create(x, y, width, 20, Component.literal("Shader"), (btn, value) -> system.shaderChoice.set(value));
        this.addRenderableWidget(widget);
        ThmStyledButtons.mark(widget);

        shaderChoiceX = x;
        shaderChoiceY = y;
        shaderChoiceWidth = width;
        shaderChoiceHeight = 20;

        return y + ROW_HEIGHT;
    }

    // Vanilla CyclingButtonWidget only cycles forward on left-click (or backward with shift held
    // - no right-click support at all), and it's built via a package-private constructor behind
    // a Builder, so it can't be subclassed to add that. Handling it here instead: right-click
    // within its bounds cycles backward, left-click still reaches the widget normally and cycles
    // forward as usual.
    //
    // Also handles the window chrome's "x"/"_" glyphs: this screen has nothing to "minimize" to
    // (unlike the title screen's own window, it isn't a stand-in for the vanilla button layout),
    // so both just close it, same as the Close button below.
    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (MainMenuFx.isCloseButton(x1, y1, x2, y2, click.x(), click.y())
                || MainMenuFx.isMinimizeButton(x1, y1, x2, y2, click.x(), click.y())) {
            this.onClose();
            return true;
        }
        if (shaderChoiceWidth > 0 && click.button() == GLFW_MOUSE_BUTTON_RIGHT
                && click.x() >= shaderChoiceX && click.x() < shaderChoiceX + shaderChoiceWidth
                && click.y() >= shaderChoiceY && click.y() < shaderChoiceY + shaderChoiceHeight) {
            cycleShaderChoice(-1);
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    private void cycleShaderChoice(int direction) {
        THMSystem system = THMSystem.get();
        List<String> options = new ArrayList<>();
        options.add("None");
        options.addAll(ShaderManager.availableShaders());

        int index = options.indexOf(system.shaderChoice.get());
        if (index < 0) index = 0;
        index = Math.floorMod(index + direction, options.size());

        system.shaderChoice.set(options.get(index));
        this.rebuildWidgets();
    }

    private int gridRows() {
        int count = ShaderManager.availableShaders().size();
        return (count + columns - 1) / columns;
    }

    // Mirrors init()'s Y increments exactly (16 top pad, 3 checkboxes, shader row(s), blur
    // slider, close button + bottom pad) - kept in sync by hand since it has to know the final
    // height *before* x1/y1 (and therefore any real widget) can be positioned.
    private int computeContentHeight(boolean random) {
        int y = 16;
        y += ROW_HEIGHT * 3; // Styled Window, Particle Trail, Random Shader
        y += random ? (12 + gridRows() * 14 + 4) : ROW_HEIGHT; // shader pool grid vs. shader choice
        y += ROW_HEIGHT * 2; // resolution/FPS and blur
        y += ROW_HEIGHT; // preview shader button
        y += 20 + 10; // close button + bottom padding
        return y;
    }

    // Simple checkbox grid instead of a scrollable two-column picker - column count comes from
    // init() (widest that fits, then more columns until the window fits the screen height).
    private int addShaderPoolGrid(THMSystem system, int x, int y, int width) {
        this.addRenderableWidget(new StringWidget(x, y, width, 12,
            Component.literal("Allowed shaders (none checked = allow all):"), this.font));
        y += 12;

        Set<String> selected = system.shaderPool.get().selected();
        List<String> shaders = ShaderManager.availableShaders();
        int columnWidth = width / columns;

        for (int i = 0; i < shaders.size(); i++) {
            String shader = shaders.get(i);
            int col = i % columns;
            int row = i / columns;

            Checkbox checkbox = Checkbox.builder(Component.literal(shader), this.font)
                .pos(x + col * columnWidth, y + row * 14)
                .selected(selected.contains(shader))
                .maxWidth(columnWidth - 4)
                .onValueChange((cb, checked) -> {
                    if (checked) selected.add(shader);
                    else selected.remove(shader);
                })
                .build();
            this.addRenderableWidget(checkbox);
        }

        return y + gridRows() * 14 + 4;
    }

    private int addSlider(String label, Setting<Integer> setting, int min, int max, int x, int y, int width) {
        int initial = setting.get();
        AbstractSliderButton slider = new AbstractSliderButton(x, y, width, 20, Component.literal(label + ": " + initial), (initial - min) / (double) (max - min)) {
            @Override
            protected void updateMessage() {
                this.setMessage(Component.literal(label + ": " + Math.round(min + this.value * (max - min))));
            }

            @Override
            protected void applyValue() {
                setting.set((int) Math.round(min + this.value * (max - min)));
            }
        };
        this.addRenderableWidget(slider);
        return y + ROW_HEIGHT;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        MainMenuFx.renderChrome(context, this.font, x1, y1, x2, y2, "THM Menu");
        super.extractRenderState(context, mouseX, mouseY, deltaTicks);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        // Keep whatever's already drawn behind this screen (the title screen's own shader/
        // window) instead of vanilla's dirt/blur background.
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }
}
