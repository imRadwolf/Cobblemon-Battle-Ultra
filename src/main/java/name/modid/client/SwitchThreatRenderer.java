package name.modid.client;

import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.battle.ActiveClientBattlePokemon;
import com.cobblemon.mod.common.client.battle.ClientBattle;
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.mojang.blaze3d.systems.RenderSystem;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.class_310;
import net.minecraft.class_327;
import net.minecraft.class_332;

public final class SwitchThreatRenderer {
    private static SwitchThreatRenderer.Prepared pending;
    private static int[] baseBox;
    private static boolean baseFancy;
    private static boolean redrawing;
    private static Method fancyBox;
    static final float TITLE_SCALE = 0.9F;
    static final float SCALE = 0.8F;
    static final int LINE_H = 10;
    static final int LEFT_COL = 86;
    private static boolean tileLeftColumn;
    private static boolean shifted;

    private SwitchThreatRenderer() {
    }

    private static Pokemon pokemonOf(Object tile) {
        for (String name : new String[]{"getPokemon", "pokemon"}) {
            try {
                Method m = tile.getClass().getMethod(name);
                if (m.invoke(tile) instanceof Pokemon p) {
                    return p;
                }
            } catch (Throwable var8) {
            }
        }

        return null;
    }

    static String norm(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    public static void begin(Object tile, int tileX, int tileW) {
        baseBox = null;

        try {
            tileLeftColumn = tileX + tileW / 2 < class_310.method_1551().method_22683().method_4486() / 2;
        } catch (Throwable var5) {
        }

        pending = null;

        try {
            pending = prepare(tile);
        } catch (Throwable var4) {
        }
    }

    public static int widthFor(int baseWidth) {
        return pending == null ? baseWidth : Math.max(baseWidth, pending.width());
    }

    public static boolean tileLeftColumn() {
        return tileLeftColumn;
    }

    public static void pushShift(class_332 gui, int shift) {
        if (!shifted) {
            gui.method_51448().method_22903();
            gui.method_51448().method_46416(-shift, 0.0F, 0.0F);
            shifted = true;
        }
    }

    public static boolean isRedrawing() {
        return redrawing;
    }

    public static void recordBaseBox(int x, int y, int w, int h, boolean fancy) {
        baseBox = new int[]{x, y, w, h};
        baseFancy = fancy;
    }

    public static void drawFancyBox(class_332 gui, int x, int y, int w, int h) {
        try {
            if (fancyBox == null) {
                fancyBox = Class.forName("name.modid.client.CustomBattleController")
                    .getDeclaredMethod("drawFancySwitchTooltipBox", class_332.class, int.class, int.class, int.class, int.class);
                fancyBox.setAccessible(true);
            }

            redrawing = true;
            fancyBox.invoke(null, gui, x, y, w, h);
        } catch (Throwable var9) {
            UiStyle.current().frame(gui, x, y, x + w, y + h, 0, 1.0F);
        } finally {
            redrawing = false;
        }
    }

    public static void finish(class_332 gui, Object tile, int tileX, int tileY, int tileW, int tileH) {
        if (shifted) {
            shifted = false;
            gui.method_51448().method_22909();
        }

        SwitchThreatRenderer.Prepared p = pending;
        pending = null;
        if (p != null) {
            class_310 mc = class_310.method_1551();
            int screenW = mc.method_22683().method_4486();
            int screenH = mc.method_22683().method_4502();
            int[] box = baseBox;
            baseBox = null;
            int x;
            int y;
            int w;
            boolean fancy;
            if (box != null) {
                boolean leftColumn = tileX + tileW / 2 < screenW / 2;
                w = Math.max(box[2], p.width());
                x = leftColumn ? box[0] + box[2] - w : box[0];
                if (box[1] + box[3] - 1 + p.height() <= screenH - 2) {
                    y = box[1] + box[3] - 1;
                } else if (box[1] - p.height() + 1 >= 2) {
                    y = box[1] - p.height() + 1;
                } else {
                    y = Math.max(2, screenH - 2 - p.height());
                }

                fancy = baseFancy;
            } else {
                w = p.width();
                boolean leftColumn = tileX + tileW / 2 < screenW / 2;
                x = leftColumn ? tileX - w - 8 : tileX + tileW + 8;
                y = tileY + tileH / 2 - p.height() / 2;
                fancy = true;
            }

            x = Math.max(2, Math.min(screenW - 2 - w, x));
            y = Math.max(2, Math.min(screenH - 2 - p.height(), y));
            draw(gui, p, x, y, w, fancy);
        }
    }

    public static void render(class_332 gui, Object tile, int tileX, int tileY, int tileW, int tileH) {
        begin(tile, tileX, tileW);
        finish(gui, tile, tileX, tileY, tileW, tileH);
    }

    private static SwitchThreatRenderer.Prepared prepare(Object tile) {
        if (ExtrasPlusConfig.get().switchThreatBox && tile != null) {
            Pokemon pokemon = pokemonOf(tile);
            ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
            if (pokemon != null && battle != null) {
                ClientBattlePokemon opponent = null;

                for (ActiveClientBattlePokemon active : BattleSideHelper.getOpponentSide(battle).getActiveClientBattlePokemon()) {
                    if (active != null && active.getBattlePokemon() != null) {
                        opponent = active.getBattlePokemon();
                        break;
                    }
                }

                List<SwitchThreatRenderer.Line> lines = new ArrayList<>();
                String title;
                if (opponent != null) {
                    ThreatCalc.Report report = ThreatCalc.compute(opponent, pokemon, null, pokemon.getForm());
                    title = "If " + opponent.getDisplayName().getString() + " attacks " + pokemon.getDisplayName(false).getString();
                    if (report != null) {
                        int shown = 0;

                        for (ThreatCalc.Threat threat : report.ranked) {
                            if (shown >= 4) {
                                break;
                            }

                            boolean seen = !threat.revealed() && "»".equals(threat.note());
                            String likely = threat.revealed()
                                ? "used"
                                : (seen ? "» seen" : threat.builds() + "/" + Math.max(threat.builds(), threat.totalBuilds()));
                            lines.add(
                                new SwitchThreatRenderer.Line(
                                    threat.name(),
                                    0xFF000000 | AttachedPanelRenderer.typeColor(threat.typeName()),
                                    threat.maxPct() == 0 ? (threat.note() != null && !seen ? "immune" : "0%") : threat.pctText() + "  " + threat.ko(),
                                    0xFF000000 | ThreatCalc.pctColor(threat.maxPct()),
                                    likely,
                                    threat.revealed() ? -1513240 : (seen ? -3692801 : -6645094)
                                )
                            );
                            shown++;
                        }

                        if (report.ranked.isEmpty()) {
                            lines.add(new SwitchThreatRenderer.Line("No damaging move known or likely", -6645094, "", 0, "", 0));
                        }
                    }

                    SpeedTiers.Report speed = SpeedTiers.lastReport;
                    if (speed != null && !speed.builds.isEmpty() && System.currentTimeMillis() - SpeedTiers.lastReportTime < 5000L) {
                        int mine = pokemon.getStat(Stats.SPEED);
                        if (pokemon.heldItem() != null && norm(pokemon.heldItem().method_7964().getString()).equals("choicescarf")) {
                            mine = (int)Math.floor(mine * 1.5);
                        }

                        if (BattleMessageSubscriber.getTailwindTurns("player") > 0) {
                            mine *= 2;
                        }

                        int faster = 0;

                        for (SpeedTiers.Tier tier : speed.builds) {
                            if (speed.trickRoom ? mine < tier.speed() : mine > tier.speed()) {
                                faster++;
                            }
                        }

                        int total = speed.builds.size();
                        int color = faster == total ? -10755206 : (faster == 0 ? -41892 : -10166);
                        String verdict = faster == total
                            ? "outspeeds all builds"
                            : (faster == 0 ? "slower than all builds" : "outspeeds " + faster + "/" + total + " builds");
                        lines.add(new SwitchThreatRenderer.Line("Speed " + mine, -34362, verdict, color, "", 0));
                    }
                } else {
                    title = pokemon.getDisplayName(false).getString();
                }

                SwitchAdvisor.Pick pick = SwitchAdvisor.best();
                if (pick != null) {
                    if (pick.uuid().equals(pokemon.getUuid())) {
                        lines.add(0, new SwitchThreatRenderer.Line("★ Best switch-in", -10496, pick.reason(), -10496, "", 0));
                    } else {
                        lines.add(new SwitchThreatRenderer.Line("Best switch-in", -6645094, pick.name() + " (" + pick.reason() + ")", -3559328, "", 0));
                    }
                }

                SwitchThreatRenderer.Line hazard = hazardLine(pokemon);
                if (hazard != null) {
                    lines.add(hazard);
                }

                if (lines.isEmpty()) {
                    return null;
                } else {
                    class_327 font = class_310.method_1551().field_1772;
                    int w = Math.max(150, AttachedPanelRenderer.width(font, title, 0.9F) + 14);

                    for (SwitchThreatRenderer.Line line : lines) {
                        int lw = 86 + AttachedPanelRenderer.width(font, line.mid(), 0.8F) + 8 + AttachedPanelRenderer.width(font, line.right(), 0.8F) + 8;
                        w = Math.max(w, lw);
                    }

                    w = Math.min(w, 280);
                    int h = 16 + lines.size() * 10 + 4;
                    return new SwitchThreatRenderer.Prepared(title, lines, w, h);
                }
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    private static void draw(class_332 gui, SwitchThreatRenderer.Prepared p, int x, int y, int w, boolean fancy) {
        class_327 font = class_310.method_1551().field_1772;
        int h = p.height();
        gui.method_51448().method_22903();
        gui.method_51448().method_46416(0.0F, 0.0F, 450.0F);
        if (fancy) {
            drawFancyBox(gui, x, y, w, h);
        } else {
            UiStyle.current().frame(gui, x, y, x + w, y + h, 0, 1.0F);
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        AttachedPanelRenderer.text(gui, font, AttachedPanelRenderer.trim(font, p.title(), w - 12, 0.9F), x + 6, y + 5, -10496, 0.9F);
        int ly = y + 16;

        for (SwitchThreatRenderer.Line line : p.lines()) {
            AttachedPanelRenderer.text(gui, font, AttachedPanelRenderer.trim(font, line.left(), 76, 0.8F), x + 6, ly, line.leftColor(), 0.8F);
            if (!line.mid().isEmpty()) {
                AttachedPanelRenderer.text(
                    gui,
                    font,
                    AttachedPanelRenderer.trim(font, line.mid(), w - 86 - 12 - AttachedPanelRenderer.width(font, line.right(), 0.8F), 0.8F),
                    x + 86,
                    ly,
                    line.midColor(),
                    0.8F
                );
            }

            if (!line.right().isEmpty()) {
                int rw = AttachedPanelRenderer.width(font, line.right(), 0.8F);
                AttachedPanelRenderer.text(gui, font, line.right(), x + w - 6 - rw, ly, line.rightColor(), 0.8F);
            }

            ly += 10;
        }

        gui.method_51448().method_22909();
    }

    static SwitchThreatRenderer.Line hazardLine(Pokemon pokemon) {
        Map<String, Integer> hazards = BattleMessageSubscriber.getHazardsForSide("player");
        if (hazards != null && !hazards.isEmpty()) {
            String item = "";
            String ability = "";
            List<String> types = new ArrayList<>();

            try {
                item = pokemon.heldItem() != null && !pokemon.heldItem().method_7960() ? norm(pokemon.heldItem().method_7964().getString()) : "";
                ability = norm(pokemon.getAbility().getTemplate().getName());

                for (ElementalType type : FormAbilityHelper.types(pokemon.getForm())) {
                    types.add(norm(type.getName()));
                }
            } catch (Throwable var14) {
            }

            if (item.equals("heavydutyboots")) {
                return new SwitchThreatRenderer.Line("Hazards", -2056096, "none (Heavy-Duty Boots)", -8592501, "", 0);
            } else {
                boolean magicGuard = ability.equals("magicguard");
                boolean grounded = !types.contains("flying") && !ability.equals("levitate") && !item.equals("airballoon");
                double pct = 0.0;
                List<String> effects = new ArrayList<>();
                if (hazards.getOrDefault("Stealth Rock", 0) > 0 && !magicGuard) {
                    double eff = 1.0;

                    for (String t : types) {
                        eff *= TypeChart.getEffectivenessAgainstTypes("rock", t, null);
                    }

                    pct += 12.5 * eff;
                }

                int spikes = hazards.getOrDefault("Spikes", 0);
                if (spikes > 0 && grounded && !magicGuard) {
                    pct += spikes >= 3 ? 25.0 : (spikes == 2 ? 16.67 : 12.5);
                }

                int toxic = hazards.getOrDefault("Toxic Spikes", 0);
                if (toxic > 0 && grounded && !types.contains("poison") && !types.contains("steel")) {
                    effects.add(toxic >= 2 ? "badly poisoned" : "poisoned");
                } else if (toxic > 0 && grounded && types.contains("poison")) {
                    effects.add("absorbs Toxic Spikes");
                }

                if (hazards.getOrDefault("Sticky Web", 0) > 0 && grounded) {
                    effects.add("−1 Spe");
                }

                if (pct <= 0.0 && effects.isEmpty()) {
                    return new SwitchThreatRenderer.Line("Hazards", -2056096, "no effect", -8592501, "", 0);
                } else {
                    String text = (pct > 0.0 ? "−" + Math.round(pct) + "% on entry" : "")
                        + (effects.isEmpty() ? "" : (pct > 0.0 ? ", " : "") + String.join(", ", effects));
                    int color = pct >= 25.0 ? -41892 : (pct > 0.0 ? -21931 : -10166);
                    return new SwitchThreatRenderer.Line("Hazards", -2056096, text, color, "", 0);
                }
            }
        } else {
            return null;
        }
    }

    private record Line(String left, int leftColor, String mid, int midColor, String right, int rightColor) {
    }

    private record Prepared(String title, List<SwitchThreatRenderer.Line> lines, int width, int height) {
    }
}
