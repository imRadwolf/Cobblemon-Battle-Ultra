package name.modid.client;

import com.cobblemon.mod.common.client.battle.ClientBattle;

/**
 * Identity of a battle for the trackers: its battle UUID rather than the client object, so leaving the battle screen
 * (R) and coming back, or the client rebuilding its battle object, never wipes what was learned during the battle.
 */
public final class BattleKey {
    private BattleKey() {
    }

    /** The key to store for this battle (its UUID when available). */
    public static Object of(Object battle) {
        if (battle instanceof ClientBattle b) {
            try {
                if (b.getBattleId() != null) {
                    return b.getBattleId();
                }
            } catch (Throwable ignored) {
            }
        }
        return battle;
    }

    /**
     * True when this battle is the one already tracked (same UUID). No battle at all (a moment where the client has none)
     * keeps what is tracked: the next battle has another UUID and resets it anyway.
     */
    public static boolean same(Object battle, Object tracked) {
        if (battle == null) {
            return true;
        }
        Object k = of(battle);
        return k == tracked || k != null && k.equals(tracked);
    }
}
