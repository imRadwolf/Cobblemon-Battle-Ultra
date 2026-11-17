package name.modid.client;

import com.cobblemon.mod.common.client.battle.ClientBattlePokemon;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class Residual {
    private Residual() {
    }

    static String norm(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    public static Residual.Res perTurn(
        String side, String species, List<String> types, String item, String ability, String status, int toxicTurns, boolean grounded
    ) {
        String it = norm(item);
        String ab = norm(ability);
        String st = norm(status);
        boolean magicGuard = ab.equals("magicguard");
        String w = norm(BattleMessageSubscriber.getCurrentWeather());
        String terrain = norm(BattleMessageSubscriber.getCurrentTerrain());
        double total = 0.0;
        List<String> labels = new ArrayList<>();
        boolean healBlocked = side != null && species != null && VolatileTracker.has(side, species, "healblock");
        double heal = 0.0;
        if (it.equals("leftovers")) {
            heal += 0.0625;
            labels.add("Leftovers");
        } else if (it.equals("blacksludge")) {
            if (types.contains("poison")) {
                heal += 0.0625;
                labels.add("Black Sludge");
            } else if (!magicGuard) {
                total -= 0.125;
                labels.add("Black Sludge");
            }
        }

        if (grounded && terrain.contains("grassy")) {
            heal += 0.0625;
            labels.add("Grassy Terrain");
        }

        if (w.contains("rain") && (ab.equals("raindish") || ab.equals("dryskin"))) {
            heal += ab.equals("dryskin") ? 0.125 : 0.0625;
            labels.add(FormAbilityHelper.abilityDisplayNameFromId(ab));
        }

        if ((w.contains("snow") || w.contains("hail")) && ab.equals("icebody")) {
            heal += 0.0625;
            labels.add("Ice Body");
        }

        if (side != null && species != null) {
            if (VolatileTracker.has(side, species, "aquaring")) {
                heal += 0.0625;
                labels.add("Aqua Ring");
            }

            if (VolatileTracker.has(side, species, "ingrain")) {
                heal += 0.0625;
                labels.add("Ingrain");
            }
        }

        if ((st.equals("psn") || st.equals("tox")) && ab.equals("poisonheal")) {
            heal += 0.125;
            labels.add("Poison Heal");
        }

        if (!healBlocked) {
            total += heal;
        }

        if (!magicGuard) {
            if (w.contains("sand")
                && !types.contains("rock")
                && !types.contains("ground")
                && !types.contains("steel")
                && !ab.equals("sandveil")
                && !ab.equals("sandrush")
                && !ab.equals("sandforce")
                && !ab.equals("overcoat")
                && !it.equals("safetygoggles")) {
                total -= 0.0625;
                labels.add("sandstorm");
            } else if (w.contains("hail")
                && !types.contains("ice")
                && !ab.equals("icebody")
                && !ab.equals("snowcloak")
                && !ab.equals("overcoat")
                && !it.equals("safetygoggles")) {
                total -= 0.0625;
                labels.add("hail");
            }

            if ((w.contains("sun") || w.contains("harsh")) && (ab.equals("dryskin") || ab.equals("solarpower"))) {
                total -= 0.125;
                labels.add(FormAbilityHelper.abilityDisplayNameFromId(ab));
            }

            if (st.equals("brn")) {
                total -= ab.equals("heatproof") ? 0.03125 : 0.0625;
                labels.add("burn");
            } else if (st.equals("psn") && !ab.equals("poisonheal")) {
                total -= 0.125;
                labels.add("poison");
            } else if (st.equals("tox") && !ab.equals("poisonheal")) {
                total -= Math.min(15, Math.max(1, toxicTurns + 1)) / 16.0;
                labels.add("toxic");
            }

            if (side != null && species != null) {
                if (VolatileTracker.has(side, species, "leechseed")) {
                    total -= 0.125;
                    labels.add("Leech Seed");
                }

                if (VolatileTracker.has(side, species, "saltcure")) {
                    total -= !types.contains("water") && !types.contains("steel") ? 0.125 : 0.25;
                    labels.add("Salt Cure");
                }

                if (VolatileTracker.has(side, species, "curse")) {
                    total -= 0.25;
                    labels.add("Curse");
                }

                for (String trap : new String[]{
                    "magmastorm", "firespin", "whirlpool", "sandtomb", "bind", "wrap", "clamp", "infestation", "snaptrap", "thundercage"
                }) {
                    if (VolatileTracker.has(side, species, trap)) {
                        total -= 0.125;
                        labels.add("trapped");
                        break;
                    }
                }
            }
        }

        return Math.abs(total) < 1.0E-6 ? Residual.Res.NONE : new Residual.Res(total, String.join(", ", labels));
    }

    public static int hitsToKo(double hp, double dmg, double perTurn, boolean survivesFirst, double oneHp) {
        if (dmg <= 0.0) {
            return 99;
        } else {
            double h = hp;

            for (int n = 1; n <= 12; n++) {
                h -= dmg;
                if (n == 1 && survivesFirst && hp >= 0.999 && h <= 0.0) {
                    h = Math.max(1.0E-4, oneHp);
                }

                if (h <= 1.0E-9) {
                    return n;
                }

                h = Math.min(1.0, h + perTurn);
                if (h <= 1.0E-9) {
                    return n;
                }
            }

            return 99;
        }
    }

    public static String koText(int best, int worst) {
        if (best >= 99) {
            return "no KO";
        } else if (best == 1 && worst == 1) {
            return "OHKO";
        } else if (best == 1) {
            return "OHKO roll";
        } else {
            String w = worst >= 99 ? best + "+" : (worst == best ? "" : "–" + worst);
            return best + w + "HKO";
        }
    }

    public static int toxicTurns(String side, ClientBattlePokemon bp) {
        BattleCounters.State st = BattleCounters.get(side, bp);
        return st != null ? Math.max(0, st.toxicTurns) : 0;
    }

    public record Res(double perTurn, String label) {
        public static final Residual.Res NONE = new Residual.Res(0.0, "");

        public boolean none() {
            return Math.abs(this.perTurn) < 1.0E-6;
        }

        public String pctText() {
            long pct = Math.round(this.perTurn * 100.0);
            return (pct > 0L ? "+" : "") + pct + "%/turn";
        }
    }
}
