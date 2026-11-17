package name.modid.client;

import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.class_1799;
import net.minecraft.class_310;
import net.minecraft.class_327;
import net.minecraft.class_332;

public final class AttachedPanelRenderer {
    public static final int WIDTH = 132;
    private static final String[] TYPE_ORDER = new String[]{
        "normal",
        "fire",
        "water",
        "grass",
        "electric",
        "ice",
        "fighting",
        "poison",
        "ground",
        "flying",
        "psychic",
        "bug",
        "rock",
        "ghost",
        "dragon",
        "dark",
        "steel",
        "fairy"
    };
    public static final int MEMORY = -3692801;
    private static final int[] STAT_ACCENTS = new int[]{-30117, -11429, -9459457, -8592501, -34362};

    private AttachedPanelRenderer() {
    }

    public static int height(PanelData data) {
        return 88 + (data != null && data.modifiers != null && !data.modifiers.isEmpty() ? 11 : 0) + speedExtra(data);
    }

    static int speedExtra(PanelData data) {
        return data != null && !data.playerSide ? 8 : 0;
    }

    public static void render(class_332 gui, PanelData data, SpeedTiers.Report speedReport, List<PinnedHoverZone> zones, float opacity) {
        class_310 mc = class_310.method_1551();
        class_327 font = mc.field_1772;
        int w = 132;
        int h = height(data);
        UiStyle style = UiStyle.current();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        gui.method_51448().method_22903();
        gui.method_51448().method_46416(0.0F, 0.0F, 300.0F);
        style.frame(gui, 0, 0, w, h, 0, opacity);
        int y = 2;
        int ability = data.abilityColor;
        boolean showLikely = !data.playerSide
            && ExtrasPlusConfig.get().showLikelyOpponentInfo
            && data.inference != null
            && !data.inference.candidates.isEmpty();
        String rawAbility = data.abilityText == null ? "???" : data.abilityText;
        String abilityFraction = null;
        boolean abilityUnknown = ability == 11579568 || ability == 9342606 || "???".equals(rawAbility);
        if (!data.playerSide && abilityUnknown && data.inference != null && data.inference.rememberedAbility != null) {
            rawAbility = "» " + FormAbilityHelper.abilityDisplayNameFromId(SmogonSets.norm(data.inference.rememberedAbility));
            abilityFraction = "seen";
            ability = 13084415;
        } else if (showLikely && abilityUnknown) {
            String topAbility = data.inference.topAbility();
            if (topAbility != null) {
                int n = data.inference.abilityCount.getOrDefault(topAbility, 0);
                rawAbility = FormAbilityHelper.abilityDisplayNameFromId(SmogonSets.norm(topAbility)) + "?";
                if (rawAbility.equals("null?")) {
                    rawAbility = topAbility + "?";
                }

                abilityFraction = n + "/" + data.inference.candidates.size();
                ability = 13222560;
            }
        }

        String abilityText = trim(font, rawAbility, abilityFraction != null ? 60 - width(font, abilityFraction, 0.36F) - 3 : 60, 0.6F);
        style.section(gui, 2, y, 68, y + 10, ability, UiStyle.Accent.LEFT, opacity);
        text(gui, font, abilityText, 5, y + 3, data.abilitySuppressed ? -7697782 : 0xFF000000 | ability, 0.6F);
        if (data.abilitySuppressed) {
            gui.method_25294(4, y + 5, 5 + width(font, abilityText, 0.6F) + 1, y + 6, op(-38037, opacity));
        }

        if (abilityFraction != null) {
            int fw = width(font, abilityFraction, 0.36F);
            text(gui, font, abilityFraction, 67 - fw, y + 6, "seen".equals(abilityFraction) ? -6518588 : -6645094, 0.36F);
        }

        if (data.abilityId != null && !data.abilityId.isBlank()) {
            zones.add(new PinnedHoverZone(2, y, 68, y + 10, "ability", data.abilityId));
        }

        style.section(gui, 70, y, w - 2, y + 10, 0, UiStyle.Accent.NONE, opacity);
        int itemTextX = 72;
        if (data.itemStack != null && !data.itemStack.method_7960()) {
            gui.method_51448().method_22903();
            gui.method_51448().method_46416(72.0F, y + 1, 0.0F);
            gui.method_51448().method_22905(0.5F, 0.5F, 1.0F);
            gui.method_51427(data.itemStack, 0, 0);
            gui.method_51448().method_22909();
            itemTextX = 82;
        }

        int itemColor = data.itemLost ? -7697782 : (data.itemKnown ? (data.itemConsumed ? -6645094 : -1513240) : -7434610);
        String rawItem = data.itemText == null ? "???" : data.itemText;
        String itemFraction = null;
        String rememberedItem = !data.playerSide && data.inference != null && !data.itemKnown && !data.itemLost ? data.inference.rememberedItem : null;
        String deducedItem = !data.playerSide && data.inference != null && !data.itemKnown && !data.itemLost ? data.inference.inferredItem : null;
        if (rememberedItem != null || (showLikely || deducedItem != null) && !data.itemKnown && !data.itemLost && data.inference != null) {
            String topItem = deducedItem != null ? deducedItem : (rememberedItem != null ? rememberedItem : data.inference.topItem());
            if (topItem != null) {
                if (deducedItem != null) {
                    rawItem = topItem + "?";
                    itemFraction = "deduced";
                    itemColor = -15770;
                    zones.add(
                        new PinnedHoverZone(
                            70, y, w - 2, y + 10, "item", "Deduced: " + topItem + " — " + data.inference.inferredReason, ItemLookup.byDisplayName(topItem)
                        )
                    );
                } else if (rememberedItem != null) {
                    rawItem = "» " + topItem;
                    itemFraction = "seen";
                    itemColor = -3692801;
                } else {
                    rawItem = topItem + "?";
                    itemFraction = data.inference.itemCount.getOrDefault(topItem, 0) + "/" + data.inference.candidates.size();
                    itemColor = -3554656;
                }

                class_1799 likelyStack = ItemLookup.byDisplayName(topItem);
                if (likelyStack != null && !likelyStack.method_7960() && itemTextX == 72) {
                    gui.method_51448().method_22903();
                    gui.method_51448().method_46416(72.0F, y + 1, 0.0F);
                    gui.method_51448().method_22905(0.5F, 0.5F, 1.0F);
                    gui.method_51427(likelyStack, 0, 0);
                    gui.method_51448().method_22909();
                    gui.method_25294(72, y + 1, 80, y + 9, op(style.dim(), opacity));
                    itemTextX = 82;
                }
            }
        }

        String shownItem = trim(font, rawItem, w - 3 - itemTextX - (itemFraction != null ? width(font, itemFraction, 0.36F) + 3 : 0), 0.55F);
        if (itemFraction != null) {
            int fw = width(font, itemFraction, 0.36F);
            text(gui, font, itemFraction, w - 3 - fw, y + 6, deducedItem != null ? -2054064 : (rememberedItem != null ? -6518588 : -6645094), 0.36F);
        }

        text(gui, font, shownItem, itemTextX, y + 3, itemColor, 0.55F);
        if (data.itemLost) {
            int iw = width(font, shownItem, 0.55F);
            gui.method_25294(itemTextX - 1, y + 5, itemTextX + iw + 1, y + 6, op(-38037, opacity));
            if (data.itemLostReason != null) {
                String reason = data.itemLostReason;
                int rw = width(font, reason, 0.36F);
                text(gui, font, reason, w - 3 - rw, y + 7, -30070, 0.36F);
            }
        }

        if (data.itemText != null && data.itemKnown && !data.itemConsumed) {
            String itemTip = data.itemLost ? data.itemText + " — " + (data.itemLostReason != null ? data.itemLostReason : "lost") : data.itemText;
            zones.add(new PinnedHoverZone(70, y, w - 2, y + 10, "item", itemTip, data.itemLost ? null : data.itemStack));
        }

        y += 12;
        int cellW = (w - 4) / 4;

        for (int i = 0; i < 4; i++) {
            int cx = 2 + i * cellW;
            style.section(gui, cx, y, cx + cellW - 1, y + 13, STAT_ACCENTS[i] & 16777215, UiStyle.Accent.TOP, opacity);
            text(gui, font, style.label(data.statLabels[i]), cx + 2, y + 3, op(-4671304, opacity), 0.42F);
            String value = data.statValues[i] == null ? "?" : data.statValues[i];
            float scale = value.length() > 7 ? 0.5F : 0.6F;
            text(gui, font, value, cx + 2, y + 7, 0xFF000000 | data.statColors[i], scale);
        }

        y += 15;
        renderSpeedBar(gui, font, data, speedReport, 2, y, w - 4, zones, opacity);
        y += 18 + speedExtra(data);
        int chipW = (w - 6) / 2;
        List<String> likely = new ArrayList<>();
        if ((showLikely || !data.playerSide && data.inference != null && !data.inference.rememberedMoves.isEmpty()) && data.moves.size() < 4) {
            Set<String> shownIds = new HashSet<>();

            for (PanelData.MoveRow row : data.moves) {
                if (row.id() != null) {
                    shownIds.add(SmogonSets.norm(row.id()));
                }
            }

            for (String id : data.inference.likelyMoves()) {
                if (!shownIds.contains(id) && likely.size() < 4 - data.moves.size() && (showLikely || data.inference.rememberedMoves.contains(id))) {
                    likely.add(id);
                }
            }
        }

        for (int i = 0; i < 4; i++) {
            int cx = 2 + i % 2 * (chipW + 2);
            int cy = y + i / 2 * 13;
            PanelData.MoveRow move = i < data.moves.size() ? data.moves.get(i) : null;
            String likelyId = move == null && i - data.moves.size() < likely.size() ? likely.get(i - data.moves.size()) : null;
            if (move == null && likelyId != null) {
                MoveTemplate template = Moves.getByName(likelyId);
                String typeName = template != null ? template.getElementalType().getName() : "normal";
                if (template != null && data.battlePokemon != null) {
                    typeName = MovePower.opponentMoveType(likelyId, typeName, data.battlePokemon);
                }

                String name = template != null ? template.getDisplayName().getString() : likelyId;
                int typeColor = typeColor(typeName);
                boolean remembered = data.inference.rememberedMoves.contains(likelyId);
                int count = data.inference.moveCount.getOrDefault(likelyId, 0);
                String fraction = remembered ? "seen" : count + "/" + data.inference.candidates.size();
                int fw = width(font, fraction, 0.42F);
                if (remembered) {
                    style.moveChip(gui, cx, cy, cx + chipW, cy + 12, typeColor, UiStyle.Chip.MEMORY, false, opacity);
                    text(gui, font, trim(font, "» " + name, chipW - 6, 0.55F), cx + 4, cy + 2, -1647873, 0.55F);
                    text(gui, font, fraction, cx + chipW - fw - 2, cy + 7, -3692801, 0.42F);
                } else {
                    style.moveChip(gui, cx, cy, cx + chipW, cy + 12, typeColor, UiStyle.Chip.LIKELY, false, opacity);
                    text(gui, font, trim(font, name + "?", chipW - 8, 0.55F), cx + 4, cy + 2, -4671304, 0.55F);
                    text(gui, font, fraction, cx + chipW - fw - 2, cy + 7, -7434610, 0.42F);
                }

                ThreatCalc.Threat threat = data.threats != null ? data.threats.byMove.get(likelyId) : null;
                drawThreat(gui, font, threat, cx + 4, cy + 7);
                zones.add(
                    new PinnedHoverZone(
                        cx, cy, cx + chipW, cy + 12, "move", likelyId, threat != null ? threat : (remembered ? "likely:seen" : "likely:" + fraction)
                    )
                );
            } else if (move == null) {
                style.moveChip(gui, cx, cy, cx + chipW, cy + 12, 4473924, UiStyle.Chip.EMPTY, false, opacity);
                text(gui, font, "???", cx + 4, cy + 4, op(-8750470, opacity), 0.55F);
            } else {
                int typeColor = typeColor(move.typeName());
                String ppText = move.ppText() == null ? "" : move.ppText();
                int ppColor = 0xFF000000 | move.ppColor();
                if (!data.playerSide && move.id() != null && ExtrasPlusConfig.get().opponentPpTracking) {
                    // opponents are assumed to run PP Max: PP Max minus the uses seen
                    String sp = data.species != null ? data.species.getName() : (data.battlePokemon != null ? data.battlePokemon.getSpecies().getName() : null);
                    int[] pp = TeamStatusTracker.theirPp(sp, move.id());
                    if (pp != null) {
                        ppText = pp[0] + "/" + pp[1];
                        ppColor = BattleCounters.ppColor(pp[0], pp[1]);
                    }
                }

                int ppW = width(font, ppText, 0.42F);
                MoveAdvisor.Eval eval = data.playerSide && !data.bench && move.id() != null ? MoveAdvisor.evalFor(move.id()) : null;
                MoveAdvisor.Advice advice = eval != null ? MoveAdvisor.current() : null;
                boolean best = advice != null && eval.moveId().equals(advice.bestId);
                style.moveChip(gui, cx, cy, cx + chipW, cy + 12, typeColor, UiStyle.Chip.REVEALED, best, opacity);
                String name = best ? "★ " + move.name() : move.name();
                text(gui, font, trim(font, name, chipW - 6, 0.55F), cx + 4, cy + 2, best ? -10496 : -1, 0.55F);
                text(gui, font, ppText, cx + chipW - ppW - 2, cy + 7, ppColor, 0.42F);
                if (eval != null && eval.damaging()) {
                    String ko = eval.koSure() > 0 && eval.koSure() >= Math.max(1, eval.builds()) ? " KO" : (eval.koAny() > 0 ? " KO?" : "");
                    String dmg = eval.immune() ? "immune" : eval.minPct() + "-" + eval.maxPct() + "%" + ko;
                    text(gui, font, dmg, cx + 4, cy + 7, 0xFF000000 | ThreatCalc.pctColor(eval.maxPct()), 0.42F);
                }

                ThreatCalc.Threat threat = !data.playerSide && data.threats != null && move.id() != null
                    ? data.threats.byMove.get(SmogonSets.norm(move.id()))
                    : null;
                drawThreat(gui, font, threat, cx + 4, cy + 7);
                if (move.id() != null) {
                    zones.add(new PinnedHoverZone(cx, cy, cx + chipW, cy + 12, "move", move.id(), eval != null ? eval : threat));
                }
            }
        }

        y += 27;
        renderWeaknesses(gui, font, data.types, 2, y, w - 4, opacity);
        if (data.modifiers != null && !data.modifiers.isEmpty()) {
            renderModifiers(gui, font, data, 2, y + 13, w - 4, zones, opacity);
        }

        gui.method_51448().method_22909();
    }

