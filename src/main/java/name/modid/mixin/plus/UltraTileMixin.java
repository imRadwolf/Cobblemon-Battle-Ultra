package name.modid.mixin.plus;

import com.cobblemon.mod.common.api.pokedex.PokedexEntryProgress;
import com.cobblemon.mod.common.client.battle.ActiveClientBattlePokemon;
import com.cobblemon.mod.common.client.gui.battle.BattleOverlay;
import name.modid.client.UltraTileRenderer;
import net.minecraft.class_310;
import net.minecraft.class_332;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
    value = {BattleOverlay.class},
    priority = 500,
    remap = false
)
public class UltraTileMixin {
    @Inject(
        method = {"drawTile"},
        at = {@At("HEAD")},
        cancellable = true,
        remap = false
    )
    private void cbePlus$ultraTile(
        class_332 context,
        float tickDelta,
        ActiveClientBattlePokemon activeBattlePokemon,
        boolean left,
        int rank,
        PokedexEntryProgress dexState,
        boolean hasCommand,
        boolean isHovered,
        boolean isCompact,
        CallbackInfo ci
    ) {
        try {
            if (UltraTileRenderer.active()) {
                try {
                    int[] b = UltraTileRenderer.bounds(left);
                    int screenWidth = class_310.method_1551().method_22683().method_4486();
                    activeBattlePokemon.setInvisibleX(left ? -b[2] - 1.0F : screenWidth);
                    activeBattlePokemon.setXDisplacement(b[0]);
                    activeBattlePokemon.animate(tickDelta);
                } catch (Throwable var13) {
                }

                UltraTileRenderer.render(context, tickDelta, activeBattlePokemon, left, hasCommand);
                ci.cancel();
            }
        } catch (Throwable var14) {
        }
    }
}
