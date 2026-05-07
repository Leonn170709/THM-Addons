package xyz.thm.addon.gui.widgets;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.themes.meteor.MeteorGuiTheme;
import meteordevelopment.meteorclient.gui.widgets.containers.WSection;
import net.minecraft.client.gui.Click;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.gui.widgets.pressable.WCheckbox;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import xyz.thm.addon.gui.ClientLook;
import xyz.thm.addon.gui.ClientLookHelper;

import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT;

public class InlineModuleWidget extends WSection {
    private static InlineModuleWidget currentlyExpanded;

    private final Module module;
    private final String title;

    private WCheckbox enabledCheckbox;
    private WButton settingsButton;
    private boolean contentBuilt;

    public InlineModuleWidget(Module module, String title, boolean expanded) {
        super(title, expanded, null);
        this.module = module;
        this.title = title;
        this.tooltip = module.description;
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

    @Override
    protected void onCalculateSize() {
        super.onCalculateSize();

        MeteorGuiTheme meteorTheme = (MeteorGuiTheme) theme;
        ClientLook look = ClientLookHelper.getLook(theme);
        double cappedWidth = switch (look) {
            case FUTURE_OLD -> meteorTheme.scale(210);
            case FUTURE_NEW -> meteorTheme.scale(220);
            case IMPACT -> meteorTheme.scale(250);
            case RUSHERHACK -> meteorTheme.scale(205);
            case NOVOLINE -> meteorTheme.scale(225);
            case MIO -> meteorTheme.scale(240);
            default -> meteorTheme.scale(220);
        };

        if (width > cappedWidth) width = cappedWidth;
    }

    private void ensureContentBuilt() {
        if (!contentBuilt && !module.settings.groups.isEmpty()) {
            add(theme.settings(module.settings)).expandX().padTop(2);
            contentBuilt = true;
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

    private class InlineHeader extends WHeader {
        public InlineHeader(String title) {
            super(title);
        }

        @Override
        public void init() {
            ClientLook look = ClientLookHelper.getLook(theme);

            if (look == ClientLook.IMPACT) add(theme.label(">")).padRight(3);
            if (look == ClientLook.NOVOLINE) add(theme.label("■")).padRight(3);

            enabledCheckbox = add(theme.checkbox(module.isActive())).padRight(3).widget();
            enabledCheckbox.action = () -> { if (module.isActive() != enabledCheckbox.checked) module.toggle(); };

            add(theme.label(title)).expandX().padLeft(4);

            String buttonText = look == ClientLook.RUSHERHACK ? "..." : "\u2699";
            settingsButton = add(theme.button(buttonText)).widget();
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
            ClientLook look = ClientLookHelper.getLook(theme);

            SettingColor line = switch (look) {
                case FUTURE_OLD -> new SettingColor(170, 15, 30, 180);
                case IMPACT -> new SettingColor(65, 185, 255, 150);
                case RUSHERHACK, FUTURE_NEW -> new SettingColor(35, 120, 245, 180);
                case NOVOLINE -> new SettingColor(25, 235, 120, 170);
                case WURST -> new SettingColor(204, 0, 102, 170);
                default -> meteorTheme.accentColor.get();
            };
            double yLine = y + height - meteorTheme.scale(1);
            renderer.quad(x, yLine, width, meteorTheme.scale(1), line);
        }
    }
}