    private static void renderModifiers(class_332 gui, class_327 font, PanelData data, int x, int y, int w, List<PinnedHoverZone> zones, float opacity) {
        UiStyle style = UiStyle.current();
        style.section(gui, x, y, x + w, y + 9, 0, UiStyle.Accent.NONE, opacity);
        int cx = x + 2;
        int shown = 0;

        for (ModifierChips.Chip chip : data.modifiers) {
            String label = chip.text();
            int cw = width(font, label, 0.4F) + 4;
            int remaining = data.modifiers.size() - shown;
            if (cx + cw > x + w - (remaining > 1 ? 12 : 2)) {
                text(gui, font, "+" + remaining, cx, y + 2, -5723992, 0.4F);
                break;
            }

            int color = chip.color();
            style.pill(gui, cx, y + 1, cx + cw, y + 8, color, opacity);
            text(gui, font, label, cx + 2, y + 2, style.pillText(color), 0.4F);
            cx += cw + 2;
            shown++;
        }

        zones.add(new PinnedHoverZone(x, y, x + w, y + 9, "modifiers", data.playerSide ? "player" : "opponent", data.modifiers));
    }

    private static void drawThreat(class_332 gui, class_327 font, ThreatCalc.Threat threat, int x, int y) {
        if (threat != null && threat.damaging()) {
            String text = threat.maxPct() == 0
                ? (threat.note() != null && !"»".equals(threat.note()) ? "immune" : "0%")
                : threat.minPct() + "-" + threat.maxPct() + "%";
            text(gui, font, text, x, y, 0xFF000000 | ThreatCalc.pctColor(threat.maxPct()), 0.42F);
        }
    }

