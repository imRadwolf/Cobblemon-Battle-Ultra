package name.modid.client;

import com.cobblemon.mod.common.api.abilities.Abilities;
import com.cobblemon.mod.common.api.abilities.AbilityTemplate;
import com.cobblemon.mod.common.api.abilities.PotentialAbility;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.battle.ClientBattle;
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.RenderablePokemon;
import com.cobblemon.mod.common.pokemon.Species;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.class_1297;
import net.minecraft.class_2561;
import net.minecraft.class_310;

public final class FormAbilityHelper {
    private static final Map<String, String> MEGA_ABILITIES = Map.ofEntries(
        Map.entry("clefable", "magicbounce"),
        Map.entry("victreebel", "innardsout"),
        Map.entry("starmie", "hugepower"),
        Map.entry("dragonite", "multiscale"),
        Map.entry("meganium", "megasol"),
        Map.entry("feraligatr", "dragonize"),
        Map.entry("skarmory", "stalwart"),
        Map.entry("chimecho", "levitate"),
        Map.entry("froslass", "snowwarning"),
        Map.entry("emboar", "moldbreaker"),
        Map.entry("excadrill", "piercingdrill"),
        Map.entry("chandelure", "infiltrator"),
        Map.entry("golurk", "unseenfist"),
        Map.entry("chesnaught", "bulletproof"),
        Map.entry("delphox", "levitate"),
        Map.entry("greninja", "protean"),
        Map.entry("floette", "fairyaura"),
        Map.entry("meowstic", "trace"),
        Map.entry("hawlucha", "noguard"),
        Map.entry("crabominable", "ironfist"),
        Map.entry("drampa", "berserk"),
        Map.entry("scovillain", "spicyspray"),
        Map.entry("glimmora", "adaptability"),
        Map.entry("absol|z", "sharpness"),
        Map.entry("garchomp|z", "levitate")
    );
    private static final Map<UUID, Object[]> LIVE_CACHE = new ConcurrentHashMap<>();

    private FormAbilityHelper() {
    }

    public static String singleFormAbilityId(Species species, FormData form) {
        List<AbilityTemplate> list = abilities(form);
        if (list.size() == 1) {
            return list.get(0).getName();
        } else if (species != null && isBattleOnlyForm(form)) {
            String id = MoveContactData.normalize(species.getName());
            String formName = safeLower(form.getName());
            boolean zForm = formName.endsWith("z") || formName.contains("mega-z") || formName.contains("mega_z") || formName.contains("megaz");
            String fromTable = zForm ? MEGA_ABILITIES.get(id + "|z") : null;
            return fromTable != null ? fromTable : (zForm ? null : MEGA_ABILITIES.get(id));
        } else {
            return null;
        }
    }

    public static Set<String> battleAspects(ClientBattlePokemon battlePokemon) {
        if (battlePokemon == null) {
            return Set.of();
        } else {
            try {
                if (battlePokemon.getState() != null && battlePokemon.getState().getCurrentAspects() != null) {
                    return battlePokemon.getState().getCurrentAspects();
                }
            } catch (Throwable var3) {
            }

            try {
                if (battlePokemon.getProperties() != null && battlePokemon.getProperties().getAspects() != null) {
                    return battlePokemon.getProperties().getAspects();
                }
            } catch (Throwable var2) {
            }

            return Set.of();
        }
    }

    public static FormData battleForm(ClientBattlePokemon battlePokemon) {
        if (battlePokemon == null) {
            return null;
        } else {
            try {
                Species species = battlePokemon.getSpecies();
                return species == null ? null : species.getForm(battleAspects(battlePokemon));
            } catch (Throwable var2) {
                return null;
            }
        }
    }

