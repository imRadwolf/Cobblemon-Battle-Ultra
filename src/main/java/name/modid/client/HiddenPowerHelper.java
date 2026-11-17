package name.modid.client;

import com.cobblemon.mod.common.api.abilities.AbilityTemplate;
import com.cobblemon.mod.common.api.moves.HiddenPowerUtil;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.api.types.ElementalTypes;
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon;
import com.cobblemon.mod.common.pokemon.Pokemon;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Set;

public final class HiddenPowerHelper {
    public static final ThreadLocal<Object> CTX = new ThreadLocal<>();
    private static final Set<String> ATE_EXEMPT = Set.of(
        "judgment", "multiattack", "naturalgift", "revelationdance", "technoblast", "terrainpulse", "weatherball"
    );
    private static final Set<String> NORMALIZE_EXEMPT = Set.of(
        "hiddenpower", "judgment", "multiattack", "naturalgift", "revelationdance", "struggle", "technoblast", "terrainpulse", "weatherball"
    );

    private HiddenPowerHelper() {
    }

    public static boolean isHiddenPower(MoveTemplate t) {
        try {
            return t != null && t.getName().toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "").equals("hiddenpower");
        } catch (Throwable var2) {
            return false;
        }
    }

    static String norm(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    public static String ateType(String ability) {
        String var1 = norm(ability);

        return switch (var1) {
            case "pixilate" -> "fairy";
            case "aerilate" -> "flying";
            case "refrigerate" -> "ice";
            case "galvanize" -> "electric";
            case "dragonize" -> "dragon";
            default -> null;
        };
    }

    public static String abilityConvertedType(String moveId, String typeName, String ability) {
        String id = norm(moveId);
        String ab = norm(ability);
        if (ab.equals("normalize")) {
            return NORMALIZE_EXEMPT.contains(id) ? null : "normal";
        } else {
            String ate = ateType(ab);
            return ate != null && "normal".equals(norm(typeName)) && !ATE_EXEMPT.contains(id) ? ate : null;
        }
    }

    public static double typeChangeBoost(MoveTemplate t, String ability) {
        try {
            if (t != null && !BuildInference.isStatus(t)) {
                String base = baseType(t, null).getName();
                return abilityConvertedType(t.getName(), base, ability) != null ? 1.2 : 1.0;
            } else {
                return 1.0;
            }
        } catch (Throwable var3) {
            return 1.0;
        }
    }

    public static double power(MoveTemplate t, Object ctx) {
        double raw = rawPower(t, ctx);

        try {
            if (t != null && MovePower.isVariable(t.getName()) && isActiveMine(ctx)) {
                double[] range = MovePower.playerPowerRange(t);
                return range[1];
            }
        } catch (Throwable var5) {
        }

        return raw;
    }

    static boolean isActiveMine(Object ctx) {
        try {
            Pokemon p = resolve(ctx);
            ClientBattlePokemon mine = MoveInsights.activeMine();
            Pokemon party = mine != null ? PartyInfoSideRenderer.findPartyPokemonForBattlePokemon(mine) : null;
            return p != null && party != null && party.getUuid().equals(p.getUuid());
        } catch (Throwable var4) {
            return false;
        }
    }

    public static double rawPower(MoveTemplate t, Object ctx) {
        if (t == null) {
            return 0.0;
        } else {
            String id = norm(t.getName());
            if (!id.equals("return") && !id.equals("frustration")) {
                return t.getPower();
            } else {
                int friendship = -1;

                try {
                    Pokemon p = resolve(ctx);
                    if (p != null) {
                        friendship = p.getFriendship();
                    }
                } catch (Throwable var5) {
                }

                return friendshipPower(id, friendship);
            }
        }
    }

    public static double friendshipPower(String moveId, int friendship) {
        String id = norm(moveId);
        if (id.equals("return")) {
            int f = friendship < 0 ? 255 : Math.min(255, friendship);
            return Math.max(1, f * 10 / 25);
        } else if (id.equals("frustration")) {
            int f = friendship < 0 ? 0 : Math.min(255, friendship);
            return Math.max(1, (255 - f) * 10 / 25);
        } else {
            return -1.0;
        }
    }

    public static double opponentPower(MoveTemplate t) {
        double p = t == null ? 0.0 : friendshipPower(t.getName(), -1);
        return p > 0.0 ? p : (t == null ? 0.0 : t.getPower());
    }

    public static String playerAbility(Pokemon p) {
        if (p == null) {
            return "";
        } else {
            try {
                ClientBattlePokemon mine = MoveInsights.activeMine();
                if (mine != null) {
                    Pokemon party = PartyInfoSideRenderer.findPartyPokemonForBattlePokemon(mine);
                    if (party != null && party.getUuid().equals(p.getUuid())) {
                        ItemAbilityTracker.State st = ItemAbilityTracker.get("player", mine);
                        if (st != null && st.abilityTemporary && st.abilityId != null) {
                            return norm(st.abilityId);
                        }

                        AbilityTemplate o = FormAbilityHelper.battleFormAbilityOverride(p, mine);
                        if (o != null) {
                            return norm(o.getName());
                        }
                    }
                }
            } catch (Throwable var6) {
            }

            try {
                return norm(p.getAbility().getName());
            } catch (Throwable var5) {
                return "";
            }
        }
    }

    public static String attackerAbility(Object ctx, String fallback) {
        try {
            Pokemon p = resolve(ctx);
            if (p != null) {
                String ab = playerAbility(p);
                if (!ab.isEmpty()) {
                    return ab;
                }
            }
        } catch (Throwable var4) {
        }

        return fallback == null ? "" : fallback;
    }

    public static ElementalType effective(MoveTemplate t) {
        return effective(t, null);
    }

    public static ElementalType effective(MoveTemplate t, Object ctx) {
        ElementalType base = baseType(t, ctx);

        try {
            if (t != null && !BuildInference.isStatus(t)) {
                Pokemon p = resolve(ctx);
                if (p != null) {
                    String converted = abilityConvertedType(t.getName(), base.getName(), playerAbility(p));
                    if (converted != null) {
                        ElementalType type = ElementalTypes.get(converted);
                        if (type != null) {
                            return type;
                        }
                    }
                }
            }
        } catch (Throwable var6) {
        }

        return base;
    }

    public static ElementalType baseType(MoveTemplate t, Object ctx) {
        if (t == null) {
            return ElementalTypes.NORMAL;
        } else if (isHiddenPower(t)) {
            try {
                Pokemon p = resolve(ctx);
                if (p != null) {
                    ElementalType type = HiddenPowerUtil.getHiddenPowerType(p);
                    if (type != null) {
                        return type;
                    }
                }
            } catch (Throwable var4) {
            }

            return t.getElementalType();
        } else {
            try {
                if (MovePower.isVariable(t.getName())
                    || "ragingbull".equals(norm(t.getName()))
                    || "revelationdance".equals(norm(t.getName()))
                    || "aurawheel".equals(norm(t.getName()))) {
                    String override = MovePower.playerTypeOverride(t, resolve(ctx));
                    if (override != null) {
                        ElementalType type = ElementalTypes.get(override);
                        if (type != null) {
                            return type;
                        }
                    }
                }
            } catch (Throwable var5) {
            }

            return t.getElementalType();
        }
    }

    static Pokemon resolve(Object ctx) {
        if (ctx == null) {
            ctx = CTX.get();
        }

        try {
            if (ctx instanceof Pokemon p) {
                return p;
            }

            if (ctx instanceof ClientBattlePokemon bp) {
                Pokemon p = PartyInfoSideRenderer.findPartyPokemonForBattlePokemon(bp);
                if (p != null) {
                    return p;
                }
            } else if (ctx != null) {
                Method m = ctx.getClass().getMethod("getPokemon");
                if (m.invoke(ctx) instanceof Pokemon p) {
                    return p;
                }
            }
        } catch (Throwable var6) {
        }

        try {
            ClientBattlePokemon mine = MoveInsights.activeMine();
            return mine != null ? PartyInfoSideRenderer.findPartyPokemonForBattlePokemon(mine) : null;
        } catch (Throwable var5) {
            return null;
        }
    }
}