    private static void renderSpeedBar(
        class_332 gui, class_327 font, PanelData data, SpeedTiers.Report report, int x, int y, int w, List<PinnedHoverZone> zones, float opacity
    ) {
        UiStyle style = UiStyle.current();
        int speedAccent = report != null && report.trickRoom ? 16740312 : 16742854;
        int bh = 16 + speedExtra(data);
        style.section(gui, x, y, x + w, y + bh, report != null && report.baseSpeed > 0 ? speedAccent : 0, UiStyle.Accent.LEFT, opacity);
        if (report != null && report.baseSpeed > 0) {
            if (data.playerSide) {
                renderMySpeed(gui, font, data, report, x, y, w);
            } else {
                renderOpponentSpeed(gui, font, report, x, y, w, opacity);
            }

            zones.add(new PinnedHoverZone(x, y, x + w, y + bh, "speed", "", report));
        } else {
            text(gui, font, style.label("SPEED"), x + 2, y + 2, -34362, 0.45F);
            text(gui, font, "no data", x + 2, y + 9, -7434610, 0.45F);
        }
    }

    private static void renderMySpeed(class_332 gui, class_327 font, PanelData data, SpeedTiers.Report report, int x, int y, int w) {
        String label = UiStyle.current().label(report.trickRoom ? "SPEED (Trick Room)" : "SPEED");
        text(gui, font, label, x + 3, y + 2, report.trickRoom ? -36904 : -34362, 0.42F);
        if (report.mySpeed == null) {
            text(gui, font, "?", x + 3, y + 8, -1513240, 0.6F);
        } else {
            int total = report.builds.size();
            int first = 0;
            int ties = 0;

            for (SpeedTiers.Tier tier : report.builds) {
                int color = SpeedTiers.verdictColor(report, tier.speed()) & 16777215;
                if (color == 6022010) {
                    first++;
                } else if (color == 16767050) {
                    ties++;
                }
            }

            String verdict;
            int color;
            if (total == 0) {
                int lowColor = SpeedTiers.verdictColor(report, report.theoMin) & 16777215;
                int highColor = SpeedTiers.verdictColor(report, report.theoMax) & 16777215;
                if (lowColor == 6022010 && highColor == 6022010) {
                    verdict = "You always move first";
                    color = -10755206;
                } else if (lowColor == 16735324 && highColor == 16735324) {
                    verdict = "They always move first";
                    color = -41892;
                } else {
                    verdict = "Depends on their build";
                    color = -10166;
                }
            } else if (first == total) {
                verdict = "You move first vs all " + total + " sets";
                color = -10755206;
            } else if (first == 0 && ties == 0) {
                verdict = "They move first (all " + total + " sets)";
                color = -41892;
            } else {
                verdict = "You move first vs " + first + "/" + total + " sets" + (ties > 0 ? ", " + ties + " tie" : "");
                color = -10166;
            }

            String value = Integer.toString(report.mySpeed);
            text(gui, font, value, x + 3, y + 8, color, 0.62F);
            int vx = x + 5 + width(font, value, 0.62F);
            text(gui, font, verdict, vx, y + 9, color, 0.45F);
            MoveInsights.PriorityMove priority = MoveInsights.opponentPriority(data.inference);
            if (priority != null) {
                String warn = "⚠ " + priority.name() + " +" + priority.priority() + " (" + priority.likelihood() + ")";
                int rw = width(font, warn, 0.38F);
                text(gui, font, warn, x + w - rw - 2, y + 2, -21931, 0.38F);
            } else {
                String range = "their range " + report.theoMin + "-" + report.theoMax;
                int rw = width(font, range, 0.38F);
                text(gui, font, range, x + w - rw - 2, y + 2, -5723992, 0.38F);
            }
        }
    }

