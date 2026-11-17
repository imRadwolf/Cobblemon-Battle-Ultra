package name.modid.client;

import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.battle.ActiveClientBattlePokemon;
import com.cobblemon.mod.common.client.battle.ClientBattle;
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Gender;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.List;
import java.util.Locale;
import net.minecraft.class_2960;
import net.minecraft.class_310;
import net.minecraft.class_327;
import net.minecraft.class_332;

public final class UltraTileRenderer {
    public static final int W = 132;
    public static final int H = 58;
    public static final int MARGIN_X = 12;
    public static final int TOP_Y = 12;
    private static final class_2960 TYPE_SHEET = class_2960.method_60654("cobblemon:textures/gui/types.png");
    private static final class_2960 PORTRAIT_BG = class_2960.method_60655("cobblemon-battle-extras", "textures/gui/plus/ultra_portrait_bg.png");
    private static final class_2960 TILE_BG = class_2960.method_60655("cobblemon-battle-extras", "textures/gui/plus/ultra_tile_bg.png");
    private static final String[] STAT_LABELS = new String[]{"ATK", "DEF", "SPA", "SPD", "SPE", "ACC", "EVA", "CRIT"};
    private static final String[] STAT_KEYS = new String[]{"attack", "defense", "special-attack", "special-defense", "speed", "accuracy", "evasion", null};
    private static final int[] STAT_ACCENTS = new int[]{16747099, 16765787, 7317759, 8184715, 16742854, 12105912, 12105912, 16757575};

    private UltraTileRenderer() {
    }

    public static boolean active() {
        try {
            if (ExtrasPlusConfig.get().ultraTiles && !BattleExtrasConfig.isHideBattleTilesEnabled()) {
                ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
                return battle != null && battle.getBattleFormat().getBattleType().getPokemonPerSide() == 1;
            } else {
                return false;
            }
        } catch (Throwable var1) {
            return false;
        }
    }

    public static float scale() {
        return Math.max(0.5F, ExtrasPlusConfig.get().pinnedPanelScale);
    }

    public static int[] bounds(boolean left) {
        float s = scale();
        int w = Math.round(132.0F * s);
        int h = Math.round(58.0F * s);
        int screenWidth = class_310.method_1551().method_22683().method_4486();
        int x = left ? 12 : screenWidth - 12 - w;
        return new int[]{x, 12, w, h};
    }

    private static float ratio(ClientBattlePokemon bp) {
        if (bp.isHpFlat()) {
            return Math.max(0.0F, Math.min(1.0F, bp.getHpValue() / Math.max(1.0F, bp.getMaxHp())));
        } else {
            float v = bp.getHpValue();
            return Math.max(0.0F, Math.min(1.0F, v > 1.0001F ? v / 100.0F : v));
        }
    }

    private static int hpColor(float ratio) {
        if (ratio > 0.5F) {
            return -11740828;
        } else {
            return ratio > 0.2F ? -670398 : -45747;
        }
    }

    private static int statusColor(String status) {
        return switch (status) {
            case "slp" -> -7566176;
            case "brn" -> -2072528;
            case "psn", "tox" -> -6274912;
            case "par" -> -3100640;
            case "frz" -> -9391904;
            default -> -8355712;
        };
    }

