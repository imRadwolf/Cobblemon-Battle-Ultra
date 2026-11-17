package name.modid.mixin.plus;

import com.cobblemon.mod.common.pokemon.Pokemon;
import java.util.List;
import name.modid.client.HiddenPowerHelper;
import name.modid.client.SwitchThreatRenderer;
import net.minecraft.class_327;
import net.minecraft.class_332;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(
    targets = {"name.modid.client.CustomBattleController"},
    remap = false
)
public class SwitchTooltipMixin {
    @Inject(
        method = {"renderSwitchMovesTooltip"},
        at = {@At("HEAD")},
        remap = false
    )
    private static void cbePlus$incoming(class_332 graphics, Object switchTile, int tileX, int tileY, int tileWidth, int tileHeight, CallbackInfo ci) {
        try {
            SwitchThreatRenderer.begin(switchTile, tileX, tileWidth);
        } catch (Throwable var8) {
        }
    }

    @Inject(
        method = {"renderSwitchMovesTooltip"},
        at = {@At("RETURN")},
        remap = false
    )
    private static void cbePlus$incomingDraw(class_332 graphics, Object switchTile, int tileX, int tileY, int tileWidth, int tileHeight, CallbackInfo ci) {
        try {
            SwitchThreatRenderer.finish(graphics, switchTile, tileX, tileY, tileWidth, tileHeight);
        } catch (Throwable var8) {
        }
    }

    @Inject(
        method = {"drawFancySwitchTooltipBox"},
        at = {@At("HEAD")},
        cancellable = true,
        remap = false
    )
    private static void cbePlus$box(class_332 graphics, int x, int y, int width, int height, CallbackInfo ci) {
        if (!SwitchThreatRenderer.isRedrawing()) {
            try {
                int w = SwitchThreatRenderer.widthFor(width);
                if (w == width) {
                    SwitchThreatRenderer.recordBaseBox(x, y, w, height, true);
                    return;
                }

                int shift = 0;
                if (SwitchThreatRenderer.tileLeftColumn()) {
                    shift = Math.max(0, Math.min(w - width, x - 2));
                    SwitchThreatRenderer.pushShift(graphics, shift);
                }

                SwitchThreatRenderer.recordBaseBox(x - shift, y, w, height, true);
                ci.cancel();
                SwitchThreatRenderer.drawFancyBox(graphics, x, y, w, height);
            } catch (Throwable var8) {
            }
        }
    }

    @Inject(
        method = {"renderClassicSwitchMovesTooltip"},
        at = {@At("HEAD")},
        remap = false
    )
    private static void cbePlus$classicBox(
        class_332 graphics,
        class_327 font,
        List<?> moveInfos,
        List<?> previews,
        String ability,
        Integer speed,
        List<?> matchups,
        int tooltipX,
        int tooltipY,
        CallbackInfo ci
    ) {
        try {
            SwitchThreatRenderer.recordBaseBox(tooltipX, tooltipY, 113, 126, false);
        } catch (Throwable var11) {
        }
    }

    @ModifyVariable(
        method = {"calculateSwitchDamagePreview"},
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0,
        remap = false
    )
    private static Pokemon cbePlus$attackerCtx(Pokemon attacker) {
        HiddenPowerHelper.CTX.set(attacker);
        return attacker;
    }

    @Inject(
        method = {"calculateSwitchDamagePreview"},
        at = {@At("RETURN")},
        remap = false
    )
    private static void cbePlus$clearCtx(CallbackInfoReturnable<Object> cir) {
        HiddenPowerHelper.CTX.remove();
    }
}
