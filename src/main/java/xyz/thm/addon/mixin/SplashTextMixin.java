package xyz.thm.addon.mixin;

import net.minecraft.client.resource.SplashTextResourceSupplier;
import net.minecraft.resource.ResourceManager;
import net.minecraft.text.Text;
import net.minecraft.util.profiler.Profiler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(SplashTextResourceSupplier.class)
public class SplashTextMixin {
    @Shadow @Mutable
    private List<Text> splashTexts;

    @Inject(method = "apply(Ljava/util/List;Lnet/minecraft/resource/ResourceManager;Lnet/minecraft/util/profiler/Profiler;)V", at = @At("TAIL"))
    private void addThmSplashes(List<Text> prepared, ResourceManager manager, Profiler profiler, CallbackInfo ci) {
        List<Text> mutable = new ArrayList<>(this.splashTexts);
        mutable.add(Text.literal("THM highway go brrr!"));
        mutable.add(Text.literal("Place the blocks, mine the blocks!"));
        mutable.add(Text.literal("Highways last forever!!"));
        mutable.add(Text.literal("6b6t at home!"));
        mutable.add(Text.literal("Running THM Addons!"));
        mutable.add(Text.literal("Highway is life!"));
        mutable.add(Text.literal("Builidng Highways!!!"));
        mutable.add(Text.literal("Mine or be mined!"));
        mutable.add(Text.literal("Be fair or be square"));
        mutable.add(Text.literal("https://discord.gg/thm"));
        this.splashTexts = mutable;
    }
}