    public static void render(class_332 gui, float tickDelta, ActiveClientBattlePokemon active, boolean left, boolean hasCommand) {
        ClientBattlePokemon bp = active != null ? active.getBattlePokemon() : null;
        if (bp != null) {
            float opacity = BattleOpacityHelper.getBattleOpacity();
            if (!(opacity <= 0.01F)) {
                class_310 mc = class_310.method_1551();
                class_327 font = mc.field_1772;
                UiStyle style = UiStyle.current();
                float s = scale();
                int[] b = bounds(left);
                String side = left ? "player" : "opponent";
                String species = bp.getSpecies().getName();
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                gui.method_51448().method_22903();
                gui.method_51448().method_46416(b[0], b[1], 0.0F);
                gui.method_51448().method_22905(s, s, 1.0F);
                style.frame(gui, 0, 0, 132, 58, 0, opacity);
                gui.method_51452();
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                gui.method_51422(1.0F, 1.0F, 1.0F, opacity);
                if (left) {
                    gui.method_25293(TILE_BG, 1, 1, 130, 56, 0.0F, 0.0F, 256, 112, 256, 112);
                } else {
                    gui.method_51448().method_22903();
                    gui.method_51448().method_46416(132.0F, 0.0F, 0.0F);
                    gui.method_51448().method_22905(-1.0F, 1.0F, 1.0F);
                    RenderSystem.disableCull();
                    gui.method_25293(TILE_BG, 1, 1, 130, 56, 0.0F, 0.0F, 256, 112, 256, 112);
                    RenderSystem.enableCull();
                    gui.method_51448().method_22909();
                }

                gui.method_51422(1.0F, 1.0F, 1.0F, 1.0F);
                if (hasCommand) {
                    UiStyle.outline(gui, 0, 0, 132, 58, AttachedPanelRenderer.op(-10166, opacity * 0.8F));
                }

                int ps = 32;
                int px = left ? 2 : 130 - ps;
                int py = 2;
                style.section(gui, px, py, px + ps, py + ps, 0, UiStyle.Accent.NONE, opacity);
                gui.method_51452();
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                gui.method_51422(1.0F, 1.0F, 1.0F, opacity);
                gui.method_25293(PORTRAIT_BG, px + 2, py + 2, ps - 4, ps - 4, 0.0F, 0.0F, 64, 64, 64, 64);
                gui.method_51422(1.0F, 1.0F, 1.0F, 1.0F);
                int cx = left ? px + ps + 3 : 3;
                int cr = left ? 129 : px - 3;
                int cw = cr - cx;
                style.section(gui, cx, 2, cr, 12, 0, UiStyle.Accent.NONE, opacity);
                String levelText = "Lv." + bp.getLevel();
                int lw = AttachedPanelRenderer.width(font, levelText, 0.6F);
                String name = bp.getDisplayName().getString();
                String shownName = AttachedPanelRenderer.trim(font, name, cw - lw - 14, 0.62F);
                AttachedPanelRenderer.text(gui, font, shownName, cx + 3, 5, AttachedPanelRenderer.op(-1, opacity), 0.62F);
                AttachedPanelRenderer.text(gui, font, levelText, cr - lw - 3, 5, AttachedPanelRenderer.op(-1513240, opacity), 0.6F);
                Gender gender = bp.getGender();
                if (gender != null && gender != Gender.GENDERLESS) {
                    int nx = cx + 3 + AttachedPanelRenderer.width(font, shownName, 0.62F) + 3;
                    AttachedPanelRenderer.text(
                        gui, font, gender == Gender.MALE ? "♂" : "♀", Math.min(nx, cr - lw - 10), 5, gender == Gender.MALE ? -9459457 : -34136, 0.6F
                    );
                }

                float r = ratio(bp);
                int barY = 14;
                style.section(gui, cx, barY, cr, barY + 10, 0, UiStyle.Accent.NONE, opacity);
                int bx1 = cx + 2;
                int bx2 = cr - 2;
                UiStyle.fill(gui, bx1, barY + 2, bx2, barY + 8, AttachedPanelRenderer.op(-15066594, opacity));
                int fill = Math.round((bx2 - bx1) * r);
                if (fill > 0) {
                    int color = hpColor(r);
                    if (left) {
                        UiStyle.fill(gui, bx1, barY + 2, bx1 + fill, barY + 8, AttachedPanelRenderer.op(color, opacity));
                        UiStyle.fill(gui, bx1, barY + 2, bx1 + fill, barY + 3, AttachedPanelRenderer.op(1442840575, opacity));
                    } else {
                        UiStyle.fill(gui, bx2 - fill, barY + 2, bx2, barY + 8, AttachedPanelRenderer.op(color, opacity));
                        UiStyle.fill(gui, bx2 - fill, barY + 2, bx2, barY + 3, AttachedPanelRenderer.op(1442840575, opacity));
                    }
                }

                int pct = r > 0.0F ? Math.max(1, Math.round(r * 100.0F)) : 0;
                String hpText = bp.isHpFlat() ? Math.round(bp.getHpValue()) + "/" + Math.round(bp.getMaxHp()) + " · " + pct + "%" : pct + "%";
                int tw = AttachedPanelRenderer.width(font, hpText, 0.55F);
                AttachedPanelRenderer.text(gui, font, hpText, bx1 + (bx2 - bx1 - tw) / 2, barY + 2, AttachedPanelRenderer.op(-1, opacity), 0.55F);
                int rowY = 26;
                List<ElementalType> types = List.of();

                try {
                    types = TypeIconRenderer.getDisplayTypes(bp, species, side, bp.getUuid() != null ? bp.getUuid().toString() : null);
                } catch (Throwable var52) {
                }

                try {
                    String tera = BattleMessageSubscriber.getTeraType(side, species);
                    FormData eff = FormAbilityHelper.effectiveBattleForm(bp, side);
                    if ((tera == null || tera.isBlank()) && FormAbilityHelper.isBattleOnlyForm(eff)) {
                        List<ElementalType> formTypes = FormAbilityHelper.types(eff);
                        if (!formTypes.isEmpty()) {
                            types = formTypes;
                        }
                    }
                } catch (Throwable var54) {
                }

                int ix = left ? cx : cr - 9;

                for (ElementalType type : types) {
                    if (type != null) {
                        RenderSystem.enableBlend();
                        RenderSystem.defaultBlendFunc();
                        gui.method_51422(1.0F, 1.0F, 1.0F, opacity);
                        gui.method_25293(TYPE_SHEET, ix, rowY, 9, 9, type.getTextureXMultiplier() * 36, 0.0F, 36, 36, 648, 36);
                        gui.method_51422(1.0F, 1.0F, 1.0F, 1.0F);
                        ix += left ? 10 : -10;
                    }
                }

                String status = null;

                try {
                    status = bp.getStatus() != null ? bp.getStatus().getShowdownName() : null;
                    if ((status == null || status.isBlank()) && left) {
                        status = LocalBattlePartyTracker.getEffectivePlayerStatusId(active, bp);
                    }
                } catch (Throwable var53) {
                }

                if (status != null && !status.isBlank()) {
                    String st = status.toLowerCase(Locale.ROOT);
                    String label = st.equals("tox") ? "TOX" : st.toUpperCase(Locale.ROOT);
                    int sw = AttachedPanelRenderer.width(font, label, 0.5F) + 6;
                    int sx = left ? ix + 2 : ix + 9 - sw - 2;
                    style.pill(gui, sx, rowY, sx + sw, rowY + 9, statusColor(st), opacity);
                    AttachedPanelRenderer.text(gui, font, label, sx + 3, rowY + 2, AttachedPanelRenderer.op(style.pillText(statusColor(st)), opacity), 0.5F);
                }

                int gy = 36;
                int cellW = 31;

                for (int i = 0; i < 8; i++) {
                    int col = i % 4;
                    int row = i / 4;
                    int x1 = 2 + col * (cellW + 1);
                    int y1 = gy + row * 10;
                    int stage = 0;
                    if (STAT_KEYS[i] != null) {
                        stage = SpeedTiers.stageFor(side, bp, STAT_KEYS[i]);
                    } else if (VolatileTracker.has(side, species, "focusenergy")) {
                        stage = 2;
                    }

                    style.section(gui, x1, y1, x1 + cellW, y1 + 9, STAT_ACCENTS[i], UiStyle.Accent.LEFT, opacity);
                    AttachedPanelRenderer.text(
                        gui, font, style.label(STAT_LABELS[i]), x1 + 3, y1 + 3, AttachedPanelRenderer.op(0xFF000000 | STAT_ACCENTS[i], opacity), 0.4F
                    );
                    int pipX = x1 + cellW - 2 - 12;
                    int pips = Math.min(6, Math.abs(stage));
                    int pipColor = stage > 0 ? -8586116 : -38037;

                    for (int k = 0; k < 6; k++) {
                        int c = k < pips ? pipColor : -12961216;
                        UiStyle.fill(gui, pipX + k * 2, y1 + 3, pipX + k * 2 + 1, y1 + 6, AttachedPanelRenderer.op(c, opacity));
                    }

                    if (stage != 0) {
                        String sv = (stage > 0 ? "+" : "") + stage;
                        int svw = AttachedPanelRenderer.width(font, sv, 0.4F);
                        AttachedPanelRenderer.text(gui, font, sv, pipX - svw - 1, y1 + 3, AttachedPanelRenderer.op(pipColor, opacity), 0.4F);
                    }
                }

                gui.method_51448().method_22909();

                try {
                    int sx = b[0] + Math.round((px + 1) * s);
                    int sy = b[1] + Math.round((py + 1) * s);
                    int size = Math.round((ps - 2) * s);
                    CustomBattleTileRenderer.renderSharedPosablePortrait(gui, bp.getSpecies(), bp.getState(), sx, sy, size, !left, tickDelta);
                } catch (Throwable var51) {
                }

                try {
                    if (BattleExtrasConfig.isPokemonCountEnabled()) {
                        int screenWidth = mc.method_22683().method_4486();
                        float dx = left ? b[0] + b[2] - 150.0F : b[0] + 2.0F - (screenWidth - 149.0F);
                        gui.method_51448().method_22903();
                        gui.method_51448().method_46416(dx, -1.0F, 0.0F);
                        PokemonCountRenderer.drawPokemonCount(gui, active, left, false);
                        gui.method_51448().method_22909();
                    }
                } catch (Throwable var50) {
                }
            }
        }
    }

    public static boolean drawThemedStrip(class_332 gui, int x, int y, int w, int h, int slots, boolean horizontal) {
        if (!ExtrasPlusConfig.get().ultraTiles) {
            return false;
        } else {
            float opacity = BattleOpacityHelper.getBattleOpacity();
            UiStyle style = UiStyle.current();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            style.frame(gui, x, y, x + w, y + h, 0, opacity);

            for (int i = 0; i < slots; i++) {
                int sx = horizontal ? x + i * 25 : x;
                int sy = horizontal ? y : y + i * 23;
                int ex = horizontal ? sx + 25 : x + w;
                int ey = horizontal ? y + h : sy + 23;
                style.section(gui, sx + 2, sy + 2, ex - 2, ey - 1, 0, UiStyle.Accent.NONE, opacity);
            }

            return true;
        }
    }

    public static boolean drawThemedBox(class_332 gui, int x, int y, int w, int h) {
        if (!ExtrasPlusConfig.get().ultraTiles) {
            return false;
        } else {
            float opacity = BattleOpacityHelper.getBattleOpacity();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            UiStyle.current().frame(gui, x, y, x + w, y + h, 0, opacity);
            return true;
        }
    }
}