    private static void renderOpponentSpeed(class_332 gui, class_327 font, SpeedTiers.Report report, int x, int y, int w, float opacity) {
        text(gui, font, UiStyle.current().label(report.trickRoom ? "SPEED (TR)" : "SPEED"), x + 3, y + 2, report.trickRoom ? -36904 : -34362, 0.42F);
        StringBuilder head = new StringBuilder("range " + report.theoMin + "-" + report.theoMax);
        if (report.theoMaxScarf > report.theoMax) {
            head.append(" · scarf ").append(report.theoMaxScarf);
        }

        if (report.ruledOut > 0) {
            head.append(" · ").append(report.builds.size()).append('/').append(report.totalBuilds).append(" builds");
        }

        if (report.memoryBattles > 0) {
            head.append(" »");
        }

        String headText = head.toString();
        int hw = width(font, headText, 0.42F);
        text(gui, font, headText, x + w - hw - 2, y + 2, -4671304, 0.42F);
        LinkedHashMap<Integer, SpeedTiers.Tier> unique = new LinkedHashMap<>();
        List<SpeedTiers.Tier> sorted = new ArrayList<>(report.builds);
        sorted.sort((a, b) -> Integer.compare(b.speed(), a.speed()));

        for (SpeedTiers.Tier tier : sorted) {
            unique.putIfAbsent(tier.speed(), tier);
        }

        int lo;
        int hi;
        if (!sorted.isEmpty()) {
            hi = sorted.get(0).speed();
            lo = sorted.get(sorted.size() - 1).speed();
        } else {
            lo = report.theoMin;
            hi = report.theoMax;
        }

        String big = lo == hi ? Integer.toString(lo) : lo + "-" + hi;
        String verdict;
        int color;
        if (report.mySpeed == null) {
            verdict = "your speed unknown";
            color = -1513240;
        } else {
            int total = sorted.size();
            int theyFirst = 0;
            int ties = 0;

            for (SpeedTiers.Tier tier : sorted) {
                int c = SpeedTiers.verdictColor(report, tier.speed()) & 16777215;
                if (c == 16735324) {
                    theyFirst++;
                } else if (c == 16767050) {
                    ties++;
                }
            }

            if (total == 0) {
                int lowColor = SpeedTiers.verdictColor(report, report.theoMin) & 16777215;
                int highColor = SpeedTiers.verdictColor(report, report.theoMax) & 16777215;
                if (lowColor == 16735324 && highColor == 16735324) {
                    verdict = "They always move first";
                    color = -41892;
                } else if (lowColor == 6022010 && highColor == 6022010) {
                    verdict = "You always move first";
                    color = -10755206;
                } else {
                    verdict = "Depends on their build";
                    color = -10166;
                }
            } else if (theyFirst == total) {
                verdict = total == 1 ? "They move first" : "They move first (all " + total + ")";
                color = -41892;
            } else if (theyFirst == 0 && ties == 0) {
                verdict = total == 1 ? "You move first" : "You move first (all " + total + ")";
                color = -10755206;
            } else {
                verdict = "They're first in " + theyFirst + "/" + total + (ties > 0 ? ", " + ties + " tie" : "");
                color = -10166;
            }

            boolean scarfPossible = sorted.isEmpty() && report.theoMaxScarf > report.theoMax && report.mySpeed < report.theoMaxScarf && color != -41892;
            if (scarfPossible) {
                verdict = color == -10755206 ? "You move first unless Scarf" : verdict + " · Scarf outspeeds";
                color = -10166;
            }

            verdict = verdict + " · you " + report.mySpeed;
        }

        text(gui, font, big, x + 3, y + 8, color, 0.62F);
        int vx = x + 5 + width(font, big, 0.62F);
        float vs = 0.45F;

        while (vs > 0.36F && vx + width(font, verdict, vs) > x + w - 2) {
            vs -= 0.02F;
        }

        text(gui, font, verdict, vx, y + 9, color, vs);
        int cx = x + 3;
        int cy = y + 16;
        int shown = 0;
        if (unique.isEmpty()) {
            text(gui, font, "no Smogon sets for this Pokémon", cx, cy + 1, -7434610, 0.4F);
        }

        for (SpeedTiers.Tier tierx : unique.values()) {
            String chip = tierx.chip() + " " + tierx.speed();
            int cw = width(font, chip, 0.4F) + 4;
            int remaining = unique.size() - shown;
            if (cx + cw > x + w - (remaining > 1 ? 12 : 1)) {
                text(gui, font, "+" + remaining, cx, cy + 1, -5723992, 0.4F);
                break;
            }

            int c = tierx.color();
            UiStyle.current().pill(gui, cx, cy, cx + cw, cy + 7, c, opacity);
            text(gui, font, chip, cx + 2, cy + 1, UiStyle.current().pillText(c), 0.4F);
            cx += cw + 2;
            shown++;
        }
    }

