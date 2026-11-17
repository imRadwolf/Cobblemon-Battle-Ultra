package name.modid.client;

import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.battle.ActiveClientBattlePokemon;
import com.cobblemon.mod.common.client.battle.ClientBattle;
import com.cobblemon.mod.common.client.battle.ClientBattleActor;
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon;
import com.cobblemon.mod.common.client.battle.ClientBattleSide;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import name.modid.client.net.TeamBallClientData;
import name.modid.platform.Services;
import net.minecraft.class_310;
import net.minecraft.class_327;
import net.minecraft.class_332;

public final class SpectatorStripRenderer {
    private static Object trackedBattle;
    private static final Map<String, SpectatorStripRenderer.Entry> SEEN = new LinkedHashMap<>();

    private SpectatorStripRenderer() {
    }

    static String norm(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    public static void init() {
        Services.EVENTS.registerScreenRender((screen, gui, mouseX, mouseY, tickDelta) -> {
            try {
                if (screen != null && screen.getClass().getName().contains("BattleGUI")) {
                    render(gui, mouseX, mouseY, tickDelta);
                }
            } catch (Throwable var6) {
            }
        });
    }

    private static void sync(ClientBattle battle, ClientBattleSide side) {
        if (!BattleKey.same(battle, trackedBattle)) {
            trackedBattle = BattleKey.of(battle);
            SEEN.clear();
        }

        for (SpectatorStripRenderer.Entry e : SEEN.values()) {
            e.active = false;
        }

        for (ClientBattleActor actor : side.getActors()) {
            for (ActiveClientBattlePokemon active : actor.getActivePokemon()) {
                ClientBattlePokemon bp = active == null ? null : active.getBattlePokemon();
                if (bp != null) {
                    String key = bp.getUuid() != null ? bp.getUuid().toString() : norm(bp.getSpecies().getName());
                    SpectatorStripRenderer.Entry e = SEEN.computeIfAbsent(key, k -> new SpectatorStripRenderer.Entry());
                    e.bp = bp;
                    e.name = bp.getDisplayName().getString();
                    e.active = true;
                    float hp = bp.getHpValue();
                    e.fainted = hp <= 0.0F;
                }
            }
        }
    }

    private static int teamSize(ClientBattleSide side) {
        int size = 0;

        try {
            for (ClientBattleActor actor : side.getActors()) {
                UUID uuid = actor.getUuid();
                if (uuid != null && TeamBallClientData.hasData(uuid)) {
                    size += TeamBallClientData.getTeamBalls(uuid).size();
                } else if (actor.getPokemon() != null) {
                    size += actor.getPokemon().size();
                }
            }
        } catch (Throwable var5) {
        }

        return size;
    }

    private static void render(class_332 gui, int mouseX, int mouseY, float tickDelta) {
        ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
        if (battle != null && BattleSideHelper.isSpectating(battle)) {
            ClientBattleSide side = BattleSideHelper.getPlayerSide(battle);
            if (side != null) {
                sync(battle, side);
                List<SpectatorStripRenderer.Entry> entries = new ArrayList<>(SEEN.values());
                int slots = Math.min(6, Math.max(entries.size(), teamSize(side)));
                if (slots > 0) {
                    class_310 mc = class_310.method_1551();
                    int screenWidth = mc.method_22683().method_4486();
                    int screenHeight = mc.method_22683().method_4502();
                    int[] other = OpponentInfoSideRenderer.getLastStripBounds();
                    boolean horizontal = other != null ? other[2] > other[3] : BattleExtrasConfig.isOpponentPartyInfoSlotsHorizontalLayout();
                    int w = horizontal ? slots * 25 + 1 : 26;
                    int h = horizontal ? 24 : slots * 23 + 1;
                    int x;
                    int y;
                    if (other != null) {
                        x = Math.max(0, screenWidth - other[0] - w);
                        y = other[1];
                    } else {
                        x = 0;
                        y = Math.max(4, (screenHeight - h) / 2 - 24);
                    }

                    if (!UltraTileRenderer.drawThemedStrip(gui, x, y, w, h, slots, horizontal)) {
                        UiStyle.current().frame(gui, x, y, x + w, y + h, 0, BattleOpacityHelper.getBattleOpacity());
                    }

                    class_327 font = mc.field_1772;
                    String hoveredName = null;

                    for (int i = 0; i < slots; i++) {
                        int sx = horizontal ? x + i * 25 : x;
                        int sy = horizontal ? y : y + i * 23;
                        if (i < entries.size()) {
                            SpectatorStripRenderer.Entry e = entries.get(i);

                            try {
                                CustomBattleTileRenderer.renderSharedPosablePortrait(
                                    gui, e.bp.getSpecies(), e.bp.getState(), sx + 4, sy + 3, 18, false, tickDelta
                                );
                            } catch (Throwable var24) {
                            }

                            try {
                                TeamStatusTracker.Mon m = TeamStatusTracker.spectatedLeft(e.bp.getSpecies().getName());
                                float ratio = e.active ? TeamStatusTracker.ratioOf(e.bp) : (m != null ? m.ratio : -1.0F);
                                String st = e.active ? TeamStatusTracker.statusOf(e.bp) : (m != null ? m.status : "");
                                TeamPanelRenderer.drawSlotBadges(gui, Math.max(-1.0F, Math.min(1.0F, ratio)), st, e.fainted, sx, sy, 25, 23);
                            } catch (Throwable ignored) {
                            }

                            if (e.fainted) {
                                gui.method_51448().method_22903();
                                gui.method_51448().method_46416(0.0F, 0.0F, 300.0F);
                                UiStyle.fill(gui, sx + 3, sy + 3, sx + 22, sy + 21, -1442840576);
                                UltraFont.draw(gui, font, "✖", sx + 9, sy + 8, -2140072, true);
                                gui.method_51448().method_22909();
                            } else if (e.active) {
                                UiStyle.outline(gui, sx + 2, sy + 2, sx + 23, sy + 22, -10166);
                            }

                            if (mouseX >= sx && mouseX < sx + 25 && mouseY >= sy && mouseY < sy + 23) {
                                hoveredName = e.name + (e.fainted ? " (fainted)" : (e.active ? " (in battle)" : ""));
                            }
                        } else {
                            RenderSystem.enableBlend();
                            AttachedPanelRenderer.text(gui, font, "?", sx + 11, sy + 8, -8750470, 1.0F);
                            if (mouseX >= sx && mouseX < sx + 25 && mouseY >= sy && mouseY < sy + 23) {
                                hoveredName = "Not revealed yet";
                            }
                        }
                    }

                    if (hoveredName != null) {
                        int tw = UltraFont.width(font, hoveredName) + 8;
                        int tx = Math.min(mouseX + 8, screenWidth - tw - 2);
                        int ty = mouseY + 8;
                        gui.method_51448().method_22903();
                        gui.method_51448().method_46416(0.0F, 0.0F, 400.0F);
                        UiStyle.current().frame(gui, tx, ty, tx + tw, ty + 13, 0, 1.0F);
                        UltraFont.draw(gui, font, hoveredName, tx + 4, ty + 3, -1, false);
                        gui.method_51448().method_22909();
                    }
                }
            }
        } else {
            SEEN.clear();
            trackedBattle = null;
        }
    }

    public static void onMessage(String key, Object[] args) {
        try {
            if ("cobblemon.battle.fainted".equals(key) && args != null && args.length > 0 && !SEEN.isEmpty()) {
                String[] w = BattleMessageSubscriber.resolvePokemonSideAndSpecies(args, 0);
                if (w != null && "player".equals(w[0]) && w[1] != null) {
                    for (SpectatorStripRenderer.Entry e : SEEN.values()) {
                        if (e.bp != null && norm(e.bp.getSpecies().getName()).equals(norm(w[1]))) {
                            e.fainted = true;
                        }
                    }
                }
            }
        } catch (Throwable var5) {
        }
    }

    private static final class Entry {
        ClientBattlePokemon bp;
        String name;
        boolean fainted;
        boolean active;
    }
}
