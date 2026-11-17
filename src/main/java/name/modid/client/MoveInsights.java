package name.modid.client;

import com.cobblemon.mod.common.api.moves.Move;
import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import com.cobblemon.mod.common.client.CobblemonClient;
import com.cobblemon.mod.common.client.battle.ActiveClientBattlePokemon;
import com.cobblemon.mod.common.client.battle.ClientBattle;
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Pokemon;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;

public final class MoveInsights {
    private MoveInsights() {
    }

    static String norm(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    static ClientBattlePokemon activeOpponent() {
        try {
            ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
            if (battle != null) {
                for (ActiveClientBattlePokemon active : BattleSideHelper.getOpponentSide(battle).getActiveClientBattlePokemon()) {
                    if (active != null && active.getBattlePokemon() != null) {
                        return active.getBattlePokemon();
                    }
                }
            }
        } catch (Throwable var3) {
        }

        return null;
    }

    static ClientBattlePokemon activeMine() {
        try {
            ClientBattle battle = CobblemonClient.INSTANCE.getBattle();
            if (battle != null) {
                for (ActiveClientBattlePokemon active : BattleSideHelper.getPlayerSide(battle).getActiveClientBattlePokemon()) {
                    if (active != null && active.getBattlePokemon() != null) {
                        return active.getBattlePokemon();
                    }
                }
            }
        } catch (Throwable var3) {
        }

        return null;
    }

    static boolean isMyMove(String moveId) {
        try {
            ClientBattlePokemon mine = activeMine();
            Pokemon pokemon = mine != null ? PartyInfoSideRenderer.findPartyPokemonForBattlePokemon(mine) : null;
            if (pokemon == null) {
                return false;
            } else {
                String id = norm(moveId);

                for (Move move : pokemon.getMoveSet().getMoves()) {
                    if (move != null && norm(move.getTemplate().getName()).equals(id)) {
                        return true;
                    }
                }

                return false;
            }
        } catch (Throwable var6) {
            return false;
        }
    }

    public static MoveInsights.PriorityMove opponentPriority(BuildInference.Info info) {
        if (info == null) {
            return null;
        } else {
            MoveInsights.PriorityMove best = null;
            List<String> ids = new ArrayList<>(info.revealedMoves);
            ids.addAll(info.rememberedMoves);

            for (String id : info.moveCount.keySet()) {
                if (!ids.contains(id)) {
                    ids.add(id);
                }
            }

            for (String idx : ids) {
                MoveTemplate move = Moves.getByName(idx);
                if (move != null && move.getPriority() > 0 && !BuildInference.isStatus(move)) {
                    boolean revealed = info.revealedMoves.contains(idx) || info.rememberedMoves.contains(idx);
                    int builds = revealed ? info.candidates.size() : info.moveCount.getOrDefault(idx, 0);
                    if (revealed || builds > 0) {
                        MoveInsights.PriorityMove candidate = new MoveInsights.PriorityMove(
                            move.getDisplayName().getString(), move.getPriority(), revealed, builds, info.candidates.size()
                        );
                        if (best == null
                            || candidate.revealed() && !best.revealed()
                            || candidate.revealed() == best.revealed()
                                && (candidate.priority() > best.priority() || candidate.priority() == best.priority() && candidate.builds() > best.builds())) {
                            best = candidate;
                        }
                    }
                }
            }

            return best;
        }
    }

    public static MoveInsights.Insight order(String moveId) {
        if (ExtrasPlusConfig.get().moveOrderLine && moveId != null && isMyMove(moveId)) {
            try {
                MoveTemplate move = Moves.getByName(norm(moveId));
                ClientBattlePokemon opponent = activeOpponent();
                if (move != null && opponent != null) {
                    int p = move.getPriority();
                    MoveInsights.PriorityMove theirs = opponentPriority(BuildInference.info(opponent));
                    String warn = theirs != null ? " · ⚠ " + theirs.name() + " +" + theirs.priority() + " (" + theirs.likelihood() + ")" : "";
                    if (p > 0) {
                        return theirs != null && theirs.priority() >= p
                            ? new MoveInsights.Insight("Priority +" + p + warn, -10166)
                            : new MoveInsights.Insight("⚡ Moves first (priority +" + p + ")", -10755206);
                    } else if (p < 0) {
                        return new MoveInsights.Insight("Moves last (priority " + p + ")", -30116);
                    } else {
                        SpeedTiers.Report report = SpeedTiers.lastReport;
                        if (report != null && report.mySpeed != null && System.currentTimeMillis() - SpeedTiers.lastReportTime <= 5000L) {
                            int total = report.builds.size();
                            int first = 0;
                            int ties = 0;

                            for (SpeedTiers.Tier tier : report.builds) {
                                int c = SpeedTiers.verdictColor(report, tier.speed()) & 16777215;
                                if (c == 6022010) {
                                    first++;
                                } else if (c == 16767050) {
                                    ties++;
                                }
                            }

                            String text;
                            int color;
                            if (total == 0) {
                                int lo = SpeedTiers.verdictColor(report, report.theoMin) & 16777215;
                                int hi = SpeedTiers.verdictColor(report, report.theoMax) & 16777215;
                                if (lo == 6022010 && hi == 6022010) {
                                    text = "Moves first";
                                    color = -10755206;
                                } else if (lo == 16735324 && hi == 16735324) {
                                    text = "Moves second";
                                    color = -41892;
                                } else {
                                    text = "Order depends on their build";
                                    color = -10166;
                                }
                            } else if (first == total) {
                                text = "Moves first (all builds)";
                                color = -10755206;
                            } else if (first == 0 && ties == 0) {
                                text = "Moves second (all builds)";
                                color = -41892;
                            } else {
                                text = "Moves first vs " + first + "/" + total + " builds";
                                color = -10166;
                            }

                            if (theirs != null && color == -10755206) {
                                color = -10166;
                            }

                            return new MoveInsights.Insight(text + warn, color);
                        } else {
                            return theirs != null ? new MoveInsights.Insight(warn.substring(3), -10166) : null;
                        }
                    }
                } else {
                    return null;
                }
            } catch (Throwable var14) {
                return null;
            }
        } else {
            return null;
        }
    }

    public static MoveInsights.Insight contact(String moveId) {
        if (ExtrasPlusConfig.get().contactWarnings && moveId != null && isMyMove(moveId)) {
            try {
                Boolean isContact = MoveContactData.isContact(norm(moveId));
                ClientBattlePokemon opponent = activeOpponent();
                if (isContact != null && isContact && opponent != null) {
                    ClientBattlePokemon mine = activeMine();
                    Pokemon myPokemon = mine != null ? PartyInfoSideRenderer.findPartyPokemonForBattlePokemon(mine) : null;
                    if (myPokemon != null) {
                        String myAbility = norm(myPokemon.getAbility().getTemplate().getName());
                        String myItem = myPokemon.heldItem() != null && !myPokemon.heldItem().method_7960()
                            ? norm(myPokemon.heldItem().method_7964().getString())
                            : "";
                        ItemAbilityTracker.State st = ItemAbilityTracker.get("player", mine);
                        if (st != null && (st.itemName != null || st.itemLost)) {
                            myItem = st.itemLost ? "" : norm(st.itemName);
                        }

                        if (myAbility.equals("longreach") || myItem.equals("protectivepads")) {
                            return null;
                        }
                    }

                    BuildInference.Info info = BuildInference.info(opponent);
                    List<String> parts = new ArrayList<>();
                    ItemAbilityTracker.State oppState = ItemAbilityTracker.get("opponent", opponent);
                    String ability = null;
                    String abilityLikely = null;
                    if (oppState != null && oppState.abilitySuppressed) {
                        ability = "";
                    } else if (oppState != null && oppState.abilityTemporary && oppState.abilityId != null) {
                        ability = norm(oppState.abilityId);
                    } else if (info != null && info.revealedAbility != null) {
                        ability = norm(info.revealedAbility);
                    } else {
                        FormData form = FormAbilityHelper.effectiveBattleForm(opponent, "opponent");
                        String single = FormAbilityHelper.singleFormAbilityId(opponent.getSpecies(), form);
                        if (single != null && !single.isBlank()) {
                            ability = norm(single);
                        }
                    }

                    if (ability == null && info != null) {
                        for (Entry<String, Integer> e : info.abilityCount.entrySet()) {
                            if (contactEffect(norm(e.getKey())) != null) {
                                ability = norm(e.getKey());
                                abilityLikely = e.getValue() + "/" + info.candidates.size();
                                break;
                            }
                        }
                    }

                    String effect = ability == null ? null : contactEffect(ability);
                    if (effect != null) {
                        parts.add(
                            FormAbilityHelper.abilityDisplayNameFromId(ability)
                                + " "
                                + effect
                                + (abilityLikely != null ? " (likely " + abilityLikely + ")" : "")
                        );
                    }

                    String item = ItemAbilityTracker.knownItemName("opponent", opponent);
                    if (item != null) {
                        if (norm(item).equals("rockyhelmet")) {
                            parts.add("Rocky Helmet −17%");
                        }
                    } else if (info != null) {
                        Integer helmets = info.itemCount.get("Rocky Helmet");
                        if (helmets != null && helmets > 0) {
                            parts.add("Rocky Helmet −17% (likely " + helmets + "/" + info.candidates.size() + ")");
                        }
                    }

                    if (parts.isEmpty()) {
                        return null;
                    } else {
                        boolean damage = String.join(" ", parts).contains("−");
                        return new MoveInsights.Insight("⚠ " + String.join(" · ", parts), damage ? -30116 : -10166);
                    }
                } else {
                    return null;
                }
            } catch (Throwable var13) {
                return null;
            }
        } else {
            return null;
        }
    }

    private static String contactEffect(String ability) {
        return switch (ability) {
            case "roughskin", "ironbarbs" -> "−12%";
            case "flamebody" -> "30% burn";
            case "static" -> "30% paralysis";
            case "poisonpoint" -> "30% poison";
            case "effectspore" -> "30% status";
            case "cutecharm" -> "30% infatuation";
            case "gooey", "tanglinghair" -> "−1 Spe";
            case "mummy", "lingeringaroma" -> "replaces your ability";
            case "wanderingspirit" -> "swaps abilities";
            case "perishbody" -> "Perish Song";
            default -> null;
        };
    }

    public record Insight(String text, int color) {
    }

    public record PriorityMove(String name, int priority, boolean revealed, int builds, int total) {
        public String likelihood() {
            return this.revealed ? "used" : this.builds + "/" + this.total;
        }
    }
}
