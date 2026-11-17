package name.modid.mixin.plus;

import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.battle.ActiveClientBattlePokemon;
import com.cobblemon.mod.common.client.battle.ClientBattle;
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon;
import com.cobblemon.mod.common.client.net.battle.BattleHealthChangeHandler;
import com.cobblemon.mod.common.net.messages.client.battle.BattleHealthChangePacket;
import name.modid.client.BattleCounters;
import name.modid.client.BattleHpLog;
import name.modid.client.TeamStatusTracker;
import name.modid.client.BattleRecap;
import name.modid.client.BattleSideHelper;
import name.modid.client.BuildInference;
import net.minecraft.class_310;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
    value = {BattleHealthChangeHandler.class},
    remap = false
)
public class HealthChangeMixin {
    @Inject(
        method = {"handle(Lcom/cobblemon/mod/common/net/messages/client/battle/BattleHealthChangePacket;Lnet/minecraft/class_310;)V"},
        at = {@At("HEAD")},
        remap = false
    )
    private void cbePlus$onHealth(BattleHealthChangePacket packet, class_310 client, CallbackInfo ci) {
        try {
            ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
            if (battle != null && battle.getPokemonFromPNX(packet.getPnx()).getSecond() instanceof ActiveClientBattlePokemon a) {
                BuildInference.onHealth(a, packet.getNewHealth());

                try {
                    ClientBattlePokemon bp = a.getBattlePokemon();
                    if (bp != null) {
                        boolean opponent = false;

                        for (ActiveClientBattlePokemon o : BattleSideHelper.getOpponentSide(battle).getActiveClientBattlePokemon()) {
                            opponent |= o == a;
                        }

                        float h = packet.getNewHealth();
                        float ratio = bp.isHpFlat() ? (bp.getMaxHp() > 0.0F ? h / bp.getMaxHp() : 1.0F) : (h > 1.0001F ? h / 100.0F : h);
                        BattleCounters.onHealth(opponent ? "opponent" : "player", bp, ratio);
                        TeamStatusTracker.onHealth(opponent, bp, ratio, h);
                        BattleHpLog.onHealth(a, opponent, bp, ratio, h);
                        if (Boolean.getBoolean("cbeplus.debug")) {
                            System.out.println("[BXH] " + (opponent ? "opponent " : "player ") + bp.getSpecies().getName() + " -> " + h);
                        }
                    }
                } catch (Throwable var11) {
                }

                BattleRecap.onHealth(a, packet.getNewHealth());
            }
        } catch (Throwable var12) {
        }
    }
}
