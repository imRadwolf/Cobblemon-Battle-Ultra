package name.modid.client;

import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.battle.ActiveClientBattlePokemon;
import com.cobblemon.mod.common.client.battle.ClientBattle;
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon;
import com.cobblemon.mod.common.client.battle.ClientBattleSide;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.class_2561;
import net.minecraft.class_2588;

public final class BuildInference {
    private static Object trackedBattle;
    private static final Map<String, Map<String, String>> ELIMINATED = new ConcurrentHashMap<>();
    private static final Map<String, List<String>> OFF_META = new ConcurrentHashMap<>();
    private static final Map<String, BuildInference.Info> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Long> CACHE_TIME = new ConcurrentHashMap<>();
    private static final Map<String, BuildInference.SpeedSnapshot> SPEEDS = new ConcurrentHashMap<>();
    private static final Map<String, Float> LAST_RATIO = new ConcurrentHashMap<>();
    private static final Map<String, BuildInference.StoredInput> INPUTS = new ConcurrentHashMap<>();
    private static int turn = 0;
    private static final List<String[]> turnMovers = new ArrayList<>();
    private static boolean turnInvalid;
    private static Map<String, BuildInference.SpeedSnapshot> turnSpeeds;
    private static BuildInference.PendingHit pending;
    private static final Map<String, BuildInference.Inferred> INFERRED = new ConcurrentHashMap<>();
    private static final Map<String, List<String>> MOVES_SINCE_SWITCH = new ConcurrentHashMap<>();
    private static final Set<String> HEALED_THIS_TURN = ConcurrentHashMap.newKeySet();
    private static BuildInference.PendingAttack oppPending;
    private static BuildInference.Entry entry;
    private static boolean opponentFaintedThisTurn;
    private static final Set<String> LATE_ARRIVALS = ConcurrentHashMap.newKeySet();
    private static final Set<String> CALLER_MOVES = Set.of("sleeptalk", "metronome", "assist", "copycat", "mefirst", "naturepower", "mirrormove", "snore");

    private BuildInference() {
    }

    static void dbg(String msg) {
        if (Boolean.getBoolean("cbeplus.debug")) {
            System.out.println("[BXI] " + msg);
        }
    }

