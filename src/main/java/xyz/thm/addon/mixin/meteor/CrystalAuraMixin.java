package xyz.thm.addon.mixin.meteor;

import meteordevelopment.meteorclient.systems.modules.combat.CrystalAura;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.thm.addon.system.THMSystem;
import xyz.thm.addon.utils.ThmMembers;

import java.util.List;

@Mixin(value = CrystalAura.class, remap = false)
public class CrystalAuraMixin {
    @Shadow private List<LivingEntity> targets;

    @Inject(method = "findTargets", at = @At("TAIL"))
    private void thm$filterThmMembers(CallbackInfo ci) {
        THMSystem system = THMSystem.get();
        if (system == null || !system.ignoreThmMembers.get()) return;

        targets.removeIf(entity -> entity instanceof PlayerEntity player && ThmMembers.isThmMember(player));
    }
}
