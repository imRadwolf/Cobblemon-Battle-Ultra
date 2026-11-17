package name.modid.client;

import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.battle.ActiveClientBattlePokemon;
import com.cobblemon.mod.common.client.battle.ClientBattle;
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon;
import com.cobblemon.mod.common.client.battle.ClientBattleSide;
import com.cobblemon.mod.common.pokemon.Pokemon;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.class_2561;
import net.minecraft.class_2588;

public final class BattleCounters {
    private static final Set<String> PROTECT = Set.of(
        "protect", "detect", "kingsshield", "spikyshield", "banefulbunker", "silktrap", "burningbulwark", "obstruct", "maxguard", "endure"
    );
    private static Object trackedBattle;
    private static final Map<String, BattleCounters.State> STATES = new ConcurrentHashMap<>();
    private static String lastAttackSide;
    private static final Map<String, Float> LAST_RATIO = new ConcurrentHashMap<>();

    private BattleCounters() {
    }

    public static String[] switchWho(String k, Object[] args) {
        int idx = switch (k) {
            case "switch.other", "switch.other.nickname", "withdraw.other" -> 1;
            case "switch.self", "withdraw.self", "dragged_out", "switch", "switch_in" -> 0;
            default -> -1;
        };
        if (idx < 0) {
            return null;
        } else {
            String[] w = who(args, idx);
            if (w == null && idx == 1) {
                w = who(args, 0);
            }

            return w;
        }
    }

    public static boolean isSwitchKey(String k) {
        return k.startsWith("switch.self")
            || k.startsWith("switch.other")
            || k.startsWith("withdraw.")
            || k.equals("dragged_out")
            || k.equals("switch")
            || k.equals("switch_in");
    }

    public static void onHealth(String side, ClientBattlePokemon bp, float ratio) {
        try {
            checkBattle();
            String key = key(side, bp.getSpecies().getName());
            Float last = LAST_RATIO.put(key, ratio);
            if (last != null && ratio < last - 1.0E-4F && lastAttackSide != null && !lastAttackSide.equals(side)) {
                state(side, bp.getSpecies().getName()).timesHit++;
            }
        } catch (Throwable var5) {
        }
    }

