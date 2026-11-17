package name.modid.client;

import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.battle.ActiveClientBattlePokemon;
import com.cobblemon.mod.common.client.battle.ClientBattle;
import com.cobblemon.mod.common.client.battle.ClientBattleActor;
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import name.modid.platform.Services;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.class_2561;
import net.minecraft.class_310;
import net.minecraft.class_327;
import net.minecraft.class_332;

public final class BattleRecap {
    private static Object trackedBattle;
    private static final Map<String, BattleRecap.Mon> MONS = new ConcurrentHashMap<>();
    private static final Map<String, Float> RATIO = new ConcurrentHashMap<>();
    private static final Map<String, BattleRecap.Learned> LEARNED = new ConcurrentHashMap<>();
    private static String lastMoverKey;
    private static String lastMoverSide;
    private static int turns;
    private static String result;
    private static String opponentName = "";
    private static long startedAt;
    private static volatile BattleRecap.Recap showing;
    private static volatile long shownAt;

    private BattleRecap() {
    }

    static String norm(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static String key(String side, String species) {
        return side + "_" + norm(species);
    }

    public static int faintedCount(String side) {
        int n = 0;

        try {
            for (BattleRecap.Mon m : MONS.values()) {
                if (m.fainted && side.equals(m.side)) {
                    n++;
                }
            }
        } catch (Throwable var4) {
        }

        return n;
    }

    private static BattleRecap.Mon mon(String side, String name) {
        return MONS.computeIfAbsent(key(side, name), k -> {
            BattleRecap.Mon m = new BattleRecap.Mon();
            m.side = side;
            m.name = name;
            return m;
        });
    }

    private static void tick() {
        ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
        if (battle != trackedBattle) {
            if (trackedBattle != null && battle == null) {
                finish();
            }

            trackedBattle = battle;
            MONS.clear();
            RATIO.clear();
            LEARNED.clear();
            lastMoverKey = null;
            lastMoverSide = null;
            turns = 0;
            result = null;
            opponentName = "";
            startedAt = System.currentTimeMillis();
            if (battle != null) {
                try {
                    List<String> names = new ArrayList<>();

                    for (ClientBattleActor actor : BattleSideHelper.getOpponentSide(battle).getActors()) {
                        names.add(actor.getDisplayName().getString());
                    }

                    opponentName = String.join(" & ", names);
                } catch (Throwable var4) {
                }
            }
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

    public static void onMessage(String key, Object[] args) {
        if (key != null && key.startsWith("cobblemon.battle.")) {
            try {
                tick();
                String k = key.substring("cobblemon.battle.".length());
                switch (k) {
                    case "turn":
                        turns++;
                        break;
                    case "used_move":
                    case "used_move_on":
                        String[] wx = who(args, 0);
                        if (wx != null) {
                            lastMoverKey = key(wx[0], wx[1]);
                            lastMoverSide = wx[0];
                            mon(wx[0], wx[1]).movesUsed++;
                        }
                        break;
                    case "fainted":
                        String[] w = who(args, 0);
                        if (w != null) {
                            mon(w[0], w[1]).fainted = true;
                            if (lastMoverKey != null && lastMoverSide != null && !lastMoverSide.equals(w[0])) {
                                BattleRecap.Mon killer = MONS.get(lastMoverKey);
                                if (killer != null) {
                                    killer.kos++;
                                }
                            }
                        }
                        break;
                    case "win":
                        String winner = args != null && args.length > 0 && args[0] instanceof class_2561 t ? t.getString() : "";
                        String me = "";

                        try {
                            me = class_310.method_1551().field_1724.method_5477().getString();
                        } catch (Throwable var8) {
                        }

                        result = !me.isEmpty() && winner.contains(me) ? "win" : "loss";
                        break;
                    case "forfeit":
                        if (result == null) {
                            result = "forfeit";
                        }
                        break;
                    case "flee":
                        if (result == null) {
                            result = "fled";
                        }
                        break;
                    default:
                        if (k.startsWith("damage.") || k.startsWith("heal.") || k.startsWith("sidestart") || k.startsWith("weather")) {
                            lastMoverKey = null;
                            lastMoverSide = null;
                        }
                }
            } catch (Throwable var9) {
            }
        }
    }

    public static void onHealth(ActiveClientBattlePokemon active, float newHealth) {
        try {
            tick();
            ClientBattlePokemon bp = active.getBattlePokemon();
            if (bp == null) {
                return;
            }

            ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
            boolean opponent = false;

            for (ActiveClientBattlePokemon a : BattleSideHelper.getOpponentSide(battle).getActiveClientBattlePokemon()) {
                if (a == active) {
                    opponent = true;
                }
            }

            String side = opponent ? "opponent" : "player";
            String name = bp.getSpecies().getName();
            String k = key(side, name);
            float max = bp.getMaxHp();
            float ratio = bp.isHpFlat() ? (max > 0.0F ? newHealth / max : 0.0F) : (newHealth > 1.0001F ? newHealth / 100.0F : newHealth);
            Float before = RATIO.get(k);
            if (before == null) {
                float cur = bp.getHpValue();
                before = bp.isHpFlat() ? (max > 0.0F ? cur / max : 1.0F) : (cur > 1.0001F ? cur / 100.0F : cur);
            }

            RATIO.put(k, ratio);
            float delta = before - ratio;
            if (delta > 0.001F) {
                BattleRecap.Mon target = mon(side, name);
                target.display = bp.getDisplayName().getString();
                target.taken += delta * 100.0;
                if (lastMoverKey != null && lastMoverSide != null && !lastMoverSide.equals(side)) {
                    BattleRecap.Mon attacker = MONS.get(lastMoverKey);
                    if (attacker != null) {
                        attacker.dealt += delta * 100.0;
                    }
                }
            }
        } catch (Throwable var14) {
        }
    }

    static void noteInfo(String speciesKey, String name, BuildInference.Info info) {
        try {
            if (info != null) {
                LEARNED.put(
                    speciesKey,
                    new BattleRecap.Learned(
                        name,
                        info.revealedItem,
                        info.revealedAbility != null ? FormAbilityHelper.abilityDisplayNameFromId(norm(info.revealedAbility)) : null,
                        new ArrayList<>(info.revealedMoves),
                        info.candidates.size(),
                        info.all.size()
                    )
                );
            }
        } catch (Throwable var4) {
        }
    }

    private static void finish() {
        if (ExtrasPlusConfig.get().battleRecap && !MONS.isEmpty()) {
            List<BattleRecap.Mon> mine = new ArrayList<>();
            List<BattleRecap.Mon> theirs = new ArrayList<>();
            Map<String, BattleRecap.Mon> merged = new LinkedHashMap<>();

            for (BattleRecap.Mon m : MONS.values()) {
                merged.putIfAbsent(m.side + "_" + norm(m.name), m);
            }

            for (BattleRecap.Mon m : merged.values()) {
                ("player".equals(m.side) ? mine : theirs).add(m);
            }

            boolean allTheirsFainted = !theirs.isEmpty() && theirs.stream().allMatch(m -> m.fainted);
            boolean allMineFainted = !mine.isEmpty() && mine.stream().allMatch(m -> m.fainted);
            if (allTheirsFainted && !allMineFainted) {
                result = "win";
            } else if (allMineFainted && !allTheirsFainted) {
                result = "loss";
            }

            for (BattleRecap.Mon m : merged.values()) {
                if (m.display != null && !m.display.isBlank()) {
                    m.name = m.display;
                } else if (m.name != null && !m.name.isEmpty()) {
                    m.name = Character.toUpperCase(m.name.charAt(0)) + m.name.substring(1);
                }
            }

            mine.sort((a, b) -> Double.compare(b.dealt, a.dealt));
            theirs.sort((a, b) -> Double.compare(b.dealt, a.dealt));
            String title;
            int color;
            if ("win".equals(result)) {
                title = "VICTORY";
                color = -10496;
            } else if ("loss".equals(result)) {
                title = "DEFEAT";
                color = -38037;
            } else if ("forfeit".equals(result)) {
                title = "FORFEIT";
                color = -4671304;
            } else {
                title = "BATTLE OVER";
                color = -4671304;
            }

            long seconds = (System.currentTimeMillis() - startedAt) / 1000L;
            String subtitle = (opponentName.isEmpty() ? "" : "vs " + opponentName + " · ")
                + turns
                + " turns · "
                + seconds / 60L
                + "m"
                + String.format("%02d", seconds % 60L)
                + "s";
            String saved = save(title, subtitle, mine, theirs, new LinkedHashMap<>(LEARNED));
            showing = new BattleRecap.Recap(title, color, subtitle, mine, theirs, new LinkedHashMap<>(LEARNED), saved);
            shownAt = System.currentTimeMillis();
        }
    }

    private static String save(
        String title, String subtitle, List<BattleRecap.Mon> mine, List<BattleRecap.Mon> theirs, Map<String, BattleRecap.Learned> learned
    ) {
        try {
            Path dir = FabricLoader.getInstance().getGameDir().resolve("cobblemon-battle-logs").resolve("recaps");
            Files.createDirectories(dir);
            String stamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
            String file = "recap_" + stamp + (opponentName.isEmpty() ? "" : "_" + opponentName.replaceAll("[^A-Za-z0-9_-]", "")) + ".txt";
            StringBuilder sb = new StringBuilder();
            sb.append(title).append("  ").append(subtitle).append("\n\n");
            sb.append("YOUR TEAM\n");

            for (BattleRecap.Mon m : mine) {
                sb.append(
                    String.format(
                        Locale.ROOT, "  %-14s dealt %5.1f%%  took %5.1f%%  KOs %d%s%n", m.name, m.dealt, m.taken, m.kos, m.fainted ? "  (fainted)" : ""
                    )
                );
            }

            sb.append("\nTHEIR TEAM\n");

            for (BattleRecap.Mon m : theirs) {
                sb.append(
                    String.format(
                        Locale.ROOT, "  %-14s dealt %5.1f%%  took %5.1f%%  KOs %d%s%n", m.name, m.dealt, m.taken, m.kos, m.fainted ? "  (fainted)" : ""
                    )
                );
                BattleRecap.Learned l = learned.get(norm(m.name));
                if (l != null) {
                    sb.append("      ").append(learnedText(l)).append("\n");
                }
            }

            Files.writeString(dir.resolve(file), sb.toString(), StandardCharsets.UTF_8);
            return "cobblemon-battle-logs/recaps/" + file;
        } catch (Throwable var12) {
            return null;
        }
    }

    static String learnedText(BattleRecap.Learned l) {
        List<String> parts = new ArrayList<>();
        if (l.item() != null) {
            parts.add(l.item());
        }

        if (l.ability() != null) {
            parts.add(l.ability());
        }

        if (!l.moves().isEmpty()) {
            List<String> names = new ArrayList<>();

            for (String id : l.moves()) {
                try {
                    names.add(Moves.getByName(id).getDisplayName().getString());
                } catch (Throwable var6) {
                    names.add(FormAbilityHelper.prettyId(id));
                }
            }

            parts.add(String.join(", ", names));
        }

        if (l.total() > 0) {
            parts.add(l.left() + "/" + l.total() + " builds left");
        }

        return parts.isEmpty() ? "nothing revealed" : String.join(" · ", parts);
    }

    public static void init() {
        Services.EVENTS.registerHudRender((gui, tickDelta) -> {
            try {
                tick();
                render(gui);
            } catch (Throwable var3) {
            }
        });
    }

    private static void render(class_332 gui) {
        BattleRecap.Recap recap = showing;
        if (recap != null) {
            long age = System.currentTimeMillis() - shownAt;
            long life = Math.max(5, ExtrasPlusConfig.get().recapSeconds) * 1000L;
            if (age <= life && CobblemonClient.INSTANCE.getBattle() == null) {
                float opacity = age > life - 1500L ? Math.max(0.0F, (float)(life - age) / 1500.0F) : Math.min(1.0F, (float)age / 250.0F);
                class_310 mc = class_310.method_1551();
                class_327 font = mc.field_1772;
                UiStyle style = UiStyle.current();
                int rows = Math.max(recap.mine().size(), recap.theirs().size());
                int w = 300;
                int h = 34 + rows * 17 + 12;
                int x = (mc.method_22683().method_4486() - w) / 2;
                int y = 18;
                gui.method_51448().method_22903();
                gui.method_51448().method_46416(0.0F, 0.0F, 600.0F);
                style.frame(gui, x, y, x + w, y + h, recap.titleColor() & 16777215, opacity);
                text(gui, font, style.label(recap.title()), x + 8, y + 6, recap.titleColor(), 1.0F, opacity);
                int tw = AttachedPanelRenderer.width(font, style.label(recap.title()), 1.0F);
                text(gui, font, recap.subtitle(), x + 12 + tw, y + 8, -4671304, 0.6F, opacity);
                int colW = (w - 18) / 2;
                int lx = x + 6;
                int rx = x + 12 + colW;
                text(gui, font, style.label("Your team"), lx + 2, y + 20, -8399617, 0.55F, opacity);
                text(gui, font, style.label("Their team"), rx + 2, y + 20, -25990, 0.55F, opacity);
                BattleRecap.Mon mvp = null;

                for (BattleRecap.Mon m : recap.mine()) {
                    if (mvp == null || m.dealt > mvp.dealt) {
                        mvp = m;
                    }
                }

                int ry = y + 29;

                for (int i = 0; i < rows; i++) {
                    if (i < recap.mine().size()) {
                        row(gui, font, style, recap.mine().get(i), null, lx, ry, colW, recap.mine().get(i) == mvp && mvp.dealt > 0.0, opacity);
                    }

                    if (i < recap.theirs().size()) {
                        BattleRecap.Mon mx = recap.theirs().get(i);
                        row(gui, font, style, mx, recap.learned().get(norm(mx.name)), rx, ry, colW, false, opacity);
                    }

                    ry += 17;
                }

                if (recap.savedAs() != null) {
                    text(gui, font, "Saved to " + recap.savedAs(), x + 8, y + h - 9, -7697782, 0.45F, opacity);
                }

                gui.method_51448().method_22909();
            } else {
                showing = null;
            }
        }
    }

    private static void row(
        class_332 gui, class_327 font, UiStyle style, BattleRecap.Mon m, BattleRecap.Learned learned, int x, int y, int w, boolean mvp, float opacity
    ) {
        style.section(gui, x, y, x + w, y + 15, m.fainted ? 16739179 : ("player".equals(m.side) ? 8377599 : 16751226), UiStyle.Accent.LEFT, opacity);
        String name = (mvp ? "★ " : "") + m.name;
        text(gui, font, AttachedPanelRenderer.trim(font, name, w / 2, 0.6F), x + 4, y + 2, mvp ? -10496 : (m.fainted ? -6645094 : -1), 0.6F, opacity);
        String stats = Math.round(m.dealt) + "% dealt" + (m.kos > 0 ? " · " + m.kos + " KO" : "") + (m.fainted ? " · fainted" : "");
        int sw = AttachedPanelRenderer.width(font, stats, 0.45F);
        text(gui, font, stats, x + w - sw - 3, y + 3, m.kos > 0 ? -21931 : -4671304, 0.45F, opacity);
        String sub = learned != null ? learnedText(learned) : (m.movesUsed > 0 ? m.movesUsed + " moves used" : "");
        text(gui, font, AttachedPanelRenderer.trim(font, sub, w - 8, 0.42F), x + 4, y + 9, learned != null ? -6305568 : -7697782, 0.42F, opacity);
    }

    private static void text(class_332 gui, class_327 font, String s, int x, int y, int color, float scale, float opacity) {
        int alpha = Math.max(4, Math.round(255.0F * opacity));
        UiStyle style = UiStyle.current();
        int c = style.text(color) & 16777215 | alpha << 24;
        gui.method_51448().method_22903();
        gui.method_51448().method_46416(x, y, 0.0F);
        gui.method_51448().method_22905(scale, scale, 1.0F);
        UltraFont.draw(gui, font, s, 0, 0, c, style.shadow());
        gui.method_51448().method_22909();
    }

    record Learned(String name, String item, String ability, List<String> moves, int left, int total) {
    }

    static final class Mon {
        String side;
        String name;
        String display;
        double dealt;
        double taken;
        int kos;
        boolean fainted;
        int movesUsed;
    }

    record Recap(
        String title,
        int titleColor,
        String subtitle,
        List<BattleRecap.Mon> mine,
        List<BattleRecap.Mon> theirs,
        Map<String, BattleRecap.Learned> learned,
        String savedAs
    ) {
    }
}
