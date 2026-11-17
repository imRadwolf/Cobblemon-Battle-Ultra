package name.modid.client;

import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.battle.ActiveClientBattlePokemon;
import com.cobblemon.mod.common.client.battle.ClientBattle;
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.class_2561;
import net.minecraft.class_310;

public final class BattleHpLog {
    private static final Map<String, Float> LAST = new HashMap<>();
    private static final Map<String, String[]> DELAYED = new HashMap<>();
    private static String pendingDelayedTarget;
    private static String pendingDelayedText;
    private static Object trackedBattle;
    private static final List<String[]> PENDING = new ArrayList<>();
    private static final List<String> AFTER_NEXT = new ArrayList<>();
    private static Method addLogMessage;

    private BattleHpLog() {
    }

    private static String norm(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static void checkBattle(ClientBattle battle) {
        if (battle != trackedBattle) {
            trackedBattle = battle;
            LAST.clear();
            DELAYED.clear();
            PENDING.clear();
            AFTER_NEXT.clear();
            pendingDelayedTarget = null;
            pendingDelayedText = null;
        }
    }

    private static boolean enabled(ClientBattle battle) {
        return battle != null && !BattleSideHelper.isSpectating(battle) && BattleLogger.isRecording();
    }

    static void write(String line) {
        try {
            if (addLogMessage == null) {
                addLogMessage = BattleLogger.class.getDeclaredMethod("addLogMessage", String.class);
                addLogMessage.setAccessible(true);
            }

            addLogMessage.invoke(null, line);
        } catch (Throwable var2) {
        }
    }

    static String ownerName(ClientBattlePokemon bp, boolean opponent) {
        try {
            return !opponent && class_310.method_1551().field_1724 != null
                ? class_310.method_1551().field_1724.method_5477().getString()
                : bp.getActor().getDisplayName().getString();
        } catch (Throwable var3) {
            return opponent ? "Opponent" : "You";
        }
    }

    private static String pct(float ratio) {
        float p = ratio * 100.0F;
        return p > 0.0F && p < 1.0F ? "<1%" : Math.round(p) + "%";
    }

    public static void onHealth(ActiveClientBattlePokemon active, boolean opponent, ClientBattlePokemon bp, float newRatio, float newHealthRaw) {
        try {
            ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
            checkBattle(battle);
            if (!enabled(battle) || bp == null) {
                return;
            }

            String key = (opponent ? "o|" : "p|") + (bp.getUuid() != null ? bp.getUuid().toString() : norm(bp.getDisplayName().getString()));
            Float old = LAST.get(key);
            if (old == null) {
                float cur = bp.getHpValue();
                old = bp.isHpFlat() ? (bp.getMaxHp() > 0.0F ? cur / bp.getMaxHp() : 1.0F) : (cur > 1.0001F ? cur / 100.0F : cur);
            }

            newRatio = Math.max(0.0F, Math.min(1.0F, newRatio));
            LAST.put(key, newRatio);
            int delta = Math.round(newRatio * 100.0F) - Math.round(old * 100.0F);
            if (Math.abs(newRatio - old) < 5.0E-4F) {
                return;
            }

            String owner = ownerName(bp, opponent);
            String mon = bp.getDisplayName().getString();
            StringBuilder line = new StringBuilder("(");
            if (pendingDelayedTarget != null && pendingDelayedTarget.equals(norm(owner) + "|" + norm(mon))) {
                line.append(pendingDelayedText).append(" · ");
                pendingDelayedTarget = null;
                pendingDelayedText = null;
            }

            line.append(owner).append("'s ").append(mon).append(": ").append(pct(old)).append(" → ").append(pct(newRatio));
            line.append(" · ").append(delta > 0 ? "+" : (delta < 0 ? "−" : "±")).append(Math.abs(delta)).append('%');
            if (!opponent && bp.isHpFlat() && bp.getMaxHp() > 0.0F) {
                int oldHp = Math.round(old * bp.getMaxHp());
                int newHp = Math.round(newHealthRaw);
                line.append(" (")
                    .append(newHp - oldHp > 0 ? "+" : "−")
                    .append(Math.abs(newHp - oldHp))
                    .append(" HP, ")
                    .append(newHp)
                    .append('/')
                    .append(Math.round(bp.getMaxHp()))
                    .append(')');
            }

            line.append(')');
            flushAfterNext();
            PENDING.add(new String[]{norm(mon), line.toString(), newRatio > old ? "+" : "-"});
        } catch (Throwable var14) {
        }
    }

    private static void flushAfterNext() {
        for (String line : AFTER_NEXT) {
            write(line);
        }

        AFTER_NEXT.clear();
    }

    public static void onMessage(String key, Object[] args) {
        try {
            ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
            checkBattle(battle);
            if (!enabled(battle) || key == null) {
                return;
            }

            String text = class_2561.method_43469(key, args == null ? new Object[0] : args).getString();
            flushAfterNext();
            if (!PENDING.isEmpty()) {
                String lk = key.toLowerCase(Locale.ROOT);
                boolean healMessage = lk.startsWith("cobblemon.battle.heal") || lk.contains("drain") || lk.contains("absorb");
                boolean causeMessage = healMessage
                    || lk.startsWith("cobblemon.battle.damage")
                    || lk.contains("leechseed")
                    || lk.contains("futuresight")
                    || lk.contains("doomdesire")
                    || text.matches(".* took the \\[?(Future Sight|Doom Desire)\\]? attack!?");
                String normText = norm(text);

                for (String[] p : PENDING) {
                    boolean sameKind = !healMessage || "+".equals(p[2]);
                    if (lk.startsWith("cobblemon.battle.damage") && "+".equals(p[2])) {
                        sameKind = false;
                    }

                    if (causeMessage && sameKind && !p[0].isEmpty() && normText.contains(p[0])) {
                        AFTER_NEXT.add(p[1]);
                    } else {
                        write(p[1]);
                    }
                }

                PENDING.clear();
            }

            if (pendingDelayedText != null && !text.startsWith("It ") && !text.startsWith("A critical")) {
                write("(" + pendingDelayedText + " · no damage)");
                pendingDelayedTarget = null;
                pendingDelayedText = null;
            }

            Matcher used = Pattern.compile("^(\\S+?)'s (.+?) used \\[?(Future Sight|Doom Desire)\\]? on (\\S+?)'s (.+?)!$").matcher(text);
            if (used.matches()) {
                DELAYED.put(norm(used.group(4)), new String[]{used.group(1) + "'s " + used.group(2), used.group(3)});
                return;
            }

            Matcher took = Pattern.compile("^(\\S+?)'s (.+?) took the \\[?(Future Sight|Doom Desire)\\]? attack!?$").matcher(text);
            if (took.matches()) {
                String[] user = DELAYED.remove(norm(took.group(1)));
                String note = took.group(3) + " from " + (user != null ? user[0] : "an earlier turn");
                String marker = "'s " + took.group(2) + ":";

                for (int i = 0; i < AFTER_NEXT.size(); i++) {
                    if (AFTER_NEXT.get(i).contains(marker)) {
                        AFTER_NEXT.set(i, "(" + note + " · " + AFTER_NEXT.get(i).substring(1));
                        return;
                    }
                }

                pendingDelayedTarget = norm(took.group(1)) + "|" + norm(took.group(2));
                pendingDelayedText = note;
            }
        } catch (Throwable var11) {
        }
    }
}
