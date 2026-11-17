package name.modid.client;

import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.battle.ActiveClientBattlePokemon;
import com.cobblemon.mod.common.client.battle.ClientBattle;
import com.cobblemon.mod.common.client.battle.ClientBattleActor;
import com.cobblemon.mod.common.client.battle.ClientBattleSide;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import name.modid.platform.Services;
import net.minecraft.class_2561;
import net.minecraft.class_2960;
import net.minecraft.class_310;
import net.minecraft.class_327;
import net.minecraft.class_332;

public final class FieldHud {
    private static final int ICON = 18;
    private static final int GAP = 3;

    private FieldHud() {
    }

    private static class_2960 tex(String name) {
        return class_2960.method_60655("cobblemon-battle-extras", "textures/gui/" + name + ".png");
    }

    public static void init() {
        if (Services.PLATFORM.isModLoaded("cobblemon")) {
            Services.EVENTS.registerHudRender((gui, tickDelta) -> {
                try {
                    render(gui);
                } catch (Throwable var3) {
                }
            });
        }
    }

    public static boolean active() {
        return ExtrasPlusConfig.get().fieldHudEnabled;
    }

    private static void render(class_332 gui) {
        if (active()) {
            ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
            if (battle != null) {
                float opacity = BattleOpacityHelper.getBattleOpacity();
                if (!(opacity <= 0.01F)) {
                    class_310 mc = class_310.method_1551();
                    int screenWidth = mc.method_22683().method_4486();
                    float scale = ExtrasPlusConfig.get().fieldHudScale;
                    int offsetY = ExtrasPlusConfig.get().fieldHudOffsetY;
                    double mouseX = BattleHudTooltipHelper.canRenderTooltips(mc) ? BattleHudTooltipHelper.getMouseGuiX(mc) : Double.NaN;
                    double mouseY = BattleHudTooltipHelper.canRenderTooltips(mc) ? BattleHudTooltipHelper.getMouseGuiY(mc) : Double.NaN;
                    List<FieldHud.Badge> center = centerBadges();
                    List<FieldHud.Badge> player = sideBadges("player");
                    List<FieldHud.Badge> opponent = sideBadges("opponent");
                    int[] playerEdge = tileEdges(mc, battle, true);
                    int[] opponentEdge = tileEdges(mc, battle, false);
                    int size = Math.round(18.0F * scale);
                    int step = size + 3;
                    int rowY = 12 + offsetY;
                    FieldHud.Badge hovered = null;
                    int hoverX = 0;
                    int hoverY = 0;
                    RenderSystem.enableBlend();
                    RenderSystem.defaultBlendFunc();
                    int centerHalf = center.isEmpty() ? 0 : (center.size() * step - 3) / 2 + 6;
                    int playerLimit = screenWidth / 2 - centerHalf - 4;
                    int opponentLimit = screenWidth / 2 + centerHalf + 4;
                    int x = playerEdge[0] + 6;
                    int y = rowY;

                    for (FieldHud.Badge badge : player) {
                        if (x + size > playerLimit && x > playerEdge[0] + 6) {
                            x = playerEdge[0] + 6;
                            y += step;
                        }

                        drawBadge(gui, mc.field_1772, badge, x, y, size, opacity);
                        if (inside(mouseX, mouseY, x, y, size)) {
                            hovered = badge;
                            hoverX = x;
                            hoverY = y;
                        }

                        x += step;
                    }

                    x = opponentEdge[1] - 6 - size;
                    y = rowY;

                    for (FieldHud.Badge badge : opponent) {
                        if (x < opponentLimit && x < opponentEdge[1] - 6 - size) {
                            x = opponentEdge[1] - 6 - size;
                            y += step;
                        }

                        drawBadge(gui, mc.field_1772, badge, x, y, size, opacity);
                        if (inside(mouseX, mouseY, x, y, size)) {
                            hovered = badge;
                            hoverX = x;
                            hoverY = y;
                        }

                        x -= step;
                    }

                    if (!center.isEmpty()) {
                        int totalW = center.size() * step - 3;
                        x = (screenWidth - totalW) / 2;
                        int cy = 14 + offsetY + Math.round(6.0F * scale);

                        for (FieldHud.Badge badge : center) {
                            drawBadge(gui, mc.field_1772, badge, x, cy, size, opacity);
                            if (inside(mouseX, mouseY, x, cy, size)) {
                                hovered = badge;
                                hoverX = x;
                                hoverY = cy;
                            }

                            x += step;
                        }
                    }

                    if (hovered != null) {
                        List<class_2561> lines = new ArrayList<>();
                        int accent = hovered.accent();
                        lines.add(class_2561.method_43470(hovered.name()).method_27694(s -> s.method_36139(accent & 16777215).method_10982(true)));

                        for (String line : hovered.info()) {
                            lines.add(class_2561.method_43470(line).method_27694(s -> s.method_36139(14211288)));
                        }

                        gui.method_51448().method_22903();
                        gui.method_51448().method_46416(0.0F, 0.0F, 800.0F);
                        CustomTooltipRenderer.renderTooltip(gui, lines, hoverX, hoverY + size + 4, accent & 16777215);
                        gui.method_51448().method_22909();
                    }
                }
            }
        }
    }

