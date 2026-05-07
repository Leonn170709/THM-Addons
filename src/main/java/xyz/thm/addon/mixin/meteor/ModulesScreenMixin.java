package xyz.thm.addon.mixin.meteor;

import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.gui.screens.ModulesScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WContainer;
import meteordevelopment.meteorclient.gui.widgets.containers.WWindow;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.thm.addon.gui.AdvancedGuiTheme;

import java.util.List;

@Mixin(value = ModulesScreen.class, remap = false)
public abstract class ModulesScreenMixin {
    @Inject(method = "createCategory", at = @At("RETURN"))
    private void thm$enableCategoryScroll(WContainer c, Category category, List<Module> moduleList, CallbackInfoReturnable<WWindow> cir) {
        if (!(GuiThemes.get() instanceof AdvancedGuiTheme advTheme && advTheme.useInlineModuleSettings())) return;
        WWindow w = cir.getReturnValue();
        if (w != null) w.view.hasScrollBar = true;
    }
}
