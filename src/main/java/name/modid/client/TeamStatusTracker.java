package name.modid.client;

import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.battle.ActiveClientBattlePokemon;
import com.cobblemon.mod.common.client.battle.ClientBattle;
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon;
import com.cobblemon.mod.common.client.battle.ClientBattleSide;
import com.cobblemon.mod.common.client.battle.SingleActionRequest;
import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.battles.InBattleMove;
import com.cobblemon.mod.common.battles.ShowdownPokemon;
import com.cobblemon.mod.common.pokemon.Pokemon;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.class_2561;
import net.minecraft.class_2588;

/**
 * HP, status and PP of every Pokemon in the battle, benched ones included, kept for the whole battle (independent from
 * the battle screen, so pressing R and coming back loses nothing).
 * <ul>
 * <li>Your team: Showdown's own team data sent with each turn's request (exact HP and status for the whole team), the
 * active Pokemon's live HP, and PP from the request's move list, minus the moves used since.</li>
 * <li>Their team: last HP / status seen while each Pokemon was on the field (HP packets and status messages included);
 * PP = PP Max minus the uses seen (Pressure counted).</li>
 * </ul>
 */
public final class TeamStatusTracker {
    /** hp ratio (0..1, -1 unknown), hp / max (exact, own side only, -1 unknown), status id ("" none, "fnt" fainted). */
    public static final class Mon {
        public float ratio = -1.0F;
        public int hp = -1;
        public int maxHp = -1;
        public String status = "";
        public boolean seen;
        /** own side: move id -> {pp left, pp max} from the latest turn request where it was active */
        public final Map<String, int[]> pp = new LinkedHashMap<>();
        /** own side: move id -> {pp left, pp max} the first time it was seen (before any use) */
        public final Map<String, int[]> startPp = new LinkedHashMap<>();
        /** own side: move id -> PP spent, from the battle messages */
        public final Map<String, Integer> used = new LinkedHashMap<>();
    }

    private static Object trackedBattle;
    private static final Map<String, Mon> MONS = new ConcurrentHashMap<>();
    private static boolean registered;

    private TeamStatusTracker() {
    }

