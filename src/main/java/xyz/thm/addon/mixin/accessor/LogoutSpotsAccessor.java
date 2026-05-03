package xyz.thm.addon.mixin.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(targets = "meteordevelopment.meteorclient.systems.modules.render.LogoutSpots", remap = false)
public interface LogoutSpotsAccessor {
    @Accessor("players")
    List<?> thm$getPlayers();
}
