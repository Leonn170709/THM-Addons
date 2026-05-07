package xyz.thm.addon.mixin.meteor;

import meteordevelopment.meteorclient.gui.themes.meteor.MeteorGuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.systems.modules.Module;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.thm.addon.gui.AdvancedGuiTheme;
import xyz.thm.addon.gui.widgets.InlineModuleWidget;

@Mixin(value = MeteorGuiTheme.class, remap = false)
public abstract class MeteorGuiThemeModuleMixin {
    @Inject(method = "module(Lmeteordevelopment/meteorclient/systems/modules/Module;Ljava/lang/String;)Lmeteordevelopment/meteorclient/gui/widgets/WWidget;", at = @At("HEAD"), cancellable = true)
    private void thm$replaceModuleWidget(Module module, String title, CallbackInfoReturnable<WWidget> cir) {
        if ((Object) this instanceof AdvancedGuiTheme theme && theme.useInlineModuleSettings()) {
            InlineModuleWidget widget = new InlineModuleWidget(module, title, theme.startModuleSettingsExpanded());
            widget.theme = (MeteorGuiTheme) (Object) this;
            cir.setReturnValue(widget);
        }
    }
}
