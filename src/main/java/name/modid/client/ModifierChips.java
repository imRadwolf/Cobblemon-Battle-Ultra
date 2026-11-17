package name.modid.client;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Pokemon;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.class_2561;

public final class ModifierChips {
    public static final int BAD = -38037;
    public static final int GOOD = -8592501;
    public static final int FIELD = -10166;
    private static Object trackedBattle;
    private static String cause;
    private static final Map<String, Map<String, Map<String, Integer>>> CAUSES = new ConcurrentHashMap<>();
    private static final String[] STAT_KEYS = new String[]{"attack", "defense", "special-attack", "special-defense", "speed", "accuracy", "evasion"};
    private static final String[] STAT_SHORT = new String[]{"atk", "def", "spa", "spd", "spe", "acc", "eva"};
    private static final String[] STAT_LABEL = new String[]{"Atk", "Def", "SpA", "SpD", "Spe", "Acc", "Eva"};

    private ModifierChips() {
    }

    static String norm(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static void checkBattle() {
        Object battle = CobblemonClient.INSTANCE.getBattle();
        if (!BattleKey.same(battle, trackedBattle)) {
            trackedBattle = BattleKey.of(battle);
            CAUSES.clear();
            cause = null;
        }
    }

    private static String text(Object arg) {
        return arg instanceof class_2561 t ? t.getString() : (arg == null ? null : String.valueOf(arg));
    }

    private static String statShort(String statText) {
        String n = norm(statText);
        if (n.contains("special") || n.startsWith("sp") && !n.startsWith("speed")) {
            return n.contains("def") ? "spd" : "spa";
        } else if (n.contains("atk") || n.contains("attack")) {
            return "atk";
        } else if (n.contains("def")) {
            return "def";
        } else if (n.contains("speed") || n.equals("spe")) {
            return "spe";
        } else if (n.contains("accuracy")) {
            return "acc";
        } else {
            return n.contains("evasi") ? "eva" : null;
        }
    }

    public static void onMessage(String key, Object[] args) {
        if (key != null && key.startsWith("cobblemon.battle.")) {
            try {
                checkBattle();
                String k = key.substring("cobblemon.battle.".length());
                String[] parts = k.split("\\.");
                String action = parts[0];
                switch (action) {
                    case "turn":
                        cause = null;
                        break;
                    case "used_move":
                    case "used_move_on":
                        cause = args != null && args.length > 1 ? text(args[1]) : null;
                        break;
                    case "ability":
                        if (parts.length > 1 && "generic".equals(parts[1])) {
                            cause = args != null && args.length > 1 ? text(args[1]) : cause;
                        } else if (parts.length > 1) {
                            cause = FormAbilityHelper.abilityDisplayNameFromId(parts[1]);
                        }
                        break;
                    case "activate":
                        if (parts.length > 1 && "stickyweb".equals(parts[1])) {
                            cause = "Sticky Web";
                        }
                        break;
                    case "item":
                    case "enditem":
                        cause = args != null && args.length > 1 ? text(args[1]) : cause;
                        break;
                    case "setboost":
                        String[] whox = who(args);
                        if (whox != null && parts.length > 1) {
                            record(whox, "atk", FormAbilityHelper.prettyId(parts[1]), 6);
                        }
                        break;
                    case "boost":
                    case "unboost":
                        if (parts.length > 1 && !"cap".equals(parts[1])) {
                            String[] whoxx = who(args);
                            String stat = args != null && args.length > 1 ? statShort(text(args[1])) : null;
                            int amount = "sharp".equals(parts[1]) ? 2 : ("severe".equals(parts[1]) ? 3 : 1);
                            if (whoxx != null && stat != null && cause != null) {
                                record(whoxx, stat, cause, "boost".equals(action) ? amount : -amount);
                            }
                        }
                        break;
                    case "clearallboost":
                        CAUSES.clear();
                        break;
                    case "clearboost":
                    case "clearallnegativeboost":
                    case "invertboost":
                    case "swapboost":
                    case "copyboost":
                    case "switch":
                    case "switch_in":
                    case "dragged_out":
                    case "withdraw":
                    case "recall":
                    case "return":
                        String[] who = who(args);
                        if (who != null) {
                            CAUSES.remove(who[0] + "_" + norm(who[1]));
                        }
                }
            } catch (Throwable var10) {
            }
        }
    }

    private static String[] who(Object[] args) {
        if (args != null && args.length != 0) {
            String[] context = BattleMessageSubscriber.resolvePokemonSideAndSpecies(args, 0);
            return context != null && context[0] != null && context[1] != null ? context : null;
        } else {
            return null;
        }
    }

    private static void record(String[] who, String stat, String source, int delta) {
        CAUSES.computeIfAbsent(who[0] + "_" + norm(who[1]), x -> new ConcurrentHashMap<>())
            .computeIfAbsent(stat, x -> new LinkedHashMap<>())
            .merge(source, delta, Integer::sum);
    }

    private static String causeFor(String side, ClientBattlePokemon bp, String stat, int stage) {
        Map<String, Map<String, Integer>> map = CAUSES.get(side + "_" + norm(bp.getSpecies().getName()));
        if (map == null) {
            return null;
        } else {
            Map<String, Integer> causes = map.get(stat);
            if (causes == null) {
                return null;
            } else {
                List<String> names = new ArrayList<>();

                for (Entry<String, Integer> e : causes.entrySet()) {
                    if (e.getValue() != 0 && Integer.signum(e.getValue()) == Integer.signum(stage)) {
                        names.add(e.getKey());
                    }
                }

                return names.isEmpty() ? null : String.join(", ", names);
            }
        }
    }

    private static String stageMultText(int stage) {
        double m = stage >= 0 ? (2.0 + stage) / 2.0 : 2.0 / (2.0 - stage);
        return "×" + (m == Math.floor(m) ? String.valueOf((int)m) : String.format(Locale.ROOT, "%.2f", m).replaceAll("0+$", ""));
    }

    private static Set<String> moveTypes(boolean playerSide, ClientBattlePokemon bp, Set<String> moveIdsOut) {
        Set<String> types = new LinkedHashSet<>();

        try {
            if (playerSide) {
                Pokemon p = PartyInfoSideRenderer.findPartyPokemonForBattlePokemon(bp);
                if (p != null) {
                    for (Move m : p.getMoveSet().getMoves()) {
                        if (m != null && !BuildInference.isStatus(m.getTemplate())) {
                            types.add(norm(HiddenPowerHelper.effective(m.getTemplate(), p).getName()));
                            moveIdsOut.add(norm(m.getTemplate().getName()));
                        }
                    }
                }
            } else {
                BuildInference.Info info = BuildInference.info(bp);
                if (info != null) {
                    List<String> ids = new ArrayList<>(info.revealedMoves);
                    List<String> likely = info.likelyMoves();
                    ids.addAll(likely.subList(0, Math.min(likely.size(), Math.max(0, 4 - ids.size()))));

                    for (String id : ids) {
                        MoveTemplate t = Moves.getByName(id);
                        if (t != null && !BuildInference.isStatus(t)) {
                            types.add(norm(t.getElementalType().getName()));
                            moveIdsOut.add(id);
                        }
                    }
                }
            }
        } catch (Throwable var10) {
        }

        return types;
    }

    private static String knownAbility(boolean playerSide, ClientBattlePokemon bp) {
        try {
            if (playerSide) {
                ItemAbilityTracker.State st = ItemAbilityTracker.get("player", bp);
                if (st != null && st.abilitySuppressed) {
                    return "";
                } else if (st != null && st.abilityTemporary && st.abilityId != null) {
                    return norm(st.abilityId);
                } else {
                    Pokemon p = PartyInfoSideRenderer.findPartyPokemonForBattlePokemon(bp);
                    return p != null ? norm(p.getAbility().getTemplate().getName()) : "";
                }
            } else {
                ItemAbilityTracker.State st = ItemAbilityTracker.get("opponent", bp);
                if (st != null && st.abilitySuppressed) {
                    return "";
                } else if (st != null && st.abilityTemporary && st.abilityId != null) {
                    return norm(st.abilityId);
                } else {
                    String revealed = BuildInference.revealedAbility(bp);
                    if (revealed != null) {
                        return norm(revealed);
                    } else {
                        FormData form = FormAbilityHelper.effectiveBattleForm(bp, "opponent");
                        String single = FormAbilityHelper.singleFormAbilityId(bp.getSpecies(), form);
                        return single != null ? norm(single) : "";
                    }
                }
            }
        } catch (Throwable var6) {
            return "";
        }
    }

    private static float ratio(ClientBattlePokemon bp) {
        try {
            float r = bp.isHpFlat() && bp.getMaxHp() > 0.0F ? bp.getHpValue() / bp.getMaxHp() : bp.getHpValue();
            return r > 1.0001F ? r / 100.0F : r;
        } catch (Throwable var2) {
            return 1.0F;
        }
    }

    public static List<ModifierChips.Chip> collect(boolean playerSide, ClientBattlePokemon bp, ClientBattlePokemon other) {
        List<ModifierChips.Chip> chips = new ArrayList<>();
        if (bp == null) {
            return chips;
        } else if (!ExtrasPlusConfig.get().modifierChips) {
            return BattleCounters.chips(playerSide ? "player" : "opponent", bp);
        } else {
            checkBattle();
            String side = playerSide ? "player" : "opponent";
            String otherSide = playerSide ? "opponent" : "player";
            String who = playerSide ? "your" : "their";

            for (int i = 0; i < STAT_KEYS.length; i++) {
                int stage = SpeedTiers.stageFor(side, bp, STAT_KEYS[i]);
                if (stage != 0) {
                    String c = causeFor(side, bp, STAT_SHORT[i], stage);
                    String text = STAT_LABEL[i] + " " + (stage > 0 ? "+" : "−") + Math.abs(stage) + (c != null ? " · " + c : "");
                    String explain = STAT_LABEL[i]
                        + " "
                        + (stage > 0 ? "+" : "")
                        + stage
                        + " → "
                        + stageMultText(stage)
                        + (c != null ? "  (from " + c + ")" : "");
                    chips.add(new ModifierChips.Chip(text, stage > 0 ? -8592501 : -38037, explain));
                }
            }

            chips.addAll(BattleCounters.chips(side, bp));
            String ability = knownAbility(playerSide, bp);

            try {
                String status = bp.getStatus() == null ? "" : norm(bp.getStatus().getShowdownName());
                if (status.equals("brn")) {
                    if (ability.equals("guts")) {
                        chips.add(new ModifierChips.Chip("Guts · Atk ×1.5", -8592501, "Burned, but Guts boosts Attack by 50% instead of halving it."));
                    } else {
                        chips.add(
                            new ModifierChips.Chip(
                                "Burn · Atk ×½", -38037, "Burn halves the damage of physical moves (Facade excepted) and costs 1/16 HP per turn."
                            )
                        );
                    }
                } else if (status.equals("par")) {
                    chips.add(
                        new ModifierChips.Chip(
                            ability.equals("quickfeet") ? "Quick Feet · Spe ×1.5" : "Para · Spe ×½",
                            ability.equals("quickfeet") ? -8592501 : -38037,
                            ability.equals("quickfeet")
                                ? "Quick Feet boosts Speed by 50% while statused."
                                : "Paralysis halves Speed and gives a 25% chance to be fully paralyzed."
                        )
                    );
                } else if (status.equals("frz") || status.equals("fsb")) {
                    chips.add(new ModifierChips.Chip("Frozen", -38037, "Can't move until it thaws (20% per turn)."));
                } else if ((status.equals("psn") || status.equals("tox") || status.equals("slp")) && ability.equals("guts")) {
                    chips.add(new ModifierChips.Chip("Guts · Atk ×1.5", -8592501, "Guts boosts Attack by 50% while statused."));
                }
            } catch (Throwable var25) {
            }

            Map<String, Integer> screens = BattleMessageSubscriber.getScreensForSide(otherSide);
            if (screens != null && !ability.equals("infiltrator")) {
                int veil = screens.getOrDefault("Aurora Veil", 0);
                int reflect = screens.getOrDefault("Reflect", 0);
                int light = screens.getOrDefault("Light Screen", 0);
                if (veil > 0) {
                    chips.add(
                        new ModifierChips.Chip(
                            "Veil · all ×½ (" + veil + ")", -38037, "The other side's Aurora Veil halves " + who + " damage for " + veil + " more turn(s)."
                        )
                    );
                } else {
                    if (reflect > 0) {
                        chips.add(
                            new ModifierChips.Chip(
                                "Reflect · phys ×½ (" + reflect + ")",
                                -38037,
                                "The other side's Reflect halves " + who + " physical damage for " + reflect + " more turn(s)."
                            )
                        );
                    }

                    if (light > 0) {
                        chips.add(
                            new ModifierChips.Chip(
                                "L.Screen · spec ×½ (" + light + ")",
                                -38037,
                                "The other side's Light Screen halves " + who + " special damage for " + light + " more turn(s)."
                            )
                        );
                    }
                }
            }

            Set<String> moveIds = new LinkedHashSet<>();
            Set<String> types = moveTypes(playerSide, bp, moveIds);
            List<String> ownTypes = new ArrayList<>();

            try {
                FormData form = FormAbilityHelper.effectiveBattleForm(bp, side);

                for (ElementalType t : FormAbilityHelper.types(form)) {
                    ownTypes.add(norm(t.getName()));
                }
            } catch (Throwable var24) {
            }

            String weather = norm(BattleMessageSubscriber.getCurrentWeather());
            if (weather.contains("rain")) {
                if (types.contains("water")) {
                    chips.add(new ModifierChips.Chip("Rain · Water ×1.5", -8592501, "Rain boosts Water moves by 50%."));
                }

                if (types.contains("fire")) {
                    chips.add(new ModifierChips.Chip("Rain · Fire ×½", -38037, "Rain halves Fire moves."));
                }
            } else if (!weather.contains("sun") && !weather.contains("harsh")) {
                if (weather.contains("sand") && ownTypes.contains("rock")) {
                    chips.add(new ModifierChips.Chip("Sand · SpD ×1.5", -8592501, "Rock types get +50% Special Defense in a sandstorm."));
                } else if (weather.contains("snow") && ownTypes.contains("ice")) {
                    chips.add(new ModifierChips.Chip("Snow · Def ×1.5", -8592501, "Ice types get +50% Defense in snow."));
                }
            } else {
                if (types.contains("fire")) {
                    chips.add(new ModifierChips.Chip("Sun · Fire ×1.5", -8592501, "Sun boosts Fire moves by 50%."));
                }

                if (types.contains("water")) {
                    chips.add(new ModifierChips.Chip("Sun · Water ×½", -38037, "Sun halves Water moves."));
                }
            }

            String terrain = norm(BattleMessageSubscriber.getCurrentTerrain());
            boolean grounded = !ownTypes.contains("flying") && !ability.equals("levitate");
            if (grounded) {
                if (terrain.contains("electric") && types.contains("electric")) {
                    chips.add(new ModifierChips.Chip("E.Terrain · Elec ×1.3", -8592501, "Electric Terrain boosts grounded users' Electric moves by 30%."));
                } else if (terrain.contains("grassy") && types.contains("grass")) {
                    chips.add(new ModifierChips.Chip("G.Terrain · Grass ×1.3", -8592501, "Grassy Terrain boosts grounded users' Grass moves by 30%."));
                } else if (terrain.contains("psychic") && types.contains("psychic")) {
                    chips.add(new ModifierChips.Chip("P.Terrain · Psy ×1.3", -8592501, "Psychic Terrain boosts grounded users' Psychic moves by 30%."));
                }
            }

            if (terrain.contains("grassy") && (moveIds.contains("earthquake") || moveIds.contains("bulldoze"))) {
                chips.add(new ModifierChips.Chip("G.Terrain · EQ ×½", -38037, "Grassy Terrain halves Earthquake and Bulldoze."));
            }

            if (terrain.contains("psychic") && other != null) {
                boolean hasPriority = false;

                for (String id : moveIds) {
                    MoveTemplate t = Moves.getByName(id);
                    if (t != null && t.getPriority() > 0) {
                        hasPriority = true;
                    }
                }

                if (hasPriority) {
                    chips.add(
                        new ModifierChips.Chip("P.Terrain · priority blocked", -38037, "Priority moves fail against grounded targets in Psychic Terrain.")
                    );
                }
            }

            if (other != null && !ability.equals("moldbreaker") && !ability.equals("teravolt") && !ability.equals("turboblaze")) {
                String theirs = knownAbility(!playerSide, other);
                String name = FormAbilityHelper.abilityDisplayNameFromId(theirs);
                switch (theirs) {
                    case "multiscale":
                    case "shadowshield":
                        if (ratio(other) >= 0.999F) {
                            chips.add(new ModifierChips.Chip(name + " · hits ×½", -38037, name + ": damage taken is halved while at full HP."));
                        }
                        break;
                    case "furcoat":
                        chips.add(new ModifierChips.Chip("Fur Coat · phys ×½", -38037, "Fur Coat doubles Defense."));
                        break;
                    case "icescales":
                        chips.add(new ModifierChips.Chip("Ice Scales · spec ×½", -38037, "Ice Scales halves special damage."));
                        break;
                    case "fluffy":
                        chips.add(new ModifierChips.Chip("Fluffy · contact ×½", -38037, "Fluffy halves contact damage (but doubles Fire damage)."));
                        break;
                    case "filter":
                    case "solidrock":
                    case "prismarmor":
                        chips.add(new ModifierChips.Chip(name + " · SE ×0.75", -38037, name + ": super-effective damage taken ×0.75."));
                        break;
                    case "thickfat":
                        if (types.contains("fire") || types.contains("ice")) {
                            chips.add(new ModifierChips.Chip("Thick Fat · Fire/Ice ×½", -38037, "Thick Fat halves Fire and Ice damage."));
                        }
                        break;
                    case "unaware":
                        boolean boosted = false;

                        for (String key : new String[]{"attack", "special-attack"}) {
                            if (SpeedTiers.stageFor(side, bp, key) > 0) {
                                boosted = true;
                            }
                        }

                        if (boosted) {
                            chips.add(new ModifierChips.Chip("Unaware · boosts ignored", -38037, "Unaware ignores the attacker's stat boosts."));
                        }
                }
            }

            return chips;
        }
    }

    public static List<class_2561> tooltip(List<ModifierChips.Chip> chips, boolean playerSide) {
        List<class_2561> lines = new ArrayList<>();
        lines.add(
            class_2561.method_43470(playerSide ? "What changes your damage & speed" : "What changes their damage & speed")
                .method_27694(s -> s.method_36139(16766720).method_10982(true))
        );

        for (ModifierChips.Chip chip : chips) {
            int c = chip.color() & 16777215;
            lines.add(class_2561.method_43470("● " + chip.text()).method_27694(s -> s.method_36139(c)));
            lines.add(class_2561.method_43470("   " + chip.explain()).method_27694(s -> s.method_36139(10132122)));
        }

        return lines;
    }

    public record Chip(String text, int color, String explain) {
    }
}
