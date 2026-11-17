package name.modid.client;

import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.class_2561;

public final class MegaTracker {
    private static Object trackedBattle;
    private static final Map<String, String> MEGA = new ConcurrentHashMap<>();

    private MegaTracker() {
    }

    static String norm(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static void checkBattle() {
        Object battle = CobblemonClient.INSTANCE.getBattle();
        if (battle != trackedBattle) {
            trackedBattle = battle;
            MEGA.clear();
        }
    }

    private static String text(Object arg) {
        return arg instanceof class_2561 t ? t.getString() : (arg == null ? "" : String.valueOf(arg));
    }

    public static void onMessage(String key, Object[] args) {
        if (key != null && args != null && args.length != 0) {
            try {
                checkBattle();
                String k = key.toLowerCase(Locale.ROOT);
                boolean formeChange = k.contains("formechange") && args.length > 1;
                if (formeChange) {
                    String f = text(args[1]).toLowerCase(Locale.ROOT).trim();
                    if (!f.contains("mega") && !f.equals("x") && !f.equals("y") && !f.equals("mega-x") && !f.equals("mega-y")) {
                        return;
                    }
                } else if (!k.contains("mega") || k.contains("megadrain") || k.contains("megahorn") || k.contains("megapunch") || k.contains("megakick")) {
                    return;
                }

                String letter = "";

                for (Object a : args) {
                    String t = text(a).toLowerCase(Locale.ROOT).trim();
                    if (t.equals("x") || t.endsWith(" x") || t.contains("-x") || t.contains("x form")) {
                        letter = "x";
                    } else if (t.equals("y") || t.endsWith(" y") || t.contains("-y") || t.contains("y form")) {
                        letter = "y";
                    }
                }

                for (int idx = 0; idx < Math.min(2, args.length); idx++) {
                    String[] w = BattleMessageSubscriber.resolvePokemonSideAndSpecies(args, idx);
                    if (w != null && w[0] != null && w[1] != null) {
                        record(w[0], w[1], letter);
                        String full = text(args[idx]);
                        int cut = full.lastIndexOf("'s ");
                        record(w[0], cut >= 0 ? full.substring(cut + 3) : full, letter);
                        return;
                    }
                }
            } catch (Throwable var10) {
            }
        }
    }

    private static void record(String side, String name, String letter) {
        String key = side + "|" + norm(name);
        String old = MEGA.get(key);
        if (old == null || old.isEmpty()) {
            MEGA.put(key, letter == null ? "" : letter);
        }
    }

    public static String megaLetter(String side, ClientBattlePokemon bp) {
        if (bp != null && side != null) {
            checkBattle();
            String v = null;

            try {
                v = MEGA.get(side + "|" + norm(bp.getSpecies().getName()));
                if (v == null) {
                    v = MEGA.get(side + "|" + norm(bp.getDisplayName().getString()));
                }
            } catch (Throwable var4) {
            }

            return v;
        } else {
            return null;
        }
    }
}
