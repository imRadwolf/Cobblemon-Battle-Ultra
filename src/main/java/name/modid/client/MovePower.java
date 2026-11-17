package name.modid.client;

import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Pokemon;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class MovePower {
    private static final Set<String> VARIABLE = Set.of(
        "lowkick",
        "grassknot",
        "heavyslam",
        "heatcrash",
        "gyroball",
        "electroball",
        "eruption",
        "waterspout",
        "dragonenergy",
        "reversal",
        "flail",
        "crushgrip",
        "wringout",
        "hardpress",
        "acrobatics",
        "hex",
        "infernalparade",
        "venoshock",
        "barbbarrage",
        "brine",
        "storedpower",
        "powertrip",
        "boltbeak",
        "fishiousrend",
        "payback",
        "avalanche",
        "revenge",
        "lastrespects",
        "ragefist",
        "weatherball",
        "terrainpulse",
        "risingvoltage",
        "expandingforce",
        "mistyexplosion",
        "psyblade",
        "solarbeam",
        "solarblade",
        "hydrosteam",
        "facade",
        "ivycudgel"
    );
    private static final Set<String> SPEED_DEPENDENT = Set.of("gyroball", "electroball", "boltbeak", "fishiousrend", "payback", "avalanche", "revenge");
    private static final Map<String, String> TYPE_ITEMS = Map.ofEntries(
        Map.entry("charcoal", "fire"),
        Map.entry("charcoalstick", "fire"),
        Map.entry("mysticwater", "water"),
        Map.entry("magnet", "electric"),
        Map.entry("miracleseed", "grass"),
        Map.entry("nevermeltice", "ice"),
        Map.entry("blackbelt", "fighting"),
        Map.entry("poisonbarb", "poison"),
        Map.entry("softsand", "ground"),
        Map.entry("sharpbeak", "flying"),
        Map.entry("twistedspoon", "psychic"),
        Map.entry("silverpowder", "bug"),
        Map.entry("hardstone", "rock"),
        Map.entry("spelltag", "ghost"),
        Map.entry("dragonfang", "dragon"),
        Map.entry("blackglasses", "dark"),
        Map.entry("metalcoat", "steel"),
        Map.entry("silkscarf", "normal"),
        Map.entry("fairyfeather", "fairy"),
        Map.entry("seaincense", "water"),
        Map.entry("waveincense", "water"),
        Map.entry("oddincense", "psychic"),
        Map.entry("rockincense", "rock"),
        Map.entry("roseincense", "grass"),
        Map.entry("flameplate", "fire"),
        Map.entry("splashplate", "water"),
        Map.entry("zapplate", "electric"),
        Map.entry("meadowplate", "grass"),
        Map.entry("icicleplate", "ice"),
        Map.entry("fistplate", "fighting"),
        Map.entry("toxicplate", "poison"),
        Map.entry("earthplate", "ground"),
        Map.entry("skyplate", "flying"),
        Map.entry("mindplate", "psychic"),
        Map.entry("insectplate", "bug"),
        Map.entry("stoneplate", "rock"),
        Map.entry("spookyplate", "ghost"),
        Map.entry("dracoplate", "dragon"),
        Map.entry("dreadplate", "dark"),
        Map.entry("ironplate", "steel"),
        Map.entry("pixieplate", "fairy")
    );
    private static final Map<String, String> TYPE_ITEM_NAMES = Map.ofEntries(
        Map.entry("fire", "Charcoal"),
        Map.entry("water", "Mystic Water"),
        Map.entry("electric", "Magnet"),
        Map.entry("grass", "Miracle Seed"),
        Map.entry("ice", "Never-Melt Ice"),
        Map.entry("fighting", "Black Belt"),
        Map.entry("poison", "Poison Barb"),
        Map.entry("ground", "Soft Sand"),
        Map.entry("flying", "Sharp Beak"),
        Map.entry("psychic", "Twisted Spoon"),
        Map.entry("bug", "Silver Powder"),
        Map.entry("rock", "Hard Stone"),
        Map.entry("ghost", "Spell Tag"),
        Map.entry("dragon", "Dragon Fang"),
        Map.entry("dark", "Black Glasses"),
        Map.entry("steel", "Metal Coat"),
        Map.entry("normal", "Silk Scarf"),
        Map.entry("fairy", "Fairy Feather")
    );
    private static final Map<String, double[]> RANGE_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Long> RANGE_TIME = new ConcurrentHashMap<>();

    private MovePower() {
    }

    static String norm(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    public static boolean isVariable(String moveId) {
        return VARIABLE.contains(norm(moveId));
    }

    public static boolean speedDependent(String moveId) {
        return SPEED_DEPENDENT.contains(norm(moveId));
    }

    static String weather() {
        return norm(BattleMessageSubscriber.getCurrentWeather());
    }

    static String terrain() {
        return norm(BattleMessageSubscriber.getCurrentTerrain());
    }

    private static boolean sun(String w) {
        return w.contains("sun") || w.contains("harsh") || w.contains("drought");
    }

    private static boolean rain(String w) {
        return w.contains("rain");
    }

    public static double power(String moveIdRaw, double base, MovePower.Ctx c) {
        String id = norm(moveIdRaw);
        if (c != null && VARIABLE.contains(id)) {
            String w = weather();
            String t = terrain();
            switch (id) {
                case "lowkick":
                case "grassknot":
                    double wt = c.defWeight;
                    if (wt <= 0.0) {
                        return base > 0.0 ? base : 60.0;
                    }

                    return wt < 100.0 ? 20.0 : (wt < 250.0 ? 40.0 : (wt < 500.0 ? 60.0 : (wt < 1000.0 ? 80.0 : (wt < 2000.0 ? 100.0 : 120.0))));
                case "heavyslam":
                case "heatcrash":
                    if (!(c.atkWeight <= 0.0) && !(c.defWeight <= 0.0)) {
                        double r = c.atkWeight / c.defWeight;
                        return r >= 5.0 ? 120.0 : (r >= 4.0 ? 100.0 : (r >= 3.0 ? 80.0 : (r >= 2.0 ? 60.0 : 40.0)));
                    }

                    return base > 0.0 ? base : 60.0;
                case "gyroball":
                    if (c.atkSpeed > 0 && c.defSpeed > 0) {
                        return Math.min(150, 25 * c.defSpeed / c.atkSpeed + 1);
                    }

                    return base > 0.0 ? base : 60.0;
                case "electroball":
                    if (c.atkSpeed > 0 && c.defSpeed > 0) {
                        int r = c.atkSpeed / c.defSpeed;
                        return r >= 4 ? 150.0 : (r >= 3 ? 120.0 : (r >= 2 ? 80.0 : (r >= 1 ? 60.0 : 40.0)));
                    }

                    return base > 0.0 ? base : 60.0;
                case "eruption":
                case "waterspout":
                case "dragonenergy":
                    return Math.max(1, (int)Math.floor(150.0 * c.atkHpRatio));
                case "reversal":
                case "flail":
                    int p = (int)Math.floor(48.0 * c.atkHpRatio);
                    return p <= 1 ? 200.0 : (p <= 4 ? 150.0 : (p <= 9 ? 100.0 : (p <= 16 ? 80.0 : (p <= 32 ? 40.0 : 20.0))));
                case "crushgrip":
                case "wringout":
                    return Math.max(1, (int)Math.floor(120.0 * c.defHpRatio));
                case "hardpress":
                    return Math.max(1, (int)Math.floor(100.0 * c.defHpRatio));
                case "acrobatics":
                    return c.atkItem != null && c.atkItem.isEmpty() ? base * 2.0 : base;
                case "hex":
                case "infernalparade":
                    return c.defStatus != null && !c.defStatus.isEmpty() ? base * 2.0 : base;
                case "venoshock":
                case "barbbarrage":
                    return !"psn".equals(c.defStatus) && !"tox".equals(c.defStatus) ? base : base * 2.0;
                case "brine":
                    return c.defHpRatio <= 0.5F ? base * 2.0 : base;
                case "storedpower":
                case "powertrip":
                    return 20.0 + 20.0 * Math.max(0, c.atkBoosts);
                case "boltbeak":
                case "fishiousrend":
                    return Boolean.TRUE.equals(c.atkMovesFirst) ? base * 2.0 : base;
                case "payback":
                case "avalanche":
                case "revenge":
                    return Boolean.FALSE.equals(c.atkMovesFirst) ? base * 2.0 : base;
                case "lastrespects":
                    return Math.min(5000.0, 50.0 + 50.0 * Math.max(0, c.faintedAllies));
                case "ragefist":
                    return Math.min(350.0, 50.0 + 50.0 * Math.max(0, c.timesHit));
                case "weatherball":
                    return weatherBallType(w) != null ? 100.0 : base;
                case "terrainpulse":
                    return c.atkGrounded && terrainPulseType(t) != null ? 100.0 : base;
                case "risingvoltage":
                    return t.contains("electric") && c.defGrounded ? base * 2.0 : base;
                case "expandingforce":
                    return t.contains("psychic") && c.atkGrounded ? base * 1.5 : base;
                case "mistyexplosion":
                    return t.contains("misty") && c.atkGrounded ? base * 1.5 : base;
                case "psyblade":
                    return t.contains("electric") ? base * 1.5 : base;
                case "solarbeam":
                case "solarblade":
                    return !w.isEmpty() && !sun(w) && !w.contains("none") && !w.contains("clear") ? base * 0.5 : base;
                case "hydrosteam":
                    return sun(w) ? base * 3.0 : base;
                case "facade":
                    return c.atkStatus != null && !c.atkStatus.isEmpty() && !"slp".equals(c.atkStatus) && !"frz".equals(c.atkStatus)
                        ? Math.max(base, 140.0)
                        : base;
                default:
                    return base;
            }
        } else {
            return base;
        }
    }

    public static String note(String moveIdRaw, double base, double power, MovePower.Ctx c) {
        String id = norm(moveIdRaw);
        if (VARIABLE.contains(id) && !(Math.abs(power - base) < 0.5)) {
            String why = switch (id) {
                case "lowkick", "grassknot" -> c.defWeight > 0.0 ? "target " + fmtKg(c.defWeight) : null;
                case "heavyslam", "heatcrash" -> c.atkWeight > 0.0 && c.defWeight > 0.0 ? fmtKg(c.atkWeight) + " vs " + fmtKg(c.defWeight) : null;
                case "gyroball", "electroball" -> c.atkSpeed > 0 && c.defSpeed > 0 ? c.atkSpeed + " vs " + c.defSpeed + " Spe" : null;
                case "eruption", "waterspout", "dragonenergy", "reversal", "flail" -> Math.round(c.atkHpRatio * 100.0F) + "% HP";
                case "crushgrip", "wringout", "hardpress", "brine" -> "target at " + Math.round(c.defHpRatio * 100.0F) + "% HP";
                case "acrobatics" -> "no item";
                case "hex", "infernalparade", "venoshock", "barbbarrage" -> "target statused";
                case "storedpower", "powertrip" -> "+" + c.atkBoosts + " boosts";
                case "boltbeak", "fishiousrend" -> "moves first";
                case "payback", "avalanche", "revenge" -> "moves last";
                case "lastrespects" -> c.faintedAllies + " fainted";
                case "ragefist" -> "hit " + c.timesHit + "×";
                case "weatherball", "solarbeam", "solarblade", "hydrosteam" -> "weather";
                case "terrainpulse", "risingvoltage", "expandingforce", "mistyexplosion", "psyblade" -> "terrain";
                case "facade" -> "statused";
                default -> null;
            };
            return Math.round(power) + " BP" + (why != null ? " (" + why + ")" : "");
        } else {
            return null;
        }
    }

    static String fmtKg(double hg) {
        double kg = hg / 10.0;
        return (kg >= 100.0 ? String.valueOf(Math.round(kg)) : String.format(Locale.ROOT, "%.1f", kg)) + " kg";
    }

    static String weatherBallType(String w) {
        if (sun(w)) {
            return "fire";
        } else if (rain(w)) {
            return "water";
        } else if (w.contains("sand")) {
            return "rock";
        } else {
            return !w.contains("snow") && !w.contains("hail") ? null : "ice";
        }
    }

    static String terrainPulseType(String t) {
        if (t.contains("electric")) {
            return "electric";
        } else if (t.contains("grassy")) {
            return "grass";
        } else if (t.contains("misty")) {
            return "fairy";
        } else {
            return t.contains("psychic") ? "psychic" : null;
        }
    }

    public static String typeBoostItem(String item) {
        return TYPE_ITEMS.get(norm(item));
    }

    public static String typeBoostItemName(String type) {
        return TYPE_ITEM_NAMES.get(norm(type));
    }

    public static String ogerponMaskType(FormData form) {
        try {
            if (form != null && form.getSpecies() != null && norm(form.getSpecies().getName()).equals("ogerpon")) {
                String name = norm(form.getName());
                if (name.contains("wellspring")) {
                    return "water";
                } else if (name.contains("hearthflame")) {
                    return "fire";
                } else {
                    return name.contains("cornerstone") ? "rock" : "grass";
                }
            } else {
                return null;
            }
        } catch (Throwable var2) {
            return null;
        }
    }

    public static String ogerponMaskItem(FormData form) {
        String type = ogerponMaskType(form);
        if (type == null) {
            return null;
        } else {
            return switch (type) {
                case "water" -> "Wellspring Mask";
                case "fire" -> "Hearthflame Mask";
                case "rock" -> "Cornerstone Mask";
                default -> null;
            };
        }
    }

    public static String typeOverride(String moveIdRaw, FormData atkForm, boolean atkGrounded) {
        String id = norm(moveIdRaw);
        switch (id) {
            case "weatherball":
                return weatherBallType(weather());
            case "terrainpulse":
                return atkGrounded ? terrainPulseType(terrain()) : null;
            case "ivycudgel":
                return ogerponMaskType(atkForm);
            case "ragingbull":
                if (atkForm == null) {
                    return null;
                }

                String n = norm(atkForm.getName());
                return n.contains("blaze") ? "fire" : (n.contains("aqua") ? "water" : (!n.contains("combat") && !n.contains("paldea") ? null : "fighting"));
            case "revelationdance":
                List<ElementalType> types = FormAbilityHelper.types(atkForm);
                return types.isEmpty() ? null : norm(types.get(0).getName());
            case "aurawheel":
                return atkForm != null && norm(atkForm.getName()).contains("hangry") ? "dark" : null;
            default:
                return null;
        }
    }

    static int positiveBoosts(String side, ClientBattlePokemon bp) {
        int sum = 0;

        for (String k : new String[]{"attack", "defense", "special-attack", "special-defense", "speed", "accuracy", "evasion"}) {
            sum += Math.max(0, SpeedTiers.stageFor(side, bp, k));
        }

        return sum;
    }

    static String status(ClientBattlePokemon bp) {
        try {
            return bp != null && bp.getStatus() != null ? norm(bp.getStatus().getShowdownName()) : "";
        } catch (Throwable var2) {
            return "";
        }
    }

    static float ratio(ClientBattlePokemon bp) {
        try {
            if (bp.isHpFlat()) {
                return bp.getMaxHp() > 0.0F ? bp.getHpValue() / bp.getMaxHp() : 1.0F;
            } else {
                return bp.getHpValue() > 1.0001F ? bp.getHpValue() / 100.0F : bp.getHpValue();
            }
        } catch (Throwable var2) {
            return 1.0F;
        }
    }

    static double weight(FormData form, String ability, String item) {
        try {
            double w = form.getWeight();
            String a = norm(ability);
            if (a.equals("heavymetal")) {
                w *= 2.0;
            } else if (a.equals("lightmetal")) {
                w = Math.max(1.0, Math.floor(w / 2.0));
            }

            if (norm(item).equals("floatstone")) {
                w = Math.max(1.0, Math.floor(w / 2.0));
            }

            return w;
        } catch (Throwable var6) {
            return -1.0;
        }
    }

    static boolean grounded(List<String> types, String ability, String item) {
        return !types.contains("flying") && !norm(ability).equals("levitate") && !norm(item).equals("airballoon");
    }

    public static MovePower.Ctx forPlayerAttack() {
        MovePower.Ctx c = new MovePower.Ctx();

        try {
            ClientBattlePokemon mine = MoveInsights.activeMine();
            Pokemon p = HiddenPowerHelper.resolve(null);
            ClientBattlePokemon opp = MoveInsights.activeOpponent();
            FormData myForm = mine != null ? FormAbilityHelper.effectiveBattleForm(mine, "player") : null;
            if (myForm == null && p != null) {
                myForm = p.getForm();
            }

            String myAbility = p != null ? HiddenPowerHelper.playerAbility(p) : "";
            String myItem = null;
            if (p != null) {
                ItemAbilityTracker.State st = mine != null ? ItemAbilityTracker.get("player", mine) : null;
                if (st != null && st.itemLost) {
                    myItem = "";
                } else if (st != null && st.itemName != null) {
                    myItem = norm(st.itemName);
                } else {
                    myItem = p.heldItem() != null && !p.heldItem().method_7960() ? norm(p.heldItem().method_7964().getString()) : "";
                }
            }

            c.atkAbility = myAbility;
            c.atkItem = myItem;
            List<String> myTypes = new ArrayList<>();

            for (ElementalType t : FormAbilityHelper.types(myForm)) {
                myTypes.add(norm(t.getName()));
            }

            c.atkGrounded = grounded(myTypes, myAbility, myItem);
            c.atkWeight = myForm != null ? weight(myForm, myAbility, myItem) : -1.0;
            if (mine != null) {
                c.atkHpRatio = ratio(mine);
                c.atkStatus = status(mine);
                c.atkBoosts = positiveBoosts("player", mine);
                BattleCounters.State counters = BattleCounters.get("player", mine);
                c.timesHit = counters != null ? counters.timesHit : 0;
            }

            c.faintedAllies = BattleRecap.faintedCount("player");
            SpeedTiers.Report report = SpeedTiers.lastReport;
            if (report != null && report.mySpeed != null && System.currentTimeMillis() - SpeedTiers.lastReportTime < 20000L) {
                c.atkSpeed = report.mySpeed;
            } else if (p != null) {
                c.atkSpeed = SpeedTiers.applyStage(p.getStat(Stats.SPEED), mine != null ? SpeedTiers.stageFor("player", mine, "speed") : 0);
            }

            if (opp != null) {
                FormData oppForm = FormAbilityHelper.effectiveBattleForm(opp, "opponent");
                if (oppForm == null && opp.getSpecies() != null) {
                    oppForm = opp.getSpecies().getStandardForm();
                }

                String oppAbility = BuildInference.revealedAbility(opp);
                c.defAbility = norm(oppAbility);
                c.defWeight = oppForm != null ? weight(oppForm, oppAbility, null) : -1.0;
                c.defHpRatio = ratio(opp);
                c.defStatus = status(opp);
                List<String> oppTypes = new ArrayList<>();

                for (ElementalType t : FormAbilityHelper.types(oppForm)) {
                    oppTypes.add(norm(t.getName()));
                }

                c.defGrounded = grounded(oppTypes, oppAbility, ItemAbilityTracker.knownItemName("opponent", opp));
            }
        } catch (Throwable var14) {
        }

        return c;
    }

    public static double[] playerPowerRange(MoveTemplate move) {
        String id = norm(move.getName());
        long now = System.currentTimeMillis();
        Long t = RANGE_TIME.get(id);
        double[] cached = RANGE_CACHE.get(id);
        if (cached != null && t != null && now - t < 250L) {
            return cached;
        } else {
            double[] r = playerPowerRange0(move);
            RANGE_CACHE.put(id, r);
            RANGE_TIME.put(id, now);
            return r;
        }
    }

    private static double[] playerPowerRange0(MoveTemplate move) {
        double base = HiddenPowerHelper.rawPower(move, null);
        String id = norm(move.getName());
        if (!VARIABLE.contains(id)) {
            return new double[]{base, base};
        } else {
            MovePower.Ctx c = forPlayerAttack();
            if (!SPEED_DEPENDENT.contains(id)) {
                double p = power(id, base, c);
                return new double[]{p, p};
            } else {
                int[] range = opponentSpeedRange();
                double lo = Double.MAX_VALUE;
                double hi = 0.0;
                boolean tr = BattleMessageSubscriber.getTrickRoomTurns() > 0;

                for (int s : range) {
                    MovePower.Ctx k = c.copy();
                    k.defSpeed = s;
                    if (move.getPriority() > 0) {
                        k.atkMovesFirst = true;
                    } else if (c.atkSpeed > 0 && s > 0 && c.atkSpeed != s) {
                        k.atkMovesFirst = tr ? c.atkSpeed < s : c.atkSpeed > s;
                    }

                    double p = power(id, base, k);
                    lo = Math.min(lo, p);
                    hi = Math.max(hi, p);
                }

                return new double[]{lo == Double.MAX_VALUE ? base : lo, hi <= 0.0 ? base : hi};
            }
        }
    }

    public static double playerPowerVsSpeed(MoveTemplate move, int opponentSpeed) {
        double base = HiddenPowerHelper.rawPower(move, null);
        String id = norm(move.getName());
        if (!VARIABLE.contains(id)) {
            return base;
        } else {
            MovePower.Ctx c = forPlayerAttack();
            c.defSpeed = opponentSpeed;
            boolean tr = BattleMessageSubscriber.getTrickRoomTurns() > 0;
            if (move.getPriority() > 0) {
                c.atkMovesFirst = true;
            } else if (c.atkSpeed > 0 && opponentSpeed > 0 && c.atkSpeed != opponentSpeed) {
                c.atkMovesFirst = tr ? c.atkSpeed < opponentSpeed : c.atkSpeed > opponentSpeed;
            }

            return power(id, base, c);
        }
    }

    static int[] opponentSpeedRange() {
        SpeedTiers.Report report = SpeedTiers.lastReport;
        if (report != null && System.currentTimeMillis() - SpeedTiers.lastReportTime < 20000L) {
            int lo = Integer.MAX_VALUE;
            int hi = 0;

            for (SpeedTiers.Tier t : report.builds) {
                lo = Math.min(lo, t.speed());
                hi = Math.max(hi, t.speed());
            }

            if (hi > 0) {
                return new int[]{lo, hi};
            }

            if (report.theoMax > 0) {
                return new int[]{report.theoMin, report.theoMax};
            }
        }

        return new int[]{-1};
    }

    public static String playerTypeOverride(MoveTemplate move, Pokemon p) {
        String id = norm(move.getName());
        if (!id.equals("weatherball")
            && !id.equals("terrainpulse")
            && !id.equals("ivycudgel")
            && !id.equals("ragingbull")
            && !id.equals("revelationdance")
            && !id.equals("aurawheel")) {
            return null;
        } else {
            try {
                FormData form = p != null ? p.getForm() : null;
                ClientBattlePokemon mine = MoveInsights.activeMine();
                if (mine != null && p != null) {
                    Pokemon party = PartyInfoSideRenderer.findPartyPokemonForBattlePokemon(mine);
                    if (party != null && party.getUuid().equals(p.getUuid())) {
                        FormData battleForm = FormAbilityHelper.effectiveBattleForm(mine, "player");
                        if (battleForm != null) {
                            form = battleForm;
                        }
                    }
                }

                List<String> types = new ArrayList<>();

                for (ElementalType t : FormAbilityHelper.types(form)) {
                    types.add(norm(t.getName()));
                }

                String item = p != null && p.heldItem() != null && !p.heldItem().method_7960() ? p.heldItem().method_7964().getString() : "";
                return typeOverride(id, form, grounded(types, p != null ? HiddenPowerHelper.playerAbility(p) : "", item));
            } catch (Throwable var8) {
                return null;
            }
        }
    }

    public static String opponentMoveType(String moveIdRaw, String baseType, ClientBattlePokemon bp) {
        String id = norm(moveIdRaw);
        String type = norm(baseType);

        try {
            FormData form = bp != null ? FormAbilityHelper.effectiveBattleForm(bp, "opponent") : null;
            if (form == null && bp != null && bp.getSpecies() != null) {
                form = bp.getSpecies().getStandardForm();
            }

            List<String> types = new ArrayList<>();

            for (ElementalType t : FormAbilityHelper.types(form)) {
                types.add(norm(t.getName()));
            }

            String ability = bp != null ? norm(BuildInference.revealedAbility(bp)) : "";
            if (ability.isEmpty() && bp != null) {
                String single = FormAbilityHelper.singleFormAbilityId(bp.getSpecies(), form);
                ability = single != null ? norm(single) : "";
            }

            String override = typeOverride(id, form, grounded(types, ability, bp != null ? ItemAbilityTracker.knownItemName("opponent", bp) : null));
            if (override != null) {
                type = override;
            }

            if (!ability.isEmpty()) {
                MoveTemplate t = Moves.getByName(id);
                if (t == null || !BuildInference.isStatus(t)) {
                    String converted = HiddenPowerHelper.abilityConvertedType(id, type, ability);
                    if (converted != null) {
                        type = converted;
                    }
                }
            }
        } catch (Throwable var11) {
        }

        return type;
    }

    public static final class Ctx {
        public double atkWeight = -1.0;
        public double defWeight = -1.0;
        public int atkSpeed = -1;
        public int defSpeed = -1;
        public float atkHpRatio = 1.0F;
        public float defHpRatio = 1.0F;
        public String atkItem;
        public String defStatus = "";
        public String atkStatus = "";
        public int atkBoosts;
        public Boolean atkMovesFirst;
        public boolean atkGrounded = true;
        public boolean defGrounded = true;
        public int faintedAllies;
        public int timesHit;
        public String atkAbility = "";
        public String defAbility = "";

        public MovePower.Ctx copy() {
            MovePower.Ctx c = new MovePower.Ctx();
            c.atkWeight = this.atkWeight;
            c.defWeight = this.defWeight;
            c.atkSpeed = this.atkSpeed;
            c.defSpeed = this.defSpeed;
            c.atkHpRatio = this.atkHpRatio;
            c.defHpRatio = this.defHpRatio;
            c.atkItem = this.atkItem;
            c.defStatus = this.defStatus;
            c.atkStatus = this.atkStatus;
            c.atkBoosts = this.atkBoosts;
            c.atkMovesFirst = this.atkMovesFirst;
            c.atkGrounded = this.atkGrounded;
            c.defGrounded = this.defGrounded;
            c.faintedAllies = this.faintedAllies;
            c.timesHit = this.timesHit;
            c.atkAbility = this.atkAbility;
            c.defAbility = this.defAbility;
            return c;
        }
    }
}
