package name.modid.mixin.plus;

import name.modid.client.UltraTileRenderer;
import net.minecraft.class_332;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(
    targets = {"name.modid.client.TypeIconRenderer"},
    remap = false
)
public class UltraTileTypeIconsMixin {
    @Inject(
        method = {"renderTypeIcons(Lnet/minecraft/class_332;)V"},
        at = {@At("HEAD")},
        cancellable = true,
        remap = false
    )
    private static void cbePlus$hideForUltra(class_332 gui, CallbackInfo ci) {
        try {
            if (UltraTileRenderer.active()) {
                ci.cancel();
            }
        } catch (Throwable var3) {
        }
    }
}
