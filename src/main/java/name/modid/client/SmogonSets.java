package name.modid.client;

import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Species;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class SmogonSets {
    private static final String RESOURCE = "/assets/cobblemon-battle-extras/data/smogon_sets.txt";
    private static Map<String, List<SmogonSets.Set>> sets;
    private static final Map<String, int[]> NATURES = new HashMap<>();
    public static final String[][] FORMATS;

    private SmogonSets() {
    }

    public static String format(SmogonSets.Set set) {
        String label = set.label() == null ? "" : set.label();
        int space = label.indexOf(32);
        return space > 0 ? label.substring(0, space) : label;
    }

    public static boolean formatEnabled(String tag) {
        try {
            return !ExtrasPlusConfig.get().disabledFormats.contains(tag);
        } catch (Throwable var2) {
            return true;
        }
    }

    /** Smogon sets bundled with the mod: Scarlet/Violet formats, then Pokemon Champions (Stat Points converted to EVs). */
    private static final String[] RESOURCES = {
        "/assets/cobblemon-battle-extras/data/smogon_sets.txt", "/assets/cobblemon-battle-extras/data/champions_sets.txt"
    };

    private static synchronized Map<String, List<SmogonSets.Set>> all() {
        if (sets == null) {
            Map<String, List<SmogonSets.Set>> map = new HashMap<>();
            for (String resource : RESOURCES) {
                load(resource, map);
            }

            sets = map;
        }

        return sets;
    }

    /** One set per line: species|label|nature|hp|atk|def|spa|spd|spe EVs|speed IV|item|ability|move1,move2/alt,... */
    private static void load(String resource, Map<String, List<SmogonSets.Set>> map) {
        {
            try (InputStream in = SmogonSets.class.getResourceAsStream(resource)) {
                if (in != null) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));

                    String line;
                    while ((line = reader.readLine()) != null) {
                        String[] p = line.split("\\|", -1);
                        if (p.length >= 12) {
                            try {
                                int[] evs = new int[6];

                                for (int i = 0; i < 6; i++) {
                                    evs[i] = Integer.parseInt(p[3 + i]);
                                }

                                List<List<String>> moves = new ArrayList<>();
                                if (p.length >= 13 && !p[12].isEmpty()) {
                                    for (String slot : p[12].split(",")) {
                                        List<String> options = new ArrayList<>();

                                        for (String option : slot.split("/")) {
                                            if (!option.isEmpty()) {
                                                options.add(option);
                                            }
                                        }

                                        if (!options.isEmpty()) {
                                            moves.add(List.copyOf(options));
                                        }
                                    }
                                }

                                map.computeIfAbsent(p[0], k -> new ArrayList<>())
                                    .add(new SmogonSets.Set(p[1], p[2], evs, Integer.parseInt(p[9]), p[10], p[11], List.copyOf(moves)));
                            } catch (NumberFormatException var17) {
                            }
                        }
                    }
                }
            } catch (Throwable var19) {
            }
        }
    }

    static String norm(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    public static List<SmogonSets.Set> forForm(Species species, FormData form, boolean[] fromBaseForm) {
        Map<String, List<SmogonSets.Set>> map = all();
        List<String> keys = new ArrayList<>();

        try {
            if (form != null) {
                keys.add(norm(form.showdownId()));
                keys.add(norm(species.showdownId() + form.formOnlyShowdownId()));
            }
        } catch (Throwable var11) {
        }

        try {
            keys.add(norm(species.showdownId()));
        } catch (Throwable var10) {
        }

        if (species != null) {
            keys.add(norm(species.getName()));
        }

        for (int i = 0; i < keys.size(); i++) {
            List<SmogonSets.Set> list = map.get(keys.get(i));
            if (list != null && !list.isEmpty()) {
                if (fromBaseForm != null && fromBaseForm.length > 0) {
                    fromBaseForm[0] = i >= 2 && form != null && FormAbilityHelper.isBattleOnlyForm(form);
                }

                List<SmogonSets.Set> allowed = new ArrayList<>();

                for (SmogonSets.Set s : list) {
                    if (formatEnabled(format(s))) {
                        allowed.add(s);
                    }
                }

                boolean fallback = allowed.isEmpty();
                if (fromBaseForm != null && fromBaseForm.length > 1) {
                    fromBaseForm[1] = fallback;
                }

                return fallback ? list : allowed;
            }
        }

        return List.of();
    }

    /**
     * Builds of the base species that hold this Mega Stone (Pokemon Champions lists Megas under their base form): the real
     * builds of the Mega, with the Mega's ability. Respects the enabled formats when some of them match.
     */
    public static List<SmogonSets.Set> holdingStone(Species species, String stone, String megaAbility) {
        if (species == null || stone == null || stone.isBlank()) {
            return List.of();
        }
        List<SmogonSets.Set> list = all().get(norm(species.getName()));
        try {
            if (list == null) {
                list = all().get(norm(species.showdownId()));
            }
        } catch (Throwable ignored) {
        }
        if (list == null) {
            return List.of();
        }
        List<SmogonSets.Set> any = new ArrayList<>();
        List<SmogonSets.Set> allowed = new ArrayList<>();
        for (SmogonSets.Set s : list) {
            if (norm(s.item()).equals(norm(stone))) {
                SmogonSets.Set mega = new SmogonSets.Set(s.label(), s.nature(), s.evs(), s.speIv(), s.item(),
                    megaAbility != null && !megaAbility.isBlank() ? megaAbility : s.ability(), s.moves());
                any.add(mega);
                if (formatEnabled(format(s))) {
                    allowed.add(mega);
                }
            }
        }
        return allowed.isEmpty() ? any : allowed;
    }

    public static String shortNature(String nature) {
        return nature != null && nature.length() >= 3 ? nature.substring(0, 3) : "";
    }

    public static List<SmogonSets.Set> estimatedForForm(Species species, FormData form, List<SmogonSets.Set> baseSets, String megaLetter) {
        return estimatedForForm(species, form, baseSets, megaLetter, null);
    }

    public static List<SmogonSets.Set> estimatedForForm(Species species, FormData form, List<SmogonSets.Set> baseSets, String megaLetter, MegaDex.Entry mega) {
        int[] base = new int[6];
        Stat[] stats = new Stat[]{Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED};

        for (int i = 0; i < 6; i++) {
            base[i] = mega != null ? mega.stats()[i] : SpeedTiers.baseStat(form, stats[i]);
        }

        int best = 1;
        int second = -1;

        for (int i = 2; i <= 5; i++) {
            if (base[i] > base[best]) {
                best = i;
            }
        }

        for (int ix = 1; ix <= 5; ix++) {
            if (ix != best && (second < 0 || base[ix] > base[second])) {
                second = ix;
            }
        }

        int[] evs = new int[6];
        evs[best] = 252;
        evs[second] = 252;
        evs[0] = 4;

        String nature = switch (best) {
            case 1 -> "Adamant";
            case 2 -> "Impish";
            case 3 -> "Modest";
            case 4 -> "Calm";
            default -> base[1] >= base[3] ? "Jolly" : "Timid";
        };
        if (best == 5 || second == 5) {
            nature = base[1] >= base[3] ? "Jolly" : "Timid";
        }

        String speciesName = species != null ? species.getName() : "";
        String stone = mega != null && !mega.stone().isEmpty()
            ? mega.stone()
            : speciesName + "ite" + (megaLetter != null && !megaLetter.isEmpty() ? " " + megaLetter.toUpperCase(Locale.ROOT) : "");
        String spread = "≈ " + shortStat(best) + "/" + shortStat(second) + " 252";
        List<SmogonSets.Set> out = new ArrayList<>();
        if (baseSets != null && !baseSets.isEmpty()) {
            java.util.Set<String> seen = new HashSet<>();

            for (SmogonSets.Set b : baseSets) {
                String movesKey = String.valueOf(b.moves());
                if (seen.add(movesKey)) {
                    out.add(
                        new SmogonSets.Set(
                            b.label() + " · Mega estimate " + spread, nature, (int[])evs.clone(), 31, stone, mega != null ? mega.ability() : "", b.moves()
                        )
                    );
                }
            }
        } else {
            out.add(new SmogonSets.Set("Mega estimate " + spread, nature, evs, 31, stone, mega != null ? mega.ability() : "", List.of()));
        }

        return out;
    }

    private static String shortStat(int i) {
        return switch (i) {
            case 1 -> "Atk";
            case 2 -> "Def";
            case 3 -> "SpA";
            case 4 -> "SpD";
            case 5 -> "Spe";
            default -> "HP";
        };
    }

    static {
        String[] var10000 = new String[]{"atk", "def", "spa", "spd", "spe"};
        String[][] table = new String[][]{
            {"Hardy", "Lonely", "Adamant", "Naughty", "Brave"},
            {"Bold", "Docile", "Impish", "Lax", "Relaxed"},
            {"Modest", "Mild", "Bashful", "Rash", "Quiet"},
            {"Calm", "Gentle", "Careful", "Quirky", "Sassy"},
            {"Timid", "Hasty", "Jolly", "Naive", "Serious"}
        };
        int[] order = new int[]{1, 2, 3, 4, 5};

        for (int plus = 0; plus < 5; plus++) {
            for (int minus = 0; minus < 5; minus++) {
                String name = table[plus][minus];
                NATURES.put(name.toLowerCase(Locale.ROOT), plus == minus ? new int[]{-1, -1} : new int[]{order[plus], order[minus]});
            }
        }

        FORMATS = new String[][]{
            {"OU", "OU"},
            {"NatDex", "National Dex OU"},
            {"Ubers", "Ubers"},
            {"UU", "UU"},
            {"RU", "RU"},
            {"NU", "NU"},
            {"PU", "PU"},
            {"ZU", "ZU"},
            {"LC", "Little Cup"},
            {"NFE", "NFE"},
            {"Mono", "Monotype"},
            {"BSS", "Battle Stadium Singles"},
            {"1v1", "1v1"},
            {"AG", "Anything Goes"},
            {"VGC25", "VGC 2025"},
            {"VGC24", "VGC 2024"},
            {"VGC23", "VGC 2023"},
            {"DOU", "Doubles OU"},
            {"Champions", "Pokémon Champions"}
        };
    }

    public record Set(String label, String nature, int[] evs, int speIv, String item, String ability, List<List<String>> moves) {
        public String id() {
            return this.label + "|" + this.nature + "|" + Arrays.toString(this.evs) + "|" + this.item + "|" + this.ability;
        }

        public boolean hasMove(String moveId) {
            String n = SmogonSets.norm(moveId);

            for (List<String> slot : this.moves) {
                if (slot.contains(n)) {
                    return true;
                }
            }

            return false;
        }

        public java.util.Set<String> allMoves() {
            java.util.Set<String> out = new LinkedHashSet<>();

            for (List<String> slot : this.moves) {
                out.addAll(slot);
            }

            return out;
        }

        public double natureMult(int statIndex) {
            int[] effect = SmogonSets.NATURES.getOrDefault(this.nature == null ? "" : this.nature.toLowerCase(Locale.ROOT), new int[]{-1, -1});
            if (effect[0] == statIndex) {
                return 1.1;
            } else {
                return effect[1] == statIndex ? 0.9 : 1.0;
            }
        }
    }
}