    private static int pos(int value, int lo, int hi, int barX, int barW) {
        if (hi <= lo) {
            return barX;
        } else {
            double t = (double)(value - lo) / (hi - lo);
            return barX + (int)Math.round(Math.max(0.0, Math.min(1.0, t)) * (barW - 1));
        }
    }

    private static void renderWeaknesses(class_332 gui, class_327 font, List<String> types, int x, int y, int w, float opacity) {
        UiStyle.current().section(gui, x, y, x + w, y + 12, 0, UiStyle.Accent.NONE, opacity);
        if (types != null && !types.isEmpty()) {
            String t1 = types.get(0).toLowerCase(Locale.ROOT);
            String t2 = types.size() > 1 ? types.get(1).toLowerCase(Locale.ROOT) : null;
            List<String[]> weak = new ArrayList<>();
            List<String[]> resist = new ArrayList<>();

            for (String attack : TYPE_ORDER) {
                float eff = TypeChart.getEffectivenessAgainstTypes(attack, t1, t2);
                if (eff >= 4.0F) {
                    weak.add(0, new String[]{attack, "4"});
                } else if (eff >= 2.0F) {
                    weak.add(new String[]{attack, "2"});
                } else if (eff <= 0.0F) {
                    resist.add(0, new String[]{attack, "0"});
                } else if (eff <= 0.25F) {
                    resist.add(new String[]{attack, "¼"});
                } else if (eff <= 0.5F) {
                    resist.add(new String[]{attack, "½"});
                }
            }

            int total = weak.size() + resist.size();
            int available = w - 8;
            int icon = total == 0 ? 8 : Math.max(5, Math.min(8, available / Math.max(1, total)));
            int cx = x + 2;
            gui.method_25294(cx, y + 2, cx + 1, y + 10, op(-38037, opacity));
            cx += 3;

            for (String[] entry : weak) {
                PartyInfoSideRenderer.drawTypeIcon(gui, cx, y + 3, entry[0], opacity, entry[1].equals("4") ? 1.25F : 1.08F, icon);
                text(gui, font, "×" + entry[1], cx, y, entry[1].equals("4") ? -38037 : -23194, 0.34F);
                cx += icon;
            }

            cx += 2;
            gui.method_25294(cx, y + 2, cx + 1, y + 10, op(-9721601, opacity));
            cx += 3;

            for (String[] entry : resist) {
                PartyInfoSideRenderer.drawTypeIcon(gui, cx, y + 3, entry[0], opacity * 0.9F, 0.8F, icon);
                int color = entry[1].equals("0") ? -5197569 : -8399617;
                text(gui, font, entry[1].equals("0") ? "×0" : entry[1], cx, y, color, 0.34F);
                cx += icon;
            }
        } else {
            text(gui, font, "Types unknown", x + 3, y + 4, -7434610, 0.5F);
        }
    }

