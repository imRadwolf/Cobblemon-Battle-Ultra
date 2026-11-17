package name.modid.client;

import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Pokemon;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ThreatCalc {
    private static final Map<String, ThreatCalc.Report> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Long> CACHE_TIME = new ConcurrentHashMap<>();
    private static final Set<String> SLICING = Set.of(
        "aerialace",
        "airslash",
        "aquacutter",
        "behemothblade",
        "bitterblade",
        "ceaselessedge",
        "crosspoison",
        "cut",
        "furycutter",
        "kowtowcleave",
        "leafblade",
        "nightslash",
        "populationbomb",
        "psychocut",
        "razorleaf",
        "razorshell",
        "sacredsword",
        "secretsword",
        "slash",
        "solarblade",
        "stoneaxe",
        "xscissor",
        "tachyoncutter",
        "mightycleave"
    );

    private ThreatCalc() {
    }

    static String norm(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    public static ThreatCalc.Report compute(ClientBattlePokemon attacker, Pokemon target, ClientBattlePokemon targetBattle, FormData targetForm) {
        if (attacker != null && target != null) {
            String key = BuildInference.key(attacker) + "|" + target.getUuid() + "|" + (targetBattle != null);
            long now = System.currentTimeMillis();
            Long t = CACHE_TIME.get(key);
            ThreatCalc.Report cached = CACHE.get(key);
            if (cached != null && t != null && now - t < 300L) {
                return cached;
            } else {
                ThreatCalc.Report report = null;

                try {
                    report = compute0(attacker, target, targetBattle, targetForm);
                } catch (Throwable var11) {
                }

                if (report != null) {
                    CACHE.put(key, report);
                    CACHE_TIME.put(key, now);
                }

                return report;
            }
        } else {
            return null;
        }
    }

    private static int stage(String side, ClientBattlePokemon bp, String stat) {
        return bp == null ? 0 : SpeedTiers.stageFor(side, bp, stat);
    }

    private static double stageMult(int stage) {
        return stage >= 0 ? (2.0 + stage) / 2.0 : 2.0 / (2.0 - stage);
    }

    private static int myStat(Pokemon p, FormData form, Stat stat) {
        if (form != null && FormAbilityHelper.isBattleOnlyForm(form)) {
            int v = SpeedTiers.computeOwnStat(p, form, stat);
            if (v > 0) {
                return v;
            }
        }

        return p.getStat(stat);
    }

    public static ThreatCalc.Env prepare(ClientBattlePokemon attacker, Pokemon target, ClientBattlePokemon targetBattle, FormData targetForm) {
        BuildInference.Info info = BuildInference.info(attacker);
        if (info == null) {
            return null;
        } else {
            ThreatCalc.Env e = new ThreatCalc.Env();
            e.attacker = attacker;
            e.info = info;
            e.target = target;
            e.targetBattle = targetBattle;
            e.atkForm = FormAbilityHelper.effectiveBattleForm(attacker, "opponent");
            if (e.atkForm == null) {
                e.atkForm = attacker.getSpecies().getStandardForm();
            }

            e.targetForm = targetForm != null ? targetForm : target.getForm();
            e.level = Math.max(1, attacker.getLevel());

            for (ElementalType type : FormAbilityHelper.types(e.atkForm)) {
                e.atkTypes.add(norm(type.getName()));
            }

            for (ElementalType type : FormAbilityHelper.types(e.targetForm)) {
                e.defTypes.add(norm(type.getName()));
            }

            e.knownItem = ItemAbilityTracker.knownItemName("opponent", attacker);
            if (e.knownItem == null) {
                e.inferredItem = BuildInference.inferredItem(attacker);
                if (e.inferredItem == null) {
                    e.inferredItem = MovePower.ogerponMaskItem(e.atkForm);
                }
            }

            ItemAbilityTracker.State atkState = ItemAbilityTracker.get("opponent", attacker);
            e.knownAbility = info.revealedAbility != null ? norm(info.revealedAbility) : null;
            if (atkState != null && atkState.abilityTemporary && atkState.abilityId != null) {
                e.knownAbility = norm(atkState.abilityId);
            }

            boolean megaLike = false;

            try {
                megaLike = FormAbilityHelper.isBattleOnlyForm(e.atkForm);
            } catch (Throwable var13) {
            }

            if (e.knownAbility == null || megaLike && (atkState == null || !atkState.abilityTemporary)) {
                String single = FormAbilityHelper.singleFormAbilityId(attacker.getSpecies(), e.atkForm);
                if (single != null && !single.isBlank()) {
                    e.knownAbility = norm(single);
                }
            }

            e.atkSuppressed = atkState != null && atkState.abilitySuppressed;
            e.maxHp = Math.max(1, target.getMaxHealth());
            e.curHp = target.getCurrentHealth();
            if (targetBattle != null) {
                try {
                    if (targetBattle.isHpFlat()) {
                        e.curHp = Math.round(targetBattle.getHpValue());
                    } else {
                        e.curHp = Math.round(targetBattle.getHpValue() * e.maxHp);
                    }
                } catch (Throwable var12) {
                }
            }

            e.curHp = Math.max(1, Math.min(e.maxHp, e.curHp));

            try {
                ItemAbilityTracker.State mine = targetBattle != null ? ItemAbilityTracker.get("player", targetBattle) : null;
                if (mine != null && (mine.itemName != null || mine.itemLost)) {
                    e.myItem = mine.itemLost ? "" : norm(mine.itemName);
                } else if (target.heldItem() != null && !target.heldItem().method_7960()) {
                    e.myItem = norm(target.heldItem().method_7964().getString());
                }
            } catch (Throwable var17) {
            }

            try {
                ItemAbilityTracker.State mine = targetBattle != null ? ItemAbilityTracker.get("player", targetBattle) : null;
                if (mine != null && mine.abilityId != null) {
                    e.myAbility = norm(mine.abilityId);
                } else {
                    e.myAbility = targetBattle != null ? HiddenPowerHelper.playerAbility(target) : norm(target.getAbility().getTemplate().getName());
                }

                if (mine != null && mine.abilitySuppressed) {
                    e.myAbility = "";
                }
            } catch (Throwable var16) {
            }

            try {
                Object st = targetBattle != null ? targetBattle.getStatus() : target.getStatus();
                e.myStatus = st != null;
                if (targetBattle != null && targetBattle.getStatus() != null) {
                    e.myStatusId = norm(targetBattle.getStatus().getShowdownName());
                } else if (target.getStatus() != null) {
                    e.myStatusId = norm(target.getStatus().getStatus().getShowdownName());
                }
            } catch (Throwable var15) {
            }

            e.weather = norm(BattleMessageSubscriber.getCurrentWeather());
            e.terrain = norm(BattleMessageSubscriber.getCurrentTerrain());
            e.screens = BattleMessageSubscriber.getScreensForSide("player");

            try {
                e.oppStatusId = attacker.getStatus() == null ? "" : norm(attacker.getStatus().getShowdownName());
                e.oppBurned = e.oppStatusId.equals("brn");
                e.oppStatused = !e.oppStatusId.isEmpty();
            } catch (Throwable var11) {
            }

            try {
                e.oppRatio = attacker.isHpFlat() ? attacker.getHpValue() / Math.max(1.0F, attacker.getMaxHp()) : attacker.getHpValue();
                if (e.oppRatio > 1.0001F) {
                    e.oppRatio /= 100.0F;
                }
            } catch (Throwable var10) {
            }

            e.atkStageAtk = stage("opponent", attacker, "attack");
            e.atkStageSpa = stage("opponent", attacker, "special-attack");
            e.atkStageDef = stage("opponent", attacker, "defense");
            e.myStageDef = stage("player", targetBattle, "defense");
            e.myStageSpd = stage("player", targetBattle, "special-defense");
            e.myStageAtk = stage("player", targetBattle, "attack");
            e.atkBoosts = MovePower.positiveBoosts("opponent", attacker);
            e.myWeight = MovePower.weight(e.targetForm, e.myAbility, e.myItem);
            e.myGrounded = MovePower.grounded(e.defTypes, e.myAbility, e.myItem)
                && (targetBattle == null || !VolatileTracker.has("player", targetBattle.getSpecies().getName(), "magnetrise"));

            try {
                SpeedTiers.Report report = SpeedTiers.lastReport;
                if (targetBattle != null && report != null && report.mySpeed != null && System.currentTimeMillis() - SpeedTiers.lastReportTime < 20000L) {
                    e.mySpeed = report.mySpeed;
                } else {
                    e.mySpeed = SpeedTiers.applyStage(myStat(target, e.targetForm, Stats.SPEED), stage("player", targetBattle, "speed"));
                    if (norm(e.myItem).equals("choicescarf")) {
                        e.mySpeed = (int)Math.floor(e.mySpeed * 1.5);
                    }
                }
            } catch (Throwable var14) {
            }

            try {
                e.myResidual = Residual.perTurn(
                    "player",
                    targetBattle != null ? targetBattle.getSpecies().getName() : null,
                    e.defTypes,
                    e.myItem,
                    e.myAbility,
                    e.myStatusId,
                    targetBattle != null ? Residual.toxicTurns("player", targetBattle) : 0,
                    e.myGrounded
                );
            } catch (Throwable var9) {
            }

            return e;
        }
    }

    static String itemFor(ThreatCalc.Env e, SmogonSets.Set set) {
        return e.knownItem != null ? norm(e.knownItem) : (e.inferredItem != null ? norm(e.inferredItem) : norm(set.item()));
    }

    static String abilityFor(ThreatCalc.Env e, SmogonSets.Set set) {
        return e.atkSuppressed ? "" : (e.knownAbility != null ? e.knownAbility : norm(set.ability()));
    }

    static String moveType(ThreatCalc.Env e, MoveTemplate move, String moveId) {
        String override = MovePower.typeOverride(moveId, e.atkForm, !e.atkTypes.contains("flying"));
        return override != null ? override : norm(move.getElementalType().getName());
    }

    public static int[] perSet(ThreatCalc.Env e, String moveId, SmogonSets.Set set, String itemOverride, float hpBefore) {
        MoveTemplate move = Moves.getByName(moveId);
        if (e != null && move != null && !BuildInference.isStatus(move)) {
            int savedHp = e.curHp;
            if (hpBefore >= 0.0F) {
                e.curHp = Math.max(1, Math.round(hpBefore * e.maxHp));
            }

            int[] var8;
            try {
                String item = itemOverride != null ? norm(itemOverride) : itemFor(e, set);
                var8 = damage(e, move, norm(moveId), moveType(e, move, norm(moveId)), set, item, abilityFor(e, set));
            } finally {
                e.curHp = savedHp;
            }

            return var8;
        } else {
            return null;
        }
    }

    private static ThreatCalc.Report compute0(ClientBattlePokemon attacker, Pokemon target, ClientBattlePokemon targetBattle, FormData targetForm) {
        ThreatCalc.Env e = prepare(attacker, target, targetBattle, targetForm);
        if (e == null) {
            return null;
        } else {
            BuildInference.Info info = e.info;
            ThreatCalc.Report report = new ThreatCalc.Report();
            report.targetName = target.getDisplayName(false).getString();
            report.totalBuilds = info.candidates.size();
            int maxHp = e.maxHp;
            int curHp = e.curHp;
            List<String> moves = new ArrayList<>(info.revealedMoves);

            for (String id : info.likelyMoves()) {
                if (!moves.contains(id)) {
                    moves.add(id);
                }
            }

            List<SmogonSets.Set> sets = info.candidates;
            if (sets.isEmpty()) {
                sets = List.of(new SmogonSets.Set("Max attack", "Hardy", new int[]{0, 252, 0, 252, 0, 0}, 31, "", "", List.of()));
                report.totalBuilds = 0;
            }

            boolean mySash = norm(e.myItem).equals("focussash") || norm(e.myAbility).equals("sturdy");
            double per = e.myResidual.perTurn();
            double oneHp = 1.0 / Math.max(1, maxHp);
            double hpFrac = (double)curHp / maxHp;

            for (String moveId : moves) {
                MoveTemplate move = Moves.getByName(moveId);
                if (move != null) {
                    boolean revealed = info.revealedMoves.contains(moveId);
                    String typeName = moveType(e, move, moveId);
                    boolean status = BuildInference.isStatus(move);
                    int priority = move.getPriority();
                    if (status) {
                        int builds = revealed ? sets.size() : info.moveCount.getOrDefault(moveId, 0);
                        report.byMove
                            .put(
                                moveId,
                                new ThreatCalc.Threat(
                                    moveId,
                                    move.getDisplayName().getString(),
                                    typeName,
                                    false,
                                    0,
                                    0,
                                    "",
                                    builds,
                                    report.totalBuilds,
                                    revealed,
                                    priority,
                                    info.rememberedMoves.contains(moveId) ? "»" : null
                                )
                            );
                    } else {
                        int lo = Integer.MAX_VALUE;
                        int hi = 0;
                        int builds = 0;
                        String note = null;
                        String shownType = typeName;
                        boolean multiHit = MoveDamagePreviewCalculator.hitRange(moveId) != null;

                        for (SmogonSets.Set set : sets) {
                            if (revealed || info.rememberedMoves.contains(moveId) || set.moves().isEmpty() || set.hasMove(moveId)) {
                                builds++;
                                String item = itemFor(e, set);
                                String ability = abilityFor(e, set);
                                String converted = HiddenPowerHelper.abilityConvertedType(moveId, typeName, ability);
                                if (converted != null && e.knownAbility != null) {
                                    shownType = converted;
                                }

                                int[] dmg = damage(e, move, moveId, typeName, set, item, ability);
                                if (dmg != null) {
                                    if (dmg[0] < 0) {
                                        note = "immune (" + FormAbilityHelper.abilityDisplayNameFromId(e.myAbility) + ")";
                                        lo = 0;
                                        hi = Math.max(hi, 0);
                                    } else {
                                        if (dmg[1] == 0 && note == null) {
                                            note = "immune";
                                        }

                                        lo = Math.min(lo, dmg[0]);
                                        hi = Math.max(hi, dmg[1]);
                                    }
                                }
                            }
                        }

                        if (builds != 0 && lo != Integer.MAX_VALUE) {
                            int loPct = (int)Math.floor(lo * 100.0 / maxHp);
                            int hiPct = (int)Math.ceil(hi * 100.0 / maxHp);
                            boolean sashNow = mySash && !multiHit && curHp >= maxHp;
                            String ko;
                            if (hi == 0) {
                                ko = note != null ? "immune" : "no damage";
                            } else if (lo >= curHp && !sashNow) {
                                ko = "KOs you";
                            } else if (hi >= curHp && !sashNow) {
                                ko = "can KO you";
                            } else {
                                int best = Residual.hitsToKo(hpFrac, (double)hi / maxHp, per, sashNow, oneHp);
                                int worst = Residual.hitsToKo(hpFrac, (double)lo / maxHp, per, sashNow, oneHp);
                                if (best == 1) {
                                    ko = worst == 1 ? "KOs you" : "can KO you";
                                } else if (best == 2) {
                                    ko = worst == 2 ? "2HKO" : "2HKO roll";
                                } else if (best == 3 && worst == 3) {
                                    ko = "3HKO";
                                } else {
                                    ko = best >= 99 ? "no KO" : best + "HKO+";
                                }

                                if (sashNow && hi >= curHp) {
                                    ko = ko + " (" + (norm(e.myAbility).equals("sturdy") ? "Sturdy" : "Sash") + ")";
                                } else if (!e.myResidual.none()
                                    && (
                                        best != Residual.hitsToKo(hpFrac, (double)hi / maxHp, 0.0, false, oneHp)
                                            || worst != Residual.hitsToKo(hpFrac, (double)lo / maxHp, 0.0, false, oneHp)
                                    )) {
                                    ko = ko + " (" + e.myResidual.label() + ")";
                                }
                            }

                            report.byMove
                                .put(
                                    moveId,
                                    new ThreatCalc.Threat(
                                        moveId,
                                        move.getDisplayName().getString(),
                                        shownType,
                                        true,
                                        Math.min(100, loPct),
                                        Math.min(100, hiPct),
                                        ko,
                                        builds,
                                        report.totalBuilds,
                                        revealed,
                                        priority,
                                        note == null && info.rememberedMoves.contains(moveId) ? "»" : note
                                    )
                                );
                        }
                    }
                }
            }

            for (ThreatCalc.Threat threat : report.byMove.values()) {
                if (threat.damaging()) {
                    report.ranked.add(threat);
                }
            }

            report.ranked.sort((a, b) -> Integer.compare(b.maxPct(), a.maxPct()));
            return report;
        }
    }

    private static int[] damage(ThreatCalc.Env e, MoveTemplate move, String moveId, String typeName, SmogonSets.Set set, String item, String ability) {
        int level = e.level;
        FormData atkForm = e.atkForm;
        List<String> atkTypes = e.atkTypes;
        List<String> defTypes = e.defTypes;
        Pokemon target = e.target;
        FormData targetForm = e.targetForm;
        String myItem = e.myItem;
        String myAbility = e.myAbility;
        int maxHp = e.maxHp;
        int curHp = e.curHp;
        boolean myStatus = e.myStatus;
        String weather = e.weather;
        String terrain = e.terrain;
        Map<String, Integer> screens = e.screens;
        boolean oppBurned = e.oppBurned;
        boolean oppStatused = e.oppStatused;
        float oppRatio = e.oppRatio;
        int atkStageAtk = e.atkStageAtk;
        int atkStageSpa = e.atkStageSpa;
        int atkStageDef = e.atkStageDef;
        int myStageDef = e.myStageDef;
        int myStageSpd = e.myStageSpd;
        int myStageAtk = e.myStageAtk;
        switch (moveId) {
            case "seismictoss":
            case "nightshade":
                return typeImmune(typeName, defTypes) ? new int[]{0, 0} : new int[]{level, level};
            case "superfang":
            case "ruination":
            case "naturesmadness":
                return new int[]{Math.max(1, curHp / 2), Math.max(1, curHp / 2)};
            default:
                int power = (int)Math.floor(HiddenPowerHelper.opponentPower(move));
                if (MovePower.isVariable(moveId)) {
                    MovePower.Ctx c = new MovePower.Ctx();
                    c.atkWeight = MovePower.weight(atkForm, ability, item);
                    c.defWeight = e.myWeight;
                    Integer setSpeed = BuildInference.setSpeed(e.attacker, set.id());
                    c.atkSpeed = setSpeed != null ? setSpeed : -1;
                    c.defSpeed = e.mySpeed;
                    c.atkHpRatio = oppRatio;
                    c.defHpRatio = (float)curHp / Math.max(1, maxHp);
                    c.atkItem = item;
                    c.defStatus = e.myStatusId;
                    c.atkStatus = e.oppStatusId;
                    c.atkBoosts = e.atkBoosts;
                    if (move.getPriority() > 0) {
                        c.atkMovesFirst = true;
                    } else if (c.atkSpeed > 0 && c.defSpeed > 0 && c.atkSpeed != c.defSpeed) {
                        boolean tr = BattleMessageSubscriber.getTrickRoomTurns() > 0;
                        c.atkMovesFirst = tr ? c.atkSpeed < c.defSpeed : c.atkSpeed > c.defSpeed;
                    }

                    c.atkGrounded = MovePower.grounded(atkTypes, ability, item);
                    c.defGrounded = e.myGrounded;
                    c.faintedAllies = BattleRecap.faintedCount("opponent");
                    BattleCounters.State counters = BattleCounters.get("opponent", e.attacker);
                    c.timesHit = counters != null ? counters.timesHit : 0;
                    c.atkAbility = ability;
                    c.defAbility = myAbility;
                    power = (int)Math.floor(MovePower.power(moveId, power, c));
                }

                if (power <= 0) {
                    return null;
                } else {
                    boolean moldBreaker = ability.equals("moldbreaker") || ability.equals("teravolt") || ability.equals("turboblaze");
                    double ateBoost = 1.0;
                    String newType = HiddenPowerHelper.abilityConvertedType(moveId, typeName, ability);
                    if (newType != null) {
                        typeName = newType;
                        ateBoost = 1.2;
                    }

                    newType = moldBreaker ? "" : myAbility;

                    boolean immuneByAbility = switch (newType) {
                        case "levitate", "eartheater" -> "ground".equals(typeName) && !"thousandarrows".equals(moveId);
                        case "flashfire", "wellbakedbody" -> "fire".equals(typeName);
                        case "waterabsorb", "stormdrain", "dryskin" -> "water".equals(typeName);
                        case "voltabsorb", "lightningrod", "motordrive" -> "electric".equals(typeName);
                        case "sapsipper" -> "grass".equals(typeName);
                        default -> false;
                    };
                    if (immuneByAbility) {
                        return new int[]{-1, -1};
                    } else {
                        double effectiveness = 1.0;

                        for (String defType : defTypes) {
                            effectiveness *= TypeChart.getEffectivenessAgainstTypes(typeName, defType, null);
                        }

                        if (effectiveness <= 0.0) {
                            return new int[]{0, 0};
                        } else if ("wonderguard".equals(newType) && effectiveness <= 1.0) {
                            return new int[]{-1, -1};
                        } else {
                            boolean physical = "physical".equalsIgnoreCase(move.getDamageCategory().getName());
                            boolean usesDefStat = physical || moveId.equals("psyshock") || moveId.equals("psystrike") || moveId.equals("secretsword");
                            int atk;
                            if (moveId.equals("foulplay")) {
                                atk = (int)Math.floor(myStat(target, targetForm, Stats.ATTACK) * stageMult(myStageAtk));
                            } else if (moveId.equals("bodypress")) {
                                int base = SpeedTiers.baseStat(atkForm, Stats.DEFENCE);
                                atk = (int)Math.floor(
                                    MoveDamagePreviewCalculator.calcNonHpStat(base, 31, set.evs()[2], set.natureMult(2), level) * stageMult(atkStageDef)
                                );
                            } else {
                                Stat stat = physical ? Stats.ATTACK : Stats.SPECIAL_ATTACK;
                                int idx = physical ? 1 : 3;
                                int base = SpeedTiers.baseStat(atkForm, stat);
                                atk = (int)Math.floor(
                                    MoveDamagePreviewCalculator.calcNonHpStat(base, 31, set.evs()[idx], set.natureMult(idx), level)
                                        * stageMult(physical ? atkStageAtk : atkStageSpa)
                                );
                            }

                            if (physical && (ability.equals("hugepower") || ability.equals("purepower"))) {
                                atk *= 2;
                            }

                            if (physical && ability.equals("hustle")) {
                                atk = (int)Math.floor(atk * 1.5);
                            }

                            if (physical && ability.equals("guts") && oppStatused) {
                                atk = (int)Math.floor(atk * 1.5);
                            }

                            if (physical && item.equals("choiceband") || !physical && item.equals("choicespecs")) {
                                atk = (int)Math.floor(atk * 1.5);
                            }

                            int def = (int)Math.floor(
                                myStat(target, targetForm, usesDefStat ? Stats.DEFENCE : Stats.SPECIAL_DEFENCE)
                                    * stageMult(usesDefStat ? myStageDef : myStageSpd)
                            );
                            if (!usesDefStat && defTypes.contains("rock") && weather.contains("sand")) {
                                def = (int)Math.floor(def * 1.5);
                            }

                            if (usesDefStat && defTypes.contains("ice") && weather.contains("snow")) {
                                def = (int)Math.floor(def * 1.5);
                            }

                            if (!usesDefStat && myItem.equals("assaultvest") || myItem.equals("eviolite")) {
                                def = (int)Math.floor(def * 1.5);
                            }

                            if (usesDefStat && newType.equals("furcoat")) {
                                def *= 2;
                            }

                            if (usesDefStat && newType.equals("marvelscale") && myStatus) {
                                def = (int)Math.floor(def * 1.5);
                            }

                            double powerMod = ateBoost;
                            if (moveId.equals("knockoff") && !myItem.isEmpty()) {
                                powerMod = ateBoost * 1.5;
                            }

                            Boolean contactObj = MoveContactData.isContact(moveId);
                            boolean contact = contactObj != null && contactObj;
                            switch (ability) {
                                case "technician":
                                    if (power <= 60) {
                                        powerMod *= 1.5;
                                    }
                                    break;
                                case "toughclaws":
                                    if (contact) {
                                        powerMod *= 1.3;
                                    }
                                    break;
                                case "ironfist":
                                    if (MoveDamagePreviewCalculator.isPunchingMove(moveId)) {
                                        powerMod *= 1.2;
                                    }
                                    break;
                                case "strongjaw":
                                    if (MoveDamagePreviewCalculator.isBitingMove(moveId)) {
                                        powerMod *= 1.5;
                                    }
                                    break;
                                case "sharpness":
                                    if (SLICING.contains(moveId)) {
                                        powerMod *= 1.5;
                                    }
                                    break;
                                case "transistor":
                                    if ("electric".equals(typeName)) {
                                        powerMod *= 1.3;
                                    }
                                    break;
                                case "dragonsmaw":
                                    if ("dragon".equals(typeName)) {
                                        powerMod *= 1.5;
                                    }
                                    break;
                                case "steelworker":
                                case "steelyspirit":
                                    if ("steel".equals(typeName)) {
                                        powerMod *= 1.5;
                                    }
                                    break;
                                case "rockypayload":
                                    if ("rock".equals(typeName)) {
                                        powerMod *= 1.5;
                                    }
                                    break;
                                case "waterbubble":
                                    if ("water".equals(typeName)) {
                                        powerMod *= 2.0;
                                    }
                                    break;
                                case "sandforce":
                                    if (weather.contains("sand") && ("rock".equals(typeName) || "ground".equals(typeName) || "steel".equals(typeName))) {
                                        powerMod *= 1.3;
                                    }
                                    break;
                                case "blaze":
                                case "torrent":
                                case "overgrow":
                                case "swarm":
                                    if (oppRatio <= 0.3334F
                                        && (
                                            "blaze".equals(ability) && "fire".equals(typeName)
                                                || "torrent".equals(ability) && "water".equals(typeName)
                                                || "overgrow".equals(ability) && "grass".equals(typeName)
                                                || "swarm".equals(ability) && "bug".equals(typeName)
                                        )) {
                                        powerMod *= 1.5;
                                    }
                            }

                            int effectivePower = Math.max(1, (int)Math.floor(power * powerMod));
                            double mod = 1.0;
                            if (atkTypes.contains(typeName)) {
                                mod *= ability.equals("adaptability") ? 2.0 : 1.5;
                            }

                            mod *= effectiveness;
                            if (weather.contains("rain")) {
                                mod *= "water".equals(typeName) ? 1.5 : ("fire".equals(typeName) ? 0.5 : 1.0);
                            } else if (weather.contains("sun") || weather.contains("harsh")) {
                                mod *= "fire".equals(typeName) ? 1.5 : ("water".equals(typeName) ? 0.5 : 1.0);
                            }

                            boolean attackerGrounded = !atkTypes.contains("flying") && !ability.equals("levitate");
                            boolean targetGrounded = !defTypes.contains("flying") && !newType.equals("levitate") && !myItem.equals("airballoon");
                            if (attackerGrounded
                                && (
                                    terrain.contains("electric") && "electric".equals(typeName)
                                        || terrain.contains("grassy") && "grass".equals(typeName)
                                        || terrain.contains("psychic") && "psychic".equals(typeName)
                                )) {
                                mod *= 1.3;
                            }

                            if (terrain.contains("misty") && targetGrounded && "dragon".equals(typeName)) {
                                mod *= 0.5;
                            }

                            if (terrain.contains("grassy") && (moveId.equals("earthquake") || moveId.equals("bulldoze"))) {
                                mod *= 0.5;
                            }

                            if (physical && oppBurned && !ability.equals("guts") && !moveId.equals("facade")) {
                                mod *= 0.5;
                            }

                            if (screens != null
                                && !ability.equals("infiltrator")
                                && (
                                    screens.getOrDefault("Aurora Veil", 0) > 0
                                        || physical && screens.getOrDefault("Reflect", 0) > 0
                                        || !physical && screens.getOrDefault("Light Screen", 0) > 0
                                )) {
                                mod *= 0.5;
                            }

                            if (item.equals("lifeorb")) {
                                mod *= 1.3;
                            } else if (item.equals("expertbelt") && effectiveness > 1.0) {
                                mod *= 1.2;
                            } else if (!item.endsWith("mask")
                                || !item.startsWith("wellspring") && !item.startsWith("hearthflame") && !item.startsWith("cornerstone")) {
                                if (typeName.equals(MovePower.typeBoostItem(item))) {
                                    mod *= 1.2;
                                }
                            } else {
                                mod *= 1.2;
                            }

                            if ((moveId.equals("collisioncourse") || moveId.equals("electrodrift")) && effectiveness > 1.0) {
                                mod *= 1.333252F;
                            }

                            if (ability.equals("tintedlens") && effectiveness < 1.0) {
                                mod *= 2.0;
                            }

                            double defMod = 1.0;
                            boolean fullHp = curHp >= maxHp;
                            switch (newType) {
                                case "multiscale":
                                case "shadowshield":
                                    if (fullHp) {
                                        defMod *= 0.5;
                                    }
                                    break;
                                case "filter":
                                case "solidrock":
                                case "prismarmor":
                                    if (effectiveness > 1.0) {
                                        defMod *= 0.75;
                                    }
                                    break;
                                case "thickfat":
                                    if ("fire".equals(typeName) || "ice".equals(typeName)) {
                                        defMod *= 0.5;
                                    }
                                    break;
                                case "heatproof":
                                case "waterbubble":
                                    if ("fire".equals(typeName)) {
                                        defMod *= 0.5;
                                    }
                                    break;
                                case "icescales":
                                    if (!physical) {
                                        defMod *= 0.5;
                                    }
                                    break;
                                case "fluffy":
                                    if (contact) {
                                        defMod *= 0.5;
                                    }

                                    if ("fire".equals(typeName)) {
                                        defMod *= 2.0;
                                    }
                                    break;
                                case "purifyingsalt":
                                    if ("ghost".equals(typeName)) {
                                        defMod *= 0.5;
                                    }
                                    break;
                                case "dryskin":
                                    if ("fire".equals(typeName)) {
                                        defMod *= 1.25;
                                    }
                            }

                            mod *= defMod;
                            int[] hitRange = MoveDamagePreviewCalculator.hitRange(moveId);
                            int[] hitPowers = MoveDamagePreviewCalculator.hitPowers(moveId);
                            int minHits = 1;
                            int maxHits = 1;
                            if (hitRange != null) {
                                minHits = hitRange[0];
                                maxHits = hitRange[1];
                                if (ability.equals("skilllink")) {
                                    minHits = maxHits;
                                }
                            }

                            int base = MoveDamagePreviewCalculator.calcBaseDamage(level, effectivePower, Math.max(1, atk), Math.max(1, def));
                            if (base <= 0) {
                                return new int[]{0, 0};
                            } else {
                                int lo = 0;
                                int hi = 0;

                                for (int i = 0; i < maxHits; i++) {
                                    int hitBase = base;
                                    if (hitPowers != null && hitPowers.length > 1) {
                                        int p = hitPowers[Math.min(i, hitPowers.length - 1)];
                                        hitBase = MoveDamagePreviewCalculator.calcBaseDamage(
                                            level, Math.max(1, (int)Math.floor(p * powerMod)), Math.max(1, atk), Math.max(1, def)
                                        );
                                    }

                                    int one = Math.max(1, (int)Math.floor(hitBase * mod));
                                    int oneLo = Math.max(1, (int)Math.floor(hitBase * mod * 0.85));
                                    hi += one;
                                    if (i < minHits) {
                                        lo += oneLo;
                                    }
                                }

                                return new int[]{lo, hi};
                            }
                        }
                    }
                }
        }
    }

    private static boolean typeImmune(String typeName, List<String> defTypes) {
        for (String t : defTypes) {
            if (TypeChart.getEffectivenessAgainstTypes(typeName, t, null) <= 0.0F) {
                return true;
            }
        }

        return false;
    }

    public static int pctColor(int pct) {
        if (pct >= 100) {
            return 16733525;
        } else if (pct >= 50) {
            return 16755285;
        } else {
            return pct >= 25 ? 16306224 : 8184715;
        }
    }

    public static final class Env {
        public ClientBattlePokemon attacker;
        public BuildInference.Info info;
        public FormData atkForm;
        public Pokemon target;
        public ClientBattlePokemon targetBattle;
        public FormData targetForm;
        public int level;
        public final List<String> atkTypes = new ArrayList<>();
        public final List<String> defTypes = new ArrayList<>();
        public String knownItem;
        public String inferredItem;
        public String knownAbility;
        public boolean atkSuppressed;
        public int maxHp;
        public int curHp;
        public String myItem = "";
        public String myAbility = "";
        public boolean myStatus;
        public String myStatusId = "";
        public String weather = "";
        public String terrain = "";
        public Map<String, Integer> screens;
        public boolean oppBurned;
        public boolean oppStatused;
        public String oppStatusId = "";
        public float oppRatio = 1.0F;
        public int atkStageAtk;
        public int atkStageSpa;
        public int atkStageDef;
        public int myStageDef;
        public int myStageSpd;
        public int myStageAtk;
        public int atkBoosts;
        public int mySpeed = -1;
        public double myWeight = -1.0;
        public boolean myGrounded = true;
        public Residual.Res myResidual = Residual.Res.NONE;
    }

    public static final class Report {
        public String targetName = "";
        public final Map<String, ThreatCalc.Threat> byMove = new LinkedHashMap<>();
        public final List<ThreatCalc.Threat> ranked = new ArrayList<>();
        public int totalBuilds;

        public ThreatCalc.Threat worst() {
            return this.ranked.isEmpty() ? null : this.ranked.get(0);
        }
    }

    public record Threat(
        String moveId,
        String name,
        String typeName,
        boolean damaging,
        int minPct,
        int maxPct,
        String ko,
        int builds,
        int totalBuilds,
        boolean revealed,
        int priority,
        String note
    ) {
        public String pctText() {
            return this.minPct + "–" + this.maxPct + "%";
        }
    }
}
