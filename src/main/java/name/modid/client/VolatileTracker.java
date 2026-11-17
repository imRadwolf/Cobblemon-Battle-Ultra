package name.modid.client;

import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.battle.ActiveClientBattlePokemon;
import com.cobblemon.mod.common.client.battle.ClientBattle;
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon;
import com.cobblemon.mod.common.client.battle.ClientBattleSide;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.class_2561;
import net.minecraft.class_2588;

public final class VolatileTracker {
    private static Object trackedBattle;
    private static int turn;
    private static final List<VolatileTracker.Effect> EFFECTS = new CopyOnWriteArrayList<>();
    private static final Map<String, VolatileTracker.Def> DEFS = new HashMap<>();

    private VolatileTracker() {
    }

    private static void def(String id, String name, String icon, int accent, int min, int max, boolean slot, String desc) {
        DEFS.put(id, new VolatileTracker.Def(name, icon, accent, min, max, slot, desc));
    }

    static String norm(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static void checkBattle() {
        Object battle = CobblemonClient.INSTANCE.getBattle();
        if (battle != trackedBattle) {
            trackedBattle = battle;
            EFFECTS.clear();
            turn = 0;
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

    private static String other(String side) {
        return "player".equals(side) ? "opponent" : "player";
    }

    private static String text(Object arg) {
        return arg instanceof class_2561 t ? t.getString() : (arg == null ? null : String.valueOf(arg));
    }

    public static boolean has(String side, String species, String id) {
        try {
            checkBattle();
            return find(id, side, species) != null;
        } catch (Throwable var4) {
            return false;
        }
    }

    private static VolatileTracker.Effect find(String id, String side, String species) {
        for (VolatileTracker.Effect e : EFFECTS) {
            if (e.id.equals(id) && e.side.equals(side) && (e.def.slot() || norm(e.species).equals(norm(species)))) {
                return e;
            }
        }

        return null;
    }

    private static VolatileTracker.Effect add(String id, String side, String species) {
        VolatileTracker.Def def = DEFS.get(id);
        if (def == null) {
            return null;
        } else {
            VolatileTracker.Effect e = find(id, side, species);
            if (e == null) {
                e = new VolatileTracker.Effect();
                e.id = id;
                e.def = def;
                e.side = side;
                EFFECTS.add(e);
            }

            e.species = species;
            e.startTurn = turn;
            return e;
        }
    }

    private static void remove(String id, String side, String species) {
        EFFECTS.removeIf(e -> e.id.equals(id) && e.side.equals(side) && (e.def.slot() || species == null || norm(e.species).equals(norm(species))));
    }

    private static void leaves(String side, String species) {
        EFFECTS.removeIf(e -> e.side.equals(side) && !e.def.slot() && norm(e.species).equals(norm(species)));
    }

    public static void onMessage(String key, Object[] args) {
        if (key != null) {
            try {
                checkBattle();
                if (key.equals("cobblemon.status.sleep.apply")) {
                    String[] w = who(args, 0);
                    if (w != null) {
                        remove("yawn", w[0], w[1]);
                    }

                    return;
                }

                if (!key.startsWith("cobblemon.battle.")) {
                    return;
                }

                String full = key.substring("cobblemon.battle.".length());
                if (BattleCounters.isSwitchKey(full)) {
                    String[] w = BattleCounters.switchWho(full, args);
                    if (w != null) {
                        leaves(w[0], w[1]);
                    }

                    return;
                }

                String[] parts = full.split("\\.");
                String action = parts[0];
                String effect = parts.length > 1 ? parts[1] : "";
                switch (action) {
                    case "turn":
                        turn++;
                        EFFECTS.removeIf(e -> e.def.maxTurns() > 0 && turn - e.startTurn > e.def.maxTurns());
                        break;
                    case "start":
                        String[] wxx = who(args, 0);
                        if (wxx == null) {
                            return;
                        }

                        if (effect.equals("futuresight") || effect.equals("doomdesire")) {
                            VolatileTracker.Effect e = add(effect, other(wxx[0]), "slot");
                            if (e != null) {
                                e.detail = "Used by " + wxx[1];
                            }
                        } else if (effect.equals("perish")) {
                            VolatileTracker.Effect e = add("perish", wxx[0], wxx[1]);
                            if (e != null) {
                                try {
                                    e.stacks = Integer.parseInt(text(args[1]).trim());
                                } catch (Throwable var14) {
                                }
                            }
                        } else if (effect.equals("stockpile")) {
                            VolatileTracker.Effect e = add("stockpile", wxx[0], wxx[1]);
                            if (e != null) {
                                try {
                                    e.stacks = Integer.parseInt(text(args[1]).replaceAll("[^0-9]", ""));
                                } catch (Throwable var13) {
                                    e.stacks++;
                                }
                            }
                        } else if (effect.equals("disable")) {
                            VolatileTracker.Effect e = add("disable", wxx[0], wxx[1]);
                            if (e != null && args.length > 1) {
                                e.detail = "Disabled move: " + text(args[1]);
                            }
                        } else if (effect.equals("curse")) {
                            String[] target = who(args, 1);
                            if (target != null) {
                                add("curse", target[0], target[1]);
                            }
                        } else if (DEFS.containsKey(effect)) {
                            add(effect, wxx[0], wxx[1]);
                        }
                        break;
                    case "activate":
                        String[] wxxxxx = who(args, 0);
                        if ((wxxxxx != null && DEFS.containsKey(effect) && DEFS.get(effect).minTurns() == 4 || wxxxxx != null && effect.equals("trapped"))
                            && find(effect, wxxxxx[0], wxxxxx[1]) == null) {
                            add(effect, wxxxxx[0], wxxxxx[1]);
                        }
                        break;
                    case "fieldactivate":
                        if ("perishsong".equals(effect)) {
                            for (String[] wxxxx : actives()) {
                                VolatileTracker.Effect e = add("perish", wxxxx[0], wxxxx[1]);
                                if (e != null && e.stacks <= 0) {
                                    e.stacks = 3;
                                }
                            }
                        }
                        break;
                    case "end":
                        String[] wxxx = who(args, 0);
                        if (wxxx != null) {
                            if (!effect.equals("futuresight") && !effect.equals("doomdesire")) {
                                remove(effect, wxxx[0], wxxx[1]);
                            } else {
                                remove(effect, wxxx[0], null);
                            }
                        }
                        break;
                    case "heal":
                        if ("wish".equals(effect)) {
                            String[] wx = who(args, 0);
                            if (wx != null) {
                                remove("wish", wx[0], null);
                            }
                        }
                        break;
                    case "used_move":
                    case "used_move_on":
                        String[] wx = who(args, 0);
                        String move = args != null
                                && args.length > 1
                                && args[1] instanceof class_2561 t
                                && t.method_10851() instanceof class_2588 tc
                                && tc.method_11022() != null
                            ? norm(tc.method_11022().replace("cobblemon.move.", ""))
                            : null;
                        if (wx != null && "wish".equals(move)) {
                            add("wish", wx[0], "slot");
                        }

                        if (wx != null && move != null && !"charge".equals(move)) {
                            VolatileTracker.Effect charge = find("charge", wx[0], wx[1]);
                            if (charge != null && turn > charge.startTurn) {
                                remove("charge", wx[0], wx[1]);
                            }
                        }
                        break;
                    case "switch":
                    case "switch_in":
                    case "dragged_out":
                    case "withdraw":
                    case "recall":
                    case "return":
                    case "fainted":
                        String[] w = who(args, 0);
                        if (w != null) {
                            leaves(w[0], w[1]);
                        }
                }
            } catch (Throwable var15) {
            }
        }
    }

    private static List<String[]> actives() {
        List<String[]> out = new ArrayList<>();

        try {
            ClientBattle battle = CobblemonClient.INSTANCE.getBattle();

            for (String side : new String[]{"player", "opponent"}) {
                ClientBattleSide s = "player".equals(side) ? BattleSideHelper.getPlayerSide(battle) : BattleSideHelper.getOpponentSide(battle);

                for (ActiveClientBattlePokemon a : s.getActiveClientBattlePokemon()) {
                    if (a != null && a.getBattlePokemon() != null) {
                        out.add(new String[]{side, a.getBattlePokemon().getSpecies().getName()});
                    }
                }
            }
        } catch (Throwable var9) {
        }

        return out;
    }

    private static ClientBattlePokemon activeOn(String side, String species) {
        try {
            ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
            ClientBattleSide s = "player".equals(side) ? BattleSideHelper.getPlayerSide(battle) : BattleSideHelper.getOpponentSide(battle);

            for (ActiveClientBattlePokemon a : s.getActiveClientBattlePokemon()) {
                if (a != null && a.getBattlePokemon() != null && norm(a.getBattlePokemon().getSpecies().getName()).equals(norm(species))) {
                    return a.getBattlePokemon();
                }
            }
        } catch (Throwable var6) {
        }

        return null;
    }

    public static List<VolatileTracker.Badge> badges(String side) {
        List<VolatileTracker.Badge> out = new ArrayList<>();
        if (!ExtrasPlusConfig.get().volatileBadges) {
            return out;
        } else {
            checkBattle();
            String owner = "player".equals(side) ? "your" : "their";

            for (VolatileTracker.Effect e : EFFECTS) {
                if (e.side.equals(side)) {
                    ClientBattlePokemon bp = e.def.slot() ? null : activeOn(side, e.species);
                    if (e.def.slot() || bp != null) {
                        int elapsed = turn - e.startTurn;
                        List<String> info = new ArrayList<>();
                        info.add(
                            e.def.slot()
                                ? ("player".equals(side) ? "Your side (slot effect)" : "Opponent's side (slot effect)")
                                : "On " + owner + " " + bp.getDisplayName().getString()
                        );
                        info.add(e.def.desc());
                        String counter;
                        if (e.id.equals("perish")) {
                            counter = Integer.toString(Math.max(0, e.stacks));
                            info.add("Perish count: " + e.stacks);
                        } else if (e.id.equals("stockpile")) {
                            counter = "×" + Math.max(1, e.stacks);
                        } else if (e.def.maxTurns() < 0) {
                            counter = "";
                        } else {
                            int lo = Math.max(0, e.def.minTurns() - elapsed);
                            int hi = Math.max(0, e.def.maxTurns() - elapsed);
                            counter = lo == hi ? Integer.toString(hi) : (lo <= 0 ? "≤" + hi : lo + "-" + hi);
                            if (!e.id.equals("futuresight") && !e.id.equals("doomdesire")) {
                                if (e.id.equals("wish")) {
                                    info.add(hi <= 0 ? "Heals at the end of this turn." : "Heals at the end of next turn.");
                                } else if (lo == hi) {
                                    info.add("Turns left: " + hi);
                                } else {
                                    info.add(
                                        "Turns left: " + (lo <= 0 ? "up to " + hi : lo + " to " + hi) + (e.def.minTurns() == 4 ? " (7 with Grip Claw)" : "")
                                    );
                                }
                            } else {
                                info.add(
                                    hi <= 0
                                        ? "Lands at the end of this turn!"
                                        : (hi == 1 ? "Lands at the end of next turn." : "Lands at the end of the turn after next.")
                                );
                            }
                        }

                        if (e.id.equals("saltcure") && bp != null) {
                            boolean doubled = false;

                            try {
                                for (ElementalType t : FormAbilityHelper.types(FormAbilityHelper.effectiveBattleForm(bp, side))) {
                                    String n = norm(t.getName());
                                    doubled |= n.equals("water") || n.equals("steel");
                                }
                            } catch (Throwable var13) {
                            }

                            info.add(doubled ? "Water/Steel type: loses 1/4 HP per turn!" : "Loses 1/8 HP per turn.");
                        }

                        if (e.detail != null) {
                            info.add(e.detail);
                        }

                        out.add(new VolatileTracker.Badge(e.def.icon(), e.def.name(), counter, e.def.accent(), info));
                    }
                }
            }

            return out;
        }
    }

    static Set<String> ids() {
        return DEFS.keySet();
    }

    static {
        def(
            "futuresight",
            "Future Sight",
            "vol_futuresight",
            11828479,
            2,
            2,
            true,
            "A Psychic attack lands on this slot at the end of the turn it counts down to."
        );
        def("doomdesire", "Doom Desire", "vol_doomdesire", 12110056, 2, 2, true, "A Steel attack lands on this slot at the end of the turn it counts down to.");
        def("wish", "Wish", "vol_wish", 16767050, 1, 1, true, "Whoever is in this slot at the end of next turn heals half of the wisher's max HP.");
        def("magmastorm", "Magma Storm", "vol_trap_fire", 16742972, 4, 5, false, "Trapped: can't switch out, loses 1/8 HP each turn (1/6 with Binding Band).");
        def("firespin", "Fire Spin", "vol_trap_fire", 16742972, 4, 5, false, "Trapped: can't switch out, loses 1/8 HP each turn (1/6 with Binding Band).");
        def("whirlpool", "Whirlpool", "vol_trap_water", 4892927, 4, 5, false, "Trapped: can't switch out, loses 1/8 HP each turn (1/6 with Binding Band).");
        def("sandtomb", "Sand Tomb", "vol_trap_sand", 14266991, 4, 5, false, "Trapped: can't switch out, loses 1/8 HP each turn (1/6 with Binding Band).");
        def("bind", "Bind", "vol_trap_bind", 13148282, 4, 5, false, "Trapped: can't switch out, loses 1/8 HP each turn (1/6 with Binding Band).");
        def("wrap", "Wrap", "vol_trap_bind", 13148282, 4, 5, false, "Trapped: can't switch out, loses 1/8 HP each turn (1/6 with Binding Band).");
        def("clamp", "Clamp", "vol_trap_water", 4892927, 4, 5, false, "Trapped: can't switch out, loses 1/8 HP each turn (1/6 with Binding Band).");
        def("infestation", "Infestation", "vol_trap_bind", 11059280, 4, 5, false, "Trapped: can't switch out, loses 1/8 HP each turn (1/6 with Binding Band).");
        def("snaptrap", "Snap Trap", "vol_trap_bind", 13148282, 4, 5, false, "Trapped: can't switch out, loses 1/8 HP each turn (1/6 with Binding Band).");
        def("thundercage", "Thunder Cage", "vol_charge", 16769354, 4, 5, false, "Trapped: can't switch out, loses 1/8 HP each turn (1/6 with Binding Band).");
        def(
            "leechseed",
            "Leech Seed",
            "vol_leechseed",
            8182634,
            -1,
            -1,
            false,
            "Loses 1/8 HP each turn, which heals the Pokémon in the other slot. Grass types are immune."
        );
        def("saltcure", "Salt Cure", "vol_saltcure", 12112112, -1, -1, false, "Loses 1/8 HP each turn (1/4 for Water and Steel types) until it switches out.");
        def("curse", "Curse", "vol_curse", 10120128, -1, -1, false, "Loses 1/4 HP each turn until it switches out.");
        def("nightmare", "Nightmare", "vol_nightmare", 9071568, -1, -1, false, "Loses 1/4 HP each turn while asleep.");
        def("yawn", "Yawn", "vol_yawn", 9414911, 1, 1, false, "Falls asleep at the end of next turn unless it switches out.");
        def("confusion", "Confusion", "vol_confusion", 16754762, 2, 5, false, "33% chance to hurt itself instead of attacking, for 2 to 5 turns.");
        def("taunt", "Taunt", "vol_taunt", 16735324, 3, 3, false, "Can't use status moves.");
        def("encore", "Encore", "vol_encore", 16747208, 3, 3, false, "Must repeat its last move.");
        def("disable", "Disable", "vol_disable", 12105920, 4, 4, false, "One move can't be used.");
        def("torment", "Torment", "vol_torment", 12611696, -1, -1, false, "Can't use the same move twice in a row.");
        def("healblock", "Heal Block", "vol_healblock", 16743066, 5, 5, false, "Can't heal (moves, items, abilities, Leftovers...).");
        def("embargo", "Embargo", "vol_embargo", 13148256, 5, 5, false, "Can't use its held item.");
        def("throatchop", "Throat Chop", "vol_disable", 12105920, 2, 2, false, "Can't use sound moves.");
        def(
            "substitute",
            "Substitute",
            "vol_substitute",
            14733488,
            -1,
            -1,
            false,
            "A substitute takes the hits until it breaks (sound moves and Infiltrator go through)."
        );
        def("magnetrise", "Magnet Rise", "vol_magnetrise", 8366335, 5, 5, false, "Immune to Ground moves and hazards on the ground.");
        def("telekinesis", "Telekinesis", "vol_telekinesis", 13667583, 3, 3, false, "Floating: immune to Ground moves, but every move hits it.");
        def("ingrain", "Ingrain", "vol_ingrain", 8173664, -1, -1, false, "Heals 1/16 HP each turn but can't switch out.");
        def("aquaring", "Aqua Ring", "vol_aquaring", 7325951, -1, -1, false, "Heals 1/16 HP each turn.");
        def("trapped", "Trapped", "vol_trapped", 13150304, -1, -1, false, "Can't switch out (Mean Look, Block, Spider Web...).");
        def("octolock", "Octolock", "vol_trapped", 13150304, -1, -1, false, "Can't switch out, and loses 1 Def and 1 Sp. Def each turn.");
        def("noretreat", "No Retreat", "vol_trapped", 13150304, -1, -1, false, "Boosted all stats but can't switch out.");
        def("perish", "Perish Song", "vol_perish", 11571408, -1, -1, false, "Faints when the count reaches 0 unless it switches out.");
        def("charge", "Charge", "vol_charge", 16769354, 1, 1, false, "Its next Electric move has doubled power.");
        def("focusenergy", "Focus Energy", "vol_focus", 16747114, -1, -1, false, "Critical hit ratio +2 stages.");
        def("stockpile", "Stockpile", "vol_stockpile", 13148400, -1, -1, false, "+1 Def and Sp. Def per stack; powers up Spit Up / Swallow.");
        def("syrupbomb", "Syrup Bomb", "vol_syrupbomb", 16747192, 3, 3, false, "Loses 1 Speed at the end of each turn.");
        def(
            "foresight",
            "Identified",
            "vol_identified",
            9419007,
            -1,
            -1,
            false,
            "Normal and Fighting moves can hit it even if it's a Ghost type; evasion boosts are ignored."
        );
        def(
            "miracleeye",
            "Identified",
            "vol_identified",
            9419007,
            -1,
            -1,
            false,
            "Psychic moves can hit it even if it's a Dark type; evasion boosts are ignored."
        );
        def("smackdown", "Grounded", "vol_grounded", 13146720, -1, -1, false, "Knocked to the ground: Ground moves and ground hazards now affect it.");
        def("tarshot", "Tar Shot", "vol_tarshot", 7368824, -1, -1, false, "Fire moves do double damage to it.");
    }

    public record Badge(String icon, String title, String counter, int accent, List<String> info) {
    }

    record Def(String name, String icon, int accent, int minTurns, int maxTurns, boolean slot, String desc) {
    }

    public static final class Effect {
        public String id;
        public VolatileTracker.Def def;
        public String side;
        public String species;
        public String display;
        public int startTurn;
        public int stacks;
        public String detail;
    }
}
