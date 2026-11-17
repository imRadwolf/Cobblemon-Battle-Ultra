package name.modid.client;

import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.battle.ClientBattle;
import com.cobblemon.mod.common.client.battle.ClientBattleActor;
import com.cobblemon.mod.common.client.battle.ClientBattleSide;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;

public final class OpponentMemory {
    private static final String FILE_NAME = "cobblemon-battle-extras-plus-memory.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Map<String, OpponentMemory.Opponent> data;
    private static Map<String, OpponentMemory.Entry> previous = Map.of();
    private static int previousBattles;
    private static long previousLastBattle;
    private static Object trackedBattle;
    private static String currentKey;
    private static boolean dirty;
    private static long lastSave;

    private OpponentMemory() {
    }

    static String norm(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("cobblemon-battle-extras-plus-memory.json");
    }

    private static synchronized Map<String, OpponentMemory.Opponent> all() {
        if (data == null) {
            data = new LinkedHashMap<>();

            try {
                Path p = path();
                if (Files.exists(p)) {
                    try (Reader reader = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                        Type type = (new TypeToken<Map<String, OpponentMemory.Opponent>>() {}).getType();
                        Map<String, OpponentMemory.Opponent> loaded = (Map<String, OpponentMemory.Opponent>)GSON.fromJson(reader, type);
                        if (loaded != null) {
                            data.putAll(loaded);
                        }
                    }
                }
            } catch (Throwable var6) {
            }
        }

        return data;
    }

    private static synchronized void save() {
        try {
            Path p = path();
            Files.createDirectories(p.getParent());

            try (Writer writer = Files.newBufferedWriter(p, StandardCharsets.UTF_8)) {
                GSON.toJson(all(), writer);
            }

            dirty = false;
            lastSave = System.currentTimeMillis();
        } catch (Throwable var6) {
        }
    }

    private static String[] opponentIdentity(ClientBattle battle) {
        try {
            ClientBattleSide side = BattleSideHelper.getOpponentSide(battle);
            List<String> names = new ArrayList<>();

            for (ClientBattleActor actor : side.getActors()) {
                String name = actor.getDisplayName().getString();
                if (name != null && !name.isBlank()) {
                    names.add(name);
                }
            }

            if (names.isEmpty()) {
                return null;
            } else {
                String display = String.join(" & ", names);
                return new String[]{norm(display), display};
            }
        } catch (Throwable var6) {
            return null;
        }
    }

    static synchronized void tick() {
        if (ExtrasPlusConfig.get().opponentMemory) {
            ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
            if (battle != trackedBattle) {
                if (dirty) {
                    save();
                }

                trackedBattle = battle;
                currentKey = null;
                previous = Map.of();
                previousBattles = 0;
                if (battle != null) {
                    String[] id = opponentIdentity(battle);
                    if (id != null && !id[0].isEmpty()) {
                        currentKey = id[0];
                        OpponentMemory.Opponent opponent = all().get(currentKey);
                        if (opponent == null) {
                            opponent = new OpponentMemory.Opponent();
                            all().put(currentKey, opponent);
                        } else {
                            Map<String, OpponentMemory.Entry> snapshot = new LinkedHashMap<>();

                            for (Map.Entry<String, OpponentMemory.Entry> e : opponent.team.entrySet()) {
                                snapshot.put(e.getKey(), e.getValue().copy());
                            }

                            previous = snapshot;
                            previousBattles = opponent.battles;
                            previousLastBattle = opponent.lastBattle;
                        }

                        opponent.name = id[1];
                        opponent.battles++;
                        opponent.lastBattle = System.currentTimeMillis();
                        dirty = true;
                    }
                }
            } else if (dirty && System.currentTimeMillis() - lastSave > 15000L) {
                save();
            }
        }
    }

    public static OpponentMemory.Entry recall(String speciesKey) {
        if (!ExtrasPlusConfig.get().opponentMemory) {
            return null;
        } else {
            tick();
            return previous.get(speciesKey);
        }
    }

    public static int previousBattles() {
        return previousBattles;
    }

    public static long previousLastBattle() {
        return previousLastBattle;
    }

    static synchronized void record(String speciesKey, String item, String ability, Set<String> moves, Map<String, String> ruledOut) {
        if (ExtrasPlusConfig.get().opponentMemory && currentKey != null && speciesKey != null && !speciesKey.isEmpty()) {
            OpponentMemory.Opponent opponent = all().get(currentKey);
            if (opponent != null) {
                OpponentMemory.Entry entry = opponent.team.computeIfAbsent(speciesKey, k -> new OpponentMemory.Entry());
                boolean changed = false;
                if (item != null && !item.equals(entry.item)) {
                    entry.item = item;
                    changed = true;
                }

                if (ability != null && !ability.equals(entry.ability)) {
                    entry.ability = ability;
                    changed = true;
                }

                for (String move : moves) {
                    if (!entry.moves.contains(move)) {
                        entry.moves.add(0, move);
                        changed = true;
                    }
                }

                while (entry.moves.size() > 6) {
                    entry.moves.remove(entry.moves.size() - 1);
                }

                if (ruledOut != null && !ruledOut.isEmpty() && !ruledOut.equals(entry.ruledOut)) {
                    entry.ruledOut = new LinkedHashMap<>(ruledOut);
                    changed = true;
                }

                if (changed) {
                    entry.lastSeen = System.currentTimeMillis();
                    dirty = true;
                }
            }
        }
    }

    public static String age(long time) {
        long minutes = Math.max(0L, (System.currentTimeMillis() - time) / 60000L);
        if (minutes < 60L) {
            return minutes <= 1L ? "just now" : minutes + " min ago";
        } else {
            return minutes < 1440L ? minutes / 60L + " h ago" : minutes / 1440L + " d ago";
        }
    }

    public static final class Entry {
        public String item;
        public String ability;
        public List<String> moves = new ArrayList<>();
        public Map<String, String> ruledOut = new LinkedHashMap<>();
        public long lastSeen;

        OpponentMemory.Entry copy() {
            OpponentMemory.Entry e = new OpponentMemory.Entry();
            e.item = this.item;
            e.ability = this.ability;
            e.moves = new ArrayList<>(this.moves);
            e.ruledOut = new LinkedHashMap<>(this.ruledOut);
            e.lastSeen = this.lastSeen;
            return e;
        }
    }

    public static final class Opponent {
        public String name;
        public int battles;
        public long lastBattle;
        public Map<String, OpponentMemory.Entry> team = new LinkedHashMap<>();
    }
}