    public static FormData effectiveBattleForm(ClientBattlePokemon battlePokemon, String side) {
        FormData form = battleForm(battlePokemon);
        if (form != null && isBattleOnlyForm(form)) {
            return form;
        } else {
            try {
                Species species = battlePokemon.getSpecies();
                if (species == null) {
                    return form;
                } else {
                    List<String> names = new ArrayList<>();
                    names.add(species.getName());

                    try {
                        names.add(species.showdownId());
                    } catch (Throwable var17) {
                    }

                    try {
                        names.add(battlePokemon.getDisplayName().getString());
                    } catch (Throwable var16) {
                    }

                    boolean mega = false;
                    List<String> megaTypes = null;

                    for (String name : names) {
                        if (name != null && !name.isBlank() && BattleMessageSubscriber.isMegaEvolved(side, name)) {
                            mega = true;
                            megaTypes = BattleMessageSubscriber.getMegaTypes(side, name);
                            break;
                        }
                    }

                    String megaLetter = MegaTracker.megaLetter(side, battlePokemon);
                    if (!mega && megaLetter != null) {
                        mega = true;
                    }

                    if (!mega) {
                        FormData live = liveBattleForm(battlePokemon);
                        if (live != null) {
                            return live;
                        }

                        boolean tileSaysMega = false;

                        try {
                            tileSaysMega = CustomBattleTileRenderer.isMegaEvolvedForTile(
                                battlePokemon, side, species.getName(), battlePokemon.getDisplayName().getString()
                            );
                        } catch (Throwable var15) {
                        }

                        if (!tileSaysMega) {
                            return form;
                        }
                    }

                    List<FormData> candidates = new ArrayList<>();

                    for (FormData candidate : species.getForms()) {
                        if (candidate != null
                            && candidate != species.getStandardForm()
                            && isBattleOnlyForm(candidate)
                            && !safeLower(candidate.getName()).contains("primal")) {
                            candidates.add(candidate);
                        }
                    }

                    if (!candidates.isEmpty() && candidates.size() > 1) {
                        FormData livex = liveBattleForm(battlePokemon);
                        if (livex != null) {
                            return livex;
                        }

                        if (megaLetter != null && !megaLetter.isEmpty()) {
                            for (FormData candidatex : candidates) {
                                String n = safeLower(candidatex.getName()).replaceAll("[^a-z]", "");
                                if (n.endsWith(megaLetter)) {
                                    return candidatex;
                                }
                            }
                        }
                    }

                    if (candidates.isEmpty()) {
                        return form;
                    } else if (candidates.size() != 1 && megaTypes != null && !megaTypes.isEmpty()) {
                        for (FormData candidatexx : candidates) {
                            List<String> typeNames = new ArrayList<>();

                            for (ElementalType type : types(candidatexx)) {
                                typeNames.add(safeLower(type.getName()));
                            }

                            boolean matches = true;

                            for (String megaType : megaTypes) {
                                if (!typeNames.contains(safeLower(megaType))) {
                                    matches = false;
                                    break;
                                }
                            }

                            if (matches) {
                                return candidatexx;
                            }
                        }

                        return candidates.get(0);
                    } else {
                        return candidates.get(0);
                    }
                }
            } catch (Throwable var18) {
                return form;
            }
        }
    }

    public static FormData liveBattleForm(ClientBattlePokemon battlePokemon) {
        if (battlePokemon != null && battlePokemon.getUuid() != null) {
            UUID uuid = battlePokemon.getUuid();
            long now = System.currentTimeMillis();
            Object[] cached = LIVE_CACHE.get(uuid);
            if (cached != null && now - (Long)cached[0] < 300L) {
                return (FormData)cached[1];
            } else {
                FormData found = null;

                try {
                    if ((battlePokemon.getProperties() != null ? battlePokemon.getProperties().asRenderablePokemon() : null) instanceof RenderablePokemon rp
                        && isBattleOnlyForm(rp.getForm())) {
                        found = rp.getForm();
                    }
                } catch (Throwable var13) {
                }

                if (found == null) {
                    try {
                        class_310 mc = class_310.method_1551();
                        ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
                        if (mc.field_1687 != null && battle != null && battlePokemon.getSpecies() != null) {
                            String species = safeLower(battlePokemon.getSpecies().getName());

                            for (class_1297 entity : mc.field_1687.method_18112()) {
                                if (entity instanceof PokemonEntity pe
                                    && battle.getBattleId().equals(pe.getBattleId())
                                    && pe.getPokemon() != null
                                    && safeLower(pe.getPokemon().getSpecies().getName()).equals(species)) {
                                    FormData byAspects = form(battlePokemon.getSpecies(), pe.getAspects());
                                    if (isBattleOnlyForm(byAspects)) {
                                        found = byAspects;
                                        break;
                                    }
                                }
                            }
                        }
                    } catch (Throwable var15) {
                    }
                }

                if (found == null) {
                    try {
                        class_310 mc = class_310.method_1551();
                        if (mc.field_1687 != null) {
                            for (class_1297 entityx : mc.field_1687.method_18112()) {
                                if (entityx instanceof PokemonEntity pex) {
                                    Pokemon p = pex.getPokemon();
                                    if (p != null && uuid.equals(p.getUuid())) {
                                        if (isBattleOnlyForm(p.getForm())) {
                                            found = p.getForm();
                                        } else {
                                            FormData byAspects = form(p.getSpecies(), p.getAspects());
                                            if (isBattleOnlyForm(byAspects)) {
                                                found = byAspects;
                                            }
                                        }
                                        break;
                                    }
                                }
                            }
                        }
                    } catch (Throwable var14) {
                    }
                }

                LIVE_CACHE.put(uuid, new Object[]{now, found});
                return found;
            }
        } else {
            return null;
        }
    }

