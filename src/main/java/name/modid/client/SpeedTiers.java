package name.modid.client;

import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Nature;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class SpeedTiers {
    public static volatile SpeedTiers.Report lastReport;
    public static volatile long lastReportTime;

    private SpeedTiers() {
    }

    private static String norm(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    public static int statFormula(int base, int iv, int ev, int level, double natureMult) {
        int raw = (2 * base + iv + ev / 4) * level / 100 + 5;
        return (int)Math.floor(raw * natureMult);
    }

    public static int applyStage(int value, int stage) {
        stage = Math.max(-6, Math.min(6, stage));
        return stage >= 0 ? value * (2 + stage) / 2 : value * 2 / (2 - stage);
    }

    public static int baseStat(FormData form, Stat stat) {
        if (form == null) {
            return 0;
        } else {
            try {
                MegaDex.Entry mega = MegaDex.forForm(form);
                int idx = mega != null ? MegaDex.statIndex(stat) : -1;
                if (idx >= 0) {
                    return mega.stats()[idx];
                } else {
                    Integer value = (Integer)form.getBaseStats().get(stat);
                    return value != null ? value : 0;
                }
            } catch (Throwable var5) {
                return 0;
            }
        }
    }

    public static int computeOwnStat(Pokemon pokemon, FormData form, Stat stat) {
        if (pokemon != null && form != null) {
            int base = baseStat(form, stat);
            if (base <= 0) {
                return 0;
            } else {
                int iv = pokemon.getIvs().getOrDefault(stat);
                int ev = pokemon.getEvs().getOrDefault(stat);
                double mult = 1.0;

                try {
                    Nature nature = pokemon.getEffectiveNature();
                    if (nature != null) {
                        if (stat.equals(nature.getIncreasedStat()) && !stat.equals(nature.getDecreasedStat())) {
                            mult = 1.1;
                        } else if (stat.equals(nature.getDecreasedStat()) && !stat.equals(nature.getIncreasedStat())) {
                            mult = 0.9;
                        }
                    }
                } catch (Throwable var9) {
                }

                return statFormula(base, iv, ev, pokemon.getLevel(), mult);
            }
        } else {
            return 0;
        }
    }

    public static int stageFor(String side, ClientBattlePokemon battlePokemon, String statKey) {
        if (battlePokemon == null) {
            return 0;
        } else {
            try {
                String displayName = battlePokemon.getDisplayName().getString();
                if (!BattleMessageSubscriber.getStatStagesForPokemon(side, displayName).isEmpty()) {
                    return BattleMessageSubscriber.getStatStage(side, displayName, statKey);
                } else {
                    String speciesName = battlePokemon.getSpecies().getName();
                    return !BattleMessageSubscriber.getStatStagesForPokemon(side, speciesName).isEmpty()
                        ? BattleMessageSubscriber.getStatStage(side, speciesName, statKey)
                        : 0;
                }
            } catch (Throwable var5) {
                return 0;
            }
        }
    }

    private static boolean isParalyzed(ClientBattlePokemon battlePokemon) {
        try {
            return battlePokemon != null && battlePokemon.getStatus() != null && "par".equals(battlePokemon.getStatus().getShowdownName());
        } catch (Throwable var2) {
            return false;
        }
    }

    private static boolean hasStatus(ClientBattlePokemon battlePokemon) {
        try {
            return battlePokemon != null && battlePokemon.getStatus() != null;
        } catch (Throwable var2) {
            return false;
        }
    }

    private static double weatherAbilityMult(String ability) {
        String a = norm(ability);
        String weather = norm(BattleMessageSubscriber.getCurrentWeather());
        String terrain = norm(BattleMessageSubscriber.getCurrentTerrain());
        if (a.isEmpty()) {
            return 1.0;
        } else if (a.equals("swiftswim") && weather.contains("rain")) {
            return 2.0;
        } else if (!a.equals("chlorophyll") || !weather.contains("sun") && !weather.contains("harsh")) {
            if (a.equals("sandrush") && weather.contains("sand")) {
                return 2.0;
            } else if (!a.equals("slushrush") || !weather.contains("snow") && !weather.contains("hail")) {
                return a.equals("surgesurfer") && terrain.contains("electric") ? 2.0 : 1.0;
            } else {
                return 2.0;
            }
        } else {
            return 2.0;
        }
    }

    public static SpeedTiers.Report forOpponent(ClientBattlePokemon opponent, PanelData myData) {
        SpeedTiers.Report report = new SpeedTiers.Report();
        if (opponent == null) {
            return report;
        } else {
            Species species = opponent.getSpecies();
            FormData form = FormAbilityHelper.effectiveBattleForm(opponent, "opponent");
            if (form == null && species != null) {
                form = species.getStandardForm();
            }

            report.subject = opponent.getDisplayName().getString();
            report.level = opponent.getLevel();
            report.baseSpeed = baseStat(form, Stats.SPEED);
            MegaDex.Entry megaEntry = MegaDex.forBattler(opponent, "opponent");
            if (megaEntry != null) {
                report.baseSpeed = megaEntry.stats()[5];
            }

            report.trickRoom = BattleMessageSubscriber.getTrickRoomTurns() > 0;
            int stage = stageFor("opponent", opponent, "speed");
            boolean tailwind = BattleMessageSubscriber.getTailwindTurns("opponent") > 0;
            boolean paralyzed = isParalyzed(opponent);
            if (stage != 0) {
                report.modifiers.add((stage > 0 ? "+" : "") + stage + " Speed");
            }

            if (tailwind) {
                report.modifiers.add("Tailwind ×2");
            }

            if (paralyzed) {
                report.modifiers.add("Paralysis ×0.5");
            }

            String revealedItem = ItemAbilityTracker.knownItemName("opponent", opponent);
            boolean knownScarf = revealedItem != null && norm(revealedItem).contains("choicescarf");
            boolean knownIronBall = revealedItem != null && norm(revealedItem).contains("ironball");
            if (knownScarf) {
                report.modifiers.add("Choice Scarf ×1.5 (revealed)");
            }

            boolean likelyScarf = false;
            if (revealedItem == null) {
                String inferred = BuildInference.inferredItem(opponent);
                if (inferred != null && norm(inferred).equals("choicescarf")) {
                    likelyScarf = true;
                    knownScarf = true;
                    report.modifiers.add("Choice Scarf ×1.5 (likely)");
                }
            }

            int level = report.level;
            int base = report.baseSpeed;
            report.theoMin = finish(statFormula(base, 0, 0, level, 0.9), stage, tailwind, paralyzed, false, knownScarf, knownIronBall, 1.0);
            report.theoNeutral = finish(statFormula(base, 31, 0, level, 1.0), stage, tailwind, paralyzed, false, knownScarf, knownIronBall, 1.0);
            report.theoMax = finish(statFormula(base, 31, 252, level, 1.1), stage, tailwind, paralyzed, false, knownScarf, knownIronBall, 1.0);
            report.theoMaxScarf = !knownScarf && revealedItem == null
                ? finish(statFormula(base, 31, 252, level, 1.1), stage, tailwind, paralyzed, true, false, false, 1.0)
                : report.theoMax;
            if (myData != null) {
                report.mySpeed = mySpeed(myData, report.myModifiers);

                try {
                    report.myName = myData.battlePokemon != null ? myData.battlePokemon.getDisplayName().getString() : "";
                } catch (Throwable var29) {
                }
            }

            if (species != null) {
                boolean[] fromBase = new boolean[1];
                BuildInference.Info inference = BuildInference.info(opponent);
                List<SmogonSets.Set> allBuilds = inference != null ? inference.all : SmogonSets.forForm(species, form, fromBase);
                Set<String> alive = new HashSet<>();
                if (inference != null) {
                    fromBase[0] = inference.fromBase;
                    report.ruledOut = inference.ruledOut();
                    report.memoryBattles = inference.hasMemory() ? Math.max(1, inference.memoryBattles) : 0;
                    report.reasons.addAll(inference.reasons);

                    for (SmogonSets.Set c : inference.candidates) {
                        alive.add(c.id());
                    }
                }

                Map<String, Integer> speedBySet = new HashMap<>();

                for (SmogonSets.Set build : allBuilds) {
                    boolean scarf = norm(build.item()).contains("choicescarf");
                    boolean ironBall = norm(build.item()).contains("ironball");
                    if (revealedItem != null || likelyScarf) {
                        scarf = knownScarf;
                        ironBall = knownIronBall;
                    }

                    double abilityMult = weatherAbilityMult(build.ability());
                    if (norm(build.ability()).equals("quickfeet") && hasStatus(opponent)) {
                        abilityMult = 1.5;
                    }

                    int speed = finish(
                        statFormula(base, build.speIv(), build.evs()[5], level, build.natureMult(5)),
                        stage,
                        tailwind,
                        paralyzed,
                        scarf,
                        false,
                        ironBall,
                        abilityMult
                    );
                    speedBySet.put(build.id(), speed);
                    if (inference == null || alive.contains(build.id())) {
                        StringBuilder detail = new StringBuilder();
                        detail.append(build.nature()).append(' ').append(build.evs()[5]).append(" EVs");
                        if (build.speIv() != 31) {
                            detail.append(", ").append(build.speIv()).append(" IV");
                        }

                        if (scarf) {
                            detail.append(", Scarf");
                        }

                        if (ironBall) {
                            detail.append(", Iron Ball");
                        }

                        if (abilityMult > 1.0) {
                            detail.append(", ").append(build.ability());
                        }

                        String chip = (scarf ? "Scarf " : "") + SmogonSets.shortNature(build.nature()) + " " + build.evs()[5];
                        report.builds.add(new SpeedTiers.Tier(build.label(), detail.toString(), speed, verdictColor(report, speed), true, chip));
                    }
                }

                report.fromBaseForm = fromBase[0];
                report.totalBuilds = allBuilds.size();
                BuildInference.recordSpeeds(opponent, speedBySet, report.mySpeed, report.trickRoom, report.theoMax, report.theoMaxScarf);
                if (myData != null) {
                    lastReport = report;
                    lastReportTime = System.currentTimeMillis();
                }
            }

            return report;
        }
    }

    private static int finish(int raw, int stage, boolean tailwind, boolean paralyzed, boolean scarf, boolean knownScarf, boolean ironBall, double abilityMult) {
        double value = applyStage(raw, stage);
        if (scarf || knownScarf) {
            value = Math.floor(value * 1.5);
        }

        if (ironBall) {
            value = Math.floor(value * 0.5);
        }

        value = Math.floor(value * abilityMult);
        if (tailwind) {
            value *= 2.0;
        }

        if (paralyzed) {
            value = Math.floor(value * 0.5);
        }

        return (int)value;
    }

    public static Integer mySpeed(PanelData data, List<String> modifiers) {
        if (data != null && data.partyPokemon != null) {
            Pokemon pokemon = data.partyPokemon;
            int raw;
            if (data.form != null && FormAbilityHelper.isBattleOnlyForm(data.form)) {
                raw = computeOwnStat(pokemon, data.form, Stats.SPEED);
            } else {
                raw = pokemon.getStat(Stats.SPEED);
            }

            if (raw <= 0) {
                return null;
            } else {
                int stage = stageFor("player", data.battlePokemon, "speed");
                boolean tailwind = BattleMessageSubscriber.getTailwindTurns("player") > 0;
                boolean paralyzed = isParalyzed(data.battlePokemon);
                boolean scarf = false;
                boolean ironBall = false;

                try {
                    String itemName = norm(data.itemLost ? "" : data.itemText);
                    scarf = itemName.contains("choicescarf");
                    ironBall = itemName.contains("ironball");
                } catch (Throwable var11) {
                }

                double abilityMult = weatherAbilityMult(data.abilityId);
                if (norm(data.abilityId).equals("quickfeet") && hasStatus(data.battlePokemon)) {
                    abilityMult = 1.5;
                }

                if (modifiers != null) {
                    if (stage != 0) {
                        modifiers.add((stage > 0 ? "+" : "") + stage + " Speed");
                    }

                    if (scarf) {
                        modifiers.add("Choice Scarf ×1.5");
                    }

                    if (ironBall) {
                        modifiers.add("Iron Ball ×0.5");
                    }

                    if (abilityMult > 1.0) {
                        modifiers.add(FormAbilityHelper.prettyId(data.abilityText) + " ×" + (abilityMult == 2.0 ? "2" : "1.5"));
                    }

                    if (tailwind) {
                        modifiers.add("Tailwind ×2");
                    }

                    if (paralyzed) {
                        modifiers.add("Paralysis ×0.5");
                    }
                }

                return finish(raw, stage, tailwind, paralyzed, scarf, false, ironBall, abilityMult);
            }
        } else {
            return null;
        }
    }

    public static int verdictColor(SpeedTiers.Report report, int opponentSpeed) {
        if (report.mySpeed == null) {
            return -3618616;
        } else {
            int mine = report.mySpeed;
            if (mine == opponentSpeed) {
                return -10166;
            } else {
                boolean iMoveFirst = report.trickRoom ? mine < opponentSpeed : mine > opponentSpeed;
                return iMoveFirst ? -10755206 : -41892;
            }
        }
    }

    public static String verdictText(SpeedTiers.Report report, int opponentSpeed) {
        if (report.mySpeed == null) {
            return "";
        } else {
            int mine = report.mySpeed;
            if (mine == opponentSpeed) {
                return "speed tie";
            } else {
                boolean iMoveFirst = report.trickRoom ? mine < opponentSpeed : mine > opponentSpeed;
                return iMoveFirst ? "you move first" : "they move first";
            }
        }
    }

    public static final class Report {
        public String subject = "";
        public int baseSpeed;
        public int level;
        public int theoMin;
        public int theoNeutral;
        public int theoMax;
        public int theoMaxScarf;
        public final List<SpeedTiers.Tier> builds = new ArrayList<>();
        public Integer mySpeed;
        public String myName = "";
        public boolean trickRoom;
        public final List<String> modifiers = new ArrayList<>();
        public final List<String> myModifiers = new ArrayList<>();
        public boolean fromBaseForm;
        public int ruledOut;
        public int totalBuilds;
        public int memoryBattles;
        public final List<String> reasons = new ArrayList<>();
    }

    public record Tier(String label, String detail, int speed, int color, boolean smogon, String chip) {
    }
}
