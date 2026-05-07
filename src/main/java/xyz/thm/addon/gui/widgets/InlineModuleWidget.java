package xyz.thm.addon.gui.widgets;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.themes.meteor.MeteorGuiTheme;
import meteordevelopment.meteorclient.gui.widgets.containers.WSection;
import net.minecraft.client.gui.Click;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.gui.widgets.pressable.WCheckbox;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import xyz.thm.addon.mixin.accessor.WSectionAccessor;

import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT;

public class InlineModuleWidget extends WSection {
    private static InlineModuleWidget currentlyExpanded;

    private final Module module;
    private final String title;

    private WCheckbox enabledCheckbox;
    private WButton settingsButton;
    private boolean contentBuilt;

    // Animated active fill (two independent progresses like WMeteorModule)
    private double activeFillBg;
    private double activeFillEdge;

    public InlineModuleWidget(Module module, String title, boolean expanded) {
        super(title, expanded, null);
        this.module = module;
        this.title = title;
        this.tooltip = module.description;
        // Start at 1 if already active so there's no flash on first render
        this.activeFillBg   = module.isActive() ? 1.0 : 0.0;
        this.activeFillEdge = module.isActive() ? 1.0 : 0.0;
    }

    @Override
    public void init() {
        super.init();
        if (isExpanded()) ensureContentBuilt();
    }

    @Override
    protected WHeader createHeader() {
        return new InlineHeader(title);
    }

    @Override
    protected void onRender(meteordevelopment.meteorclient.gui.renderer.GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        if (enabledCheckbox != null) enabledCheckbox.checked = module.isActive();
        super.onRender(renderer, mouseX, mouseY, delta);
    }

    private void ensureContentBuilt() {
        if (!contentBuilt && !module.settings.groups.isEmpty()) {
            add(theme.settings(module.settings)).expandX().padTop(2);
            contentBuilt = true;
            // Reset WSection animation so the section re-animates properly with the
            // newly-added settings content (fixes the "snap" / invisible-settings bug).
            WSectionAccessor acc = (WSectionAccessor) this;
            acc.thm$setForcedHeight(-1);
            acc.thm$setFirstTime(true);
            acc.thm$setAnimProgress(0);
            invalidate();
        }
    }

    private void toggleExpandedState() {
        if (!isExpanded()) {
            if (currentlyExpanded != null && currentlyExpanded != this) currentlyExpanded.setExpanded(false);
            currentlyExpanded = this;
            ensureContentBuilt();
            setExpanded(true);
            invalidate();
        } else {
            setExpanded(false);
            if (currentlyExpanded == this) currentlyExpanded = null;
            invalidate();
        }
    }

    @Override
    public void setExpanded(boolean expanded) {
        super.setExpanded(expanded);
        if (expanded) currentlyExpanded = this;
        else if (currentlyExpanded == this) currentlyExpanded = null;
    }

    // ──────────────────────────────────────────────────────────────────────────

    private class InlineHeader extends WHeader {
        public InlineHeader(String title) {
            super(title);
        }

        @Override
        public void init() {
            enabledCheckbox = add(theme.checkbox(module.isActive())).padRight(3).widget();
            enabledCheckbox.action = () -> { if (module.isActive() != enabledCheckbox.checked) module.toggle(); };

            add(theme.label(title)).expandX().padLeft(4);

            settingsButton = add(theme.button("⚙")).widget();
            settingsButton.tooltip = "Expand module settings";
            settingsButton.action = InlineModuleWidget.this::toggleExpandedState;
        }

        @Override
        public boolean onMouseClicked(Click click, boolean doubled) {
            if (!mouseOver || doubled) return false;

            int button = click.button();
            if (button == GLFW_MOUSE_BUTTON_LEFT) {
                module.toggle();
                return true;
            }
            if (button == GLFW_MOUSE_BUTTON_RIGHT) {
                toggleExpandedState();
                return true;
            }
            return super.onMouseClicked(click, false);
        }

        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            super.onRender(renderer, mouseX, mouseY, delta);

            MeteorGuiTheme meteorTheme = (MeteorGuiTheme) theme;

            boolean active = module.isActive();
            activeFillBg   += delta * 4 * ((active || mouseOver) ? 1 : -1);
            activeFillBg    = Math.max(0, Math.min(1, activeFillBg));
            activeFillEdge += delta * 6 * (active ? 1 : -1);
            activeFillEdge  = Math.max(0, Math.min(1, activeFillEdge));

            SettingColor accent = meteorTheme.accentColor.get();

            if (activeFillBg > 0) {
                SettingColor bg = new SettingColor(accent.r, accent.g, accent.b,
                    (int)(accent.a * activeFillBg * 0.18));
                renderer.quad(x, y, width * activeFillBg, height, bg);
            }
            if (activeFillEdge > 0) {
                renderer.quad(x, y + height * (1 - activeFillEdge), meteorTheme.scale(2),
                    height * activeFillEdge, accent);
            }

            SettingColor line = new SettingColor(accent.r, accent.g, accent.b, 190);
            renderer.quad(x, y + height - meteorTheme.scale(1), width, meteorTheme.scale(1), line);
        }
    }
}
