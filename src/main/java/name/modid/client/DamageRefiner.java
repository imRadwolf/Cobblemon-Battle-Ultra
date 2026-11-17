package name.modid.client;

import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.api.types.ElementalType;
import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.battle.ActiveClientBattlePokemon;
import com.cobblemon.mod.common.client.battle.ClientBattle;
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon;
import com.cobblemon.mod.common.client.battle.ClientBattleSide;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Species;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DamageRefiner {
    private static final Map<String, DamageRefiner.Detail> DETAILS = new ConcurrentHashMap<>();
    private static final Map<String, String> RESIST_BERRIES = new HashMap<>();
    static final ThreadLocal<DamageRefiner.Input> LAST_INPUT;
    static final ThreadLocal<DamageRefiner.Result> LAST_RESULT;
    static final ThreadLocal<Boolean> QUIET;
    static final ThreadLocal<Boolean> HYPOTHETICAL;

    private DamageRefiner() {
    }

    static String norm(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private static String key(String moveName, int min, int max) {
        return norm(moveName) + "|" + min + "|" + max;
    }

    public static DamageRefiner.Detail detailFor(String moveDisplayOrId, String percentText) {
        if (moveDisplayOrId != null && percentText != null) {
            Matcher m = Pattern.compile("(\\d+)\\s*[–-]\\s*(\\d+)%").matcher(percentText);
            if (!m.find()) {
                return null;
            } else {
                String suffix = "|" + m.group(1) + "|" + m.group(2);
                DamageRefiner.Detail d = DETAILS.get(norm(moveDisplayOrId) + suffix);
                if (d == null) {
                    String id = MoveContactData.resolveMoveIdByDisplayName(moveDisplayOrId);
                    if (id != null) {
                        d = DETAILS.get(norm(id) + suffix);
                    }
                }

                return d != null && System.currentTimeMillis() - d.time() < 600000L ? d : null;
            }
        } else {
            return null;
        }
    }

    private static ClientBattlePokemon findDefender(MoveDamagePreviewCalculator.DefenceContext ctx, FormData[] formOut) {
        ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
        if (battle == null) {
            return null;
        } else {
            ClientBattleSide side = BattleSideHelper.getOpponentSide(battle);
            if (side == null) {
                return null;
            } else {
                ClientBattlePokemon fallback = null;
                FormData fallbackForm = null;
                int count = 0;

                for (ActiveClientBattlePokemon active : side.getActiveClientBattlePokemon()) {
                    ClientBattlePokemon bp = active == null ? null : active.getBattlePokemon();
                    if (bp != null) {
                        FormData form = FormAbilityHelper.effectiveBattleForm(bp, "opponent");
                        if (form == null && bp.getSpecies() != null) {
                            form = bp.getSpecies().getStandardForm();
                        }

                        count++;
                        fallback = bp;
                        fallbackForm = form;
                        if (form != null
                            && SpeedTiers.baseStat(form, Stats.HP) == ctx.baseHp()
                            && SpeedTiers.baseStat(form, Stats.DEFENCE) == ctx.baseDefence()
                            && SpeedTiers.baseStat(form, Stats.SPECIAL_DEFENCE) == ctx.baseSpecialDefence()) {
                            formOut[0] = form;
                            return bp;
                        }
                    }
                }

                if (count == 1) {
                    formOut[0] = fallbackForm;
                    return fallback;
                } else {
                    return null;
                }
            }
        }
    }

    public static DamageRefiner.Result refine(DamageRefiner.Input in) {
        LAST_INPUT.set(in);
        LAST_RESULT.set(null);

        try {
            DamageRefiner.Result result = refine0(in);
            LAST_RESULT.set(result);
            return result;
        } catch (Throwable var2) {
            return null;
        }
    }

    static DamageRefiner.Env prepare(DamageRefiner.Input in, ClientBattlePokemon forcedDefender, FormData forcedForm, float hpOverride) {
        MoveDamagePreviewCalculator.DefenceContext ctx = in.defender();
        FormData[] formHolder = new FormData[]{forcedForm};
        ClientBattlePokemon bp = forcedDefender != null ? forcedDefender : findDefender(ctx, formHolder);
        if (bp == null) {
            return null;
        } else {
            DamageRefiner.Env e = new DamageRefiner.Env();
            e.in = in;
            e.ctx = ctx;
            e.bp = bp;
            e.form = formHolder[0];
            e.species = bp.getSpecies();
            e.level = Math.max(1, ctx.level());
            e.usesSpDef = in.physical() == in.wonderRoom();
            e.defBase = e.usesSpDef ? ctx.baseSpecialDefence() : ctx.baseDefence();
            e.statIndex = e.usesSpDef ? 4 : 2;
            e.moveType = norm(HiddenPowerHelper.effective(in.move()).getName());
            e.moveId = norm(in.move().getName());

            for (ElementalType t : FormAbilityHelper.types(e.form)) {
                e.defTypes.add(norm(t.getName()));
            }

            e.weather = norm(BattleMessageSubscriber.getCurrentWeather());
            e.atkAbility = norm(in.attackerAbility());
            e.moldBreaker = "moldbreaker".equals(e.atkAbility) || "teravolt".equals(e.atkAbility) || "turboblaze".equals(e.atkAbility);
            boolean infiltrator = "infiltrator".equals(e.atkAbility);
            Boolean contactObj = MoveContactData.isContact(in.move().getName());
            e.contact = contactObj != null && contactObj && !"longreach".equals(e.atkAbility);
            float hpRatio = hpOverride >= 0.0F ? hpOverride : ctx.currentHpRatio();
            if (Float.isNaN(hpRatio) || Float.isInfinite(hpRatio)) {
                hpRatio = 1.0F;
            }

            e.hpRatio = Math.max(0.001F, Math.min(1.0F, hpRatio));
            e.fullHp = e.hpRatio >= 0.999F;
            e.exactHp = ctx.hpFlat() && ctx.actualMaxHp() >= 10.0F;
            ItemAbilityTracker.State tracked = ItemAbilityTracker.get("opponent", bp);
            e.knownItem = ItemAbilityTracker.knownItemName("opponent", bp);
            if (e.knownItem == null) {
                e.inferredItem = BuildInference.inferredItem(bp);
            }

            e.abilitySuppressed = tracked != null && tracked.abilitySuppressed;
            if (tracked != null && tracked.abilityId != null && !tracked.abilityId.isBlank()) {
                e.knownAbility = norm(tracked.abilityId);
            } else if (tracked != null && tracked.abilityName != null && !tracked.abilityName.isBlank()) {
                e.knownAbility = norm(tracked.abilityName);
            } else {
                String revealed = BuildInference.revealedAbility(bp);
                if (revealed != null) {
                    e.knownAbility = norm(revealed);
                } else {
                    String single = FormAbilityHelper.singleFormAbilityId(e.species, e.form);
                    if (single != null && !single.isBlank()) {
                        e.knownAbility = norm(single);
                    }
                }
            }

            if (!infiltrator) {
                Map<String, Integer> screens = BattleMessageSubscriber.getScreensForSide("opponent");
                if (screens != null) {
                    if (screens.getOrDefault("Aurora Veil", 0) > 0) {
                        e.screenMult = 0.5;
                        e.screenNote = "Aurora Veil ×0.5";
                    } else if (!e.usesSpDef && in.physical() && screens.getOrDefault("Reflect", 0) > 0) {
                        e.screenMult = 0.5;
                        e.screenNote = "Reflect ×0.5";
                    } else if (!in.physical() && screens.getOrDefault("Light Screen", 0) > 0) {
                        e.screenMult = 0.5;
                        e.screenNote = "Light Screen ×0.5";
                    }
                }
            }

            e.stageMult = in.defenceStage() >= 0 ? (2.0 + in.defenceStage()) / 2.0 : 2.0 / (2.0 - in.defenceStage());
            return e;
        }
    }

    static DamageRefiner.Spread spreadOf(DamageRefiner.Env e, SmogonSets.Set s) {
        int statIndex = e.statIndex;
        String spread = s.evs()[0]
            + " HP / "
            + s.evs()[statIndex]
            + (s.natureMult(statIndex) > 1.0 ? "+ " : (s.natureMult(statIndex) < 1.0 ? "- " : " "))
            + (e.usesSpDef ? "SpD" : "Def");
        return new DamageRefiner.Spread(
            s.label(), s.evs()[0], s.evs()[statIndex], s.natureMult(statIndex), s.item(), s.ability(), spread, BuildInference.setSpeed(e.bp, s.id())
        );
    }

    static DamageRefiner.Outcome evaluate(DamageRefiner.Env e, DamageRefiner.Spread sp, int minHits, int maxHits) {
        DamageRefiner.Input in = e.in;
        String item = e.knownItem != null ? norm(e.knownItem) : (e.inferredItem != null ? norm(e.inferredItem) : norm(sp.item()));
        String ability = !e.abilitySuppressed && !e.moldBreaker ? (e.knownAbility != null ? e.knownAbility : norm(sp.ability())) : "";
        String moveType = e.moveType;
        List<String> effects = new ArrayList<>();
        double defMult = e.stageMult;
        double mod = in.modifier() * e.screenMult;
        if (e.usesSpDef && e.ctx.rockType() && e.weather.contains("sand")) {
            defMult *= 1.5;
        }

        if (!e.usesSpDef && e.defTypes.contains("ice") && e.weather.contains("snow")) {
            defMult *= 1.5;
        }

        if ("assaultvest".equals(item) && !in.physical()) {
            defMult *= 1.5;
            effects.add("Assault Vest");
        } else if ("eviolite".equals(item)) {
            defMult *= 1.5;
            effects.add("Eviolite");
        }

        String berryType = RESIST_BERRIES.get(item);
        boolean berry = berryType != null && berryType.equals(moveType) && (in.typeEffectiveness() > 1.0F || "normal".equals(berryType));
        if (berry) {
            effects.add(FormAbilityHelper.prettyId(item));
        }
        boolean abilityImmune = switch (ability) {
            case "levitate", "eartheater" -> "ground".equals(moveType) && !"thousandarrows".equals(e.moveId);
            case "flashfire", "wellbakedbody" -> "fire".equals(moveType);
            case "waterabsorb", "stormdrain", "dryskin" -> "water".equals(moveType);
            case "voltabsorb", "lightningrod", "motordrive" -> "electric".equals(moveType);
            case "sapsipper" -> "grass".equals(moveType);
            case "wonderguard" -> in.typeEffectiveness() <= 1.0F;
            default -> false;
        };
        if (abilityImmune) {
            return new DamageRefiner.Outcome(sp.label(), 0, 0, 1, FormAbilityHelper.abilityDisplayNameFromId(ability) + ": immune");
        } else {
            double abilityMult = 1.0;
            double firstHitMult = berry ? 0.5 : 1.0;
            switch (ability) {
                case "multiscale":
                case "shadowshield":
                    if (e.fullHp) {
                        firstHitMult *= 0.5;
                        effects.add(FormAbilityHelper.abilityDisplayNameFromId(ability) + " ×0.5" + (maxHits > 1 ? " (1st hit)" : ""));
                    }
                    break;
                case "furcoat":
                    if (in.physical()) {
                        defMult *= 2.0;
                        effects.add("Fur Coat");
                    }
                    break;
                case "icescales":
                    if (!in.physical()) {
                        abilityMult = 0.5;
                    }
                    break;
                case "fluffy":
                    if (e.contact) {
                        abilityMult *= 0.5;
                    }

                    if ("fire".equals(moveType)) {
                        abilityMult *= 2.0;
                    }
                    break;
                case "filter":
                case "solidrock":
                case "prismarmor":
                    if (in.typeEffectiveness() > 1.0F) {
                        abilityMult = 0.75;
                    }
                    break;
                case "thickfat":
                    if ("fire".equals(moveType) || "ice".equals(moveType)) {
                        abilityMult = 0.5;
                    }
                    break;
                case "heatproof":
                case "waterbubble":
                    if ("fire".equals(moveType)) {
                        abilityMult = 0.5;
                    }
                    break;
                case "purifyingsalt":
                    if ("ghost".equals(moveType)) {
                        abilityMult = 0.5;
                    }
                    break;
                case "dryskin":
                    if ("fire".equals(moveType)) {
                        abilityMult = 1.25;
                    }
                    break;
                case "marvelscale":
                    if (in.physical() && e.bp.getStatus() != null) {
                        defMult *= 1.5;
                        effects.add("Marvel Scale");
                    }
            }

            if ("knockoff".equals(e.moveId) && !item.isEmpty()) {
                mod *= 1.5;
                effects.add("holds item ×1.5");
            }

            if (abilityMult != 1.0) {
                mod *= abilityMult;
                effects.add(FormAbilityHelper.abilityDisplayNameFromId(ability) + " ×" + trim(abilityMult));
            }

            int def = Math.max(1, (int)Math.floor(MoveDamagePreviewCalculator.calcNonHpStat(e.defBase, 31, sp.defEv(), sp.nature(), e.level) * defMult));
            int hp = e.exactHp ? Math.round(e.ctx.actualMaxHp()) : MoveDamagePreviewCalculator.calcHpStat(e.ctx.baseHp(), 31, sp.hpEv(), e.level);
            if (e.ctx.baseHp() == 1) {
                hp = 1;
            }

            int power = in.power();
            if (MovePower.speedDependent(e.moveId) && sp.speed() > 0) {
                int p = (int)Math.floor(MovePower.playerPowerVsSpeed(in.move(), sp.speed()));
                if (p > 0 && p != power) {
                    power = p;
                    effects.add(p + " BP vs " + sp.speed() + " Spe");
                }
            }

            int minDmg = hits(in, def, mod, firstHitMult, 0.85, minHits, power);
            int maxDmg = hits(in, def, mod, firstHitMult, 1.0, maxHits, power);
            boolean survives = in.maxHits() <= 1 && ("focussash".equals(item) || "sturdy".equals(ability));
            Residual.Res res = Residual.Res.NONE;

            try {
                String status = e.bp.getStatus() != null ? e.bp.getStatus().getShowdownName() : "";
                boolean grounded = !e.defTypes.contains("flying") && !"levitate".equals(ability) && !"airballoon".equals(item);
                res = Residual.perTurn(
                    "opponent",
                    e.species != null ? e.species.getName() : null,
                    e.defTypes,
                    item,
                    ability,
                    status,
                    Residual.toxicTurns("opponent", e.bp),
                    grounded
                );
            } catch (Throwable var29) {
            }

            return new DamageRefiner.Outcome(sp.label() + " (" + sp.spreadText() + ")", minDmg, maxDmg, hp, String.join(", ", effects), survives, res);
        }
    }

    public static double[] percentForSet(DamageRefiner.Input in, ClientBattlePokemon defender, FormData form, SmogonSets.Set set, int hits, float hpBefore) {
        return percentForSet(in, defender, form, set, hits, hpBefore, null, null);
    }

    public static double[] percentForSet(
        DamageRefiner.Input in,
        ClientBattlePokemon defender,
        FormData form,
        SmogonSets.Set set,
        int hits,
        float hpBefore,
        String itemOverride,
        String abilityOverride
    ) {
        try {
            DamageRefiner.Env e = prepare(in, defender, form, hpBefore);
            if (e == null) {
                return null;
            } else {
                DamageRefiner.Spread sp = spreadOf(e, set);
                if (itemOverride != null) {
                    e.knownItem = null;
                    e.inferredItem = null;
                    sp = sp.withItem(itemOverride);
                }

                if (abilityOverride != null) {
                    e.knownAbility = null;
                    sp = sp.withAbility(abilityOverride);
                }

                DamageRefiner.Outcome o = evaluate(e, sp, hits, hits);
                return new double[]{o.minDmg() * 100.0 / Math.max(1, o.hp()), o.maxDmg() * 100.0 / Math.max(1, o.hp())};
            }
        } catch (Throwable var11) {
            return null;
        }
    }

    private static DamageRefiner.Result refine0(DamageRefiner.Input in) {
        DamageRefiner.Env e = prepare(in, null, null, -1.0F);
        if (e == null) {
            return null;
        } else {
            if (!HYPOTHETICAL.get()) {
                BuildInference.rememberInput(in, e.bp, QUIET.get());
            }

            MoveDamagePreviewCalculator.DefenceContext ctx = e.ctx;
            boolean usesSpDef = e.usesSpDef;
            float hpRatio = e.hpRatio;
            boolean fullHp = e.fullHp;
            String knownItem = e.knownItem;
            String knownAbility = e.knownAbility;
            boolean abilitySuppressed = e.abilitySuppressed;
            boolean moldBreaker = e.moldBreaker;
            String atkAbility = e.atkAbility;
            String screenNote = e.screenNote;
            BuildInference.Info info = BuildInference.info(e.bp);
            List<SmogonSets.Set> sets = info == null ? List.of() : info.candidates;
            List<DamageRefiner.Spread> spreads = new ArrayList<>();
            String source;
            if (!sets.isEmpty()) {
                source = (info.candidates.size() < info.all.size() ? "Likely builds " + info.candidates.size() + "/" + info.all.size() : "Smogon builds")
                    + (info.fromBase ? " (base form spreads)" : "");

                for (SmogonSets.Set s : sets) {
                    spreads.add(spreadOf(e, s));
                }
            } else {
                source = "No Smogon data: typical spreads";
                spreads.add(new DamageRefiner.Spread("Max bulk", 252, 252, 1.1, "", "", "252 HP / 252+ " + (usesSpDef ? "SpD" : "Def")));
                spreads.add(new DamageRefiner.Spread("HP invested", 252, 4, 1.0, "", "", "252 HP / 4 " + (usesSpDef ? "SpD" : "Def")));
                spreads.add(new DamageRefiner.Spread("Offensive", 4, 0, 1.0, "", "", "4 HP / 0 " + (usesSpDef ? "SpD" : "Def")));
            }

            Map<String, DamageRefiner.Outcome> outcomes = new LinkedHashMap<>();
            boolean immune = false;
            String immuneReason = null;

            for (DamageRefiner.Spread sp : spreads) {
                DamageRefiner.Outcome o = evaluate(e, sp, in.minHits(), in.maxHits());
                if (o.maxDmg() == 0 && o.effects().endsWith(": immune")) {
                    if (knownAbility != null && !abilitySuppressed && !moldBreaker) {
                        immune = true;
                        immuneReason = o.effects().substring(0, o.effects().length() - ": immune".length());
                        break;
                    }

                    outcomes.putIfAbsent("immune|" + o.effects(), o);
                } else {
                    outcomes.putIfAbsent(o.minDmg() + "|" + o.maxDmg() + "|" + o.hp() + "|" + o.effects(), o);
                }
            }

            String theoretical = in.theoMinPercent() + "–" + in.theoMaxPercent() + "%";
            if (immune) {
                DETAILS.put(
                    key(in.move().getName(), 0, 0),
                    new DamageRefiner.Detail(
                        theoretical,
                        List.of(new DamageRefiner.Row(-1, -1, "", "Immune: " + immuneReason, "")),
                        null,
                        "Known ability",
                        System.currentTimeMillis()
                    )
                );
                return new DamageRefiner.Result(0, 0, "move.battleinfo.ko.unlikely", -7829368);
            } else if (outcomes.isEmpty()) {
                return null;
            } else {
                int minPct = Integer.MAX_VALUE;
                int maxPct = 0;
                int ohkoSure = 0;
                int ohkoAny = 0;
                int twoHko = 0;
                int n = 0;
                List<DamageRefiner.Row> rows = new ArrayList<>();

                for (DamageRefiner.Outcome o : outcomes.values()) {
                    if (o.maxDmg() == 0) {
                        rows.add(new DamageRefiner.Row(0, 0, "immune", o.label(), o.effects()));
                        minPct = 0;
                        n++;
                    } else {
                        int hpPctCap = (int)Math.ceil(hpRatio * 100.0);
                        int lo = Math.min(hpPctCap, (int)Math.floor(o.minDmg() * 100.0 / o.hp()));
                        int hi = Math.min(hpPctCap, (int)Math.ceil(o.maxDmg() * 100.0 / o.hp()));
                        minPct = Math.min(minPct, lo);
                        maxPct = Math.max(maxPct, hi);
                        n++;
                        double hpFrac = Math.min(1.0, (double)hpRatio);
                        double oneHp = 1.0 / Math.max(1, o.hp());
                        double per = o.residual() != null ? o.residual().perTurn() : 0.0;
                        int hitsBest = Residual.hitsToKo(hpFrac, (double)o.maxDmg() / o.hp(), per, o.survivesFirst(), oneHp);
                        int hitsWorst = Residual.hitsToKo(hpFrac, (double)o.minDmg() / o.hp(), per, o.survivesFirst(), oneHp);
                        int plainBest = Residual.hitsToKo(hpFrac, (double)o.maxDmg() / o.hp(), 0.0, false, oneHp);
                        int plainWorst = Residual.hitsToKo(hpFrac, (double)o.minDmg() / o.hp(), 0.0, false, oneHp);
                        if (hitsWorst == 1) {
                            ohkoSure++;
                        }

                        if (hitsBest == 1) {
                            ohkoAny++;
                        }

                        if (hitsBest <= 2) {
                            twoHko++;
                        }

                        String ko = Residual.koText(hitsBest, hitsWorst);
                        String effects = o.effects();
                        if (o.survivesFirst() && plainBest == 1 && hitsBest > 1) {
                            effects = (effects.isEmpty() ? "" : effects + ", ") + "Sash/Sturdy saves it";
                        }

                        if (o.residual() != null && !o.residual().none() && (hitsBest != plainBest || hitsWorst != plainWorst)) {
                            effects = (effects.isEmpty() ? "" : effects + ", ") + o.residual().label() + " " + o.residual().pctText();
                        }

                        rows.add(new DamageRefiner.Row(lo, hi, ko, o.label(), effects));
                    }
                }

                if (minPct == Integer.MAX_VALUE) {
                    minPct = 0;
                }

                String koKey;
                int koColor;
                if (ohkoSure == n) {
                    koKey = "move.battleinfo.ko.one_hit";
                    koColor = -43691;
                } else if (ohkoAny > 0) {
                    koKey = "move.battleinfo.ko.possible";
                    koColor = -22016;
                } else if (twoHko > 0) {
                    koKey = "move.battleinfo.ko.two_hit_range";
                    koColor = -470992;
                } else {
                    koKey = "move.battleinfo.ko.unlikely";
                    koColor = -7829368;
                }

                List<String> notes = new ArrayList<>();
                if (screenNote != null) {
                    notes.add(screenNote);
                }

                if (knownItem != null) {
                    notes.add(knownItem.isEmpty() ? "no item (lost)" : "item: " + knownItem);
                } else if (e.inferredItem != null) {
                    notes.add("likely " + e.inferredItem);
                }

                if (abilitySuppressed) {
                    notes.add("ability suppressed");
                } else if (moldBreaker) {
                    notes.add("ability ignored (" + FormAbilityHelper.abilityDisplayNameFromId(atkAbility) + ")");
                } else if (knownAbility != null) {
                    notes.add("ability: " + FormAbilityHelper.abilityDisplayNameFromId(knownAbility));
                }

                if (in.defenceStage() != 0) {
                    notes.add((in.defenceStage() > 0 ? "+" : "") + in.defenceStage() + " " + (usesSpDef ? "SpD" : "Def"));
                }

                if (!fullHp) {
                    notes.add("at " + Math.round(hpRatio * 100.0F) + "% HP");
                }

                if (rows.size() > 4) {
                    int extra = rows.size() - 4;
                    rows = new ArrayList<>(rows.subList(0, 4));
                    rows.add(new DamageRefiner.Row(-1, -1, "", "+" + extra + " more build" + (extra > 1 ? "s" : ""), ""));
                }

                if (!QUIET.get()) {
                    DETAILS.put(
                        key(in.move().getName(), minPct, maxPct),
                        new DamageRefiner.Detail(theoretical, rows, notes.isEmpty() ? null : String.join(" · ", notes), source, System.currentTimeMillis())
                    );
                }

                if (DETAILS.size() > 256) {
                    DETAILS.clear();
                }

                return new DamageRefiner.Result(minPct, maxPct, koKey, koColor, ohkoSure, ohkoAny, n);
            }
        }
    }

    private static int hits(DamageRefiner.Input in, int def, double mod, double firstHitMult, double roll, int count, int power) {
        if (count <= 0) {
            return 0;
        } else {
            int[] powers = in.hitPowers();
            if (power != in.power() && powers != null && powers.length == 1) {
                powers = new int[]{power};
            }

            int first = MoveDamagePreviewCalculator.totalDamageForHits(in.attackerLevel(), in.attack(), def, mod * firstHitMult, roll, 1, powers, power);
            if (count == 1) {
                return first;
            } else {
                int[] rest = powers != null && powers.length > 1 ? Arrays.copyOfRange(powers, 1, powers.length) : powers;
                return first + MoveDamagePreviewCalculator.totalDamageForHits(in.attackerLevel(), in.attack(), def, mod, roll, count - 1, rest, power);
            }
        }
    }

    public static int koColor(String ko) {
        if (ko == null) {
            return 11184810;
        } else if (ko.startsWith("OHKO")) {
            return 16733525;
        } else if (ko.startsWith("2")) {
            return 16755285;
        } else {
            return ko.startsWith("3") ? 16306224 : 11184810;
        }
    }

    private static String trim(double d) {
        return d == Math.floor(d) ? String.valueOf((int)d) : String.valueOf(d);
    }

    static {
        String[][] berries = new String[][]{
            {"occaberry", "fire"},
            {"passhoberry", "water"},
            {"wacanberry", "electric"},
            {"rindoberry", "grass"},
            {"yacheberry", "ice"},
            {"chopleberry", "fighting"},
            {"kebiaberry", "poison"},
            {"shucaberry", "ground"},
            {"cobaberry", "flying"},
            {"payapaberry", "psychic"},
            {"tangaberry", "bug"},
            {"chartiberry", "rock"},
            {"kasibberry", "ghost"},
            {"habanberry", "dragon"},
            {"colburberry", "dark"},
            {"babiriberry", "steel"},
            {"roseliberry", "fairy"},
            {"chilanberry", "normal"}
        };

        for (String[] b : berries) {
            RESIST_BERRIES.put(b[0], b[1]);
        }

        LAST_INPUT = new ThreadLocal<>();
        LAST_RESULT = new ThreadLocal<>();
        QUIET = ThreadLocal.withInitial(() -> false);
        HYPOTHETICAL = ThreadLocal.withInitial(() -> false);
    }

    public record Detail(String theoretical, List<DamageRefiner.Row> rows, String notes, String source, long time) {
    }

    static final class Env {
        DamageRefiner.Input in;
        MoveDamagePreviewCalculator.DefenceContext ctx;
        ClientBattlePokemon bp;
        FormData form;
        Species species;
        int level;
        boolean usesSpDef;
        int defBase;
        int statIndex;
        String moveType;
        String moveId;
        List<String> defTypes = new ArrayList<>();
        String weather;
        String atkAbility;
        boolean moldBreaker;
        boolean contact;
        float hpRatio;
        boolean fullHp;
        boolean exactHp;
        String knownItem;
        String inferredItem;
        String knownAbility;
        boolean abilitySuppressed;
        double screenMult = 1.0;
        String screenNote;
        double stageMult;
    }

    public record Input(
        int attackerLevel,
        int attack,
        double modifier,
        int minHits,
        int maxHits,
        int[] hitPowers,
        int power,
        boolean physical,
        boolean wonderRoom,
        int defenceStage,
        MoveDamagePreviewCalculator.DefenceContext defender,
        MoveTemplate move,
        float typeEffectiveness,
        String attackerAbility,
        int theoMinPercent,
        int theoMaxPercent
    ) {
    }

    record Outcome(String label, int minDmg, int maxDmg, int hp, String effects, boolean survivesFirst, Residual.Res residual) {
        Outcome(String label, int minDmg, int maxDmg, int hp, String effects) {
            this(label, minDmg, maxDmg, hp, effects, false, Residual.Res.NONE);
        }
    }

    public record Result(int minPercent, int maxPercent, String koKey, int koColor, int koSure, int koAny, int builds) {
        public Result(int minPercent, int maxPercent, String koKey, int koColor) {
            this(minPercent, maxPercent, koKey, koColor, 0, 0, 0);
        }
    }

    public record Row(int lo, int hi, String ko, String label, String effects) {
        public String plain() {
            return this.lo < 0
                ? this.label
                : this.lo + "–" + this.hi + "% " + this.ko + "  " + this.label + (this.effects != null && !this.effects.isEmpty() ? " + " + this.effects : "");
        }
    }

    record Spread(String label, int hpEv, int defEv, double nature, String item, String ability, String spreadText, int speed) {
        Spread(String label, int hpEv, int defEv, double nature, String item, String ability, String spreadText) {
            this(label, hpEv, defEv, nature, item, ability, spreadText, -1);
        }

        DamageRefiner.Spread withItem(String newItem) {
            return new DamageRefiner.Spread(this.label, this.hpEv, this.defEv, this.nature, newItem, this.ability, this.spreadText, this.speed);
        }

        DamageRefiner.Spread withAbility(String newAbility) {
            return new DamageRefiner.Spread(this.label, this.hpEv, this.defEv, this.nature, this.item, newAbility, this.spreadText, this.speed);
        }
    }
}