    static String norm(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    public static void init() {
        if (!registered) {
            registered = true;
            name.modid.platform.Services.EVENTS.registerClientTick(mc -> {
                try {
                    tick();
                } catch (Throwable ignored) {
                }
            });
        }
    }

    private static void checkBattle(ClientBattle battle) {
        if (battle != trackedBattle) {
            trackedBattle = battle;
            MONS.clear();
        }
    }

    private static String key(String side, UUID uuid, String species) {
        return "player".equals(side) && uuid != null ? "p|" + uuid : side + "|" + norm(species);
    }

    static Mon mon(String side, UUID uuid, String species) {
        return MONS.computeIfAbsent(key(side, uuid, species), k -> new Mon());
    }

    /** Own Pokemon (by party UUID). */
    public static Mon mine(Pokemon pokemon) {
        if (pokemon == null) {
            return null;
        }
        checkBattle(CobblemonClient.INSTANCE.getBattle());
        return MONS.get(key("player", pokemon.getUuid(), null));
    }

    /** Left trainer's Pokemon when spectating (by species). */
    public static Mon spectatedLeft(String species) {
        checkBattle(CobblemonClient.INSTANCE.getBattle());
        return species == null ? null : MONS.get(key("opponent2", null, species));
    }

    /** Opponent Pokemon (by species). */
    public static Mon theirs(String species) {
        checkBattle(CobblemonClient.INSTANCE.getBattle());
        return species == null ? null : MONS.get(key("opponent", null, species));
    }

    static String statusOf(ClientBattlePokemon bp) {
        try {
            return bp.getStatus() == null ? "" : norm(bp.getStatus().getShowdownName());
        } catch (Throwable t) {
            return "";
        }
    }

    static float ratioOf(ClientBattlePokemon bp) {
        float h = bp.getHpValue();
        return bp.isHpFlat() ? (bp.getMaxHp() > 0.0F ? h / bp.getMaxHp() : 1.0F) : (h > 1.0001F ? h / 100.0F : h);
    }

    private static void tick() {
        ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
        checkBattle(battle);
        if (battle == null) {
            return;
        }
        boolean spectating = BattleSideHelper.isSpectating(battle);
        sampleSide(BattleSideHelper.getPlayerSide(battle), spectating ? "opponent2" : "player");
        sampleSide(BattleSideHelper.getOpponentSide(battle), "opponent");
        if (!spectating) {
            readRequests(battle);
        }
    }

    private static void sampleSide(ClientBattleSide side, String sideName) {
        if (side == null) {
            return;
        }
        for (ActiveClientBattlePokemon active : side.getActiveClientBattlePokemon()) {
            ClientBattlePokemon bp = active == null ? null : active.getBattlePokemon();
            if (bp == null) {
                continue;
            }
            Mon m = mon(sideName, bp.getUuid(), bp.getSpecies().getName());
            m.seen = true;
            float r = Math.max(0.0F, Math.min(1.0F, ratioOf(bp)));
            m.ratio = r;
            if (bp.isHpFlat() && bp.getMaxHp() > 0.0F) {
                m.hp = Math.round(bp.getHpValue());
                m.maxHp = Math.round(bp.getMaxHp());
            }
            String st = statusOf(bp);
            m.status = r <= 0.0F ? "fnt" : st;
        }
    }

    /** Showdown's team data for your side: exact HP / status of the bench, PP of the active Pokemon. */
    private static void readRequests(ClientBattle battle) {
        SingleActionRequest req = null;
        try {
            req = battle.getFirstUnansweredRequest();
        } catch (Throwable ignored) {
        }
        if (req == null) {
            try {
                req = battle.getLastAnsweredRequest();
            } catch (Throwable ignored) {
            }
        }
        if (req == null || req.getSide() == null) {
            return;
        }
        Object reqId = req;
        if (reqId == lastRequest) {
            return;
        }
        lastRequest = reqId;
        try {
            for (ShowdownPokemon sp : req.getSide().getPokemon()) {
                if (sp == null || sp.getUuid() == null) {
                    continue;
                }
                Mon m = mon("player", sp.getUuid(), null);
                parseCondition(m, sp.getCondition());
                if (Boolean.getBoolean("cbeplus.debug")) {
                    System.out.println("[BXT] req " + sp.getIdent() + " " + sp.getCondition() + " active=" + sp.getActive());
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            ActiveClientBattlePokemon active = req.getActivePokemon();
            ClientBattlePokemon bp = active == null ? null : active.getBattlePokemon();
            if (bp != null && req.getMoveSet() != null) {
                Mon m = mon("player", bp.getUuid(), null);
                for (InBattleMove mv : req.getMoveSet().getMoves()) {
                    if (mv != null && mv.getId() != null && mv.getMaxpp() > 0) {
                        String id = norm(mv.getId());
                        m.pp.put(id, new int[]{mv.getPp(), mv.getMaxpp()});
                        // first sight of this move: nothing was spent with it yet in this battle (it just came in)
                        m.startPp.putIfAbsent(id, new int[]{mv.getPp() + m.used.getOrDefault(id, 0), mv.getMaxpp()});
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static Object lastRequest;

    /** "253/341 par", "0 fnt", "341/341". */
    static void parseCondition(Mon m, String cond) {
        if (cond == null || cond.isBlank()) {
            return;
        }
        String[] parts = cond.trim().split(" ");
        String hp = parts[0];
        String status = parts.length > 1 ? norm(parts[1]) : "";
        if (hp.equals("0") || status.equals("fnt")) {
            m.ratio = 0.0F;
            m.hp = 0;
            m.status = "fnt";
            return;
        }
        int slash = hp.indexOf('/');
        if (slash > 0) {
            try {
                int cur = Integer.parseInt(hp.substring(0, slash));
                int max = Integer.parseInt(hp.substring(slash + 1).replaceAll("[^0-9]", ""));
                if (max > 0) {
                    m.hp = cur;
                    m.maxHp = max;
                    m.ratio = Math.max(0.0F, Math.min(1.0F, cur / (float)max));
                }
            } catch (NumberFormatException ignored) {
            }
        }
        m.status = status;
    }

    /** HP packet (before Cobblemon applies it): keeps the last value even if the Pokemon is switched out right after. */
    public static void onHealth(boolean opponent, ClientBattlePokemon bp, float ratio, float raw) {
        try {
            ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
            checkBattle(battle);
            if (battle == null || bp == null) {
                return;
            }
            String side = opponent ? "opponent" : (BattleSideHelper.isSpectating(battle) ? "opponent2" : "player");
            Mon m = mon(side, bp.getUuid(), bp.getSpecies().getName());
            m.seen = true;
            m.ratio = Math.max(0.0F, Math.min(1.0F, ratio));
            if (!opponent && bp.isHpFlat() && bp.getMaxHp() > 0.0F) {
                m.hp = Math.round(raw);
                m.maxHp = Math.round(bp.getMaxHp());
            }
            if (m.ratio <= 0.0F) {
                m.status = "fnt";
            }
        } catch (Throwable ignored) {
        }
    }

    private static String moveId(Object arg) {
        if (arg instanceof class_2561 t && t.method_10851() instanceof class_2588 tc && tc.method_11022() != null
            && tc.method_11022().startsWith("cobblemon.move.")) {
            return norm(tc.method_11022().substring("cobblemon.move.".length()));
        } else if (arg instanceof class_2561 t) {
            String id = MoveContactData.resolveMoveIdByDisplayName(t.getString());
            return id == null ? null : norm(id);
        }
        return null;
    }

    /** Battle messages: own PP spent since the last request, statuses cured / applied, faints. */
    public static void onMessage(String key, Object[] args) {
        try {
            ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
            checkBattle(battle);
            if (battle == null || key == null) {
                return;
            }
            if (key.equals("cobblemon.battle.used_move") || key.equals("cobblemon.battle.used_move_on")) {
                String[] w = args != null && args.length > 1 ? BattleMessageSubscriber.resolvePokemonSideAndSpecies(args, 0) : null;
                String move = args != null && args.length > 1 ? moveId(args[1]) : null;
                if (w != null && "player".equals(w[0]) && move != null && !BattleSideHelper.isSpectating(battle)) {
                    ClientBattlePokemon bp = activeOn(BattleSideHelper.getPlayerSide(battle), w[1]);
                    if (bp != null) {
                        Mon m = mon("player", bp.getUuid(), null);
                        // messages are shown with a delay and can arrive after the next turn's request: count the uses
                        // and combine them with the request values when reading (see myPp)
                        m.used.merge(move, pressureAgainstMe() ? 2 : 1, Integer::sum);
                        if (!m.startPp.containsKey(move)) {
                            int[] party = partyPp(bp, move);
                            if (party != null) {
                                m.startPp.put(move, party);
                            }
                        }
                    }
                }
                return;
            }
            if (key.equals("cobblemon.battle.fainted") && args != null && args.length > 0) {
                String[] w = BattleMessageSubscriber.resolvePokemonSideAndSpecies(args, 0);
                if (w != null && w[1] != null) {
                    ClientBattleSide side = "player".equals(w[0]) ? BattleSideHelper.getPlayerSide(battle) : BattleSideHelper.getOpponentSide(battle);
                    ClientBattlePokemon bp = activeOn(side, w[1]);
                    Mon m = bp != null ? mon(w[0], bp.getUuid(), bp.getSpecies().getName()) : ("opponent".equals(w[0]) ? mon("opponent", null, w[1]) : null);
                    if (m != null) {
                        m.ratio = 0.0F;
                        m.hp = 0;
                        m.status = "fnt";
                    }
                }
                return;
            }
            // Heal Bell / Aromatherapy: the whole side is cured
            if (key.contains("cureteam") || key.contains("healbell") || key.contains("aromatherapy")) {
                String[] w = args != null && args.length > 0 ? BattleMessageSubscriber.resolvePokemonSideAndSpecies(args, 0) : null;
                if (w != null && w[0] != null) {
                    String prefix = "player".equals(w[0]) ? "p|" : w[0] + "|";
                    for (Map.Entry<String, Mon> e : MONS.entrySet()) {
                        if (e.getKey().startsWith(prefix) && !"fnt".equals(e.getValue().status)) {
                            e.getValue().status = "";
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static ClientBattlePokemon activeOn(ClientBattleSide side, String species) {
        if (side == null) {
            return null;
        }
        for (ActiveClientBattlePokemon a : side.getActiveClientBattlePokemon()) {
            ClientBattlePokemon bp = a == null ? null : a.getBattlePokemon();
            if (bp != null && (norm(bp.getSpecies().getName()).equals(norm(species)) || norm(bp.getDisplayName().getString()).equals(norm(species)))) {
                return bp;
            }
        }
        return null;
    }

    private static int[] partyPp(ClientBattlePokemon bp, String move) {
        try {
            Pokemon p = PartyInfoSideRenderer.findPartyPokemonForBattlePokemon(bp);
            if (p != null) {
                for (Move mv : p.getMoveSet().getMoves()) {
                    if (mv != null && norm(mv.getName()).equals(move)) {
                        return new int[]{mv.getCurrentPp(), mv.getMaxPp()};
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /** The opponent's active Pokemon has Pressure (revealed): each of your moves costs 2 PP. */
    private static boolean pressureAgainstMe() {
        try {
            ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
            for (ActiveClientBattlePokemon a : BattleSideHelper.getOpponentSide(battle).getActiveClientBattlePokemon()) {
                ClientBattlePokemon bp = a == null ? null : a.getBattlePokemon();
                if (bp != null) {
                    ItemAbilityTracker.State st = ItemAbilityTracker.get("opponent", bp);
                    if (st != null && st.abilityId != null && norm(st.abilityId).equals("pressure")) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /** PP left / max for one of your moves (party values when it hasn't been tracked yet). */
    public static int[] myPp(Pokemon pokemon, Move move) {
        Mon m = mine(pokemon);
        String id = norm(move.getName());
        if (m == null) {
            return new int[]{move.getCurrentPp(), move.getMaxPp()};
        }
        int[] start = m.startPp.get(id);
        int[] req = m.pp.get(id);
        if (start == null) {
            return req != null ? req : new int[]{move.getCurrentPp(), move.getMaxPp()};
        }
        int left = Math.max(0, start[0] - m.used.getOrDefault(id, 0));
        if (req != null) {
            // the turn request is the truth when it is newer than the last counted use (Spite, unseen Pressure...)
            left = Math.min(left, req[0]);
        }
        return new int[]{left, start[1]};
    }

    /** PP left / PP Max for an opponent move (they're assumed to run PP Max). */
    public static int[] theirPp(String species, String moveId) {
        MoveTemplate t = moveId == null ? null : Moves.getByName(norm(moveId));
        if (t == null || t.getMaxPp() <= 0) {
            return null;
        }
        int used = 0;
        try {
            BattleCounters.State st = BattleCounters.bySpecies("opponent", species);
            Integer u = st != null ? st.ppUsed.get(norm(moveId)) : null;
            used = u != null ? u : 0;
        } catch (Throwable ignored) {
        }
        return new int[]{Math.max(0, t.getMaxPp() - used), t.getMaxPp()};
    }

    public static int hpColor(float ratio) {
        return ratio > 0.5F ? 0xFF52D273 : (ratio > 0.2F ? 0xFFF2C94C : 0xFFEB5757);
    }

    /** Short label + colour for a status id. */
    public static String statusLabel(String s) {
        return switch (s == null ? "" : s) {
            case "psn" -> "PSN";
            case "tox" -> "TOX";
            case "par" -> "PAR";
            case "brn" -> "BRN";
            case "slp" -> "SLP";
            case "frz" -> "FRZ";
            case "fnt" -> "FNT";
            default -> null;
        };
    }

    public static int statusColor(String s) {
        return switch (s == null ? "" : s) {
            case "psn" -> 0xFFA040A0;
            case "tox" -> 0xFF7B2C8F;
            case "par" -> 0xFFE0B000;
            case "brn" -> 0xFFE0602A;
            case "slp" -> 0xFF8C8C9C;
            case "frz" -> 0xFF5BC0DE;
            case "fnt" -> 0xFF5A5A5A;
            default -> 0xFF808080;
        };
    }
}
