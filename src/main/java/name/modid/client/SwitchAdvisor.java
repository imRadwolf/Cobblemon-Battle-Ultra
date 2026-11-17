package name.modid.client;

import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon;
import com.cobblemon.mod.common.client.gui.battle.BattleGUI;
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleSwitchPokemonSelection;
import com.cobblemon.mod.common.client.gui.battle.subscreen.BattleSwitchPokemonSelection.SwitchTile;
import com.cobblemon.mod.common.pokemon.Pokemon;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import name.modid.platform.Services;
import net.minecraft.class_310;
import net.minecraft.class_327;
import net.minecraft.class_332;

public final class SwitchAdvisor {
    private static volatile SwitchAdvisor.Pick cached;
    private static volatile long cachedAt;
    private static Class<?> controller;
    private static volatile SwitchAdvisor.Pick tileCached;
    private static volatile long tileCachedAt;
    private static volatile String tileCacheKey = "";

    private SwitchAdvisor() {
    }

    static String norm(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    static double hazardPct(Pokemon pokemon) {
        Map<String, Integer> hazards = BattleMessageSubscriber.getHazardsForSide("player");
        if (hazards != null && !hazards.isEmpty()) {
            try {
                String item = pokemon.heldItem() != null && !pokemon.heldItem().method_7960() ? norm(pokemon.heldItem().method_7964().getString()) : "";
                String ability = norm(pokemon.getAbility().getTemplate().getName());
                if (!item.equals("heavydutyboots") && !ability.equals("magicguard")) {
                    List<String> types = new ArrayList<>();

                    for (ElementalType t : FormAbilityHelper.types(pokemon.getForm())) {
                        types.add(norm(t.getName()));
                    }

                    boolean grounded = !types.contains("flying") && !ability.equals("levitate") && !item.equals("airballoon");
                    double pct = 0.0;
                    if (hazards.getOrDefault("Stealth Rock", 0) > 0) {
                        double eff = 1.0;

                        for (String t : types) {
                            eff *= TypeChart.getEffectivenessAgainstTypes("rock", t, null);
                        }

                        pct += 12.5 * eff;
                    }

                    int spikes = hazards.getOrDefault("Spikes", 0);
                    if (spikes > 0 && grounded) {
                        pct += spikes >= 3 ? 25.0 : (spikes == 2 ? 16.67 : 12.5);
                    }

                    return pct;
                } else {
                    return 0.0;
                }
            } catch (Throwable var12) {
                return 0.0;
            }
        } else {
            return 0.0;
        }
    }

    public static SwitchAdvisor.Pick best() {
        if (!ExtrasPlusConfig.get().switchAdvisor) {
            return null;
        } else {
            long now = System.currentTimeMillis();
            if (now - cachedAt < 500L) {
                return cached;
            } else {
                SwitchAdvisor.Pick pick = null;

                try {
                    pick = compute();
                } catch (Throwable var4) {
                }

                cached = pick;
                cachedAt = now;
                return pick;
            }
        }
    }

    public static SwitchAdvisor.Pick bestAmong(List<Pokemon> options) {
        if (ExtrasPlusConfig.get().switchAdvisor && options != null && !options.isEmpty()) {
            StringBuilder key = new StringBuilder();

            for (Pokemon p : options) {
                key.append(p.getUuid()).append(',');
            }

            long now = System.currentTimeMillis();
            if (now - tileCachedAt < 500L && key.toString().equals(tileCacheKey)) {
                return tileCached;
            } else {
                SwitchAdvisor.Pick pick = null;

                try {
                    pick = compute(options);
                } catch (Throwable var6) {
                }

                tileCached = pick;
                tileCachedAt = now;
                tileCacheKey = key.toString();
                return pick;
            }
        } else {
            return null;
        }
    }

    private static SwitchAdvisor.Pick compute() {
        return compute(null);
    }

    private static SwitchAdvisor.Pick compute(List<Pokemon> options) {
        ClientBattlePokemon opponent = MoveInsights.activeOpponent();
        ClientBattlePokemon mine = MoveInsights.activeMine();
        if (opponent == null) {
            return null;
        } else {
            UUID activeUuid = null;

            try {
                Pokemon active = mine != null ? PartyInfoSideRenderer.findPartyPokemonForBattlePokemon(mine) : null;
                activeUuid = active != null ? active.getUuid() : null;
            } catch (Throwable var20) {
            }

            SwitchAdvisor.Pick best = null;
            double bestScore = Double.MAX_VALUE;
            int candidates = 0;

            for (Pokemon p : options != null ? options : LocalBattlePartyTracker.getLocalPartySnapshot()) {
                if (p != null && (options != null || p.getCurrentHealth() > 0 && !PartyInfoSideRenderer.isTrackedFainted(p)) && !p.getUuid().equals(activeUuid)
                    )
                 {
                    ThreatCalc.Report report = ThreatCalc.compute(opponent, p, null, p.getForm());
                    if (report != null) {
                        candidates++;
                        ThreatCalc.Threat worst = report.worst();
                        int worstPct = worst != null ? worst.maxPct() : 0;
                        double hazards = hazardPct(p);
                        double hpPct = 100.0 * p.getCurrentHealth() / Math.max(1, p.getMaxHealth());
                        double score = worstPct + hazards;
                        if (worstPct + hazards >= hpPct) {
                            score += 200.0;
                        }

                        score += (100.0 - hpPct) * 0.3;
                        if (score < bestScore) {
                            bestScore = score;
                            String reason;
                            if (worst != null && worstPct != 0) {
                                reason = "takes ≤" + worstPct + "% (worst: " + worst.name() + ")";
                            } else {
                                reason = "no likely move hurts it";
                            }

                            if (hazards > 0.5) {
                                reason = reason + ", hazards −" + Math.round(hazards) + "%";
                            }

                            best = new SwitchAdvisor.Pick(
                                p.getUuid(),
                                p.getDisplayName(false).getString(),
                                worstPct,
                                (int)Math.round(hazards),
                                worst != null ? worst.name() : null,
                                reason
                            );
                        }
                    }
                }
            }

            return candidates >= 1 ? best : null;
        }
    }

    private static Class<?> controller() throws ClassNotFoundException {
        if (controller == null) {
            controller = Class.forName("name.modid.client.CustomBattleController");
        }

        return controller;
    }

    private static Object call(String name, Object tile) throws Exception {
        Method m = controller().getDeclaredMethod(name, Object.class);
        m.setAccessible(true);
        return m.invoke(null, tile);
    }

    public static void init() {
        Services.EVENTS.registerScreenRender((screen, graphics, mouseX, mouseY, tickDelta) -> {
            try {
                if (screen != null && screen.getClass().getName().contains("BattleGUI")) {
                    renderOnSwitchScreen(graphics);
                }
            } catch (Throwable var6) {
            }
        });
    }

    public static void renderOnSwitchScreen(class_332 gui) {
        try {
            if (!ExtrasPlusConfig.get().switchAdvisor) {
                return;
            }

            if (!(class_310.method_1551().field_1755 instanceof BattleGUI battleGui)) {
                return;
            }

            if (!(battleGui.getCurrentActionSelection() instanceof BattleSwitchPokemonSelection switchSelection)) {
                return;
            }

            List<SwitchTile> tiles = switchSelection.getTiles();
            List<Pokemon> options = new ArrayList<>();

            for (SwitchTile tile : tiles) {
                if (tile != null && tile.getPokemon() != null && !tile.isFainted() && !tile.isCurrentlyInBattle()) {
                    options.add(tile.getPokemon());
                }
            }

            SwitchAdvisor.Pick pick = bestAmong(options);
            if (pick == null) {
                return;
            }

            for (SwitchTile tilex : tiles) {
                if (tilex != null && tilex.getPokemon() != null && tilex.getPokemon().getUuid().equals(pick.uuid())) {
                    int x = Math.round(tilex.getX());
                    int y = Math.round(tilex.getY());
                    int w = 94;
                    int h = 29;

                    try {
                        int cx = ((Number)call("getTileX", tilex)).intValue();
                        int cy = ((Number)call("getTileY", tilex)).intValue();
                        int cw = ((Number)call("getTileWidth", tilex)).intValue();
                        int ch = ((Number)call("getTileHeight", tilex)).intValue();
                        if (cw > 0 && ch > 0) {
                            x = cx;
                            y = cy;
                            w = cw;
                            h = ch;
                        }
                    } catch (Throwable var18) {
                    }

                    drawStar(gui, x, y, w, h);
                }
            }
        } catch (Throwable var19) {
        }
    }

    private static void drawStar(class_332 gui, int x, int y, int w, int h) {
        class_327 font = class_310.method_1551().field_1772;
        int gold = -10496;
        gui.method_51448().method_22903();
        gui.method_51448().method_46416(0.0F, 0.0F, 420.0F);
        UiStyle.outline(gui, x - 1, y - 1, x + w + 1, y + h + 1, gold);
        UiStyle.outline(gui, x - 2, y - 2, x + w + 2, y + h + 2, 1728042752);
        String label = "★ BEST";
        int lw = AttachedPanelRenderer.width(font, label, 0.5F) + 4;
        UiStyle.fill(gui, x + w - lw - 1, y - 3, x + w + 1, y + 3, -12964352);
        UiStyle.outline(gui, x + w - lw - 1, y - 3, x + w + 1, y + 3, gold);
        gui.method_51448().method_22903();
        gui.method_51448().method_46416(x + w - lw + 1, y - 2, 0.0F);
        gui.method_51448().method_22905(0.5F, 0.5F, 1.0F);
        UltraFont.draw(gui, font, label, 0, 0, gold, false);
        gui.method_51448().method_22909();
        gui.method_51448().method_22909();
    }

    public record Pick(UUID uuid, String name, int worstPct, int hazardPct, String worstMove, String reason) {
    }
}