    static int typeColor(String typeName) {
        try {
            Integer color = BattleMessageColorizer.getTypeColorByName(typeName);
            if (color != null) {
                return color & 16777215;
            }
        } catch (Throwable var2) {
        }

        return 10132122;
    }

    static void text(class_332 gui, class_327 font, String text, int x, int y, int color, float scale) {
        if (text != null && !text.isEmpty()) {
            gui.method_51448().method_22903();
            gui.method_51448().method_46416(x, y, 0.0F);
            gui.method_51448().method_22905(scale, scale, 1.0F);
            UiStyle style = UiStyle.current();
            UltraFont.draw(gui, font, text, 0, 0, style.text(color | 0xFF000000), style.shadow());
            gui.method_51448().method_22909();
        }
    }

    static int width(class_327 font, String text, float scale) {
        return text == null ? 0 : (int)Math.ceil(UltraFont.width(font, text) * scale);
    }

    static String trim(class_327 font, String text, int maxWidth, float scale) {
        if (text == null) {
            return "";
        } else if (width(font, text, scale) <= maxWidth) {
            return text;
        } else {
            String t = text;

            while (t.length() > 1 && width(font, t + "…", scale) > maxWidth) {
                t = t.substring(0, t.length() - 1);
            }

            return t + "…";
        }
    }

    static int op(int color, float opacity) {
        return BattleOpacityHelper.applyOpacity(color, opacity);
    }

    static int alpha(int rgb, int a) {
        return a << 24 | rgb & 16777215;
    }

    static int darken(int rgb, float factor) {
        int r = (int)((rgb >> 16 & 0xFF) * factor);
        int g = (int)((rgb >> 8 & 0xFF) * factor);
        int b = (int)((rgb & 0xFF) * factor);
        return r << 16 | g << 8 | b;
    }

    static int mix(int a, int b, float t) {
        int ar = a >> 16 & 0xFF;
        int ag = a >> 8 & 0xFF;
        int ab = a & 0xFF;
        int br = b >> 16 & 0xFF;
        int bg = b >> 8 & 0xFF;
        int bb = b & 0xFF;
        int r = (int)(ar + (br - ar) * t);
        int g = (int)(ag + (bg - ag) * t);
        int bl = (int)(ab + (bb - ab) * t);
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }
}
