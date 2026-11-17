package name.modid.client;

import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.battle.ActiveClientBattlePokemon;
import com.cobblemon.mod.common.client.battle.ClientBattle;
import com.cobblemon.mod.common.client.battle.ClientBattleActor;
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon;
import com.cobblemon.mod.common.client.battle.ClientBattleSide;
import com.cobblemon.mod.common.pokemon.Pokemon;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.class_2561;
import net.minecraft.class_2588;

public final class ItemAbilityTracker {
    private static final Map<String, ItemAbilityTracker.State> STATES = new ConcurrentHashMap<>();
    private static Object trackedBattle;

    private ItemAbilityTracker() {
    }

    private static String norm(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static void checkBattle() {
        Object battle = CobblemonClient.INSTANCE.getBattle();
        if (!BattleKey.same(battle, trackedBattle)) {
            trackedBattle = BattleKey.of(battle);
            STATES.clear();
        }
    }

    private static String key(String side, String species) {
        return side + "_" + norm(species);
    }

    private static ItemAbilityTracker.State state(String side, String species) {
        return STATES.computeIfAbsent(key(side, species), k -> new ItemAbilityTracker.State());
    }

    public static ItemAbilityTracker.State get(String side, ClientBattlePokemon battlePokemon) {
        checkBattle();
        if (battlePokemon == null) {
            return null;
        } else {
            try {
                ItemAbilityTracker.State state = STATES.get(key(side, battlePokemon.getSpecies().getName()));
                if (state == null) {
                    state = STATES.get(key(side, battlePokemon.getDisplayName().getString()));
                }

                return state;
            } catch (Throwable var3) {
                return null;
            }
        }
    }

    public static String knownItemName(String side, ClientBattlePokemon battlePokemon) {
        ItemAbilityTracker.State state = get(side, battlePokemon);
        if (state != null) {
            if (state.itemName != null) {
                return state.itemName;
            }

            if (state.itemLost) {
                return "";
            }
        }

        return null;
    }

    private static String text(Object arg) {
        if (arg == null) {
            return null;
        } else {
            return arg instanceof class_2561 component ? component.getString() : String.valueOf(arg);
        }
    }

    private static String abilityIdFromArg(Object arg) {
        if (arg instanceof class_2561 component && component.method_10851() instanceof class_2588 tc) {
            String k = tc.method_11022();
            if (k != null && k.startsWith("cobblemon.ability.")) {
                String[] parts = k.split("\\.");
                if (parts.length >= 3) {
                    return parts[2];
                }
            }
        }

        return norm(text(arg));
    }

    private static String[] who(Object[] args, int idx) {
        String[] context = BattleMessageSubscriber.resolvePokemonSideAndSpecies(args, idx);
        return context != null && context[0] != null && context[1] != null && !context[1].isBlank() ? context : null;
    }

    private static String[] otherActive(String side) {
        try {
            ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
            if (battle == null) {
                return null;
            } else {
                String otherSide = "player".equals(side) ? "opponent" : "player";
                ClientBattleSide battleSide = "player".equals(otherSide) ? BattleSideHelper.getPlayerSide(battle) : BattleSideHelper.getOpponentSide(battle);

                for (ClientBattleActor actor : battleSide.getActors()) {
                    for (ActiveClientBattlePokemon active : actor.getActivePokemon()) {
                        if (active.getBattlePokemon() != null) {
                            return new String[]{otherSide, active.getBattlePokemon().getSpecies().getName()};
                        }
                    }
                }

                return null;
            }
        } catch (Throwable var8) {
            return null;
        }
    }

    private static void held(String[] who, String itemName) {
        held(who, itemName, false);
    }

    private static void held(String[] who, String itemName, boolean acquired) {
        if (who != null && itemName != null && !itemName.isBlank()) {
            ItemAbilityTracker.State st = state(who[0], who[1]);
            if (!acquired && !st.swapPending && st.originalItem == null) {
                st.originalItem = itemName;
            }

            st.swapPending = false;
            st.itemName = itemName;
            st.itemLost = false;
            st.lostItemName = null;
            st.lostReason = null;
        }
    }

    private static void lost(String[] who, String itemName, String reason) {
        if (who != null) {
            ItemAbilityTracker.State st = state(who[0], who[1]);
            String previous = st.itemName;
            if (previous == null && "player".equals(who[0])) {
                previous = playerPartyItemName(who[1]);
            }

            if (previous == null && "opponent".equals(who[0])) {
                try {
                    RevealedBattleInfo.PokemonRevealedInfo info = RevealedBattleInfo.getRevealedInfo(norm(who[1]));
                    if (info != null) {
                        previous = info.getHeldItemDisplay();
                    }
                } catch (Throwable var6) {
                }
            }

            st.itemLost = true;
            st.lostItemName = itemName != null && !itemName.isBlank() ? itemName : previous;
            if (st.originalItem == null && st.lostItemName != null && !st.swapPending) {
                st.originalItem = st.lostItemName;
            }

            st.swapPending = reason != null && reason.startsWith("Swapped");
            st.lostReason = reason;
            st.itemName = null;
        }
    }

    private static String playerPartyItemName(String species) {
        try {
            for (Pokemon pokemon : LocalBattlePartyTracker.getLocalPartySnapshot()) {
                if (pokemon != null && norm(pokemon.getSpecies().getName()).equals(norm(species))) {
                    return pokemon.heldItem() != null && !pokemon.heldItem().method_7960() ? pokemon.heldItem().method_7964().getString() : null;
                }
            }
        } catch (Throwable var3) {
        }

        return null;
    }

    private static String currentAbility(String side, String species, String[] idOut) {
        ItemAbilityTracker.State st = STATES.get(key(side, species));
        if (st != null && st.abilityName != null) {
            idOut[0] = st.abilityId;
            return st.abilityName;
        } else if ("opponent".equals(side)) {
            RevealedBattleInfo.PokemonRevealedInfo info = RevealedBattleInfo.getRevealedInfo(norm(species));
            if (info != null && info.getAbilityDisplay() != null) {
                idOut[0] = info.getAbility();
                return info.getAbilityDisplay();
            } else {
                return null;
            }
        } else {
            try {
                for (Pokemon pokemon : LocalBattlePartyTracker.getLocalPartySnapshot()) {
                    if (pokemon != null && norm(pokemon.getSpecies().getName()).equals(norm(species))) {
                        String id = pokemon.getAbility().getTemplate().getName();
                        idOut[0] = id;
                        return FormAbilityHelper.abilityDisplayNameFromId(id);
                    }
                }
            } catch (Throwable var7) {
            }

            return null;
        }
    }

    private static void setAbility(String[] who, String name, String id, boolean temporary, String note) {
        if (who != null && name != null) {
            ItemAbilityTracker.State st = state(who[0], who[1]);
            st.abilityName = name;
            st.abilityId = id;
            st.abilityTemporary = temporary;
            st.abilitySuppressed = false;
            st.abilityNote = note;
        }
    }

    private static String lostReason(String effect) {
        return switch (effect) {
            case "knockoff" -> "Knocked off";
            case "incinerate" -> "Burned up";
            case "corrosivegas" -> "Corroded";
            case "stealeat" -> "Stolen & eaten";
            case "fling" -> "Flung";
            case "airballoon" -> "Popped";
            case "eat" -> "Eaten";
            case "gem" -> "Used up";
            default -> effect.endsWith("berry") ? "Eaten" : "Used up";
        };
    }

    public static void onMessage(String key, Object[] args) {
        if (key != null && args != null && args.length > 0) {
            checkBattle();
            String k = key.startsWith("cobblemon.") ? key.substring("cobblemon.".length()) : key;
            String[] parts = k.split("\\.");
            if (parts.length >= 2 && "battle".equals(parts[0])) {
                String action = parts[1];
                String effect = parts.length >= 3 ? parts[2] : "";

                try {
                    switch (action) {
                        case "enditem":
                            String itemName = args.length >= 2 ? text(args[1]) : null;
                            if ("stealeat".equals(effect)) {
                                lost(who(args, 0), itemName, "Stolen & eaten");
                            } else {
                                lost(who(args, 0), itemName, lostReason(effect));
                            }
                            break;
                        case "item":
                            switch (effect) {
                                case "thief":
                                    held(who(args, 0), text(args.length >= 2 ? args[1] : null), true);
                                    if (args.length >= 3) {
                                        lost(who(args, 2), text(args[1]), "Stolen");
                                    }

                                    return;
                                case "bestow":
                                    held(who(args, 0), text(args.length >= 2 ? args[1] : null), true);
                                    if (args.length >= 3) {
                                        lost(who(args, 2), text(args[1]), "Given away");
                                    }

                                    return;
                                case "frisk":
                                    held(who(args, 0), text(args.length >= 2 ? args[1] : null));
                                    return;
                                case "trick":
                                case "switcheroo":
                                    held(who(args, 0), text(args.length >= 2 ? args[1] : null), true);
                                    return;
                                default:
                                    held(who(args, 0), text(args.length >= 2 ? args[1] : null));
                                    return;
                            }
                        case "activate":
                            if ("trick".equals(effect) || "switcheroo".equals(effect)) {
                                String[] user = who(args, 0);
                                String[] target = user != null ? otherActive(user[0]) : null;
                                String reason = "trick".equals(effect) ? "Swapped away (Trick)" : "Swapped away (Switcheroo)";
                                lost(user, null, reason);
                                lost(target, null, reason);
                            } else if ("skillswap".equals(effect)) {
                                String[] user = who(args, 0);
                                String[] target = user != null ? otherActive(user[0]) : null;
                                if (user != null && target != null) {
                                    String[] userId = new String[1];
                                    String[] targetId = new String[1];
                                    String userAbility = currentAbility(user[0], user[1], userId);
                                    String targetAbility = currentAbility(target[0], target[1], targetId);
                                    setAbility(user, targetAbility != null ? targetAbility : "???", targetId[0], true, "Skill Swap");
                                    setAbility(target, userAbility != null ? userAbility : "???", userId[0], true, "Skill Swap");
                                }
                            }
                            break;
                        case "ability":
                            if ("trace".equals(effect) && args.length >= 3) {
                                setAbility(who(args, 0), text(args[2]), abilityIdFromArg(args[2]), true, "Traced");
                            } else if ("replace".equals(effect) && args.length >= 2) {
                                setAbility(who(args, 0), text(args[1]), abilityIdFromArg(args[1]), true, "Changed");
                            } else if (!"receiver".equals(effect)) {
                                String[] pokemonxx = who(args, 0);
                                if (pokemonxx != null) {
                                    String name;
                                    String id;
                                    if ("generic".equals(effect)) {
                                        name = args.length >= 2 ? text(args[1]) : null;
                                        id = args.length >= 2 ? abilityIdFromArg(args[1]) : null;
                                    } else {
                                        id = effect;
                                        name = FormAbilityHelper.abilityDisplayNameFromId(effect);
                                    }

                                    ItemAbilityTracker.State st = state(pokemonxx[0], pokemonxx[1]);
                                    if (name != null && (!st.abilityTemporary || st.abilityName == null)) {
                                        st.abilityName = name;
                                        st.abilityId = id;
                                    }
                                }
                            }
                            break;
                        case "endability":
                            String[] pokemonx = who(args, 0);
                            if (pokemonx != null) {
                                ItemAbilityTracker.State st = state(pokemonx[0], pokemonx[1]);
                                st.abilitySuppressed = true;
                                if (st.abilityName == null) {
                                    String[] id = new String[1];
                                    String current = currentAbility(pokemonx[0], pokemonx[1], id);
                                    st.abilityName = current;
                                    st.abilityId = id[0];
                                }
                            }
                            break;
                        case "withdraw":
                        case "recall":
                        case "return":
                            String[] pokemon = who(args, 0);
                            if (pokemon != null) {
                                ItemAbilityTracker.State st = STATES.get(key(pokemon[0], pokemon[1]));
                                if (st != null) {
                                    st.abilitySuppressed = false;
                                    if (st.abilityTemporary) {
                                        st.abilityName = null;
                                        st.abilityId = null;
                                        st.abilityTemporary = false;
                                        st.abilityNote = null;
                                    }
                                }
                            }
                    }
                } catch (Throwable var14) {
                }
            }
        }
    }

    public static final class State {
        public String itemName;
        public boolean itemLost;
        public String lostItemName;
        public String lostReason;
        public String abilityName;
        public String abilityId;
        public boolean abilityTemporary;
        public boolean abilitySuppressed;
        public String abilityNote;
        public String originalItem;
        boolean swapPending;
    }
}
