package name.modid.client;

import com.cobblemon.mod.common.api.moves.Move;
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
import net.minecraft.class_1799;
import net.minecraft.class_2960;
import net.minecraft.class_7923;

public final class MoveAdvisor {
    public static final int GOLD = -10496;
    public static final int DANGER = -41892;
    public static final int PLAN = -6305568;
    private static volatile MoveAdvisor.Advice cached;
    private static volatile long cachedAt;
    private static final Map<String, int[]> SETUP = new LinkedHashMap<>();

    private MoveAdvisor() {
    }

    static String norm(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    static String koText(int sure, int any, int builds, int maxPct) {
        if (builds <= 0) {
            return sure > 0 ? "KO" : (any > 0 ? "KO possible" : "");
        } else if (sure >= builds) {
            return "KOs all " + builds;
        } else if (sure > 0) {
            return "KOs " + sure + "/" + builds + (any > sure ? " (" + any + " w/ roll)" : "");
        } else {
            return any > 0 ? "KO roll " + any + "/" + builds : "";
        }
    }

    public static MoveAdvisor.Advice current() {
        long now = System.currentTimeMillis();
        MoveAdvisor.Advice advice = cached;
        if (advice != null && now - cachedAt < 400L) {
            return advice;
        } else {
            try {
                advice = compute();
            } catch (Throwable var4) {
                advice = null;
            }

            cached = advice;
            cachedAt = now;
            return advice;
        }
    }

    public static MoveAdvisor.Eval evalFor(String moveIdOrName) {
        MoveAdvisor.Advice advice = current();
        if (advice != null && moveIdOrName != null) {
            MoveAdvisor.Eval eval = advice.byMove.get(norm(moveIdOrName));
            if (eval == null) {
                String id = MoveContactData.resolveMoveIdByDisplayName(moveIdOrName);
                eval = id != null ? advice.byMove.get(norm(id)) : null;
            }

            return eval;
        } else {
            return null;
        }
    }

    private static int stat(Pokemon pokemon, FormData form, Stat stat) {
        if (form != null && FormAbilityHelper.isBattleOnlyForm(form)) {
            int v = SpeedTiers.computeOwnStat(pokemon, form, stat);
            if (v > 0) {
                return v;
            }
        }

        return pokemon.getStat(stat);
    }

    private static String itemPath(Pokemon pokemon, ClientBattlePokemon battlePokemon) {
        try {
            ItemAbilityTracker.State st = ItemAbilityTracker.get("player", battlePokemon);
            class_1799 stack = pokemon.heldItem();
            if (st != null && st.itemLost) {
                return "";
            } else {
                if (st != null && st.itemName != null) {
                    stack = ItemLookup.byDisplayName(st.itemName);
                }

                if (stack != null && !stack.method_7960()) {
                    class_2960 key = class_7923.field_41178.method_10221(stack.method_7909());
                    return key != null ? key.method_12832() : "";
                } else {
                    return "";
                }
            }
        } catch (Throwable var5) {
            return "";
        }
    }

    private static MoveAdvisor.Advice compute() {
        if (!ExtrasPlusConfig.get().moveAdvisor) {
            return null;
        } else {
            ClientBattlePokemon myBp = MoveInsights.activeMine();
            ClientBattlePokemon oppBp = MoveInsights.activeOpponent();
            Pokemon pokemon = myBp != null ? PartyInfoSideRenderer.findPartyPokemonForBattlePokemon(myBp) : null;
            if (pokemon != null && oppBp != null) {
                MoveAdvisor.Advice advice = new MoveAdvisor.Advice();
                advice.opponentName = oppBp.getDisplayName().getString();
                FormData myForm = FormAbilityHelper.effectiveBattleForm(myBp, "player");
                if (myForm == null) {
                    myForm = pokemon.getForm();
                }

                FormData oppForm = FormAbilityHelper.effectiveBattleForm(oppBp, "opponent");
                if (oppForm == null) {
                    oppForm = oppBp.getSpecies().getStandardForm();
                }

                List<String> myTypes = new ArrayList<>();

                for (ElementalType t : FormAbilityHelper.types(myForm)) {
                    myTypes.add(norm(t.getName()));
                }

                List<String> oppTypes = new ArrayList<>();

                for (ElementalType t : FormAbilityHelper.types(oppForm)) {
                    oppTypes.add(norm(t.getName()));
                }

                String ability = "";

                try {
                    ability = pokemon.getAbility().getName().toLowerCase(Locale.ROOT);
                    ItemAbilityTracker.State st = ItemAbilityTracker.get("player", myBp);
                    if (st != null && st.abilityTemporary && st.abilityId != null) {
                        ability = norm(st.abilityId);
                    }
                } catch (Throwable var74) {
                }

                String status = null;

                try {
                    status = myBp.getStatus() != null ? myBp.getStatus().getShowdownName() : null;
                } catch (Throwable var73) {
                }

                float myRatio = ratio(myBp);
                float oppRatio = ratio(oppBp);
                advice.opponentHp = oppRatio;
                int atkStage = SpeedTiers.stageFor("player", myBp, "attack");
                int spaStage = SpeedTiers.stageFor("player", myBp, "special-attack");
                int defStage = SpeedTiers.stageFor("player", myBp, "defense");
                String item = itemPath(pokemon, myBp);
                boolean grounded = !myTypes.contains("flying") && !norm(ability).equals("levitate");
                int atk = stat(pokemon, myForm, Stats.ATTACK);
                int spa = stat(pokemon, myForm, Stats.SPECIAL_ATTACK);
                int def = stat(pokemon, myForm, Stats.DEFENCE);
                MoveDamagePreviewCalculator.DefenceContext defence = new MoveDamagePreviewCalculator.DefenceContext(
                    Math.max(1, oppBp.getLevel()),
                    SpeedTiers.baseStat(oppForm, Stats.HP),
                    SpeedTiers.baseStat(oppForm, Stats.DEFENCE),
                    SpeedTiers.baseStat(oppForm, Stats.SPECIAL_DEFENCE),
                    oppBp.getMaxHp(),
                    oppBp.isHpFlat(),
                    SpeedTiers.stageFor("opponent", oppBp, "defense"),
                    SpeedTiers.stageFor("opponent", oppBp, "special-defense"),
                    oppRatio,
                    !oppTypes.contains("flying"),
                    oppTypes.contains("rock")
                );
                BuildInference.Info info = BuildInference.info(oppBp);
                List<SmogonSets.Set> candidates = info != null ? info.candidates : List.of();
                double residual = residualPct(oppBp, oppTypes, info);
                String residualLabel = residualLabel(oppBp, oppTypes, info);
                String setupId = null;

                for (Move m : pokemon.getMoveSet().getMoves()) {
                    if (m != null && SETUP.containsKey(norm(m.getTemplate().getName()))) {
                        setupId = norm(m.getTemplate().getName());
                        break;
                    }
                }

                ThreatCalc.Report threats = ThreatCalc.compute(oppBp, pokemon, myBp, myForm);
                ThreatCalc.Threat worst = threats != null ? threats.worst() : null;
                MoveAdvisor.Eval best = null;
                double bestScore = -1.0;
                boolean danger = false;
                int fasterBuilds = 0;
                int totalBuilds = 0;
                if (worst != null && worst.maxPct() >= Math.round(myRatio * 100.0F) && worst.maxPct() > 0) {
                    danger = true;
                    SpeedTiers.Report report = SpeedTiers.lastReport;
                    if (report != null && report.mySpeed != null) {
                        totalBuilds = report.builds.size();

                        for (SpeedTiers.Tier tier : report.builds) {
                            if ((SpeedTiers.verdictColor(report, tier.speed()) & 16777215) == 16735324) {
                                fasterBuilds++;
                            }
                        }
                    }
                }

                for (Move mx : pokemon.getMoveSet().getMoves()) {
                    if (mx != null) {
                        MoveTemplate move = mx.getTemplate();
                        String id = norm(move.getName());
                        String typeName = norm(HiddenPowerHelper.effective(move, pokemon).getName());
                        int priority = move.getPriority();
                        if (!BuildInference.isStatus(move) && !(HiddenPowerHelper.power(move, pokemon) <= 0.0)) {
                            float eff = 1.0F;

                            for (String t : oppTypes) {
                                eff *= TypeChart.getEffectivenessAgainstTypes(typeName, t, null);
                            }

                            if (eff <= 0.0F) {
                                advice.byMove
                                    .put(
                                        id,
                                        new MoveAdvisor.Eval(
                                            id, move.getDisplayName().getString(), typeName, true, 0, 0, 0, 0, candidates.size(), priority, true, List.of()
                                        )
                                    );
                            } else {
                                float stab = myTypes.contains(typeName) ? 1.5F : 1.0F;
                                MoveDamagePreviewCalculator.AttackContext attack = new MoveDamagePreviewCalculator.AttackContext(
                                    Math.max(1, myBp.getLevel()), atk, spa, atkStage, spaStage, status, grounded, item, ability, myRatio, def, defStage
                                );
                                DamageRefiner.QUIET.set(true);
                                DamageRefiner.LAST_INPUT.set(null);
                                DamageRefiner.LAST_RESULT.set(null);

                                MoveDamagePreviewCalculator.DamagePreview preview;
                                DamageRefiner.Input input;
                                DamageRefiner.Result result;
                                try {
                                    preview = MoveDamagePreviewCalculator.calculatePreview(attack, defence, move, eff, stab);
                                    input = DamageRefiner.LAST_INPUT.get();
                                    result = DamageRefiner.LAST_RESULT.get();
                                } finally {
                                    DamageRefiner.QUIET.set(false);
                                }

                                if (preview != null) {
                                    int sure;
                                    int any;
                                    int builds;
                                    if (result != null && result.builds() > 0) {
                                        sure = result.koSure();
                                        any = result.koAny();
                                        builds = result.builds();
                                    } else {
                                        sure = "move.battleinfo.ko.one_hit".equals(preview.koTextKey()) ? 1 : 0;
                                        any = sure <= 0 && !"move.battleinfo.ko.possible".equals(preview.koTextKey()) ? 0 : 1;
                                        builds = 0;
                                    }

                                    List<MoveAdvisor.Plan> plans = new ArrayList<>();
                                    if (residual > 0.5 && input != null) {
                                        int[] c = counts(input, oppBp, oppForm, candidates, oppRatio, 1, residual, preview);
                                        if (c[1] > any || c[0] > sure) {
                                            plans.add(
                                                new MoveAdvisor.Plan("+ end of turn (" + residualLabel + ")", c[2], c[3], c[0], c[1], candidates.size(), null)
                                            );
                                        }
                                    }

                                    if (input != null && sure < Math.max(1, builds)) {
                                        int[] c = counts(input, oppBp, oppForm, candidates, oppRatio, 2, residual > 0.5 ? residual : 0.0, preview);
                                        plans.add(
                                            new MoveAdvisor.Plan(
                                                "Two hits" + (residual > 0.5 ? " + chip" : ""), c[2], c[3], c[0], c[1], candidates.size(), null
                                            )
                                        );
                                    }

                                    if (setupId != null && !setupId.equals(id)) {
                                        int[] boost = SETUP.get(setupId);
                                        boolean physical = "physical".equalsIgnoreCase(move.getDamageCategory().getName());
                                        int add = physical ? boost[0] : boost[1];
                                        if (add > 0 && !"bodypress".equals(id) && !"foulplay".equals(id)) {
                                            MoveDamagePreviewCalculator.AttackContext boosted = new MoveDamagePreviewCalculator.AttackContext(
                                                Math.max(1, myBp.getLevel()),
                                                atk,
                                                spa,
                                                physical ? Math.min(6, atkStage + add) : atkStage,
                                                physical ? spaStage : Math.min(6, spaStage + add),
                                                status,
                                                grounded,
                                                item,
                                                ability,
                                                myRatio,
                                                def,
                                                defStage
                                            );
                                            DamageRefiner.QUIET.set(true);
                                            DamageRefiner.HYPOTHETICAL.set(true);

                                            MoveDamagePreviewCalculator.DamagePreview boostedPreview;
                                            DamageRefiner.Input boostedInput;
                                            try {
                                                boostedPreview = MoveDamagePreviewCalculator.calculatePreview(boosted, defence, move, eff, stab);
                                                boostedInput = DamageRefiner.LAST_INPUT.get();
                                            } finally {
                                                DamageRefiner.QUIET.set(false);
                                                DamageRefiner.HYPOTHETICAL.set(false);
                                            }

                                            if (boostedPreview != null && boostedInput != null) {
                                                int[] c = counts(boostedInput, oppBp, oppForm, candidates, oppRatio, 1, 0.0, boostedPreview);
                                                String setupName = FormAbilityHelper.prettyId(setupId);

                                                try {
                                                    setupName = Moves.getByName(setupId).getDisplayName().getString();
                                                } catch (Throwable var70) {
                                                }

                                                String note = worst != null && worst.maxPct() > 0 ? "they hit you first: up to " + worst.maxPct() + "%" : null;
                                                plans.add(
                                                    new MoveAdvisor.Plan(
                                                        "After " + setupName + " (+" + Math.min(6, (physical ? atkStage : spaStage) + add) + ")",
                                                        c[2],
                                                        c[3],
                                                        c[0],
                                                        c[1],
                                                        candidates.size(),
                                                        note
                                                    )
                                                );
                                            }
                                        }
                                    }

                                    MoveAdvisor.Eval eval = new MoveAdvisor.Eval(
                                        id,
                                        move.getDisplayName().getString(),
                                        typeName,
                                        true,
                                        preview.minPercent(),
                                        preview.maxPercent(),
                                        sure,
                                        any,
                                        builds,
                                        priority,
                                        false,
                                        plans
                                    );
                                    advice.byMove.put(id, eval);
                                    double n = Math.max(1, builds);
                                    double score = 1000.0 * sure / n + 400.0 * any / n + (preview.minPercent() + preview.maxPercent()) / 2.0;
                                    if (danger && priority > 0 && any > 0) {
                                        score += 1500.0;
                                    }

                                    if (mx.getCurrentPp() <= 0) {
                                        score = -1.0;
                                    }

                                    if (score > bestScore) {
                                        bestScore = score;
                                        best = eval;
                                    }
                                }
                            }
                        } else {
                            advice.byMove
                                .put(
                                    id, new MoveAdvisor.Eval(id, move.getDisplayName().getString(), typeName, false, 0, 0, 0, 0, 0, priority, false, List.of())
                                );
                        }
                    }
                }

                if (best != null && best.maxPct() > 0) {
                    advice.bestId = best.moveId();
                    int nx = best.builds();
                    if (nx > 0 && best.koSure() >= nx) {
                        advice.bestReason = "KOs all " + nx + " builds";
                    } else if (best.koSure() > 0 && nx > 0) {
                        advice.bestReason = "KOs " + best.koSure() + "/" + nx + " builds";
                    } else if (best.koAny() > 0 && nx > 0) {
                        advice.bestReason = "KO roll vs " + best.koAny() + "/" + nx + " builds";
                    } else {
                        advice.bestReason = "most damage (" + best.pctText() + ")";
                    }

                    if (danger && best.priority() > 0 && best.koAny() > 0) {
                        advice.bestReason = advice.bestReason + ", before they can hit";
                    }
                }

                if (danger) {
                    boolean theyFirst = worst.priority() > 0 || fasterBuilds > 0;
                    if (worst.priority() > 0) {
                        advice.danger = "⚠ " + worst.name() + " (+" + worst.priority() + ") can KO you before you move";
                    } else if (fasterBuilds > 0 && fasterBuilds >= totalBuilds) {
                        advice.danger = "⚠ " + worst.name() + " can KO you, and they move first";
                    } else if (fasterBuilds > 0) {
                        advice.danger = "⚠ " + worst.name() + " can KO you first (faster in " + fasterBuilds + "/" + totalBuilds + " builds)";
                    } else {
                        advice.danger = "⚠ " + worst.name() + " can KO you (you move first)";
                    }

                    advice.dangerColor = theyFirst ? -41892 : -21931;
                }

                return advice;
            } else {
                return null;
            }
        }
    }

    public static int koColor(int sure, int any, int builds) {
        if (sure > 0 && sure >= Math.max(1, builds)) {
            return -43691;
        } else {
            return sure <= 0 && any <= 0 ? -4671304 : -21931;
        }
    }

    public static List<MoveAdvisor.Line> lines(String moveIdOrName) {
        List<MoveAdvisor.Line> out = new ArrayList<>();
        MoveAdvisor.Advice advice = current();
        MoveAdvisor.Eval eval = evalFor(moveIdOrName);
        if (advice != null && eval != null) {
            if (eval.moveId().equals(advice.bestId)) {
                out.add(new MoveAdvisor.Line("★ Best move", -10496, advice.bestReason == null ? "" : advice.bestReason, -10496));
            }

            for (MoveAdvisor.Plan plan : eval.plans()) {
                String ko = plan.koText();
                out.add(
                    new MoveAdvisor.Line(
                        plan.label(),
                        -6305568,
                        plan.minPct() + "–" + plan.maxPct() + "%" + (ko.isEmpty() ? "" : "  " + ko),
                        koColor(plan.koSure(), plan.koAny(), plan.builds())
                    )
                );
                if (plan.note() != null) {
                    out.add(new MoveAdvisor.Line("   " + plan.note(), -7697782, "", 0));
                }
            }

            if (advice.danger != null && eval.damaging()) {
                out.add(new MoveAdvisor.Line(advice.danger, advice.dangerColor, "", 0));
            }
        }

        return out;
    }

    private static float ratio(ClientBattlePokemon bp) {
        try {
            float r = bp.isHpFlat() && bp.getMaxHp() > 0.0F ? bp.getHpValue() / bp.getMaxHp() : bp.getHpValue();
            return r > 1.0001F ? r / 100.0F : Math.max(0.001F, Math.min(1.0F, r));
        } catch (Throwable var2) {
            return 1.0F;
        }
    }

    private static int[] counts(
        DamageRefiner.Input input,
        ClientBattlePokemon oppBp,
        FormData oppForm,
        List<SmogonSets.Set> candidates,
        float oppRatio,
        int hitsInRow,
        double extraPct,
        MoveDamagePreviewCalculator.DamagePreview fallback
    ) {
        double hpPct = oppRatio * 100.0;
        int sure = 0;
        int any = 0;
        double lo = Double.MAX_VALUE;
        double hi = 0.0;
        if (candidates.isEmpty()) {
            lo = fallback.minPercent() * hitsInRow + extraPct;
            hi = fallback.maxPercent() * hitsInRow + extraPct;
            sure = lo >= hpPct ? 1 : 0;
            any = hi >= hpPct ? 1 : 0;
        } else {
            for (SmogonSets.Set set : candidates) {
                double[] min = DamageRefiner.percentForSet(input, oppBp, oppForm, set, input.minHits(), oppRatio);
                double[] max = input.maxHits() == input.minHits() ? min : DamageRefiner.percentForSet(input, oppBp, oppForm, set, input.maxHits(), oppRatio);
                if (min != null && max != null) {
                    double l = min[0] * hitsInRow + extraPct;
                    double h = max[1] * hitsInRow + extraPct;
                    lo = Math.min(lo, l);
                    hi = Math.max(hi, h);
                    if (l >= hpPct) {
                        sure++;
                    }

                    if (h >= hpPct) {
                        any++;
                    }
                }
            }

            if (lo == Double.MAX_VALUE) {
                lo = 0.0;
            }
        }

        int cap = (int)Math.ceil(hpPct);
        return new int[]{sure, any, (int)Math.min((double)cap, Math.floor(lo)), (int)Math.min((double)cap, Math.ceil(hi))};
    }

    private static String statusId(ClientBattlePokemon bp) {
        try {
            return bp.getStatus() == null ? "" : norm(bp.getStatus().getShowdownName());
        } catch (Throwable var2) {
            return "";
        }
    }

    private static boolean sandImmune(List<String> types) {
        return types.contains("rock") || types.contains("ground") || types.contains("steel");
    }

    static double residualPct(ClientBattlePokemon bp, List<String> types, BuildInference.Info info) {
        double pct = 0.0;
        String weather = norm(BattleMessageSubscriber.getCurrentWeather());
        String ability = info != null && info.revealedAbility != null ? norm(info.revealedAbility) : "";
        if (ability.equals("magicguard")) {
            return 0.0;
        } else {
            if (weather.contains("sand")
                && !sandImmune(types)
                && !ability.equals("sandveil")
                && !ability.equals("sandrush")
                && !ability.equals("sandforce")
                && !ability.equals("overcoat")) {
                pct += 6.25;
            }

            String status = statusId(bp);
            if (status.equals("brn")) {
                pct += 6.25;
            } else if (status.equals("psn") || status.equals("tox")) {
                pct += ability.equals("poisonheal") ? -12.5 : 12.5;
            }

            String item = ItemAbilityTracker.knownItemName("opponent", bp);
            if (item != null && (norm(item).equals("leftovers") || norm(item).equals("blacksludge") && types.contains("poison"))) {
                pct -= 6.25;
            }

            if (norm(BattleMessageSubscriber.getCurrentTerrain()).contains("grassy") && !types.contains("flying")) {
                pct -= 6.25;
            }

            return pct;
        }
    }

    static String residualLabel(ClientBattlePokemon bp, List<String> types, BuildInference.Info info) {
        List<String> parts = new ArrayList<>();
        String weather = norm(BattleMessageSubscriber.getCurrentWeather());
        if (weather.contains("sand") && !sandImmune(types)) {
            parts.add("sand");
        }

        String status = statusId(bp);
        if (status.equals("brn")) {
            parts.add("burn");
        } else if (status.equals("psn")) {
            parts.add("poison");
        } else if (status.equals("tox")) {
            parts.add("toxic");
        }

        return String.join(", ", parts);
    }

    static {
        SETUP.put("swordsdance", new int[]{2, 0});
        SETUP.put("nastyplot", new int[]{0, 2});
        SETUP.put("shellsmash", new int[]{2, 2});
        SETUP.put("filletaway", new int[]{2, 2});
        SETUP.put("tailglow", new int[]{0, 3});
        SETUP.put("bellydrum", new int[]{6, 0});
        SETUP.put("geomancy", new int[]{0, 2});
        SETUP.put("dragondance", new int[]{1, 0});
        SETUP.put("calmmind", new int[]{0, 1});
        SETUP.put("quiverdance", new int[]{0, 1});
        SETUP.put("bulkup", new int[]{1, 0});
        SETUP.put("coil", new int[]{1, 0});
        SETUP.put("honeclaws", new int[]{1, 0});
        SETUP.put("workup", new int[]{1, 1});
        SETUP.put("growth", new int[]{1, 1});
        SETUP.put("tidyup", new int[]{1, 0});
        SETUP.put("victorydance", new int[]{1, 0});
        SETUP.put("takeheart", new int[]{0, 1});
        SETUP.put("torchsong", new int[]{0, 1});
    }

    public static final class Advice {
        public final Map<String, MoveAdvisor.Eval> byMove = new LinkedHashMap<>();
        public String bestId;
        public String bestReason;
        public String danger;
        public int dangerColor;
        public String opponentName = "";
        public float opponentHp = 1.0F;
    }

    public record Eval(
        String moveId,
        String name,
        String typeName,
        boolean damaging,
        int minPct,
        int maxPct,
        int koSure,
        int koAny,
        int builds,
        int priority,
        boolean immune,
        List<MoveAdvisor.Plan> plans
    ) {
        public String pctText() {
            return this.immune ? "immune" : this.minPct + "–" + this.maxPct + "%";
        }

        public String koText() {
            return MoveAdvisor.koText(this.koSure, this.koAny, this.builds, this.maxPct);
        }
    }

    public record Line(String left, int leftColor, String right, int rightColor) {
    }

    public record Plan(String label, int minPct, int maxPct, int koSure, int koAny, int builds, String note) {
        public String koText() {
            return MoveAdvisor.koText(this.koSure, this.koAny, this.builds, this.maxPct);
        }
    }
}