    public static FormData form(Species species, Collection<String> aspects) {
        if (species == null) {
            return null;
        } else {
            try {
                return species.getForm((Set)(aspects == null ? Set.of() : new HashSet<>(aspects)));
            } catch (Throwable var3) {
                return species.getStandardForm();
            }
        }
    }

    public static boolean isBattleOnlyForm(FormData form) {
        if (form == null) {
            return false;
        } else {
            String name = safeLower(form.getName());
            if (!name.contains("mega") && !name.contains("primal") && !name.contains("ultra") && !name.contains("eternamax")) {
                try {
                    for (String aspect : form.getAspects()) {
                        String a = safeLower(aspect);
                        if (a.startsWith("mega") || a.equals("primal")) {
                            return true;
                        }
                    }
                } catch (Throwable var6) {
                }

                try {
                    for (String label : form.getLabels()) {
                        String l = safeLower(label);
                        if (l.contains("mega") || l.contains("primal")) {
                            return true;
                        }
                    }
                } catch (Throwable var5) {
                }

                return false;
            } else {
                return true;
            }
        }
    }

    public static boolean sameForm(FormData a, FormData b) {
        return a != null && b != null ? a == b || safeLower(a.getName()).equals(safeLower(b.getName())) : a == b;
    }

    public static List<AbilityTemplate> abilities(FormData form) {
        List<AbilityTemplate> result = new ArrayList<>();
        if (form == null) {
            return result;
        } else {
            Set<String> seen = new LinkedHashSet<>();

            try {
                for (PotentialAbility potential : form.getAbilities()) {
                    AbilityTemplate template = potential != null ? potential.getTemplate() : null;
                    if (template != null && template.getName() != null && seen.add(template.getName())) {
                        result.add(template);
                    }
                }
            } catch (Throwable var6) {
            }

            return result;
        }
    }

    public static String abilityDisplayName(AbilityTemplate template) {
        if (template == null) {
            return null;
        } else {
            try {
                String key = template.getDisplayName();
                if (key != null && !key.isBlank()) {
                    String translated = class_2561.method_43471(key).getString();
                    if (translated != null && !translated.isBlank() && !translated.equals(key)) {
                        return translated;
                    }
                }
            } catch (Throwable var3) {
            }

            return prettyId(template.getName());
        }
    }

    public static String abilityDisplayNameFromId(String id) {
        if (id != null && !id.isBlank()) {
            String norm = MoveContactData.normalize(id);
            String key = "cobblemon.ability." + norm;
            String translated = class_2561.method_43471(key).getString();
            if (translated != null && !translated.isBlank() && !translated.equals(key)) {
                return translated;
            } else {
                String fallback = FallbackAbilityText.name(norm);
                return fallback != null ? fallback : prettyId(id);
            }
        } else {
            return null;
        }
    }

    public static List<ElementalType> types(FormData form) {
        List<ElementalType> result = new ArrayList<>();
        if (form != null) {
            try {
                for (ElementalType type : form.getTypes()) {
                    if (type != null) {
                        result.add(type);
                    }
                }
            } catch (Throwable var4) {
            }
        }

        return result;
    }

    public static AbilityTemplate battleFormAbilityOverride(Pokemon partyPokemon, ClientBattlePokemon battlePokemon) {
        if (partyPokemon != null && battlePokemon != null) {
            FormData battleForm = effectiveBattleForm(battlePokemon, "player");
            if (battleForm == null) {
                return null;
            } else {
                FormData partyForm = null;

                try {
                    partyForm = partyPokemon.getForm();
                } catch (Throwable var8) {
                }

                if (sameForm(battleForm, partyForm)) {
                    return null;
                } else {
                    List<AbilityTemplate> list = abilities(battleForm);
                    if (list.size() == 1) {
                        return list.get(0);
                    } else if (isBattleOnlyForm(battleForm)) {
                        String id = singleFormAbilityId(battlePokemon.getSpecies(), battleForm);
                        if (id != null) {
                            try {
                                AbilityTemplate template = Abilities.get(id);
                                if (template != null) {
                                    return template;
                                }
                            } catch (Throwable var7) {
                            }
                        }

                        return list.isEmpty() ? null : list.get(0);
                    } else {
                        return null;
                    }
                }
            }
        } else {
            return null;
        }
    }

    public static String prettyId(String id) {
        if (id != null && !id.isBlank()) {
            String cleaned = id.replace('_', ' ').replace('-', ' ').trim();
            StringBuilder out = new StringBuilder();

            for (String word : cleaned.split("\\s+")) {
                if (!word.isEmpty()) {
                    if (out.length() > 0) {
                        out.append(' ');
                    }

                    out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
                }
            }

            return out.toString();
        } else {
            return id;
        }
    }

    private static String safeLower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }
}
