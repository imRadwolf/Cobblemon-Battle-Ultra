package name.modid.client;

import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.battle.ActiveClientBattlePokemon;
import com.cobblemon.mod.common.client.battle.ClientBattle;
import com.cobblemon.mod.common.client.battle.ClientBattleActor;
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon;
import com.cobblemon.mod.common.client.battle.ClientBattleSide;
import com.cobblemon.mod.common.pokemon.Pokemon;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import name.modid.platform.Services;
import net.minecraft.class_1799;
import net.minecraft.class_2561;
import net.minecraft.class_310;
import net.minecraft.class_332;
import net.minecraft.class_437;

public final class PinnedInfoPanelRenderer {
    private static final int BODY_WIDTH = 149;
    private static final int BODY_HEIGHT = 117;
    private static final int GAP_BELOW_TILE = 18;
    private static final int PANEL_SPACING = 3;
    private static PinnedInfoPanelRenderer.PanelHover pendingHover = null;
    private static final Map<String, List<class_2561>> THREAT_LINES = new ConcurrentHashMap<>();

    private PinnedInfoPanelRenderer() {
    }

    public static void initLate() {
        if (Services.PLATFORM.isModLoaded("cobblemon")) {
            Services.EVENTS.registerScreenRender((screen, gui, mouseX, mouseY, tickDelta) -> {
                PinnedInfoPanelRenderer.PanelHover hover = pendingHover;
                pendingHover = null;

                try {
                    if (hover != null && isBattleGUI(screen)) {
                        renderHoverTooltip(gui, hover, mouseX, mouseY);
                    }
                } catch (Throwable var7) {
                }
            });
        }
    }

    public static void init() {
        if (Services.PLATFORM.isModLoaded("cobblemon")) {
            Services.EVENTS.registerScreenRender((screen, gui, mouseX, mouseY, tickDelta) -> {
                try {
                    pendingHover = null;
                    if (isBattleGUI(screen)) {
                        render(gui, mouseX, mouseY);
                    }
                } catch (Throwable var6) {
                }
            });
        }
    }

    private static boolean isBattleGUI(class_437 screen) {
        return screen != null && screen.getClass().getName().contains("BattleGUI");
    }

    private static void render(class_332 gui, int mouseX, int mouseY) {
        ExtrasPlusConfig cfg = ExtrasPlusConfig.get();
        if (cfg.pinnedPanelsEnabled) {
            ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
            if (battle != null && !battle.getMinimised() && !(BattleOpacityHelper.getBattleOpacity() <= 0.01F)) {
                List<PinnedInfoPanelRenderer.PanelHover> hovered = new ArrayList<>();
                if (cfg.pinnedPlayerPanel) {
                    renderSide(gui, battle, true, mouseX, mouseY, hovered);
                }

                if (cfg.pinnedOpponentPanel) {
                    renderSide(gui, battle, false, mouseX, mouseY, hovered);
                }

                pendingHover = cfg.pinnedHoverTooltips && !hovered.isEmpty() ? hovered.get(hovered.size() - 1) : null;
            }
        }
    }

    private static List<ActiveClientBattlePokemon> collectActives(ClientBattleSide side) {
        List<ActiveClientBattlePokemon> list = new ArrayList<>();
        if (side != null) {
            for (ClientBattleActor actor : side.getActors()) {
                for (ActiveClientBattlePokemon active : actor.getActivePokemon()) {
                    if (active != null && active.getBattlePokemon() != null) {
                        list.add(active);
                    }
                }
            }
        }

        return list;
    }

