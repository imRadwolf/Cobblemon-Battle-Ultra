package name.modid.client;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class FormatPresets {
    public static final String[] NAMES = new String[]{
        "All formats", "OU + National Dex OU", "Smogon singles tiers", "Singles (all)", "Doubles (VGC / DOU)", "Custom"
    };
    private static final Set<String> SINGLES_TIERS = Set.of("OU", "NatDex", "Ubers", "UU", "RU", "NU", "PU", "ZU", "LC", "NFE", "AG", "Mono");
    private static final Set<String> DOUBLES = Set.of("VGC25", "VGC24", "VGC23", "DOU");

    private FormatPresets() {
    }

    private static Set<String> enabled(int i) {
        return switch (i) {
            case 1 -> Set.of("OU", "NatDex");
            case 2 -> SINGLES_TIERS;
            case 3 -> Set.of("OU", "NatDex", "Ubers", "UU", "RU", "NU", "PU", "ZU", "LC", "NFE", "AG", "Mono", "BSS", "1v1");
            case 4 -> DOUBLES;
            case 5 -> null;
            default -> allTags();
        };
    }

    private static Set<String> allTags() {
        Set<String> all = new HashSet<>();

        for (String[] f : SmogonSets.FORMATS) {
            all.add(f[0]);
        }

        return all;
    }

    public static int current(List<String> disabled) {
        Set<String> on = allTags();
        on.removeAll(disabled);

        for (int i = 0; i < NAMES.length - 1; i++) {
            if (on.equals(enabled(i))) {
                return i;
            }
        }

        return NAMES.length - 1;
    }

    public static void apply(int preset, List<String> disabled) {
        Set<String> on = enabled(preset);
        if (on != null) {
            List<String> off = new ArrayList<>();

            for (String[] f : SmogonSets.FORMATS) {
                if (!on.contains(f[0])) {
                    off.add(f[0]);
                }
            }

            disabled.clear();
            disabled.addAll(off);
        }
    }
}
