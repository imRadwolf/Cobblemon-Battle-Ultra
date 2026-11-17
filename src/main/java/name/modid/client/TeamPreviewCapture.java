package name.modid.client;

import com.cobblemon.mod.common.CobblemonItemComponents;
import com.cobblemon.mod.common.api.battles.model.actor.ActorType;
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.client.battle.ClientBattle;
import com.cobblemon.mod.common.client.battle.ClientBattleActor;
import com.cobblemon.mod.common.client.battle.ClientBattleSide;
import com.cobblemon.mod.common.item.components.PokemonItemComponent;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Species;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import name.modid.client.net.TeamBallClientData;
import name.modid.net.payload.PokemonTeamData;
import name.modid.platform.Services;
import net.minecraft.class_1661;
import net.minecraft.class_1703;
import net.minecraft.class_1735;
import net.minecraft.class_1799;
import net.minecraft.class_437;
import net.minecraft.class_465;

public final class TeamPreviewCapture {
    private static final Pattern LEVEL_PATTERN = Pattern.compile("\\(lvl\\s*(\\d+)\\)", 2);
    private static final Pattern FORMATTING_PATTERN = Pattern.compile("(?i)§[0-9A-FK-OR]");
    private static List<TeamPreviewCapture.PreviewMon> pending = List.of();
    private static String pendingRival = null;
    private static long pendingTime = 0L;
    private static boolean pendingConsumed = true;
    private static Object appliedBattle = null;
    private static final Set<UUID> INJECTED_ACTORS = new HashSet<>();
    private static final Map<UUID, List<TeamPreviewCapture.PreviewMon>> ACTIVE = new HashMap<>();

    private TeamPreviewCapture() {
    }

    public static void init() {
        Services.EVENTS.registerScreenRender((screen, gui, mouseX, mouseY, tickDelta) -> {
            try {
                capture(screen);
            } catch (Throwable var6) {
            }
        });
    }

    private static String lastLoggedScreen;

    /** One Pokemon item found in the lead-selection menu. */
    private record Found(int row, int col, Species species, Set<String> aspects, String name, int level, String owner, boolean taggedOpponent) {
    }

    private static final Pattern VS_PATTERN = Pattern.compile("\\bvs\\.?\\s+([A-Za-z0-9_]{3,16})", Pattern.CASE_INSENSITIVE);