    static String norm(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static void checkBattle() {
        Object battle = CobblemonClient.INSTANCE.getBattle();
        if (battle != trackedBattle) {
            trackedBattle = battle;
            ELIMINATED.clear();
            OFF_META.clear();
            CACHE.clear();
            CACHE_TIME.clear();
            SPEEDS.clear();
            LAST_RATIO.clear();
            INPUTS.clear();
            INFERRED.clear();
            MOVES_SINCE_SWITCH.clear();
            HEALED_THIS_TURN.clear();
            LATE_ARRIVALS.clear();
            opponentFaintedThisTurn = false;
            oppPending = null;
            entry = null;
            turn = 0;
            turnMovers.clear();
            turnInvalid = false;
            turnSpeeds = null;
            pending = null;
        }
    }

    public static String key(ClientBattlePokemon bp) {
        try {
            return norm(bp.getSpecies().getName());
        } catch (Throwable var2) {
            return "";
        }
    }

    private static void invalidate(String key) {
        CACHE.remove(key);
        CACHE_TIME.remove(key);
    }

    private static RevealedBattleInfo.PokemonRevealedInfo revealed(ClientBattlePokemon bp) {
        try {
            RevealedBattleInfo.PokemonRevealedInfo info = RevealedBattleInfo.getRevealedInfo(norm(bp.getSpecies().getName()));
            if (info == null) {
                info = RevealedBattleInfo.getRevealedInfo(norm(bp.getDisplayName().getString()));
            }

            return info;
        } catch (Throwable var2) {
            return null;
        }
    }

    public static String revealedAbility(ClientBattlePokemon bp) {
        ItemAbilityTracker.State st = ItemAbilityTracker.get("opponent", bp);
        if (st != null && !st.abilityTemporary && st.abilityName != null) {
            return st.abilityId != null && !st.abilityId.isBlank() ? st.abilityId : st.abilityName;
        } else {
            RevealedBattleInfo.PokemonRevealedInfo info = revealed(bp);
            if (info != null && info.getAbility() != null && !info.getAbility().isBlank()) {
                return info.getAbility();
            } else {
                return info != null && info.getAbilityDisplay() != null ? info.getAbilityDisplay() : null;
            }
        }
    }

    public static String originalItem(ClientBattlePokemon bp) {
        ItemAbilityTracker.State st = ItemAbilityTracker.get("opponent", bp);
        if (st != null && st.originalItem != null) {
            return st.originalItem;
        } else {
            RevealedBattleInfo.PokemonRevealedInfo info = revealed(bp);
            return info != null && info.getHeldItemDisplay() != null && !info.getHeldItemDisplay().isBlank() ? info.getHeldItemDisplay() : null;
        }
    }

    public static Set<String> revealedMoves(ClientBattlePokemon bp) {
        Set<String> out = new LinkedHashSet<>();
        RevealedBattleInfo.PokemonRevealedInfo info = revealed(bp);
        if (info != null) {
            for (RevealedBattleInfo.PokemonRevealedInfo.MoveInfo m : info.getMoves()) {
                if (m != null) {
                    String id = m.moveId != null && !m.moveId.isBlank() ? m.moveId : MoveContactData.resolveMoveIdByDisplayName(m.displayName);
                    if (id != null && !id.isBlank()) {
                        out.add(norm(id));
                    }
                }
            }
        }

        return out;
    }

    public static BuildInference.Info info(ClientBattlePokemon bp) {
        if (bp == null) {
            return null;
        } else {
            try {
                checkBattle();
                String key = key(bp);
                long now = System.currentTimeMillis();
                Long t = CACHE_TIME.get(key);
                BuildInference.Info cached = CACHE.get(key);
                if (cached != null && t != null && now - t < 200L) {
                    return cached;
                } else {
                    BuildInference.Info info = compute(bp, key);
                    CACHE.put(key, info);
                    CACHE_TIME.put(key, now);
                    return info;
                }
            } catch (Throwable var7) {
                return null;
            }
        }
    }

    private static FormData formOf(ClientBattlePokemon bp) {
        FormData form = FormAbilityHelper.effectiveBattleForm(bp, "opponent");
        if (form == null && bp.getSpecies() != null) {
            form = bp.getSpecies().getStandardForm();
        }

        return form;
    }

    private static BuildInference.Info compute(ClientBattlePokemon bp, String key) {
        BuildInference.Info info = new BuildInference.Info();
        Species species = bp.getSpecies();
        FormData form = formOf(bp);
        boolean[] fromBase = new boolean[2];
        info.all = SmogonSets.forForm(species, form, fromBase);
        info.fromBase = fromBase[0];
        MegaDex.Entry megaEntry = MegaDex.forBattler(bp, "opponent");
        boolean megaForm = FormAbilityHelper.isBattleOnlyForm(form) && MegaDex.letterOf(form) != null;
        List<SmogonSets.Set> stoneSets = megaEntry != null && (info.fromBase || !megaForm || info.all.isEmpty())
            ? SmogonSets.holdingStone(species, megaEntry.stone(), megaEntry.ability())
            : List.of();
        if (!stoneSets.isEmpty()) {
            // real builds of this Mega (Pokemon Champions lists them under the base form, holding the stone)
            info.all = stoneSets;
            info.fromBase = false;
            info.reasons.add("Builds for this Mega: the " + species.getName() + " builds holding " + megaEntry.stone());
        } else if (megaEntry != null && !megaForm) {
            info.all = SmogonSets.estimatedForForm(species, form, info.all, MegaTracker.megaLetter("opponent", bp), megaEntry);
            info.fromBase = true;
            info.reasons.add("Mega Evolved: its Mega stats with max EVs in its two best stats (moves from the base form)");
        } else if (info.fromBase && FormAbilityHelper.isBattleOnlyForm(form)) {
            info.all = SmogonSets.estimatedForForm(species, form, info.all, MegaTracker.megaLetter("opponent", bp), megaEntry);
            info.reasons.add("No Smogon build for this Mega: its stats with max EVs in its two best stats (moves from the base form)");
        } else if (info.all.isEmpty() && FormAbilityHelper.isBattleOnlyForm(form)) {
            info.all = SmogonSets.estimatedForForm(species, form, List.of(), MegaTracker.megaLetter("opponent", bp), megaEntry);
            info.fromBase = true;
            info.reasons.add("No Smogon build for this Mega: its stats with max EVs in its two best stats");
        }

        if (fromBase[1] && !info.all.isEmpty()) {
            info.reasons.add("No build in your selected formats: using all formats");
        }

        info.revealedMoves.addAll(revealedMoves(bp));
        info.revealedItem = originalItem(bp);
        info.revealedAbility = revealedAbility(bp);
        OpponentMemory.Entry memory = OpponentMemory.recall(key);
        if (memory != null) {
            info.memoryBattles = OpponentMemory.previousBattles();
            info.memoryLastSeen = memory.lastSeen;
            if (info.revealedItem == null && memory.item != null) {
                info.rememberedItem = memory.item;
            }

            if (info.revealedAbility == null && memory.ability != null) {
                info.rememberedAbility = memory.ability;
            }

            for (String move : memory.moves) {
                if (!info.revealedMoves.contains(move)) {
                    info.rememberedMoves.add(move);
                }
            }
        }

        List<SmogonSets.Set> list = new ArrayList<>(info.all);
        if (!list.isEmpty() && ExtrasPlusConfig.get().buildInference) {
            if (info.revealedItem != null) {
                String item = norm(info.revealedItem);
                List<SmogonSets.Set> kept = new ArrayList<>();

                for (SmogonSets.Set s : list) {
                    if (norm(s.item()).equals(item)) {
                        kept.add(s);
                    }
                }

                if (kept.isEmpty()) {
                    info.reasons.add(info.revealedItem + ": not in any Smogon build");
                } else if (kept.size() < list.size()) {
                    info.reasons.add(info.revealedItem + " revealed: " + (list.size() - kept.size()) + " ruled out");
                    list = kept;
                }
            }

            if (info.revealedAbility != null && !FormAbilityHelper.isBattleOnlyForm(form)) {
                String ability = norm(info.revealedAbility);
                List<SmogonSets.Set> kept = new ArrayList<>();

                for (SmogonSets.Set sx : list) {
                    if (norm(sx.ability()).equals(ability) || sx.ability().isBlank()) {
                        kept.add(sx);
                    }
                }

                if (!kept.isEmpty() && kept.size() < list.size()) {
                    info.reasons.add(FormAbilityHelper.abilityDisplayNameFromId(ability) + " revealed: " + (list.size() - kept.size()) + " ruled out");
                    list = kept;
                }
            }

            if (!info.revealedMoves.isEmpty()) {
                int best = 0;
                Map<SmogonSets.Set, Integer> score = new HashMap<>();

                for (SmogonSets.Set sxx : list) {
                    int n = 0;

                    for (String movex : info.revealedMoves) {
                        if (sxx.hasMove(movex)) {
                            n++;
                        }
                    }

                    score.put(sxx, n);
                    best = Math.max(best, n);
                }

                if (best <= 0) {
                    info.reasons.add("moves used match no Smogon build");
                } else {
                    List<SmogonSets.Set> kept = new ArrayList<>();

                    for (SmogonSets.Set sxx : list) {
                        if (score.get(sxx) == best) {
                            kept.add(sxx);
                        }
                    }

                    if (kept.size() < list.size()) {
                        info.reasons.add("moves used: " + (list.size() - kept.size()) + " ruled out");
                        list = kept;
                    }

                    if (best < info.revealedMoves.size()) {
                        info.reasons.add(info.revealedMoves.size() - best + " move(s) used are in no Smogon build");
                    }
                }
            }

            if (memory != null) {
                String when = " (" + OpponentMemory.age(memory.lastSeen) + ")";
                if (info.rememberedItem != null) {
                    List<SmogonSets.Set> kept = new ArrayList<>();

                    for (SmogonSets.Set sxxx : list) {
                        if (norm(sxxx.item()).equals(norm(info.rememberedItem))) {
                            kept.add(sxxx);
                        }
                    }

                    if (!kept.isEmpty() && kept.size() < list.size()) {
                        info.reasons.add("» " + info.rememberedItem + " last time" + when + ": " + (list.size() - kept.size()) + " ruled out");
                        list = kept;
                    }
                }

                if (info.rememberedAbility != null && !FormAbilityHelper.isBattleOnlyForm(form)) {
                    List<SmogonSets.Set> kept = new ArrayList<>();

                    for (SmogonSets.Set sxxxx : list) {
                        if (norm(sxxxx.ability()).equals(norm(info.rememberedAbility)) || sxxxx.ability().isBlank()) {
                            kept.add(sxxxx);
                        }
                    }

                    if (!kept.isEmpty() && kept.size() < list.size()) {
                        info.reasons
                            .add(
                                "» "
                                    + FormAbilityHelper.abilityDisplayNameFromId(norm(info.rememberedAbility))
                                    + " last time: "
                                    + (list.size() - kept.size())
                                    + " ruled out"
                            );
                        list = kept;
                    }
                }

                if (!info.rememberedMoves.isEmpty()) {
                    int best = 0;
                    Map<SmogonSets.Set, Integer> score = new HashMap<>();

                    for (SmogonSets.Set sxxxxx : list) {
                        int n = 0;

                        for (String movexx : info.rememberedMoves) {
                            if (sxxxxx.hasMove(movexx)) {
                                n++;
                            }
                        }

                        score.put(sxxxxx, n);
                        best = Math.max(best, n);
                    }

                    if (best > 0) {
                        List<SmogonSets.Set> kept = new ArrayList<>();

                        for (SmogonSets.Set sxxxxx : list) {
                            if (score.get(sxxxxx) == best) {
                                kept.add(sxxxxx);
                            }
                        }

                        if (kept.size() < list.size()) {
                            info.reasons.add("» moves seen last time: " + (list.size() - kept.size()) + " ruled out");
                            list = kept;
                        }
                    }
                }

                if (!memory.ruledOut.isEmpty()) {
                    List<SmogonSets.Set> kept = new ArrayList<>();

                    for (SmogonSets.Set sxxxxxx : list) {
                        if (!memory.ruledOut.containsKey(sxxxxxx.id())) {
                            kept.add(sxxxxxx);
                        }
                    }

                    if (!kept.isEmpty() && kept.size() < list.size()) {
                        info.reasons.add("» speed/bulk seen last time: " + (list.size() - kept.size()) + " ruled out");
                        list = kept;
                    }
                }
            }

            Map<String, String> eliminated = ELIMINATED.getOrDefault(key, Map.of());
            if (!eliminated.isEmpty()) {
                List<SmogonSets.Set> kept = new ArrayList<>();
                Map<String, Integer> why = new LinkedHashMap<>();

                for (SmogonSets.Set sxxxxxxx : list) {
                    String reason = eliminated.get(sxxxxxxx.id());
                    if (reason == null) {
                        kept.add(sxxxxxxx);
                    } else {
                        why.merge(reason, 1, Integer::sum);
                    }
                }

                if (!kept.isEmpty()) {
                    for (Map.Entry<String, Integer> e : why.entrySet()) {
                        info.reasons.add(e.getKey() + ": " + e.getValue() + " ruled out");
                    }

                    list = kept;
                }
            }

            info.reasons.addAll(OFF_META.getOrDefault(key, List.of()));
            BuildInference.Inferred inferred = INFERRED.get(key);
            if (inferred != null && info.revealedItem == null) {
                info.inferredItem = inferred.item();
                info.inferredReason = inferred.reason();
                List<SmogonSets.Set> kept = new ArrayList<>();

                for (SmogonSets.Set sxxxxxxxx : list) {
                    if (norm(sxxxxxxxx.item()).equals(norm(inferred.item()))) {
                        kept.add(sxxxxxxxx);
                    }
                }

                info.reasons
                    .add(
                        "Likely "
                            + inferred.item()
                            + ": "
                            + inferred.reason()
                            + (!kept.isEmpty() && kept.size() < list.size() ? " (" + (list.size() - kept.size()) + " ruled out)" : "")
                    );
                if (!kept.isEmpty()) {
                    list = kept;
                }
            }
        }

        info.candidates = List.copyOf(list);

        for (SmogonSets.Set sxxxxxxxxx : info.candidates) {
            for (List<String> slot : sxxxxxxxxx.moves()) {
                double w = 1.0 / Math.max(1, slot.size());

                for (String movexxx : slot) {
                    info.moveWeight.merge(movexxx, w / info.candidates.size(), Double::sum);
                }
            }

            for (String movexxx : sxxxxxxxxx.allMoves()) {
                info.moveCount.merge(movexxx, 1, Integer::sum);
            }

            if (!sxxxxxxxxx.item().isBlank()) {
                info.itemCount.merge(sxxxxxxxxx.item(), 1, Integer::sum);
            }

            if (!sxxxxxxxxx.ability().isBlank()) {
                info.abilityCount.merge(sxxxxxxxxx.ability(), 1, Integer::sum);
            }
        }

        for (String movexxx : info.rememberedMoves) {
            info.moveWeight.put(movexxx, 0.999);
        }

        for (String movexxx : info.revealedMoves) {
            info.moveWeight.put(movexxx, 1.0);
        }

        try {
            BattleRecap.noteInfo(key, species != null ? species.getName() : key, info);
        } catch (Throwable var19) {
        }

        try {
            OpponentMemory.record(key, info.revealedItem, info.revealedAbility, info.revealedMoves, ELIMINATED.get(key));
        } catch (Throwable var18) {
        }

        return info;
    }

    private static void eliminate(String key, List<String> setIds, String reason) {
        Map<String, String> map = ELIMINATED.computeIfAbsent(key, k -> new ConcurrentHashMap<>());

        for (String id : setIds) {
            map.putIfAbsent(id, reason);
        }

        invalidate(key);
    }

    private static void offMeta(String key, String note) {
        List<String> list = OFF_META.computeIfAbsent(key, k -> new ArrayList<>());
        if (!list.contains(note)) {
            list.add(note);
        }

        invalidate(key);
    }

    public static String inferredItem(ClientBattlePokemon bp) {
        try {
            checkBattle();
            BuildInference.Inferred inferred = INFERRED.get(key(bp));
            return inferred != null && originalItem(bp) == null ? inferred.item() : null;
        } catch (Throwable var2) {
            return null;
        }
    }

    public static Integer setSpeed(ClientBattlePokemon bp, String setId) {
        try {
            BuildInference.SpeedSnapshot snap = SPEEDS.get(key(bp));
            return snap != null ? snap.speedBySet().get(setId) : null;
        } catch (Throwable var3) {
            return null;
        }
    }

    private static void infer(String key, String item, String reason) {
        BuildInference.Inferred old = INFERRED.get(key);
        if (old == null || !norm(old.item()).equals(norm(item))) {
            INFERRED.put(key, new BuildInference.Inferred(item, reason));
            dbg("infer " + key + " -> " + item + " (" + reason + ")");
            invalidate(key);
        }
    }

    private static void ruleOutItem(String key, String reason, String... items) {
        ClientBattlePokemon bp = activeOpponent(key);
        if (bp != null) {
            BuildInference.Info info = info(bp);
            if (info != null && info.revealedItem == null) {
                Set<String> banned = new HashSet<>();

                for (String i : items) {
                    banned.add(norm(i));
                }

                BuildInference.Inferred inferred = INFERRED.get(key);
                if (inferred != null && banned.contains(norm(inferred.item()))) {
                    INFERRED.remove(key);
                    offMeta(key, "not " + inferred.item() + " after all: " + reason);
                }

                List<String> out = new ArrayList<>();
                int alive = 0;

                for (SmogonSets.Set s : info.all) {
                    if (banned.contains(norm(s.item()))) {
                        out.add(s.id());
                    }
                }

                for (SmogonSets.Set sx : info.candidates) {
                    if (!banned.contains(norm(sx.item()))) {
                        alive++;
                    }
                }

                if (!out.isEmpty()) {
                    dbg("rule out " + String.join("/", items) + " for " + key + ": " + reason + " (" + out.size() + " builds)");
                    if (alive == 0 && !info.candidates.isEmpty()) {
                        offMeta(key, reason + " (every likely build had one)");
                    }

                    eliminate(key, out, reason);
                }
            }
        }
    }

    private static void ruleOutAbility(String key, String reason, String... abilities) {
        ClientBattlePokemon bp = activeOpponent(key);
        if (bp != null) {
            BuildInference.Info info = info(bp);
            if (info != null && info.revealedAbility == null) {
                Set<String> banned = new HashSet<>();

                for (String a : abilities) {
                    banned.add(norm(a));
                }

                List<String> out = new ArrayList<>();
                boolean anyAlive = false;

                for (SmogonSets.Set s : info.all) {
                    if (banned.contains(norm(s.ability()))) {
                        out.add(s.id());
                    }
                }

                for (SmogonSets.Set sx : info.candidates) {
                    if (!banned.contains(norm(sx.ability()))) {
                        anyAlive = true;
                    }
                }

                if (!out.isEmpty() && anyAlive) {
                    eliminate(key, out, reason);
                }
            }
        }
    }

    private static boolean anyCandidateAbility(BuildInference.Info info, String... abilities) {
        if (info.revealedAbility != null) {
            String a = norm(info.revealedAbility);

            for (String x : abilities) {
                if (a.equals(x)) {
                    return true;
                }
            }

            return false;
        } else {
            for (SmogonSets.Set s : info.candidates) {
                for (String xx : abilities) {
                    if (norm(s.ability()).equals(xx)) {
                        return true;
                    }
                }
            }

            return false;
        }
    }

    private static boolean itemLost(ClientBattlePokemon bp) {
        ItemAbilityTracker.State st = ItemAbilityTracker.get("opponent", bp);
        return st != null && (st.itemLost || st.swapPending);
    }

    public static void recordSpeeds(
        ClientBattlePokemon opponent, Map<String, Integer> speedBySet, Integer mySpeed, boolean trickRoom, int theoMax, int theoMaxScarf
    ) {
        if (opponent != null && mySpeed != null && speedBySet != null && !speedBySet.isEmpty()) {
            checkBattle();
            SPEEDS.put(
                key(opponent), new BuildInference.SpeedSnapshot(Map.copyOf(speedBySet), mySpeed, trickRoom, System.currentTimeMillis(), theoMax, theoMaxScarf)
            );
        }
    }

    public static void rememberInput(DamageRefiner.Input input, ClientBattlePokemon defender) {
        rememberInput(input, defender, false);
    }

    public static void rememberInput(DamageRefiner.Input input, ClientBattlePokemon defender, boolean weak) {
        if (input != null && defender != null) {
            checkBattle();
            if (weak) {
                BuildInference.StoredInput existing = INPUTS.get(norm(input.move().getName()));
                if (existing != null && System.currentTimeMillis() - existing.time() < 15000L) {
                    return;
                }
            }

            INPUTS.put(norm(input.move().getName()), new BuildInference.StoredInput(input, key(defender), turn, System.currentTimeMillis()));
        }
    }

    private static String moveIdFromArg(Object arg) {
        if (arg instanceof class_2561 text && text.method_10851() instanceof class_2588 tc) {
            String k = tc.method_11022();
            if (k != null && k.startsWith("cobblemon.move.")) {
                return norm(k.substring("cobblemon.move.".length()));
            }
        }

        if (arg instanceof class_2561 textx) {
            String id = MoveContactData.resolveMoveIdByDisplayName(textx.getString());
            return id == null ? null : norm(id);
        } else {
            return null;
        }
    }

    private static boolean singles() {
        try {
            ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
            return battle != null && battle.getBattleFormat().getBattleType().getPokemonPerSide() == 1;
        } catch (Throwable var1) {
            return false;
        }
    }

    private static ClientBattlePokemon activeOpponent(String speciesKey) {
        try {
            ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
            ClientBattleSide side = BattleSideHelper.getOpponentSide(battle);

            for (ActiveClientBattlePokemon active : side.getActiveClientBattlePokemon()) {
                if (active != null && active.getBattlePokemon() != null && (speciesKey == null || key(active.getBattlePokemon()).equals(speciesKey))) {
                    return active.getBattlePokemon();
                }
            }
        } catch (Throwable var5) {
        }

        return null;
    }

    private static float currentRatio(ClientBattlePokemon bp) {
        try {
            float max = bp.getMaxHp();
            float value = bp.getHpValue();
            if (bp.isHpFlat()) {
                return max > 0.0F ? value / max : -1.0F;
            } else {
                return value > 1.0001F ? value / 100.0F : value;
            }
        } catch (Throwable var3) {
            return -1.0F;
        }
    }

    private static String[] whoArg(Object[] args, int idx) {
        if (args != null && args.length > idx) {
            String[] c = BattleMessageSubscriber.resolvePokemonSideAndSpecies(args, idx);
            return c != null && c[0] != null && c[1] != null ? c : null;
        } else {
            return null;
        }
    }

    private static void finishAll() {
        finishPending();
        finishOppPending();
        finishEntry();
    }

    public static void onMessage(String key, Object[] args) {
        if (key != null) {
            try {
                checkBattle();
                if (!key.startsWith("cobblemon.battle.")) {
                    return;
                }

                String k = key.substring("cobblemon.battle.".length());
                if (BattleCounters.isSwitchKey(k)) {
                    finishAll();
                    String[] w = BattleCounters.switchWho(k, args);
                    if (w != null && "opponent".equals(w[0])) {
                        String sk = norm(w[1]);
                        MOVES_SINCE_SWITCH.remove(sk);
                        if (!k.startsWith("withdraw")) {
                            startEntry(sk);
                            if (opponentFaintedThisTurn) {
                                LATE_ARRIVALS.add(sk);
                            }
                        }
                    }

                    return;
                }

                if (k.equals("fainted")) {
                    String[] w = whoArg(args, 0);
                    if (w != null && "opponent".equals(w[0])) {
                        opponentFaintedThisTurn = true;
                    }
                }

                if (k.equals("turn")) {
                    finishAll();
                    checkLeftovers();
                    HEALED_THIS_TURN.clear();
                    LATE_ARRIVALS.clear();
                    opponentFaintedThisTurn = false;
                    turn++;
                    turnMovers.clear();
                    turnInvalid = false;
                    turnSpeeds = null;
                    return;
                }

                if (k.equals("heal.leftovers") || k.equals("heal.item")) {
                    String[] w = whoArg(args, 0);
                    if (w != null && "opponent".equals(w[0])) {
                        HEALED_THIS_TURN.add(norm(w[1]));
                    }
                }

                if (k.equals("damage.stealthrock")) {
                    String[] w = whoArg(args, 0);
                    if (w != null && "opponent".equals(w[0]) && entry != null && entry.key.equals(norm(w[1]))) {
                        entry.tookRocks = true;
                        ruleOutItem(entry.key, "took Stealth Rock damage on entry", "Heavy-Duty Boots");
                    }

                    return;
                }

                if (k.equals("damage.lifeorb")) {
                    String[] w = whoArg(args, 0);
                    BuildInference.PendingAttack q = oppPending;
                    if (w != null && "opponent".equals(w[0]) && q != null && q.oppKey.equals(norm(w[1]))) {
                        q.lifeOrb = true;
                    }

                    return;
                }

                if (k.equals("damage.rockyhelmet")) {
                    if (pending != null) {
                        pending.helmet = true;
                    }

                    return;
                }

                if (!k.equals("damage.roughskin") && !k.equals("damage.ironbarbs")) {
                    if (k.equals("damage.recoil")) {
                        return;
                    }

                    if (!k.equals("used_move") && !k.equals("used_move_on")) {
                        if (k.startsWith("activate.quickclaw")
                            || k.startsWith("activate.custapberry")
                            || k.startsWith("activate.quash")
                            || k.startsWith("activate.afteryou")) {
                            turnInvalid = true;
                        }

                        BuildInference.PendingHit p = pending;
                        if (p != null) {
                            dbg("msg while pending: " + k);
                            if (k.equals("crit") || k.startsWith("crit_")) {
                                p.crit = true;
                            } else if (k.startsWith("hit_count")) {
                                p.hits = hitCount(k, args);
                            } else if (!k.equals("fainted") && !k.contains("ffective") && !k.startsWith("resisted")) {
                                if (p.hit) {
                                    finishPending();
                                } else {
                                    p.tainted = true;
                                }
                            }
                        }

                        BuildInference.PendingAttack q = oppPending;
                        if (q != null) {
                            if (k.equals("crit") || k.startsWith("crit_")) {
                                q.crit = true;
                                return;
                            } else {
                                if (k.startsWith("hit_count")) {
                                    q.hits = hitCount(k, args);
                                } else if (!k.equals("fainted")
                                    && !k.startsWith("enditem.focussash")
                                    && !k.startsWith("activate.sturdy")
                                    && !k.startsWith("activate.endure")
                                    && !k.contains("ffective")
                                    && !k.startsWith("resisted")
                                    && !k.startsWith("damage.")) {
                                    if (q.hit) {
                                        finishOppPending();
                                    } else {
                                        q.tainted = true;
                                    }

                                    return;
                                }

                                return;
                            }
                        }

                        return;
                    }

                    finishAll();
                    String[] who = args != null && args.length > 0 ? BattleMessageSubscriber.resolvePokemonSideAndSpecies(args, 0) : null;
                    String moveId = args != null && args.length > 1 ? moveIdFromArg(args[1]) : null;
                    if (who != null && who[0] != null && who[1] != null && moveId != null) {
                        if (turnSpeeds == null) {
                            turnSpeeds = new HashMap<>(SPEEDS);
                        }

                        turnMovers.add(new String[]{who[0], norm(who[1]), moveId});
                        dbg("used_move " + who[0] + " " + who[1] + " " + moveId + " turn=" + turn + " snapshots=" + turnSpeeds.keySet());
                        if (turnMovers.size() == 2) {
                            inferFromOrder();
                        }

                        if ("player".equals(who[0]) && singles()) {
                            ClientBattlePokemon opp = activeOpponent(null);
                            if (opp != null) {
                                BuildInference.PendingHit px = new BuildInference.PendingHit();
                                px.defenderKey = key(opp);
                                px.moveId = moveId;
                                Float last = LAST_RATIO.get(px.defenderKey);
                                px.before = last != null ? last : currentRatio(opp);
                                px.contact = myContact(moveId);
                                pending = px;
                            }
                        }

                        if ("opponent".equals(who[0])) {
                            onOpponentMove(norm(who[1]), moveId);
                        }
                    }

                    return;
                }

                if (pending != null) {
                    pending.roughSkin = true;
                }

                return;
            } catch (Throwable var8) {
            }
        }
    }

    private static int hitCount(String k, Object[] args) {
        if (k.equals("hit_count_singular")) {
            return 1;
        } else {
            if (args != null && args.length > 0) {
                try {
                    return Integer.parseInt((args[0] instanceof class_2561 t ? t.getString() : String.valueOf(args[0])).trim());
                } catch (NumberFormatException var4) {
                }
            }

            return -1;
        }
    }

    private static boolean myContact(String moveId) {
        try {
            Boolean contact = MoveContactData.isContact(moveId);
            if (contact != null && contact) {
                ClientBattlePokemon mine = MoveInsights.activeMine();
                Pokemon p = mine != null ? PartyInfoSideRenderer.findPartyPokemonForBattlePokemon(mine) : null;
                if (p == null) {
                    return false;
                } else {
                    String ability = HiddenPowerHelper.playerAbility(p);
                    String item = p.heldItem() != null && !p.heldItem().method_7960() ? norm(p.heldItem().method_7964().getString()) : "";
                    ItemAbilityTracker.State st = ItemAbilityTracker.get("player", mine);
                    if (st != null && st.itemLost) {
                        item = "";
                    }

                    return !ability.equals("longreach")
                        && !ability.equals("magicguard")
                        && !item.equals("protectivepads")
                        && (!item.equals("punchingglove") || !MoveDamagePreviewCalculator.isPunchingMove(norm(moveId)));
                }
            } else {
                return false;
            }
        } catch (Throwable var7) {
            return false;
        }
    }

    private static void onOpponentMove(String oppKey, String moveId) {
        ClientBattlePokemon bp = activeOpponent(oppKey);
        MoveTemplate move = Moves.getByName(moveId);
        if (bp != null && move != null && ExtrasPlusConfig.get().buildInference) {
            String name = move.getDisplayName().getString();
            List<String> used = MOVES_SINCE_SWITCH.computeIfAbsent(oppKey, x -> new ArrayList<>());
            String previous = used.isEmpty() ? null : used.get(used.size() - 1);
            boolean called = previous != null && CALLER_MOVES.contains(previous);
            if (!called && !moveId.equals("struggle")) {
                if (!itemLost(bp)) {
                    for (String m : used) {
                        if (!m.equals(moveId) && !CALLER_MOVES.contains(m) && !m.equals("struggle")) {
                            ruleOutItem(oppKey, "used 2 different moves without switching", "Choice Band", "Choice Specs", "Choice Scarf");
                            break;
                        }
                    }

                    if (isStatus(move)) {
                        ruleOutItem(oppKey, "used " + name + " (status move)", "Assault Vest");
                    }
                }

                used.add(moveId);
            }

            if (singles() && !isStatus(move)) {
                BuildInference.PendingAttack q = new BuildInference.PendingAttack();
                q.oppKey = oppKey;
                q.moveId = moveId;
                oppPending = q;
            }
        }
    }

    private static void startEntry(String oppKey) {
        try {
            Map<String, Integer> hazards = BattleMessageSubscriber.getHazardsForSide("opponent");
            if (hazards != null && hazards.getOrDefault("Stealth Rock", 0) > 0) {
                BuildInference.Entry e = new BuildInference.Entry();
                e.key = oppKey;
                e.stealthRockUp = true;
                entry = e;
            }
        } catch (Throwable var3) {
        }
    }

    private static void finishEntry() {
        BuildInference.Entry e = entry;
        entry = null;
        if (e != null && e.stealthRockUp && !e.tookRocks && ExtrasPlusConfig.get().buildInference) {
            try {
                ClientBattlePokemon bp = activeOpponent(e.key);
                if (bp == null) {
                    return;
                }

                BuildInference.Info info = info(bp);
                if (info == null || info.revealedItem != null || anyCandidateAbility(info, "magicguard")) {
                    return;
                }

                dbg("entry without rocks: " + e.key);
                infer(e.key, "Heavy-Duty Boots", "took no Stealth Rock damage when switching in");
            } catch (Throwable var3) {
            }
        }
    }

    private static void checkLeftovers() {
        if (turn >= 1 && ExtrasPlusConfig.get().buildInference) {
            try {
                ClientBattle battle = CobblemonClient.INSTANCE.getBattle();

                for (ActiveClientBattlePokemon active : BattleSideHelper.getOpponentSide(battle).getActiveClientBattlePokemon()) {
                    ClientBattlePokemon bp = active == null ? null : active.getBattlePokemon();
                    if (bp != null) {
                        String k = key(bp);
                        Float packet = LAST_RATIO.get(k);
                        float ratio = packet != null ? packet : currentRatio(bp);
                        if (!(ratio <= 0.0F) && !(ratio >= 0.995F) && !HEALED_THIS_TURN.contains(k) && !LATE_ARRIVALS.contains(k) && !itemLost(bp)) {
                            String species = bp.getSpecies().getName();
                            if (!VolatileTracker.has("opponent", species, "healblock") && !VolatileTracker.has("opponent", species, "embargo")) {
                                boolean poison = false;

                                for (ElementalType t : FormAbilityHelper.types(formOf(bp))) {
                                    poison |= norm(t.getName()).equals("poison");
                                }

                                dbg("no leftovers heal for " + k + " at " + ratio);
                                if (poison) {
                                    ruleOutItem(k, "didn't heal at the end of the turn", "Leftovers", "Black Sludge");
                                } else {
                                    ruleOutItem(k, "didn't heal at the end of the turn", "Leftovers");
                                }
                            }
                        }
                    }
                }
            } catch (Throwable var11) {
            }
        }
    }

    public static void onHealth(ActiveClientBattlePokemon active, float newHealth) {
        try {
            checkBattle();
            if (active == null || active.getBattlePokemon() == null) {
                return;
            }

            ClientBattlePokemon bp = active.getBattlePokemon();
            ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
            ClientBattleSide opponentSide = BattleSideHelper.getOpponentSide(battle);
            boolean isOpponent = false;

            for (ActiveClientBattlePokemon a : opponentSide.getActiveClientBattlePokemon()) {
                if (a == active) {
                    isOpponent = true;
                }
            }

            if (!isOpponent) {
                BuildInference.PendingAttack q = oppPending;
                if (q != null && bp.isHpFlat() && bp.getMaxHp() > 0.0F) {
                    int now = Math.round(bp.getHpValue());
                    int next = Math.round(newHealth);
                    if (q.myBeforeHp < 0) {
                        q.myBeforeHp = now;
                    }

                    q.myMaxHp = Math.round(bp.getMaxHp());
                    if (next < q.myBeforeHp) {
                        q.myAfterHp = next;
                        q.hit = true;
                    }

                    dbg("my health " + now + " -> " + next + " pending " + q.moveId);
                }

                return;
            }

            float ratio;
            if (bp.isHpFlat()) {
                ratio = bp.getMaxHp() > 0.0F ? newHealth / bp.getMaxHp() : -1.0F;
            } else {
                ratio = newHealth > 1.0001F ? newHealth / 100.0F : newHealth;
            }

            if (ratio < 0.0F) {
                return;
            }

            String key = key(bp);
            BuildInference.PendingHit p = pending;
            if (p != null && p.defenderKey.equals(key)) {
                if (p.before < 0.0F) {
                    Float last = LAST_RATIO.get(key);
                    p.before = last != null ? last : 1.0F;
                }

                p.after = ratio;
                p.hit = true;
            }

            LAST_RATIO.put(key, ratio);
            dbg("health " + key + " -> " + ratio + " pending=" + (p != null ? p.moveId + " before=" + p.before : "none"));
        } catch (Throwable var10) {
        }
    }

    private static void inferFromOrder() {
        if (!turnInvalid && singles() && turnSpeeds != null) {
            String[] first = turnMovers.get(0);
            String[] second = turnMovers.get(1);
            if (first[0].equals(second[0])) {
                dbg("order skip 2");
            } else {
                String[] opp = "opponent".equals(first[0]) ? first : second;
                String[] mine = "player".equals(first[0]) ? first : second;
                boolean opponentFirst = opp == first;
                BuildInference.SpeedSnapshot snap = turnSpeeds.get(opp[1]);
                if (snap != null && System.currentTimeMillis() - snap.time() <= 180000L) {
                    MoveTemplate oppMove = Moves.getByName(opp[2]);
                    MoveTemplate myMove = Moves.getByName(mine[2]);
                    if (oppMove == null || myMove == null || oppMove.getPriority() != myMove.getPriority()) {
                        dbg("order skip 4");
                    } else if (BattleMessageSubscriber.getTrickRoomTurns() > 0 != snap.trickRoom()) {
                        dbg("order skip 5");
                    } else {
                        ClientBattlePokemon bp = activeOpponent(opp[1]);
                        if (bp == null) {
                            dbg("order skip 6");
                        } else {
                            BuildInference.Info info = info(bp);
                            if (info != null && !info.candidates.isEmpty()) {
                                boolean oppStatus = isStatus(oppMove);

                                for (SmogonSets.Set s : info.candidates) {
                                    String ab = norm(s.ability());
                                    if (oppStatus && ab.equals("prankster")
                                        || ab.equals("stall")
                                        || ab.equals("myceliummight")
                                        || ab.equals("galewings") && "flying".equals(norm(oppMove.getElementalType().getName()))) {
                                        dbg("order skip 8");
                                        return;
                                    }
                                }

                                String myAbility = norm(currentPlayerAbility());
                                if ((!isStatus(myMove) || !myAbility.equals("prankster")) && !myAbility.equals("stall") && !myAbility.equals("myceliummight")) {
                                    int mySpeed = snap.mySpeed();
                                    boolean tr = snap.trickRoom();
                                    List<String> out = new ArrayList<>();
                                    dbg("order check " + opp[1] + " first=" + opponentFirst + " my=" + mySpeed + " speeds=" + snap.speedBySet().values());

                                    for (SmogonSets.Set sx : info.candidates) {
                                        Integer speed = snap.speedBySet().get(sx.id());
                                        if (speed != null && speed != mySpeed) {
                                            boolean theyAreFaster = tr ? speed < mySpeed : speed > mySpeed;
                                            if (theyAreFaster != opponentFirst) {
                                                out.add(sx.id());
                                            }
                                        }
                                    }

                                    String reason = opponentFirst ? "moved before you (" + mySpeed + " Spe)" : "moved after you (" + mySpeed + " Spe)";
                                    if (out.size() >= info.candidates.size()) {
                                        boolean alreadyScarf = "choicescarf".equals(norm(INFERRED.containsKey(opp[1]) ? INFERRED.get(opp[1]).item() : ""));
                                        String scarfReason = null;
                                        if (opponentFirst && !tr && info.revealedItem == null && !alreadyScarf) {
                                            boolean speedAbility = anyCandidateAbility(info, "protosynthesis", "quarkdrive", "unburden", "speedboost");
                                            String suffix = speedAbility ? " (or a Speed-boosting ability)" : "";
                                            if (snap.theoMax() > 0 && snap.theoMax() < mySpeed && snap.theoMaxScarf() > mySpeed) {
                                                scarfReason = "moved first vs your "
                                                    + mySpeed
                                                    + " Spe: even max Speed ("
                                                    + snap.theoMax()
                                                    + ") is slower without it"
                                                    + suffix;
                                            } else {
                                                for (SmogonSets.Set sxx : info.candidates) {
                                                    Integer speed = snap.speedBySet().get(sxx.id());
                                                    if (speed != null
                                                        && !norm(sxx.item()).equals("choicescarf")
                                                        && Math.floor(speed.intValue() * 1.5) > mySpeed) {
                                                        scarfReason = "moved first vs your " + mySpeed + " Spe: no build is that fast without it" + suffix;
                                                        break;
                                                    }
                                                }
                                            }
                                        }

                                        if (scarfReason != null) {
                                            infer(opp[1], "Choice Scarf", scarfReason);
                                        } else if (opponentFirst && !tr && snap.theoMaxScarf() > 0 && snap.theoMaxScarf() <= mySpeed && !alreadyScarf) {
                                            offMeta(
                                                opp[1],
                                                "outsped your " + mySpeed + " Spe: faster than possible even with Choice Scarf (untracked Speed boost?)"
                                            );
                                        } else if (opponentFirst && snap.theoMax() >= mySpeed) {
                                            offMeta(opp[1], "outsped your " + mySpeed + " Spe: more Speed than any build (or Choice Scarf)");
                                        } else {
                                            offMeta(
                                                opp[1],
                                                (opponentFirst ? "outsped you" : "was outsped")
                                                    + " at "
                                                    + mySpeed
                                                    + " Spe: no build fits (item or off-meta spread?)"
                                            );
                                        }
                                    } else if (!out.isEmpty()) {
                                        eliminate(opp[1], out, reason);
                                    }
                                } else {
                                    dbg("order skip 9");
                                }
                            } else {
                                dbg("order skip 7");
                            }
                        }
                    }
                } else {
                    dbg("order skip 3");
                }
            }
        } else {
            dbg("order skip 1");
        }
    }

    private static String currentPlayerAbility() {
        try {
            ClientBattle battle = CobblemonClient.INSTANCE.getBattle();

            for (ActiveClientBattlePokemon a : BattleSideHelper.getPlayerSide(battle).getActiveClientBattlePokemon()) {
                if (a != null && a.getBattlePokemon() != null) {
                    Pokemon p = PartyInfoSideRenderer.findPartyPokemonForBattlePokemon(a.getBattlePokemon());
                    return p != null ? p.getAbility().getTemplate().getName() : "";
                }
            }
        } catch (Throwable var4) {
        }

        return "";
    }

    static boolean isStatus(MoveTemplate move) {
        try {
            return "status".equalsIgnoreCase(move.getDamageCategory().getName());
        } catch (Throwable var2) {
            return false;
        }
    }

    private static boolean bulkHypotheses(
        BuildInference.PendingHit p,
        DamageRefiner.Input in,
        ClientBattlePokemon bp,
        FormData form,
        BuildInference.Info info,
        int hits,
        double observed,
        boolean lowerBoundOnly,
        double tolerance,
        String moveName
    ) {
        if (!lowerBoundOnly && info.revealedItem == null) {
            double lowest = Double.MAX_VALUE;

            for (SmogonSets.Set s : info.candidates) {
                double[] pct = DamageRefiner.percentForSet(in, bp, form, s, hits, p.before);
                if (pct != null) {
                    lowest = Math.min(lowest, pct[0]);
                }
            }

            if (lowest != Double.MAX_VALUE && !(observed >= lowest - tolerance)) {
                List<String> fitting = new ArrayList<>();
                List<String> items = new ArrayList<>();
                if (!in.physical()) {
                    items.add("Assault Vest");
                }

                try {
                    if (form != null && !form.getEvolutions().isEmpty()) {
                        items.add("Eviolite");
                    }
                } catch (Throwable var21) {
                }

                for (String item : items) {
                    for (SmogonSets.Set sx : info.candidates) {
                        double[] pct = DamageRefiner.percentForSet(in, bp, form, sx, hits, p.before, item, null);
                        if (pct != null && pct[1] + tolerance >= observed && pct[0] - tolerance <= observed) {
                            fitting.add(item);
                            break;
                        }
                    }
                }

                dbg("bulk hypotheses for " + p.defenderKey + ": " + fitting);
                if (fitting.size() == 1) {
                    infer(p.defenderKey, fitting.get(0), moveName + " did only " + Math.round(observed) + "%");
                    return true;
                } else if (fitting.size() > 1) {
                    offMeta(p.defenderKey, moveName + " did only " + Math.round(observed) + "%: likely " + String.join(" or ", fitting));
                    return true;
                } else {
                    return false;
                }
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    private static void finishOppPending() {
        BuildInference.PendingAttack q = oppPending;
        oppPending = null;
        if (q != null && q.hit && ExtrasPlusConfig.get().buildInference) {
            try {
                ClientBattlePokemon bp = activeOpponent(q.oppKey);
                ClientBattlePokemon mine = MoveInsights.activeMine();
                if (bp == null || mine == null) {
                    return;
                }

                BuildInference.Info info = info(bp);
                if (info == null) {
                    return;
                }

                MoveTemplate move = Moves.getByName(q.moveId);
                String moveName = move != null ? move.getDisplayName().getString() : q.moveId;
                dbg(
                    "opp attack "
                        + q.oppKey
                        + " "
                        + q.moveId
                        + " "
                        + q.myBeforeHp
                        + "->"
                        + q.myAfterHp
                        + "/"
                        + q.myMaxHp
                        + " crit="
                        + q.crit
                        + " tainted="
                        + q.tainted
                        + " lo="
                        + q.lifeOrb
                );
                if (!q.lifeOrb && !itemLost(bp) && !anyCandidateAbility(info, "magicguard", "sheerforce")) {
                    ruleOutItem(q.oppKey, "no Life Orb recoil after " + moveName, "Life Orb");
                }

                if (q.crit || q.tainted || q.myBeforeHp <= 0 || q.myAfterHp < 0 || q.myMaxHp <= 0 || info.candidates.isEmpty()) {
                    return;
                }

                if (MoveDamagePreviewCalculator.hitRange(q.moveId) != null) {
                    return;
                }

                Pokemon target = PartyInfoSideRenderer.findPartyPokemonForBattlePokemon(mine);
                if (target == null) {
                    return;
                }

                ThreatCalc.Env env = ThreatCalc.prepare(bp, target, mine, null);
                if (env == null) {
                    return;
                }

                float before = (float)q.myBeforeHp / q.myMaxHp;
                int observed = q.myBeforeHp - q.myAfterHp;
                boolean lowerBoundOnly = q.myAfterHp <= 1;
                List<String> out = new ArrayList<>();
                int evaluated = 0;
                int highest = 0;
                int lowest = Integer.MAX_VALUE;

                for (SmogonSets.Set s : info.candidates) {
                    int[] dmg = ThreatCalc.perSet(env, q.moveId, s, null, before);
                    if (dmg != null && dmg[0] >= 0) {
                        evaluated++;
                        highest = Math.max(highest, dmg[1]);
                        lowest = Math.min(lowest, dmg[0]);
                        boolean fits = lowerBoundOnly ? dmg[1] + 1 >= observed : dmg[1] + 1 >= observed && dmg[0] - 1 <= observed;
                        if (!fits) {
                            out.add(s.id());
                        }
                    }
                }

                String pct = Math.round(observed * 100.0 / q.myMaxHp) + "%";
                dbg("offense check " + moveName + " observed=" + observed + " range=" + lowest + "-" + highest + " out=" + out.size() + "/" + evaluated);
                if (evaluated == 0) {
                    return;
                }

                if (out.size() < evaluated) {
                    if (!out.isEmpty()) {
                        eliminate(q.oppKey, out, moveName + " did " + pct + " to you");
                    }
                } else if (observed > highest + 1) {
                    List<String> hyps = new ArrayList<>();
                    boolean physical = move != null && "physical".equalsIgnoreCase(move.getDamageCategory().getName());
                    hyps.add(physical ? "Choice Band" : "Choice Specs");
                    hyps.add("Life Orb");
                    hyps.add("Expert Belt");
                    String typeItem = MovePower.typeBoostItemName(ThreatCalc.moveType(env, move, q.moveId));
                    if (typeItem != null) {
                        hyps.add(typeItem);
                    }

                    List<String> fitting = new ArrayList<>();

                    for (String h : hyps) {
                        if (!h.equals("Life Orb") || q.lifeOrb) {
                            for (SmogonSets.Set sx : info.candidates) {
                                int[] dmg = ThreatCalc.perSet(env, q.moveId, sx, h, before);
                                if (dmg != null && dmg[0] >= 0 && dmg[1] + 1 >= observed && (lowerBoundOnly || dmg[0] - 1 <= observed)) {
                                    fitting.add(h);
                                    break;
                                }
                            }
                        }
                    }

                    dbg("offense hypotheses: " + fitting);
                    if (fitting.size() == 1) {
                        infer(q.oppKey, fitting.get(0), moveName + " did " + pct + " to you, more than any build without it");
                    } else if (!fitting.isEmpty()) {
                        offMeta(q.oppKey, moveName + " did " + pct + " to you: hits harder than any build (" + String.join(" or ", fitting) + "?)");
                    } else {
                        offMeta(q.oppKey, moveName + " did " + pct + " to you: hits harder than any known build");
                    }
                } else if (!lowerBoundOnly && observed < lowest - 1) {
                    offMeta(q.oppKey, moveName + " did only " + pct + " to you: less Attack investment than any build");
                }
            } catch (Throwable var25) {
                dbg("opp attack check failed: " + var25);
            }
        }
    }

    private static void finishPending() {
        BuildInference.PendingHit p = pending;
        pending = null;
        if (p != null) {
            dbg(
                "finish "
                    + p.moveId
                    + " hit="
                    + p.hit
                    + " crit="
                    + p.crit
                    + " tainted="
                    + p.tainted
                    + " before="
                    + p.before
                    + " after="
                    + p.after
                    + " hits="
                    + p.hits
            );
        }

        if (p != null && p.hit && p.contact && p.after >= 0.0F && p.after < p.before && ExtrasPlusConfig.get().buildInference) {
            try {
                if (!p.helmet) {
                    ruleOutItem(p.defenderKey, "no Rocky Helmet damage on contact", "Rocky Helmet");
                }

                if (!p.roughSkin && p.after > 0.0F) {
                    ruleOutAbility(p.defenderKey, "no Rough Skin / Iron Barbs damage on contact", "Rough Skin", "Iron Barbs");
                }
            } catch (Throwable var18) {
            }
        }

        if (p != null && p.hit && !p.crit && !p.tainted && !(p.before <= 0.0F) && !(p.after < 0.0F) && !(p.after >= p.before)) {
            try {
                BuildInference.StoredInput stored = INPUTS.get(p.moveId);
                dbg("stored input " + (stored == null ? "none" : stored.defenderKey() + " vs " + p.defenderKey));
                if (stored == null || !stored.defenderKey().equals(p.defenderKey) || System.currentTimeMillis() - stored.time() > 180000L) {
                    return;
                }

                DamageRefiner.Input in = stored.input();
                int hits;
                if (p.hits > 0) {
                    hits = p.hits;
                } else {
                    if (in.minHits() != in.maxHits()) {
                        return;
                    }

                    hits = in.maxHits();
                }

                ClientBattlePokemon bp = activeOpponent(p.defenderKey);
                if (bp == null) {
                    return;
                }

                BuildInference.Info info = info(bp);
                if (info == null || info.candidates.size() < 2) {
                    return;
                }

                FormData form = formOf(bp);
                double observed = (p.before - p.after) * 100.0;
                boolean lowerBoundOnly = p.after <= 0.03F;
                double tolerance = 1.6;
                List<String> out = new ArrayList<>();
                int evaluated = 0;

                for (SmogonSets.Set s : info.candidates) {
                    double[] pct = DamageRefiner.percentForSet(in, bp, form, s, hits, p.before);
                    if (pct != null) {
                        evaluated++;
                        boolean fits = lowerBoundOnly ? pct[1] + tolerance >= observed : pct[1] + tolerance >= observed && pct[0] - tolerance <= observed;
                        if (!fits) {
                            out.add(s.id());
                        }
                    }
                }

                String moveName = in.move().getDisplayName().getString();
                dbg("damage check " + moveName + " observed=" + observed + " evaluated=" + evaluated + " out=" + out.size() + "/" + info.candidates.size());
                if (evaluated == 0) {
                    return;
                }

                if (out.size() >= evaluated) {
                    if (!bulkHypotheses(p, in, bp, form, info, hits, observed, lowerBoundOnly, tolerance, moveName)) {
                        offMeta(p.defenderKey, moveName + " did " + Math.round(observed) + "%: no build fits (bulk item or off-meta spread?)");
                    }
                } else if (!out.isEmpty()) {
                    eliminate(p.defenderKey, out, moveName + " did " + Math.round(observed) + "%");
                }
            } catch (Throwable var19) {
            }
        }
    }

    private static final class Entry {
        String key;
        boolean stealthRockUp;
        boolean tookRocks;
    }

    public record Inferred(String item, String reason) {
    }

    public static final class Info {
        public List<SmogonSets.Set> all = List.of();
        public List<SmogonSets.Set> candidates = List.of();
        public boolean fromBase;
        public final List<String> reasons = new ArrayList<>();
        public final Set<String> revealedMoves = new LinkedHashSet<>();
        public String revealedItem;
        public String revealedAbility;
        public final Map<String, Double> moveWeight = new LinkedHashMap<>();
        public final Map<String, Integer> moveCount = new LinkedHashMap<>();
        public final Map<String, Integer> itemCount = new LinkedHashMap<>();
        public final Map<String, Integer> abilityCount = new LinkedHashMap<>();
        public String rememberedItem;
        public String rememberedAbility;
        public final Set<String> rememberedMoves = new LinkedHashSet<>();
        public int memoryBattles;
        public long memoryLastSeen;
        public String inferredItem;
        public String inferredReason;

        public boolean hasMemory() {
            return this.rememberedItem != null
                || this.rememberedAbility != null
                || !this.rememberedMoves.isEmpty()
                || this.memoryBattles > 0 && this.memoryLastSeen > 0L;
        }

        public int ruledOut() {
            return this.all.size() - this.candidates.size();
        }

        public List<String> likelyMoves() {
            List<String> ids = new ArrayList<>(this.moveWeight.keySet());
            ids.removeIf(this.revealedMoves::contains);
            ids.sort((a, b) -> Double.compare(this.moveWeight.get(b), this.moveWeight.get(a)));
            return ids;
        }

        public String topItem() {
            return this.inferredItem != null ? this.inferredItem : top(this.itemCount);
        }

        public String topAbility() {
            return top(this.abilityCount);
        }

        private static String top(Map<String, Integer> counts) {
            String best = null;
            int bestCount = 0;

            for (Map.Entry<String, Integer> e : counts.entrySet()) {
                if (e.getKey() != null && !e.getKey().isBlank() && e.getValue() > bestCount) {
                    best = e.getKey();
                    bestCount = e.getValue();
                }
            }

            return best;
        }
    }

    private static final class PendingAttack {
        String oppKey;
        String moveId;
        int myBeforeHp = -1;
        int myAfterHp = -1;
        int myMaxHp = -1;
        boolean hit;
        boolean crit;
        boolean tainted;
        boolean lifeOrb;
        int hits = -1;
    }

    private static final class PendingHit {
        String defenderKey;
        String moveId;
        float before = -1.0F;
        float after = -1.0F;
        boolean hit;
        boolean crit;
        boolean tainted;
        int hits = -1;
        boolean contact;
        boolean helmet;
        boolean roughSkin;
    }

    public record SpeedSnapshot(Map<String, Integer> speedBySet, int mySpeed, boolean trickRoom, long time, int theoMax, int theoMaxScarf) {
    }

    private record StoredInput(DamageRefiner.Input input, String defenderKey, int turn, long time) {
    }
}