    static String norm(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static void checkBattle() {
        Object battle = CobblemonClient.INSTANCE.getBattle();
        if (battle != trackedBattle) {
            trackedBattle = battle;
            STATES.clear();
            LAST_RATIO.clear();
            lastAttackSide = null;
        }
    }

    private static String key(String side, String species) {
        return side + "_" + norm(species);
    }

    private static BattleCounters.State state(String side, String species) {
        return STATES.computeIfAbsent(key(side, species), k -> new BattleCounters.State());
    }

    public static BattleCounters.State bySpecies(String side, String species) {
        checkBattle();
        return species == null ? null : STATES.get(key(side, species));
    }

    public static BattleCounters.State get(String side, ClientBattlePokemon bp) {
        checkBattle();
        if (bp == null) {
            return null;
        } else {
            try {
                return STATES.get(key(side, bp.getSpecies().getName()));
            } catch (Throwable var3) {
                return null;
            }
        }
    }

    private static String[] who(Object[] args, int idx) {
        if (args != null && args.length > idx) {
            String[] c = BattleMessageSubscriber.resolvePokemonSideAndSpecies(args, idx);
            return c != null && c[0] != null && c[1] != null ? c : null;
        } else {
            return null;
        }
    }

    private static String moveId(Object arg) {
        if (arg instanceof class_2561 t
            && t.method_10851() instanceof class_2588 tc
            && tc.method_11022() != null
            && tc.method_11022().startsWith("cobblemon.move.")) {
            return norm(tc.method_11022().substring("cobblemon.move.".length()));
        } else if (arg instanceof class_2561 t) {
            String id = MoveContactData.resolveMoveIdByDisplayName(t.getString());
            return id == null ? null : norm(id);
        } else {
            return null;
        }
    }

    private static boolean pressureOnMySide() {
        try {
            ClientBattlePokemon mine = MoveInsights.activeMine();
            Pokemon p = mine != null ? PartyInfoSideRenderer.findPartyPokemonForBattlePokemon(mine) : null;
            return p != null && norm(p.getAbility().getTemplate().getName()).equals("pressure");
        } catch (Throwable var2) {
            return false;
        }
    }

    public static void onMessage(String key, Object[] args) {
        if (key != null) {
            try {
                checkBattle();
                if (key.startsWith("cobblemon.status.")) {
                    String[] w = who(args, 0);
                    if (w == null) {
                        return;
                    }

                    BattleCounters.State st = state(w[0], w[1]);
                    switch (key) {
                        case "cobblemon.status.poisonbadly.apply":
                            st.toxicTurns = 0;
                            break;
                        case "cobblemon.status.poison.cure":
                        case "cobblemon.status.poisonbadly.cure":
                            st.toxicTurns = -1;
                            break;
                        case "cobblemon.status.sleep.apply":
                            st.sleepTurns = 0;
                            break;
                        case "cobblemon.status.sleep.is":
                            st.sleepTurns = Math.max(0, st.sleepTurns) + 1;
                            break;
                        case "cobblemon.status.sleep.cure":
                            st.sleepTurns = -1;
                    }

                    return;
                }

                if (!key.startsWith("cobblemon.battle.")) {
                    return;
                }

                String k = key.substring("cobblemon.battle.".length());
                if (isSwitchKey(k)) {
                    String[] w = switchWho(k, args);
                    if (w != null) {
                        BattleCounters.State st = state(w[0], w[1]);
                        st.protectStreak = 0;
                        st.perish = -1;
                        st.taunt = 0;
                        st.encore = 0;
                        st.disable = 0;
                        st.movesSinceSwitch.clear();
                        st.lastMove = null;
                        if (st.toxicTurns > 0) {
                            st.toxicTurns = 0;
                        }
                    }

                    lastAttackSide = null;
                    return;
                }

                switch (k) {
                    case "turn":
                        lastAttackSide = null;
                        onTurn();
                        break;
                    case "used_move":
                    case "used_move_on":
                        String[] wxxxxxxxxx = who(args, 0);
                        String move = args != null && args.length > 1 ? moveId(args[1]) : null;
                        if (wxxxxxxxxx != null && move != null) {
                            BattleCounters.State st = state(wxxxxxxxxx[0], wxxxxxxxxx[1]);
                            if ("opponent".equals(wxxxxxxxxx[0]) && !"struggle".equals(move)) {
                                st.ppUsed.merge(move, pressureOnMySide() ? 2 : 1, Integer::sum);
                            }

                            st.protectStreak = PROTECT.contains(move) ? (PROTECT.contains(st.lastMove) ? st.protectStreak + 1 : 1) : 0;
                            st.lastMove = move;
                            st.movesSinceSwitch.add(move);
                            MoveTemplate t = Moves.getByName(move);
                            lastAttackSide = t != null && !BuildInference.isStatus(t) ? wxxxxxxxxx[0] : null;
                        }
                        break;
                    case "start.perish":
                        String[] wxxxxxx = who(args, 0);
                        if (wxxxxxx != null && args.length > 1) {
                            try {
                                state(wxxxxxx[0], wxxxxxx[1]).perish = Integer.parseInt(
                                    (args[1] instanceof class_2561 t ? t.getString() : String.valueOf(args[1])).trim()
                                );
                            } catch (NumberFormatException var9) {
                            }
                        }
                        break;
                    case "fieldactivate.perishsong":
                        for (String[] wxxxxxxxx : actives()) {
                            BattleCounters.State st = state(wxxxxxxxx[0], wxxxxxxxx[1]);
                            if (st.perish < 0) {
                                st.perish = 3;
                            }
                        }
                        break;
                    case "start.taunt":
                        String[] wxxxxx = who(args, 0);
                        if (wxxxxx != null) {
                            state(wxxxxx[0], wxxxxx[1]).taunt = 3;
                        }
                        break;
                    case "end.taunt":
                        String[] wxxxx = who(args, 0);
                        if (wxxxx != null) {
                            state(wxxxx[0], wxxxx[1]).taunt = 0;
                        }
                        break;
                    case "start.encore":
                        String[] wxxx = who(args, 0);
                        if (wxxx != null) {
                            BattleCounters.State st = state(wxxx[0], wxxx[1]);
                            st.encore = 3;
                            st.encoreMove = st.lastMove;
                        }
                        break;
                    case "end.encore":
                        String[] wxx = who(args, 0);
                        if (wxx != null) {
                            state(wxx[0], wxx[1]).encore = 0;
                        }
                        break;
                    case "start.disable":
                        String[] wxxxxxxx = who(args, 0);
                        if (wxxxxxxx != null) {
                            BattleCounters.State st = state(wxxxxxxx[0], wxxxxxxx[1]);
                            st.disable = 4;
                            st.disabledMove = args.length > 1 && args[1] instanceof class_2561 t ? t.getString() : null;
                        }
                        break;
                    case "end.disable":
                        String[] wx = who(args, 0);
                        if (wx != null) {
                            state(wx[0], wx[1]).disable = 0;
                        }
                        break;
                    case "switch":
                    case "switch_in":
                    case "dragged_out":
                    case "withdraw":
                    case "recall":
                    case "return":
                        String[] w = who(args, 0);
                        if (w != null) {
                            BattleCounters.State st = state(w[0], w[1]);
                            st.protectStreak = 0;
                            st.perish = -1;
                            st.taunt = 0;
                            st.encore = 0;
                            st.disable = 0;
                            if (st.toxicTurns > 0) {
                                st.toxicTurns = 0;
                            }
                        }
                }
            } catch (Throwable var10) {
            }
        }
    }

    private static List<String[]> actives() {
        List<String[]> out = new ArrayList<>();

        try {
            ClientBattle battle = CobblemonClient.INSTANCE.getBattle();

            for (String side : new String[]{"player", "opponent"}) {
                ClientBattleSide battleSide = "player".equals(side) ? BattleSideHelper.getPlayerSide(battle) : BattleSideHelper.getOpponentSide(battle);

                for (ActiveClientBattlePokemon a : battleSide.getActiveClientBattlePokemon()) {
                    if (a != null && a.getBattlePokemon() != null) {
                        out.add(new String[]{side, a.getBattlePokemon().getSpecies().getName(), statusOf(a.getBattlePokemon())});
                    }
                }
            }
        } catch (Throwable var9) {
        }

        return out;
    }

    private static String statusOf(ClientBattlePokemon bp) {
        try {
            return bp.getStatus() == null ? "" : norm(bp.getStatus().getShowdownName());
        } catch (Throwable var2) {
            return "";
        }
    }

    private static void onTurn() {
        for (String[] w : actives()) {
            BattleCounters.State st = STATES.get(key(w[0], w[1]));
            if (st == null) {
                if (!"tox".equals(w[2])) {
                    continue;
                }

                st = state(w[0], w[1]);
            }

            if ("tox".equals(w[2])) {
                st.toxicTurns = Math.max(0, st.toxicTurns) + 1;
            } else if (st.toxicTurns >= 0 && !w[2].isEmpty()) {
                st.toxicTurns = -1;
            }

            if (st.taunt > 0) {
                st.taunt--;
            }

            if (st.encore > 0) {
                st.encore--;
            }

            if (st.disable > 0) {
                st.disable--;
            }
        }
    }

    public static int[] ppLeft(ClientBattlePokemon bp, String moveId) {
        if (!ExtrasPlusConfig.get().opponentPpTracking) {
            return null;
        } else {
            BattleCounters.State st = get("opponent", bp);
            Integer used = st != null ? st.ppUsed.get(norm(moveId)) : null;
            if (used != null && used > 0) {
                MoveTemplate t = Moves.getByName(norm(moveId));
                int max = t != null ? t.getMaxPp() : 0;
                return max <= 0 ? null : new int[]{Math.max(0, max - used), max};
            } else {
                return null;
            }
        }
    }

    public static int ppColor(int left, int max) {
        float r = max <= 0 ? 1.0F : (float)left / max;
        return r > 0.5F ? -8592501 : (r > 0.25F ? -10166 : -38037);
    }

    public static List<ModifierChips.Chip> chips(String side, ClientBattlePokemon bp) {
        List<ModifierChips.Chip> out = new ArrayList<>();
        if (!ExtrasPlusConfig.get().opponentPpTracking) {
            return out;
        } else {
            BattleCounters.State st = get(side, bp);
            String status = statusOf(bp);
            if ("tox".equals(status)) {
                int n = st != null ? Math.max(0, st.toxicTurns) : 0;
                int next = Math.min(15, n + 1);
                out.add(
                    new ModifierChips.Chip(
                        "Toxic · next −" + Math.round(next * 6.25) + "%",
                        -38037,
                        "Badly poisoned for " + n + " turn(s): the next hit of poison takes " + next + "/16 of max HP (it grows every turn, resets on switch)."
                    )
                );
            }

            if ("slp".equals(status)) {
                int n = st != null ? Math.max(0, st.sleepTurns) : 0;
                out.add(
                    new ModifierChips.Chip(
                        "Asleep · " + n + "/3",
                        -10166,
                        "Has slept " + n + " turn(s). Sleep lasts 1 to 3 turns: " + (n >= 3 ? "it wakes up this turn." : "it can wake up any turn now.")
                    )
                );
            }

            if (st != null) {
                if (st.protectStreak >= 1) {
                    String odds = st.protectStreak == 1 ? "1/3" : (st.protectStreak == 2 ? "1/9" : "1/27");
                    out.add(
                        new ModifierChips.Chip(
                            "Protect ×" + st.protectStreak + " · next " + odds,
                            -10166,
                            "Used a protecting move " + st.protectStreak + " turn(s) in a row: the next one only works " + odds + " of the time."
                        )
                    );
                }

                if (st.perish >= 0) {
                    out.add(new ModifierChips.Chip("Perish " + st.perish, -38037, "Faints when the Perish count reaches 0 unless it switches out."));
                }

                if (st.taunt > 0) {
                    out.add(new ModifierChips.Chip("Taunt " + st.taunt, -38037, "Can't use status moves for " + st.taunt + " more turn(s)."));
                }

                if (st.encore > 0) {
                    String move = st.encoreMove != null ? FormAbilityHelper.prettyId(st.encoreMove) : "its last move";
                    out.add(new ModifierChips.Chip("Encore " + st.encore, -38037, "Locked into " + move + " for " + st.encore + " more turn(s)."));
                }

                if (st.disable > 0) {
                    out.add(
                        new ModifierChips.Chip(
                            "Disabled " + st.disable,
                            -38037,
                            (st.disabledMove != null ? st.disabledMove : "A move") + " is disabled for " + st.disable + " more turn(s)."
                        )
                    );
                }
            }

            return out;
        }
    }

    public static final class State {
        public final Map<String, Integer> ppUsed = new LinkedHashMap<>();
        public int toxicTurns = -1;
        public int sleepTurns = -1;
        public int protectStreak;
        public int perish = -1;
        public int taunt;
        public int encore;
        public String encoreMove;
        public int disable;
        public String disabledMove;
        String lastMove;
        public int timesHit;
        public final List<String> movesSinceSwitch = new ArrayList<>();
    }
}