    /**
     * Reads the opponent's team from the server's lead-selection menu (/challenge, /challengedouble4v4, /challenge accept...).
     * The menu layout differs between commands (chest size, which column holds whose team), so instead of a fixed column it
     * finds every Pokemon item, then tells the two teams apart by owner name ("Rival's Garchomp") or, failing that, by which
     * column does not match your own party.
     */
    private static void capture(class_437 screen) {
        if (ExtrasPlusConfig.get().teamPreviewCapture && screen instanceof class_465<?> handled) {
            class_1703 handler = handled.method_17577();
            if (handler != null) {
                List<class_1735> slots = handler.field_7761;
                List<class_1735> container = new ArrayList<>();

                for (class_1735 slot : slots) {
                    if (!(slot.field_7871 instanceof class_1661)) {
                        container.add(slot);
                    }
                }

                if (container.size() < 9 || container.size() % 9 != 0) {
                    return;
                }

                List<Found> found = new ArrayList<>();
                for (int i = 0; i < container.size(); i++) {
                    class_1799 stack = container.get(i).method_7677();
                    if (stack == null || stack.method_7960()) {
                        continue;
                    }
                    PokemonItemComponent component = (PokemonItemComponent)stack.method_57824(CobblemonItemComponents.POKEMON_ITEM);
                    if (component == null || component.getSpecies() == null) {
                        continue;
                    }
                    Species species = PokemonSpecies.getByIdentifier(component.getSpecies());
                    if (species == null) {
                        continue;
                    }
                    String name = stripFormatting(stack.method_7964().getString());
                    int possessive = name.indexOf("'s ");
                    String owner = possessive > 0 ? name.substring(0, possessive).trim() : null;
                    int level = -1;
                    Matcher matcher = LEVEL_PATTERN.matcher(name);
                    if (matcher.find()) {
                        try {
                            level = Integer.parseInt(matcher.group(1));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    Set<String> aspects = component.getAspects() != null ? Set.copyOf(component.getAspects()) : Set.of();
                    // lore line under the name, e.g. "Opponent" in CobbleGalaxy's "Doubles 4v4 vs X" menu
                    boolean tagged = false;
                    try {
                        net.minecraft.class_9290 lore = stack.method_57824(net.minecraft.class_9334.field_49632);
                        if (lore != null) {
                            for (net.minecraft.class_2561 line : lore.comp_2400()) {
                                String l = stripFormatting(line.getString()).toLowerCase(Locale.ROOT);
                                if (l.contains("opponent") || l.contains("adversaire") || l.contains("rival")) {
                                    tagged = true;
                                }
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                    found.add(new Found(i / 9, i % 9, species, aspects, name, level, owner, tagged));
                }

                if (found.isEmpty()) {
                    return;
                }

                String title = stripFormatting(screen.method_25440().getString());
                logScreen(title, container.size(), found);
                String lowerTitle = title.toLowerCase(Locale.ROOT);
                boolean titleMatch = lowerTitle.contains("lead")
                    || lowerTitle.contains("select") && (lowerTitle.contains("pokemon") || lowerTitle.contains("pokémon") || lowerTitle.contains("team"))
                    || lowerTitle.contains("team preview")
                    || lowerTitle.contains("challenge")
                    || VS_PATTERN.matcher(title).find();
                String me = myName();
                Set<String> mySpecies = myPartySpecies();

                List<Found> theirs = new ArrayList<>();
                String rival = null;
                Matcher vs = VS_PATTERN.matcher(title);
                if (vs.find()) {
                    rival = vs.group(1);
                }

                // 1) items tagged "Opponent" in their description
                for (Found f : found) {
                    if (f.taggedOpponent()) {
                        theirs.add(f);
                    }
                }

                // 2) owner names: everything not owned by you is theirs
                if (theirs.isEmpty()) {
                    for (Found f : found) {
                        if (f.owner() != null && (me == null || !f.owner().equalsIgnoreCase(me))) {
                            theirs.add(f);
                            if (rival == null) {
                                rival = f.owner();
                            }
                        }
                    }
                }

                // 3) two halves split by the middle column (your team left, theirs right, or the other way round)
                if (theirs.isEmpty()) {
                    List<Found> left = new ArrayList<>();
                    List<Found> right = new ArrayList<>();
                    for (Found f : found) {
                        if (f.col() < 4) {
                            left.add(f);
                        } else if (f.col() > 4) {
                            right.add(f);
                        }
                    }
                    if (!left.isEmpty() && !right.isEmpty() && left.size() + right.size() == found.size()) {
                        int leftNotMine = 0;
                        int rightNotMine = 0;
                        for (Found f : left) {
                            if (!mySpecies.contains(normalizeId(speciesId(f.species())))) {
                                leftNotMine++;
                            }
                        }
                        for (Found f : right) {
                            if (!mySpecies.contains(normalizeId(speciesId(f.species())))) {
                                rightNotMine++;
                            }
                        }
                        theirs.addAll(leftNotMine > rightNotMine ? left : right);
                    }
                }
                boolean mineShown = false;
                for (Found f : found) {
                    if (f.owner() != null && me != null && f.owner().equalsIgnoreCase(me) || mySpecies.contains(normalizeId(speciesId(f.species())))) {
                        mineShown = true;
                    }
                }

                // 4) last resort: the column holding the most Pokemon that are not in your party
                if (theirs.isEmpty()) {
                    Map<Integer, List<Found>> byCol = new HashMap<>();
                    for (Found f : found) {
                        byCol.computeIfAbsent(f.col(), k -> new ArrayList<>()).add(f);
                    }
                    int bestCol = -1;
                    int bestScore = -1;
                    for (Map.Entry<Integer, List<Found>> e : byCol.entrySet()) {
                        int notMine = 0;
                        for (Found f : e.getValue()) {
                            if (!mySpecies.contains(normalizeId(speciesId(f.species())))) {
                                notMine++;
                            }
                        }
                        // ties: the rightmost column (the original layout)
                        int score = notMine * 16 + e.getKey();
                        if (score > bestScore) {
                            bestScore = score;
                            bestCol = e.getKey();
                        }
                    }
                    if (bestCol >= 0 && byCol.size() >= 2) {
                        theirs.addAll(byCol.get(bestCol));
                    } else if (bestCol >= 0 && titleMatch) {
                        theirs.addAll(byCol.get(bestCol));
                    }
                }

                // only trust menus that look like a battle preview (not the GTS, a PC or a shop showing Pokemon)
                boolean looksLikePreview = titleMatch || mineShown && !theirs.isEmpty() && theirs.size() < found.size();
                if (theirs.isEmpty() || !looksLikePreview || theirs.size() > 6) {
                    return;
                }

                theirs.sort((a, b) -> a.row() != b.row() ? Integer.compare(a.row(), b.row()) : Integer.compare(a.col(), b.col()));
                List<TeamPreviewCapture.PreviewMon> mons = new ArrayList<>();
                for (Found f : theirs) {
                    mons.add(new TeamPreviewCapture.PreviewMon(mons.size(), f.species(), f.aspects(), f.level()));
                }

                pending = List.copyOf(mons);
                pendingRival = rival;
                pendingTime = System.currentTimeMillis();
                pendingConsumed = false;
            }
        }
    }

    private static void logScreen(String title, int size, List<Found> found) {
        String key = title + "|" + size + "|" + found.size();
        if (!key.equals(lastLoggedScreen)) {
            lastLoggedScreen = key;
            StringBuilder sb = new StringBuilder();
            for (Found f : found) {
                sb.append(" [r").append(f.row()).append("c").append(f.col()).append(' ').append(f.name()).append(']');
            }
            System.out.println("[Battle Ultra] Pokemon menu \"" + title + "\" (" + size + " slots):" + sb);
        }
    }

    private static String myName() {
        try {
            return net.minecraft.class_310.method_1551().field_1724.method_5477().getString();
        } catch (Throwable t) {
            return null;
        }
    }

    private static Set<String> myPartySpecies() {
        Set<String> out = new HashSet<>();
        try {
            for (com.cobblemon.mod.common.pokemon.Pokemon p : com.cobblemon.mod.common.client.CobblemonClient.INSTANCE.getStorage().getParty()) {
                if (p != null) {
                    out.add(normalizeId(speciesId(p.getSpecies())));
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    public static void applyToBattle(ClientBattle battle, ClientBattleSide opponentSide) {
        if (!BattleKey.same(battle, appliedBattle)) {
            appliedBattle = BattleKey.of(battle);

            for (UUID actor : INJECTED_ACTORS) {
                TeamBallClientData.remove(actor);
            }

            INJECTED_ACTORS.clear();
            ACTIVE.clear();
        }

        if (!pendingConsumed && ExtrasPlusConfig.get().teamPreviewCapture && opponentSide != null && !pending.isEmpty()) {
            long age = System.currentTimeMillis() - pendingTime;
            if (age > ExtrasPlusConfig.get().teamPreviewMaxAgeSeconds * 1000L) {
                pendingConsumed = true;
            } else {
                ClientBattleActor target = chooseTargetActor(opponentSide);
                if (target != null && target.getUuid() != null) {
                    UUID uuid = target.getUuid();
                    pendingConsumed = true;
                    if (TeamBallClientData.hasData(uuid) && ExtrasPlusConfig.get().teamPreviewOverrideServerData) {
                        TeamBallClientData.remove(uuid);
                    }

                    if (TeamBallClientData.hasData(uuid)) {
                        ACTIVE.put(uuid, pending);
                    } else {
                        List<PokemonTeamData> team = new ArrayList<>();

                        for (TeamPreviewCapture.PreviewMon mon : pending) {
                            team.add(new PokemonTeamData(mon.slot(), speciesId(mon.species()), formId(mon), "poke_ball", false));
                        }

                        TeamBallClientData.setTeamData(uuid, team);
                        INJECTED_ACTORS.add(uuid);
                        ACTIVE.put(uuid, pending);
                    }
                }
            }
        }
    }

    private static ClientBattleActor chooseTargetActor(ClientBattleSide opponentSide) {
        List<ClientBattleActor> players = new ArrayList<>();

        for (ClientBattleActor actor : opponentSide.getActors()) {
            if (actor != null && (ActorType.PLAYER.equals(actor.getType()) || ExtrasPlusConfig.get().teamPreviewAllowNonPlayers)) {
                players.add(actor);
            }
        }

        if (players.isEmpty()) {
            return null;
        } else {
            if (pendingRival != null && !pendingRival.isBlank()) {
                String rival = pendingRival.toLowerCase(Locale.ROOT);

                for (ClientBattleActor actorx : players) {
                    try {
                        String name = stripFormatting(actorx.getDisplayName().getString()).toLowerCase(Locale.ROOT);
                        if (!name.isBlank() && (name.contains(rival) || rival.contains(name))) {
                            return actorx;
                        }
                    } catch (Throwable var6) {
                    }
                }
            }

            return players.size() == 1 ? players.get(0) : null;
        }
    }

    public static boolean isPreviewActor(UUID actorUuid) {
        return actorUuid != null && INJECTED_ACTORS.contains(actorUuid);
    }

    public static TeamPreviewCapture.PreviewMon getPreviewMon(UUID actorUuid, int slotIndex) {
        List<TeamPreviewCapture.PreviewMon> mons = actorUuid == null ? null : ACTIVE.get(actorUuid);
        if (mons != null && slotIndex >= 0) {
            String slotSpecies = normalizeId(TeamBallClientData.getSpeciesName(actorUuid, slotIndex));
            if (slotSpecies == null) {
                return null;
            } else if (slotIndex < mons.size() && slotSpecies.equals(normalizeId(speciesId(mons.get(slotIndex).species())))) {
                return mons.get(slotIndex);
            } else {
                for (TeamPreviewCapture.PreviewMon mon : mons) {
                    if (slotSpecies.equals(normalizeId(speciesId(mon.species())))) {
                        return mon;
                    }
                }

                return null;
            }
        } else {
            return null;
        }
    }

    private static String normalizeId(String raw) {
        if (raw == null) {
            return null;
        } else {
            String cleaned = raw.toLowerCase(Locale.ROOT);
            int colon = cleaned.indexOf(58);
            if (colon >= 0) {
                cleaned = cleaned.substring(colon + 1);
            }

            cleaned = cleaned.replaceAll("[^a-z0-9]", "");
            return cleaned.isEmpty() ? null : cleaned;
        }
    }

    private static String speciesId(Species species) {
        try {
            String id = species.showdownId();
            if (id != null && !id.isBlank()) {
                return id;
            }
        } catch (Throwable var2) {
        }

        return species.getName();
    }

    private static String formId(TeamPreviewCapture.PreviewMon mon) {
        try {
            FormData form = FormAbilityHelper.form(mon.species(), mon.aspects());
            if (form != null && form != mon.species().getStandardForm()) {
                String id = form.formOnlyShowdownId();
                if (id != null && !id.isBlank()) {
                    return id;
                }
            }
        } catch (Throwable var3) {
        }

        return "default";
    }

    private static String stripFormatting(String text) {
        return text == null ? "" : FORMATTING_PATTERN.matcher(text).replaceAll("");
    }

    public record PreviewMon(int slot, Species species, Set<String> aspects, int level) {
    }
}
