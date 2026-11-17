package name.modid.client;

import com.cobblemon.mod.common.api.pokemon.PokemonProperties;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import java.util.Locale;
import net.minecraft.class_2960;

public final class SafePokemonFactory {
    private static volatile boolean createBroken = Boolean.getBoolean("cbe.forceSafePokemon");

    private SafePokemonFactory() {
    }

    public static Pokemon create(PokemonProperties properties) {
        if (properties == null) {
            return new Pokemon();
        } else {
            if (!createBroken) {
                try {
                    return properties.create();
                } catch (IllegalArgumentException var6) {
                    String message = var6.getMessage();
                    if (message == null || !message.contains("MovesetBuilder")) {
                        throw var6;
                    }
                }

                createBroken = true;
            }

            Pokemon pokemon = new Pokemon();

            try {
                String speciesName = properties.getSpecies();
                if (speciesName != null && !speciesName.isBlank()) {
                    Species species = speciesName.contains(":")
                        ? PokemonSpecies.getByIdentifier(class_2960.method_60654(speciesName))
                        : PokemonSpecies.getByName(speciesName.toLowerCase(Locale.ROOT));
                    if (species != null) {
                        pokemon.setSpecies(species);
                    }
                }
            } catch (Throwable var5) {
            }

            try {
                properties.apply(pokemon);
            } catch (Throwable var4) {
            }

            return pokemon;
        }
    }
}
