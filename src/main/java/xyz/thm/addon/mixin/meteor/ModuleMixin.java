package xyz.thm.addon.mixin.meteor;

import meteordevelopment.meteorclient.systems.modules.Module;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.thm.addon.modules.ModuleManager;

@Mixin(value = Module.class, remap = false)
public abstract class ModuleMixin {
    @Inject(method = "toggle", at = @At("HEAD"))
    private void thm$traceToggle(CallbackInfo ci) {
        ModuleManager.traceModuleMethodInvocation((Module) (Object) this, "toggle");
    }

    @Inject(method = "enable", at = @At("HEAD"))
    private void thm$traceEnable(CallbackInfo ci) {
        ModuleManager.traceModuleMethodInvocation((Module) (Object) this, "enable");
    }

    @Inject(method = "disable", at = @At("HEAD"))
    private void thm$traceDisable(CallbackInfo ci) {
        ModuleManager.traceModuleMethodInvocation((Module) (Object) this, "disable");
    }
}
