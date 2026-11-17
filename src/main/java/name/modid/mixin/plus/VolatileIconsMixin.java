package name.modid.mixin.plus;

import name.modid.client.ExtrasPlusConfig;
import name.modid.client.FieldHud;
import net.minecraft.class_310;
import net.minecraft.class_332;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(
    targets = {"name.modid.client.StatStageRenderer"},
    remap = false
)
public class VolatileIconsMixin {
    @Inject(
        method = {"renderVolatileStatus(Lnet/minecraft/class_332;Lnet/minecraft/class_310;Ljava/lang/String;IIZFLjava/lang/String;)V"},
        at = {@At("HEAD")},
        cancellable = true,
        remap = false
    )
    private static void cbePlus$hideDuplicateIcons(
        class_332 gui, class_310 mc, String species, int x, int y, boolean alignRight, float opacity, String side, CallbackInfo ci
    ) {
        try {
            if (FieldHud.active() && ExtrasPlusConfig.get().volatileBadges) {
                ci.cancel();
            }
        } catch (Throwable var10) {
        }
    }
}
