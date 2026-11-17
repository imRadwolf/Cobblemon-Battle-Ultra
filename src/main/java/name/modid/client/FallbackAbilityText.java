package name.modid.client;

import java.util.Map;

public final class FallbackAbilityText {
    private static final Map<String, String[]> ABILITIES = Map.ofEntries(
        Map.entry(
            "megasol",
            new String[]{"Mega Sol", "Even when the sunlight has not turned harsh, the Pokémon can use its moves as if the weather were harsh sunlight."}
        ),
        Map.entry("dragonize", new String[]{"Dragonize", "The Pokémon's Normal-type moves become Dragon-type moves and their power is boosted by 20%."}),
        Map.entry(
            "piercingdrill",
            new String[]{
                "Piercing Drill",
                "When the Pokémon uses contact moves, it can hit even targets that are protecting themselves, dealing 1/4 of the damage that the move would otherwise deal."
            }
        ),
        Map.entry("spicyspray", new String[]{"Spicy Spray", "When the Pokémon takes damage from a move, it burns the attacker."}),
        Map.entry("auraguard", new String[]{"Aura Guard", "Halves the damage the Pokémon takes from moves that make direct contact."}),
        Map.entry("waveshield", new String[]{"Wave Shield", "Halves the damage the Pokémon takes from moves that make direct contact."})
    );

    private FallbackAbilityText() {
    }

    public static String name(String normalizedId) {
        String[] entry = normalizedId == null ? null : ABILITIES.get(normalizedId);
        return entry != null ? entry[0] : null;
    }

    public static String description(String normalizedId) {
        String[] entry = normalizedId == null ? null : ABILITIES.get(normalizedId);
        return entry != null ? entry[1] : null;
    }
}