    private static boolean inside(double mx, double my, int x, int y, int size) {
        return !Double.isNaN(mx) && mx >= x && mx < x + size && my >= y && my < y + size;
    }

    private static int[] tileEdges(class_310 mc, ClientBattle battle, boolean playerSide) {
        int screenWidth = mc.method_22683().method_4486();
        int left = playerSide ? 12 : screenWidth - 152;
        int right = playerSide ? 152 : screenWidth - 12;
        if (UltraTileRenderer.active()) {
            int[] tile = UltraTileRenderer.bounds(playerSide);
            return new int[]{tile[0] + tile[2], tile[0]};
        } else {
            try {
                ClientBattleSide side = playerSide ? BattleSideHelper.getPlayerSide(battle) : BattleSideHelper.getOpponentSide(battle);
                boolean compact = battle.getBattleFormat().getBattleType().getPokemonPerSide() > 1;
                int rank = 0;
                boolean found = false;

                for (ClientBattleActor actor : side.getActors()) {
                    for (ActiveClientBattlePokemon active : actor.getActivePokemon()) {
                        if (active.getBattlePokemon() != null) {
                            CustomBattleTileRenderer.TilePlacement p = CustomBattleTileRenderer.calculateTilePlacement(
                                mc, battle, active, playerSide, rank++, compact
                            );
                            int l = Math.round(p.x());
                            int r = l + p.width();
                            if (!found) {
                                left = l;
                                right = r;
                                found = true;
                            } else {
                                left = Math.min(left, l);
                                right = Math.max(right, r);
                            }
                        }
                    }
                }
            } catch (Throwable var17) {
            }

            return new int[]{right, left};
        }
    }

    private static void drawBadge(class_332 gui, class_327 font, FieldHud.Badge badge, int x, int y, int size, float opacity) {
        gui.method_51448().method_22903();
        gui.method_51448().method_46416(0.0F, 0.0F, 200.0F);
        gui.method_51422(1.0F, 1.0F, 1.0F, opacity);
        gui.method_25293(badge.texture(), x, y, size, size, 0.0F, 0.0F, 64, 64, 64, 64);
        gui.method_51422(1.0F, 1.0F, 1.0F, 1.0F);
        if (badge.counter() != null && !badge.counter().isEmpty()) {
            float scale = 0.55F;
            int tw = (int)Math.ceil(UltraFont.width(font, badge.counter()) * scale);
            int bx = x + size - tw - 1;
            int by = y + size - 5;
            gui.method_25294(bx - 2, by - 1, bx + tw + 1, by + 6, BattleOpacityHelper.applyOpacity(-535818224, opacity));
            gui.method_25294(bx - 2, by - 1, bx + tw + 1, by, BattleOpacityHelper.applyOpacity(0xFF000000 | badge.accent(), opacity));
            AttachedPanelRenderer.text(gui, font, badge.counter(), bx, by + 1, -1, scale);
        }

        gui.method_51448().method_22909();
    }

    private static String turns(int remaining, boolean canBeExtended, boolean extensionKnown) {
        if (remaining < 0) {
            return "∞";
        } else if (remaining == 0) {
            return "";
        } else {
            return canBeExtended && !extensionKnown ? remaining + "/" + (remaining + 3) : Integer.toString(remaining);
        }
    }

    private static List<String> turnInfo(int remaining, boolean canBeExtended, boolean extensionKnown, String itemName) {
        List<String> info = new ArrayList<>();
        if (remaining < 0) {
            info.add("Lasts until the Pokémon that set it leaves the field.");
        } else if (remaining > 0) {
            if (canBeExtended && !extensionKnown) {
                info.add("Turns left: " + remaining + " (or " + (remaining + 3) + " if the setter holds " + itemName + ")");
            } else {
                info.add("Turns left: " + remaining);
            }
        }

        return info;
    }

