package xyz.thm.addon.mixin.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.UUID;

@Mixin(targets = "meteordevelopment.meteorclient.systems.modules.render.LogoutSpots$Entry", remap = false)
public interface LogoutSpotsEntryAccessor {
    @Accessor("x")
    double thm$getX();

    @Accessor("y")
    double thm$getY();

    @Accessor("z")
    double thm$getZ();

    @Accessor("xWidth")
    double thm$getXWidth();

    @Accessor("zWidth")
    double thm$getZWidth();

    @Accessor("height")
    double thm$getHeight();

    @Accessor("uuid")
    UUID thm$getUuid();
}