    private static void renderSide(
        class_332 gui, ClientBattle battle, boolean playerSide, int mouseX, int mouseY, List<PinnedInfoPanelRenderer.PanelHover> hovered
    ) {
        if (ExtrasPlusConfig.get().attachedPanelStyle) {
            renderAttachedSide(gui, battle, playerSide, mouseX, mouseY, hovered);
        } else {
            ClientBattleSide side = playerSide ? BattleSideHelper.getPlayerSide(battle) : BattleSideHelper.getOpponentSide(battle);
            List<ActiveClientBattlePokemon> actives = collectActives(side);
            if (!actives.isEmpty()) {
                class_310 mc = class_310.method_1551();
                int screenWidth = mc.method_22683().method_4486();
                int screenHeight = mc.method_22683().method_4502();
                boolean isCompact = battle.getBattleFormat().getBattleType().getPokemonPerSide() > 1;
                int total = actives.size();
                int maxBottom = 0;
                float edgeX = playerSide ? Float.MAX_VALUE : -Float.MAX_VALUE;
                List<ActiveClientBattlePokemon> ordered = new ArrayList<>(total);

                for (int i = 0; i < total; i++) {
                    ordered.add(null);
                }

                for (int i = 0; i < total; i++) {
                    ActiveClientBattlePokemon active = actives.get(i);
                    int rank = playerSide ? i : total - i - 1;
                    CustomBattleTileRenderer.TilePlacement placement = CustomBattleTileRenderer.calculateTilePlacement(
                        mc, battle, active, playerSide, rank, isCompact
                    );
                    maxBottom = Math.max(maxBottom, placement.y() + placement.height());
                    if (playerSide) {
                        edgeX = Math.min(edgeX, placement.x() + 1.0F);
                    } else {
                        edgeX = Math.max(edgeX, placement.x() + placement.width() - 1.0F);
                    }

                    if (rank >= 0 && rank < total) {
                        ordered.set(rank, active);
                    }
                }

                if (total == 1 && !isCompact && UltraTileRenderer.active()) {
                    int[] tile = UltraTileRenderer.bounds(playerSide);
                    maxBottom = tile[1] + tile[3] - 18 - 1;
                    edgeX = playerSide ? tile[0] : tile[0] + tile[2];
                }

                ExtrasPlusConfig cfg = ExtrasPlusConfig.get();
                float scale = cfg.pinnedPanelScale;
                int width = Math.round(149.0F * scale);
                int height = Math.round(117.0F * scale);
                int bodyY = maxBottom + 18 + cfg.pinnedOffsetY;
                int firstX = playerSide ? Math.round(edgeX) + cfg.pinnedOffsetX : Math.round(edgeX) - width - cfg.pinnedOffsetX;
                if (cfg.pinnedAvoidSideStrips) {
                    int[] strip = playerSide ? PartyInfoSideRenderer.getLastStripBounds() : OpponentInfoSideRenderer.getLastStripBounds();
                    int groupWidth = total * width + (total - 1) * 3;
                    int groupLeft = playerSide ? firstX : firstX - (total - 1) * (width + 3);
                    if (strip != null && overlaps(groupLeft, bodyY, groupWidth, height, strip[0], strip[1], strip[2], strip[3])) {
                        boolean stripOnLeft = strip[0] + strip[2] / 2 < screenWidth / 2;
                        if (playerSide) {
                            firstX = stripOnLeft ? strip[0] + strip[2] + 2 : strip[0] - groupWidth - 2;
                        } else {
                            firstX = stripOnLeft ? strip[0] + strip[2] + 2 + (total - 1) * (width + 3) : strip[0] - width - 2;
                        }
                    }
                }

                bodyY = Math.max(0, Math.min(bodyY, screenHeight - height));

                for (int i = 0; i < ordered.size(); i++) {
                    ActiveClientBattlePokemon activex = ordered.get(i);
                    if (activex != null && activex.getBattlePokemon() != null) {
                        int bodyX = playerSide ? firstX + i * (width + 3) : firstX - i * (width + 3);
                        bodyX = Math.max(0, Math.min(bodyX, screenWidth - width));
                        List<PinnedHoverZone> zones = new ArrayList<>();
                        gui.method_51448().method_22903();
                        gui.method_51448().method_46416(bodyX, bodyY, 0.0F);
                        gui.method_51448().method_22905(scale, scale, 1.0F);

                        try {
                            if (playerSide) {
                                ClientBattlePokemon battlePokemon = activex.getBattlePokemon();
                                Pokemon partyPokemon = PartyInfoSideRenderer.findPartyPokemonForBattlePokemon(battlePokemon);
                                if (partyPokemon != null) {
                                    PartyInfoSideRenderer.renderPinnedPanel(gui, partyPokemon, 0, 0, zones);
                                } else {
                                    OpponentInfoSideRenderer.renderPinnedPanelForActive(gui, battle, activex, 0, 0, zones);
                                }
                            } else {
                                OpponentInfoSideRenderer.renderPinnedPanelForActive(gui, battle, activex, 0, 0, zones);
                            }
                        } catch (Throwable var34) {
                        } finally {
                            gui.method_51448().method_22909();
                        }

                        double localX = (mouseX - bodyX) / scale;
                        double localY = (mouseY - bodyY) / scale;
                        if (localX >= 0.0 && localY >= 0.0 && localX < 149.0 && localY < 117.0) {
                            for (PinnedHoverZone zone : zones) {
                                if (zone.contains(localX, localY)) {
                                    hovered.add(new PinnedInfoPanelRenderer.PanelHover(zone, mouseX, mouseY));
                                    break;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static PanelData collectData(ClientBattle battle, ActiveClientBattlePokemon active, boolean playerSide) {
        if (playerSide) {
            Pokemon partyPokemon = PartyInfoSideRenderer.findPartyPokemonForBattlePokemon(active.getBattlePokemon());
            if (partyPokemon != null) {
                return PartyInfoSideRenderer.collectPanelData(partyPokemon);
            }
        }

        PanelData data = OpponentInfoSideRenderer.collectPanelDataForActive(battle, active);
        data.playerSide = playerSide;
        return data;
    }

    private static void renderAttachedSide(
        class_332 gui, ClientBattle battle, boolean playerSide, int mouseX, int mouseY, List<PinnedInfoPanelRenderer.PanelHover> hovered
    ) {
        ClientBattleSide side = playerSide ? BattleSideHelper.getPlayerSide(battle) : BattleSideHelper.getOpponentSide(battle);
        List<ActiveClientBattlePokemon> actives = collectActives(side);
        if (!actives.isEmpty()) {
            class_310 mc = class_310.method_1551();
            int screenWidth = mc.method_22683().method_4486();
            int screenHeight = mc.method_22683().method_4502();
            boolean isCompact = battle.getBattleFormat().getBattleType().getPokemonPerSide() > 1;
            ExtrasPlusConfig cfg = ExtrasPlusConfig.get();
            float scale = cfg.pinnedPanelScale;
            float opacity = BattleOpacityHelper.getBattleOpacity();
            int total = actives.size();
            List<ActiveClientBattlePokemon> ordered = new ArrayList<>();
            List<CustomBattleTileRenderer.TilePlacement> placements = new ArrayList<>();

            for (int i = 0; i < total; i++) {
                ordered.add(null);
                placements.add(null);
            }

            int maxBottom = 0;

            for (int i = 0; i < total; i++) {
                ActiveClientBattlePokemon active = actives.get(i);
                int rank = playerSide ? i : total - i - 1;
                CustomBattleTileRenderer.TilePlacement placement = CustomBattleTileRenderer.calculateTilePlacement(
                    mc, battle, active, playerSide, rank, isCompact
                );
                maxBottom = Math.max(maxBottom, placement.y() + placement.height());
                if (rank >= 0 && rank < total) {
                    ordered.set(rank, active);
                    placements.set(rank, placement);
                }
            }

            ActiveClientBattlePokemon myActive = firstActive(BattleSideHelper.getPlayerSide(battle));
            ActiveClientBattlePokemon theirActive = firstActive(BattleSideHelper.getOpponentSide(battle));
            boolean spectating = BattleSideHelper.isSpectating(battle);
            PanelData myData = myActive != null && !spectating ? collectData(battle, myActive, true) : null;
            int width = Math.round(132.0F * scale);

            for (int ix = 0; ix < ordered.size(); ix++) {
                ActiveClientBattlePokemon active = ordered.get(ix);
                CustomBattleTileRenderer.TilePlacement placement = placements.get(ix);
                if (active != null && active.getBattlePokemon() != null && placement != null) {
                    PanelData data = playerSide && active == myActive && myData != null ? myData : collectData(battle, active, playerSide && !spectating);
                    if (spectating) {
                        data.playerSide = false;
                    }

                    try {
                        ClientBattlePokemon otherBp = playerSide
                            ? (theirActive != null ? theirActive.getBattlePokemon() : null)
                            : (myActive != null ? myActive.getBattlePokemon() : null);
                        data.modifiers = ModifierChips.collect(playerSide, active.getBattlePokemon(), otherBp);
                    } catch (Throwable var49) {
                    }

                    try {
                        if (spectating) {
                            data.inference = BuildInference.info(active.getBattlePokemon());
                        } else if (playerSide) {
                            if (theirActive != null && theirActive.getBattlePokemon() != null) {
                                data.inference = BuildInference.info(theirActive.getBattlePokemon());
                            }
                        } else {
                            data.inference = BuildInference.info(active.getBattlePokemon());
                            if (ExtrasPlusConfig.get().showIncomingDamage && myActive != null && myActive.getBattlePokemon() != null) {
                                Pokemon mine = PartyInfoSideRenderer.findPartyPokemonForBattlePokemon(myActive.getBattlePokemon());
                                if (mine != null) {
                                    data.threats = ThreatCalc.compute(
                                        active.getBattlePokemon(), mine, myActive.getBattlePokemon(), myData != null ? myData.form : null
                                    );
                                }
                            }
                        }
                    } catch (Throwable var48) {
                    }

                    SpeedTiers.Report report = null;

                    try {
                        if (spectating) {
                            report = SpeedTiers.forOpponent(active.getBattlePokemon(), null);
                        } else if (playerSide) {
                            if (theirActive != null && theirActive.getBattlePokemon() != null) {
                                report = SpeedTiers.forOpponent(theirActive.getBattlePokemon(), data);
                            }
                        } else {
                            report = SpeedTiers.forOpponent(active.getBattlePokemon(), myData);
                        }
                    } catch (Throwable var47) {
                    }

                    int height = Math.round(AttachedPanelRenderer.height(data) * scale);
                    int panelX;
                    int panelY;
                    if (total == 1 && !isCompact && UltraTileRenderer.active()) {
                        int[] tile = UltraTileRenderer.bounds(playerSide);
                        panelX = playerSide ? tile[0] : tile[0] + tile[2] - width;
                        panelY = tile[1] + tile[3] - 1;
                    } else if (total == 1 && !isCompact) {
                        panelX = playerSide ? Math.round(placement.x()) + 1 : Math.round(placement.x()) + 7 + 132 - width;
                        panelY = placement.y() + placement.height() + 14;
                    } else {
                        int edge = playerSide ? Math.round(placements.get(0).x()) + 1 : Math.round(placements.get(0).x()) + placements.get(0).width() - 1;
                        panelX = playerSide ? edge + ix * (width + 3) : edge - width - ix * (width + 3);
                        panelY = maxBottom + 14;
                    }

                    boolean glued = total == 1 && !isCompact && UltraTileRenderer.active();
                    if (!glued) {
                        panelX += playerSide ? cfg.pinnedOffsetX : -cfg.pinnedOffsetX;
                        panelY += cfg.pinnedOffsetY;
                    }

                    if (cfg.pinnedAvoidSideStrips) {
                        int[] strip = playerSide ? PartyInfoSideRenderer.getLastStripBounds() : OpponentInfoSideRenderer.getLastStripBounds();
                        if (strip != null && overlaps(panelX, panelY, width, height, strip[0], strip[1], strip[2], strip[3])) {
                            boolean stripOnLeft = strip[0] + strip[2] / 2 < screenWidth / 2;
                            panelX = stripOnLeft ? strip[0] + strip[2] + 2 : strip[0] - width - 2;
                        }
                    }

                    panelX = Math.max(0, Math.min(panelX, screenWidth - width));
                    panelY = Math.max(0, Math.min(panelY, screenHeight - height));
                    List<PinnedHoverZone> zones = new ArrayList<>();
                    gui.method_51448().method_22903();
                    gui.method_51448().method_46416(panelX, panelY, 0.0F);
                    gui.method_51448().method_22905(scale, scale, 1.0F);

                    try {
                        AttachedPanelRenderer.render(gui, data, report, zones, opacity);
                    } catch (Throwable var45) {
                    } finally {
                        gui.method_51448().method_22909();
                    }

                    double localX = (mouseX - panelX) / scale;
                    double localY = (mouseY - panelY) / scale;

                    for (PinnedHoverZone zone : zones) {
                        if (zone.contains(localX, localY)) {
                            hovered.add(new PinnedInfoPanelRenderer.PanelHover(zone, mouseX, mouseY));
                            break;
                        }
                    }
                }
            }
        }
    }

    private static ActiveClientBattlePokemon firstActive(ClientBattleSide side) {
        List<ActiveClientBattlePokemon> list = collectActives(side);
        return list.isEmpty() ? null : list.get(0);
    }

    private static List<class_2561> withThreatLines(String moveId, List<class_2561> base, Object payload) {
        List<class_2561> extra = new ArrayList<>();
        String extraKey;
        if (payload instanceof ThreatCalc.Threat t) {
            extraKey = moveId + "|" + t.pctText() + "|" + t.ko() + "|" + t.builds() + "/" + t.totalBuilds() + "|" + t.revealed();
            if (t.damaging()) {
                String vs = "vs your active: ";
                int color = ThreatCalc.pctColor(t.maxPct());
                String dmg = t.maxPct() == 0 ? (t.note() != null ? t.note() : "no damage") : t.pctText() + " · " + t.ko();
                extra.add(
                    class_2561.method_43470(vs)
                        .method_27694(st -> st.method_36139(12105912))
                        .method_10852(class_2561.method_43470(dmg).method_27694(st -> st.method_36139(color)))
                );
            }

            int[] pp = BattleCounters.ppLeft(MoveInsights.activeOpponent(), moveId);
            if (pp != null) {
                extraKey = extraKey + "|pp" + pp[0];
                int ppc = BattleCounters.ppColor(pp[0], pp[1]) & 16777215;
                extra.add(
                    class_2561.method_43470("PP left: ")
                        .method_27694(st -> st.method_36139(12105912))
                        .method_10852(class_2561.method_43470(pp[0] + "/" + pp[1]).method_27694(st -> st.method_36139(ppc)))
                        .method_10852(class_2561.method_43470("  (counted from its uses, assuming PP Ups)").method_27694(st -> st.method_36139(8026746)))
                );
            }

            boolean seenBefore = !t.revealed() && t.note() != null && t.note().startsWith("»");
            extra.add(
                class_2561.method_43470(
                        t.revealed()
                            ? "Revealed (used this battle)"
                            : (
                                seenBefore
                                    ? "» Used by this opponent in an earlier battle"
                                    : "Likely: in " + t.builds() + "/" + t.totalBuilds() + " remaining builds"
                            )
                    )
                    .method_27694(st -> st.method_36139(t.revealed() ? 15263976 : (seenBefore ? 13084415 : 10132122)))
            );
        } else if (payload instanceof MoveAdvisor.Eval ev) {
            MoveAdvisor.Advice advice = MoveAdvisor.current();
            List<MoveAdvisor.Line> advisor = MoveAdvisor.lines(moveId);
            extraKey = moveId + "|" + ev.pctText() + "|" + ev.koText() + "|" + advisor.size() + "|" + (advice != null ? advice.bestId + advice.danger : "");
            if (ev.damaging()) {
                String vs = "vs " + (advice != null ? advice.opponentName : "them") + ": ";
                int color = ThreatCalc.pctColor(ev.maxPct());
                String ko = ev.koText();
                extra.add(
                    class_2561.method_43470(vs)
                        .method_27694(st -> st.method_36139(12105912))
                        .method_10852(class_2561.method_43470(ev.pctText()).method_27694(st -> st.method_36139(color)))
                        .method_10852(
                            class_2561.method_43470(ko.isEmpty() ? "" : "  " + ko)
                                .method_27694(st -> st.method_36139(MoveAdvisor.koColor(ev.koSure(), ev.koAny(), ev.builds()) & 16777215))
                        )
                );
            }

            for (MoveAdvisor.Line line : advisor) {
                int lc = line.leftColor() & 16777215;
                int rc = line.rightColor() & 16777215;
                extra.add(
                    class_2561.method_43470(line.left())
                        .method_27694(st -> st.method_36139(lc))
                        .method_10852(class_2561.method_43470(line.right().isEmpty() ? "" : "  " + line.right()).method_27694(st -> st.method_36139(rc)))
                );
            }

            if (extra.isEmpty()) {
                return base;
            }
        } else {
            if (!(payload instanceof String str) || !str.startsWith("likely:")) {
                return base;
            }

            extraKey = moveId + "|" + str;
            if (str.equals("likely:seen")) {
                extra.add(class_2561.method_43470("» Used by this opponent in an earlier battle").method_27694(st -> st.method_36139(13084415)));
            } else {
                extra.add(class_2561.method_43470("Likely: in " + str.substring(7) + " remaining builds").method_27694(st -> st.method_36139(10132122)));
            }
        }

        List<class_2561> cached = THREAT_LINES.get(extraKey);
        if (cached == null) {
            cached = new ArrayList<>(base);
            cached.add(class_2561.method_43473());
            cached.addAll(extra);
            if (THREAT_LINES.size() > 128) {
                THREAT_LINES.clear();
            }

            THREAT_LINES.put(extraKey, cached);
        }

        return cached;
    }

    private static void renderSpeedTooltip(class_332 gui, SpeedTiers.Report report, int mouseX, int mouseY) {
        List<class_2561> lines = new ArrayList<>();
        lines.add(
            class_2561.method_43470("⚡ Speed — " + report.subject + "  (Lv " + report.level + ", base " + report.baseSpeed + ")")
                .method_27694(s -> s.method_36139(16766720).method_10982(true))
        );
        if (report.trickRoom) {
            lines.add(class_2561.method_43470("Trick Room: the slower Pokémon moves first").method_27694(s -> s.method_36139(16740312)));
        }

        if (report.mySpeed != null) {
            String mods = report.myModifiers.isEmpty() ? "" : "  [" + String.join(", ", report.myModifiers) + "]";
            lines.add(class_2561.method_43470("You (" + report.myName + "): " + report.mySpeed + mods).method_27694(s -> s.method_36139(16777215)));
        }

        lines.add(class_2561.method_43473());
        String mods = report.modifiers.isEmpty() ? "" : "  [" + String.join(", ", report.modifiers) + "]";
        lines.add(class_2561.method_43470("Possible range" + mods).method_27694(s -> s.method_36139(12105912)));
        addTierLine(lines, report, "  Min (0 IV, − nature)", report.theoMin);
        addTierLine(lines, report, "  Neutral, 0 EV", report.theoNeutral);
        addTierLine(lines, report, "  Max (252 EV, + nature)", report.theoMax);
        if (report.theoMaxScarf > report.theoMax) {
            addTierLine(lines, report, "  Max + Choice Scarf", report.theoMaxScarf);
        }

        if (!report.builds.isEmpty()) {
            lines.add(class_2561.method_43473());
            lines.add(
                class_2561.method_43470(report.fromBaseForm ? "Smogon SV sets (base form spreads, Mega speed)" : "Smogon SV Strategy Dex sets")
                    .method_27694(s -> s.method_36139(12105912))
            );

            for (SpeedTiers.Tier tier : report.builds) {
                int color = tier.color() & 16777215;
                String verdict = SpeedTiers.verdictText(report, tier.speed());
                lines.add(
                    class_2561.method_43470("● " + tier.speed() + "  ")
                        .method_27694(s -> s.method_36139(color).method_10982(true))
                        .method_10852(class_2561.method_43470(tier.label()).method_27694(s -> s.method_36139(15263976)))
                        .method_10852(class_2561.method_43470("  " + tier.detail()).method_27694(s -> s.method_36139(10132122)))
                        .method_10852(class_2561.method_43470(verdict.isEmpty() ? "" : "  → " + verdict).method_27694(s -> s.method_36139(color)))
                );
            }
        } else {
            lines.add(class_2561.method_43470("No Smogon set data for this Pokémon").method_27694(s -> s.method_36139(9342606)));
        }

        if (report.memoryBattles > 0) {
            lines.add(class_2561.method_43473());
            lines.add(
                class_2561.method_43470("» You've battled this opponent before: what it showed then is pre-filled in lavender")
                    .method_27694(s -> s.method_36139(13084415))
            );
        }

        if (!report.reasons.isEmpty()) {
            lines.add(class_2561.method_43473());
            lines.add(
                class_2561.method_43470("Builds left: " + report.builds.size() + "/" + report.totalBuilds)
                    .method_27694(s -> s.method_36139(8377599).method_10982(true))
            );

            for (String reason : report.reasons) {
                lines.add(class_2561.method_43470("  • " + reason).method_27694(s -> s.method_36139(10471648)));
            }
        }

        lines.add(class_2561.method_43473());
        lines.add(class_2561.method_43470("Green: you move first · Red: they move first · Yellow: tie").method_27694(s -> s.method_36139(8026746)));
        gui.method_51448().method_22903();
        gui.method_51448().method_46416(0.0F, 0.0F, 500.0F);
        CustomTooltipRenderer.renderTooltip(gui, lines, mouseX + 8, Math.max(0, mouseY - 8), 16742854);
        gui.method_51448().method_22909();
    }

    private static void addTierLine(List<class_2561> lines, SpeedTiers.Report report, String label, int speed) {
        int color = SpeedTiers.verdictColor(report, speed) & 16777215;
        String verdict = SpeedTiers.verdictText(report, speed);
        lines.add(
            class_2561.method_43470(label + ": ")
                .method_27694(s -> s.method_36139(13684944))
                .method_10852(class_2561.method_43470(Integer.toString(speed)).method_27694(s -> s.method_36139(color).method_10982(true)))
                .method_10852(class_2561.method_43470(verdict.isEmpty() ? "" : "  → " + verdict).method_27694(s -> s.method_36139(color)))
        );
    }

    private static boolean overlaps(int ax, int ay, int aw, int ah, int bx, int by, int bw, int bh) {
        return ax < bx + bw && ax + aw > bx && ay < by + bh && ay + ah > by;
    }

    private static void renderHoverTooltip(class_332 gui, PinnedInfoPanelRenderer.PanelHover hover, int mouseX, int mouseY) {
        PinnedHoverZone zone = hover.zone();
        if ("speed".equals(zone.kind()) && zone.payload() instanceof SpeedTiers.Report report) {
            renderSpeedTooltip(gui, report, mouseX, mouseY);
        } else if ("move".equals(zone.kind())) {
            BattleLogMoveTooltipHelper.MoveTooltipData data = BattleLogMoveTooltipHelper.getTooltipData(zone.id());
            if (data != null && !data.lines().isEmpty()) {
                List<class_2561> lines = withThreatLines(zone.id(), data.lines(), zone.payload());
                CustomTooltipRenderer.renderMoveTooltipWithIcons(gui, lines, mouseX + 8, mouseY - 8, data.typeHue(), data.type(), data.category());
            }
        } else if ("modifiers".equals(zone.kind()) && zone.payload() instanceof List<?> list) {
            List<ModifierChips.Chip> chips = new ArrayList<>();

            for (Object o : list) {
                if (o instanceof ModifierChips.Chip c) {
                    chips.add(c);
                }
            }

            gui.method_51448().method_22903();
            gui.method_51448().method_46416(0.0F, 0.0F, 500.0F);
            CustomTooltipRenderer.renderTooltip(gui, ModifierChips.tooltip(chips, "player".equals(zone.id())), mouseX + 8, Math.max(0, mouseY - 8), 16766720);
            gui.method_51448().method_22909();
        } else if ("item".equals(zone.kind())) {
            gui.method_51448().method_22903();
            gui.method_51448().method_46416(0.0F, 0.0F, 500.0F);
            if (zone.payload() instanceof class_1799 stack && !stack.method_7960()) {
                gui.method_51446(class_310.method_1551().field_1772, stack, mouseX, mouseY);
            } else {
                gui.method_51438(class_310.method_1551().field_1772, class_2561.method_43470(zone.id()), mouseX, mouseY);
            }

            gui.method_51448().method_22909();
        } else if ("ability".equals(zone.kind())) {
            List<class_2561> lines = new ArrayList<>();
            int border = 16766720;

            for (String id : zone.id().split(",")) {
                BattleLogAbilityTooltipHelper.AbilityTooltipData data = BattleLogAbilityTooltipHelper.getTooltipData(id);
                if (data != null && !data.lines().isEmpty()) {
                    if (!lines.isEmpty()) {
                        lines.add(class_2561.method_43473());
                    }

                    lines.addAll(data.lines());
                    border = data.borderColor();
                } else {
                    String name = FormAbilityHelper.abilityDisplayNameFromId(id);
                    if (name != null) {
                        if (!lines.isEmpty()) {
                            lines.add(class_2561.method_43473());
                        }

                        lines.add(class_2561.method_43470(name));
                    }
                }
            }

            if (!lines.isEmpty()) {
                gui.method_51448().method_22903();
                gui.method_51448().method_46416(0.0F, 0.0F, 500.0F);
                CustomTooltipRenderer.renderTooltip(gui, lines, mouseX + 8, Math.max(0, mouseY - 8), border);
                gui.method_51448().method_22909();
            }
        }
    }

    private record PanelHover(PinnedHoverZone zone, int mouseX, int mouseY) {
    }
}