    private static List<FieldHud.Badge> centerBadges() {
        List<FieldHud.Badge> badges = new ArrayList<>();
        String weather = BattleMessageSubscriber.getCurrentWeather();
        if (weather != null && !weather.isEmpty()) {
            int turns = BattleMessageSubscriber.getWeatherTurns();
            boolean rock = BattleMessageSubscriber.isWeatherRockKnown();
            String icon;
            int accent;
            String desc;
            String rockName;
            switch (weather) {
                case "Sun":
                case "Harsh Sunlight":
                    icon = "weather_sun";
                    accent = 16757575;
                    desc = weather.equals("Sun")
                        ? "Fire moves ×1.5, Water moves ×0.5. Solar Beam needs no charge."
                        : "Extremely harsh sunlight: Water moves fail.";
                    rockName = "Heat Rock";
                    break;
                case "Rain":
                case "Heavy Rain":
                    icon = "weather_rain";
                    accent = 7317759;
                    desc = weather.equals("Rain") ? "Water moves ×1.5, Fire moves ×0.5. Thunder & Hurricane never miss." : "Heavy rain: Fire moves fail.";
                    rockName = "Damp Rock";
                    break;
                case "Sandstorm":
                    icon = "weather_sand";
                    accent = 14266991;
                    desc = "Rock types get +50% Sp. Def. Non Rock/Ground/Steel lose 1/16 HP each turn.";
                    rockName = "Smooth Rock";
                    break;
                case "Hail":
                    icon = "weather_hail";
                    accent = 11065599;
                    desc = "Non Ice types lose 1/16 HP each turn. Blizzard never misses.";
                    rockName = "Icy Rock";
                    break;
                case "Strong Winds":
                    icon = "weather_wind";
                    accent = 8378568;
                    desc = "Moves super effective on Flying types become neutral.";
                    rockName = "";
                    break;
                default:
                    icon = "weather_snow";
                    accent = 13628415;
                    desc = "Ice types get +50% Defense. Blizzard never misses.";
                    rockName = "Icy Rock";
            }

            List<String> info = new ArrayList<>();
            info.add(desc);
            info.addAll(turnInfo(turns, !rockName.isEmpty(), rock, rockName));
            badges.add(new FieldHud.Badge(tex("plus/" + icon), weather, turns(turns, !rockName.isEmpty(), rock), accent, info));
        }

        String terrain = BattleMessageSubscriber.getCurrentTerrain();
        if (terrain != null && !terrain.isEmpty()) {
            int turns = BattleMessageSubscriber.getTerrainTurns();
            String lower = terrain.toLowerCase();
            String icon;
            int accent;
            String desc;
            if (lower.contains("electric")) {
                icon = "terrain_electric";
                accent = 16769354;
                desc = "Grounded Pokémon: Electric moves ×1.3 and can't fall asleep.";
            } else if (lower.contains("grassy")) {
                icon = "terrain_grassy";
                accent = 8182634;
                desc = "Grounded Pokémon: Grass moves ×1.3, heal 1/16 HP per turn. Earthquake/Bulldoze halved.";
            } else if (lower.contains("misty")) {
                icon = "terrain_misty";
                accent = 16751317;
                desc = "Grounded Pokémon: Dragon moves halved, protected from status conditions.";
            } else {
                icon = "terrain_psychic";
                accent = 13073919;
                desc = "Grounded Pokémon: Psychic moves ×1.3, protected from priority moves.";
            }

            List<String> info = new ArrayList<>();
            info.add(desc);
            info.addAll(turnInfo(turns, true, false, "a Terrain Extender"));
            badges.add(new FieldHud.Badge(tex("plus/" + icon), terrain, turns(turns, true, false), accent, info));
        }

        addRoom(
            badges,
            BattleMessageSubscriber.getTrickRoomTurns(),
            "room_trick",
            "Trick Room",
            16740312,
            "The slower Pokémon moves first (within the same priority)."
        );
        addRoom(
            badges,
            BattleMessageSubscriber.getGravityTurns(),
            "room_gravity",
            "Gravity",
            11767039,
            "Everyone is grounded, accuracy ×1.67, flying moves can't be used."
        );
        addRoom(badges, BattleMessageSubscriber.getWonderRoomTurns(), "room_wonder", "Wonder Room", 7317759, "Defense and Sp. Def are swapped.");
        addRoom(badges, BattleMessageSubscriber.getMagicRoomTurns(), "room_magic", "Magic Room", 13073919, "Held items have no effect.");
        return badges;
    }

