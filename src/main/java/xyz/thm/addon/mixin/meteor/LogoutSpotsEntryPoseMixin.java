package xyz.thm.addon.mixin.meteor;

import meteordevelopment.meteorclient.systems.modules.render.LogoutSpots;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.LimbAnimator;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.thm.addon.interfaces.LogoutSpotsPoseData;

@Mixin(targets = "meteordevelopment.meteorclient.systems.modules.render.LogoutSpots$Entry", remap = false)
public class LogoutSpotsEntryPoseMixin implements LogoutSpotsPoseData {
    @Unique private String thm$name;
    @Unique private float thm$bodyYaw;
    @Unique private float thm$yaw;
    @Unique private float thm$pitch;
    @Unique private float thm$headYaw;
    @Unique private float thm$limbPos;
    @Unique private float thm$limbSpeed;
    @Unique private float thm$limbAmplitude;
    @Unique private boolean thm$sneaking;
    @Unique private boolean thm$lowPose;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void thm$capturePose(LogoutSpots outer, PlayerEntity entity, CallbackInfo ci) {
        LimbAnimator limbAnimator = ((LivingEntityAccessor) entity).thm$getLimbAnimator();

        thm$name = entity.getName().getString();
        thm$bodyYaw = entity.getBodyYaw();
        thm$yaw = entity.getYaw();
        thm$pitch = entity.getPitch();
        thm$headYaw = entity.headYaw;
        thm$limbPos = limbAnimator.getAnimationProgress();
        thm$limbSpeed = limbAnimator.getSpeed();
        thm$limbAmplitude = limbAnimator.getAmplitude(1);
        thm$sneaking = entity.isSneaking();
        thm$lowPose = entity.isCrawling() || entity.isSwimming() || entity.getPose() == EntityPose.SWIMMING;
    }

    @Override
    public float thm$getBodyYaw() {
        return thm$bodyYaw;
    }

    @Override
    public String thm$getName() {
        return thm$name;
    }

    @Override
    public float thm$getYaw() {
        return thm$yaw;
    }

    @Override
    public float thm$getPitch() {
        return thm$pitch;
    }

    @Override
    public float thm$getHeadYaw() {
        return thm$headYaw;
    }

    @Override
    public float thm$getLimbPos() {
        return thm$limbPos;
    }

    @Override
    public float thm$getLimbSpeed() {
        return thm$limbSpeed;
    }

    @Override
    public float thm$getLimbAmplitude() {
        return thm$limbAmplitude;
    }

    @Override
    public boolean thm$isSneaking() {
        return thm$sneaking;
    }

    @Override
    public boolean thm$isLowPose() {
        return thm$lowPose;
    }
}
