package name.modid.mixin.plus;

import name.modid.client.ExtrasPlusConfig;
import net.minecraft.class_310;
import net.minecraft.class_332;
import net.minecraft.class_338;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
    value = {class_338.class},
    priority = 500
)
public class ChatHudHideMixin {
    @Inject(
        method = {"method_1805"},
        at = {@At("HEAD")},
        cancellable = true,
        remap = false
    )
    private void cbePlus$hideChatInBattle(class_332 context, int currentTick, int mouseX, int mouseY, boolean focused, CallbackInfo ci) {
        try {
            if (ExtrasPlusConfig.get().hideChatInBattleMenu) {
                class_310 mc = class_310.method_1551();
                if (mc.field_1755 != null && mc.field_1755.getClass().getName().contains("BattleGUI")) {
                    ci.cancel();
                }
            }
        } catch (Throwable var8) {
        }
    }
}