    private static void addRoom(List<FieldHud.Badge> badges, int turns, String icon, String name, int accent, String desc) {
        if (turns > 0) {
            List<String> info = new ArrayList<>();
            info.add(desc);
            info.addAll(turnInfo(turns, false, true, ""));
            badges.add(new FieldHud.Badge(tex("plus/" + icon), name, Integer.toString(turns), accent, info));
        }
    }

    private static List<FieldHud.Badge> sideBadges(String side) {
        List<FieldHud.Badge> badges = new ArrayList<>();
        String owner = "player".equals(side) ? "Your side" : "Opponent's side";
        Map<String, Integer> hazards = BattleMessageSubscriber.getHazardsForSide(side);
        if (hazards != null) {
            addHazard(badges, hazards, "Stealth Rock", "stealth_rock", 13214571, owner, "Switch-ins take Rock-type damage (12.5% if neutral, up to 50%).");
            addHazard(badges, hazards, "Spikes", "spikes", 14721120, owner, "Grounded switch-ins lose 1/8, 1/6 or 1/4 HP (1, 2 or 3 layers).");
            addHazard(
                badges,
                hazards,
                "Toxic Spikes",
                "toxic_spikes",
                13073919,
                owner,
                "Grounded switch-ins are poisoned (badly poisoned with 2 layers). A grounded Poison type removes them."
            );
            addHazard(badges, hazards, "Sticky Web", "sticky_web", 14215274, owner, "Grounded switch-ins get −1 Speed.");
        }

        Map<String, Integer> screens = BattleMessageSubscriber.getScreensForSide(side);
        if (screens != null) {
            boolean clay = BattleMessageSubscriber.isLightClayKnown();
            addScreen(badges, screens, "Reflect", "screen_reflect", 16752988, true, clay, owner, "Physical damage taken is halved.");
            addScreen(badges, screens, "Light Screen", "screen_light", 16769354, true, clay, owner, "Special damage taken is halved.");
            addScreen(badges, screens, "Aurora Veil", "screen_veil", 8382709, true, clay, owner, "Physical and special damage taken is halved.");
            addScreen(badges, screens, "Safeguard", "screen_safeguard", 8182682, false, true, owner, "Protected from status conditions and confusion.");
            addScreen(badges, screens, "Mist", "screen_mist", 13162720, false, true, owner, "Stats can't be lowered by the opponent.");
            addScreen(badges, screens, "Lucky Chant", "screen_luckychant", 13166698, false, true, owner, "The opponent can't land critical hits.");
        }

        int tailwind = BattleMessageSubscriber.getTailwindTurns(side);
        if (tailwind > 0) {
            List<String> info = new ArrayList<>();
            info.add(owner);
            info.add("Speed is doubled.");
            info.add("Turns left: " + tailwind);
            badges.add(new FieldHud.Badge(tex("tailwind"), "Tailwind", Integer.toString(tailwind), 9427199, info));
        }

        for (VolatileTracker.Badge v : VolatileTracker.badges(side)) {
            badges.add(new FieldHud.Badge(tex("plus/" + v.icon()), v.title(), v.counter(), v.accent(), v.info()));
        }

        return badges;
    }

    private static void addHazard(List<FieldHud.Badge> badges, Map<String, Integer> hazards, String name, String icon, int accent, String owner, String desc) {
        Integer layers = hazards.get(name);
        if (layers != null && layers > 0) {
            List<String> info = new ArrayList<>();
            info.add(owner);
            info.add(desc);
            if (!name.equals("Stealth Rock") && !name.equals("Sticky Web")) {
                info.add("Layers: " + layers);
            }

            info.add("Removed by Rapid Spin, Defog, Mortal Spin, Tidy Up or Court Change.");
            badges.add(new FieldHud.Badge(tex(icon), name, layers > 1 ? "×" + layers : "", accent, info));
        }
    }

    private static void addScreen(
        List<FieldHud.Badge> badges,
        Map<String, Integer> screens,
        String name,
        String icon,
        int accent,
        boolean clayable,
        boolean clayKnown,
        String owner,
        String desc
    ) {
        Integer turns = screens.get(name);
        if (turns != null && turns > 0) {
            List<String> info = new ArrayList<>();
            info.add(owner);
            info.add(desc);
            info.addAll(turnInfo(turns, clayable, clayKnown, "Light Clay"));
            badges.add(new FieldHud.Badge(tex("plus/" + icon), name, turns(turns, clayable, clayKnown), accent, info));
        }
    }

    private record Badge(class_2960 texture, String name, String counter, int accent, List<String> info) {
    }
}
