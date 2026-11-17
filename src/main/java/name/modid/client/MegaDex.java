package name.modid.client;

import com.cobblemon.mod.common.api.pokemon.stats.Stat;
import com.cobblemon.mod.common.api.pokemon.stats.Stats;
import com.cobblemon.mod.common.client.battle.ClientBattlePokemon;
import com.cobblemon.mod.common.pokemon.FormData;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class MegaDex {
    private static final Map<String, MegaDex.Entry> DEX = new HashMap<>();

    private MegaDex() {
    }

    private static void put(String species, String letter, int[] stats, String types, String ability, String stone) {
        DEX.put(species + "|" + letter, new MegaDex.Entry(stats, types, ability, stone));
    }

    static String norm(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    public static MegaDex.Entry get(String species, String letter) {
        String sp = norm(species);
        String l = letter == null ? "" : norm(letter);
        MegaDex.Entry e = DEX.get(sp + "|" + l);
        if (e == null && l.isEmpty()) {
            e = DEX.get(sp + "|x");
        }

        return e;
    }

    public static String letterOf(FormData form) {
        if (form == null) {
            return null;
        } else {
            try {
                String n = norm(form.getName());
                int i = n.indexOf("mega");
                if (i < 0) {
                    return null;
                } else {
                    String rest = n.substring(i + 4);
                    return rest.length() == 1 ? rest : "";
                }
            } catch (Throwable var4) {
                return null;
            }
        }
    }

    public static MegaDex.Entry forForm(FormData form) {
        String letter = letterOf(form);
        if (letter == null) {
            return null;
        } else {
            try {
                return get(form.getSpecies().getName(), letter);
            } catch (Throwable var3) {
                return null;
            }
        }
    }

    public static MegaDex.Entry forBattler(ClientBattlePokemon bp, String side) {
        if (bp == null) {
            return null;
        } else {
            try {
                MegaDex.Entry e = forForm(FormAbilityHelper.effectiveBattleForm(bp, side));
                if (e != null) {
                    return e;
                } else {
                    String letter = MegaTracker.megaLetter(side, bp);
                    return letter == null ? null : get(bp.getSpecies().getName(), letter);
                }
            } catch (Throwable var4) {
                return null;
            }
        }
    }

    public static int statIndex(Stat stat) {
        Stat[] order = new Stat[]{Stats.HP, Stats.ATTACK, Stats.DEFENCE, Stats.SPECIAL_ATTACK, Stats.SPECIAL_DEFENCE, Stats.SPEED};

        for (int i = 0; i < order.length; i++) {
            if (order[i].equals(stat)) {
                return i;
            }
        }

        return -1;
    }

    static {
        put("venusaur", "", new int[]{80, 100, 123, 122, 120, 80}, "Grass/Poison", "Thick Fat", "Venusaurite");
        put("charizard", "x", new int[]{78, 130, 111, 130, 85, 100}, "Fire/Dragon", "Tough Claws", "Charizardite X");
        put("charizard", "y", new int[]{78, 104, 78, 159, 115, 100}, "Fire/Flying", "Drought", "Charizardite Y");
        put("blastoise", "", new int[]{79, 103, 120, 135, 115, 78}, "Water", "Mega Launcher", "Blastoisinite");
        put("beedrill", "", new int[]{65, 150, 40, 15, 80, 145}, "Bug/Poison", "Adaptability", "Beedrillite");
        put("pidgeot", "", new int[]{83, 80, 80, 135, 80, 121}, "Normal/Flying", "No Guard", "Pidgeotite");
        put("raichu", "x", new int[]{60, 135, 95, 90, 95, 110}, "Electric", "Electric Surge", "Raichunite X");
        put("raichu", "y", new int[]{60, 100, 55, 160, 80, 130}, "Electric", "No Guard", "Raichunite Y");
        put("clefable", "", new int[]{95, 80, 93, 135, 110, 70}, "Fairy/Flying", "Magic Bounce", "Clefablite");
        put("alakazam", "", new int[]{55, 50, 65, 175, 105, 150}, "Psychic", "Trace", "Alakazite");
        put("victreebel", "", new int[]{80, 125, 85, 135, 95, 70}, "Grass/Poison", "Innards Out", "Victreebelite");
        put("slowbro", "", new int[]{95, 75, 180, 130, 80, 30}, "Water/Psychic", "Shell Armor", "Slowbronite");
        put("gengar", "", new int[]{60, 65, 80, 170, 95, 130}, "Ghost/Poison", "Shadow Tag", "Gengarite");
        put("kangaskhan", "", new int[]{105, 125, 100, 60, 100, 100}, "Normal", "Parental Bond", "Kangaskhanite");
        put("starmie", "", new int[]{60, 100, 105, 130, 105, 120}, "Water/Psychic", "Huge Power", "Starminite");
        put("pinsir", "", new int[]{65, 155, 120, 65, 90, 105}, "Bug/Flying", "Aerilate", "Pinsirite");
        put("gyarados", "", new int[]{95, 155, 109, 70, 130, 81}, "Water/Dark", "Mold Breaker", "Gyaradosite");
        put("aerodactyl", "", new int[]{80, 135, 85, 70, 95, 150}, "Rock/Flying", "Tough Claws", "Aerodactylite");
        put("dragonite", "", new int[]{91, 124, 115, 145, 125, 100}, "Dragon/Flying", "Multiscale", "Dragoninite");
        put("mewtwo", "x", new int[]{106, 190, 100, 154, 100, 130}, "Psychic/Fighting", "Steadfast", "Mewtwonite X");
        put("mewtwo", "y", new int[]{106, 150, 70, 194, 120, 140}, "Psychic", "Insomnia", "Mewtwonite Y");
        put("meganium", "", new int[]{80, 92, 115, 143, 115, 80}, "Grass/Fairy", "Mega Sol", "Meganiumite");
        put("feraligatr", "", new int[]{85, 160, 125, 89, 93, 78}, "Water/Dragon", "Dragonize", "Feraligite");
        put("ampharos", "", new int[]{90, 95, 105, 165, 110, 45}, "Electric/Dragon", "Mold Breaker", "Ampharosite");
        put("steelix", "", new int[]{75, 125, 230, 55, 95, 30}, "Steel/Ground", "Sand Force", "Steelixite");
        put("scizor", "", new int[]{70, 150, 140, 65, 100, 75}, "Bug/Steel", "Technician", "Scizorite");
        put("heracross", "", new int[]{80, 185, 115, 40, 105, 75}, "Bug/Fighting", "Skill Link", "Heracronite");
        put("skarmory", "", new int[]{65, 140, 110, 40, 100, 110}, "Steel/Flying", "Stalwart", "Skarmorite");
        put("houndoom", "", new int[]{75, 90, 90, 140, 90, 115}, "Dark/Fire", "Solar Power", "Houndoominite");
        put("tyranitar", "", new int[]{100, 164, 150, 95, 120, 71}, "Rock/Dark", "Sand Stream", "Tyranitarite");
        put("sceptile", "", new int[]{70, 110, 75, 145, 85, 145}, "Grass/Dragon", "Lightning Rod", "Sceptilite");
        put("blaziken", "", new int[]{80, 160, 80, 130, 80, 100}, "Fire/Fighting", "Speed Boost", "Blazikenite");
        put("swampert", "", new int[]{100, 150, 110, 95, 110, 70}, "Water/Ground", "Swift Swim", "Swampertite");
        put("gardevoir", "", new int[]{68, 85, 65, 165, 135, 100}, "Psychic/Fairy", "Pixilate", "Gardevoirite");
        put("sableye", "", new int[]{50, 85, 125, 85, 115, 20}, "Dark/Ghost", "Magic Bounce", "Sablenite");
        put("mawile", "", new int[]{50, 105, 125, 55, 95, 50}, "Steel/Fairy", "Huge Power", "Mawilite");
        put("aggron", "", new int[]{70, 140, 230, 60, 80, 50}, "Steel", "Filter", "Aggronite");
        put("medicham", "", new int[]{60, 100, 85, 80, 85, 100}, "Fighting/Psychic", "Pure Power", "Medichamite");
        put("manectric", "", new int[]{70, 75, 80, 135, 80, 135}, "Electric", "Intimidate", "Manectite");
        put("sharpedo", "", new int[]{70, 140, 70, 110, 65, 105}, "Water/Dark", "Strong Jaw", "Sharpedonite");
        put("camerupt", "", new int[]{70, 120, 100, 145, 105, 20}, "Fire/Ground", "Sheer Force", "Cameruptite");
        put("altaria", "", new int[]{75, 110, 110, 110, 105, 80}, "Dragon/Fairy", "Pixilate", "Altarianite");
        put("banette", "", new int[]{64, 165, 75, 93, 83, 75}, "Ghost", "Prankster", "Banettite");
        put("chimecho", "", new int[]{75, 50, 110, 135, 120, 65}, "Psychic/Steel", "Levitate", "Chimechite");
        put("absol", "", new int[]{65, 150, 60, 115, 60, 115}, "Dark", "Magic Bounce", "Absolite");
        put("absol", "z", new int[]{65, 154, 60, 75, 60, 151}, "Dark/Ghost", "Sharpness", "Absolite Z");
        put("glalie", "", new int[]{80, 120, 80, 120, 80, 100}, "Ice", "Refrigerate", "Glalitite");
        put("salamence", "", new int[]{95, 145, 130, 120, 90, 120}, "Dragon/Flying", "Aerilate", "Salamencite");
        put("metagross", "", new int[]{80, 145, 150, 105, 110, 110}, "Steel/Psychic", "Tough Claws", "Metagrossite");
        put("latias", "", new int[]{80, 100, 120, 140, 150, 110}, "Dragon/Psychic", "Levitate", "Latiasite");
        put("latios", "", new int[]{80, 130, 100, 160, 120, 110}, "Dragon/Psychic", "Levitate", "Latiosite");
        put("rayquaza", "", new int[]{105, 180, 100, 180, 100, 115}, "Dragon/Flying", "Delta Stream", "");
        put("staraptor", "", new int[]{85, 140, 100, 60, 90, 110}, "Fighting/Flying", "Contrary", "Staraptite");
        put("lopunny", "", new int[]{65, 136, 94, 54, 96, 135}, "Normal/Fighting", "Scrappy", "Lopunnite");
        put("garchomp", "", new int[]{108, 170, 115, 120, 95, 92}, "Dragon/Ground", "Sand Force", "Garchompite");
        put("garchomp", "z", new int[]{108, 130, 85, 141, 85, 151}, "Dragon", "Levitate", "Garchompite Z");
        put("lucario", "", new int[]{70, 145, 88, 140, 70, 112}, "Fighting/Steel", "Adaptability", "Lucarionite");
        put("lucario", "z", new int[]{70, 100, 70, 164, 70, 151}, "Fighting/Steel", "Aura Guard", "Lucarionite Z");
        put("abomasnow", "", new int[]{90, 132, 105, 132, 105, 30}, "Grass/Ice", "Snow Warning", "Abomasite");
        put("gallade", "", new int[]{68, 165, 95, 65, 115, 110}, "Psychic/Fighting", "Inner Focus", "Galladite");
        put("froslass", "", new int[]{70, 80, 70, 140, 100, 120}, "Ice/Ghost", "Snow Warning", "Froslassite");
        put("heatran", "", new int[]{91, 120, 106, 175, 141, 67}, "Fire/Steel", "Flash Fire", "Heatranite");
        put("darkrai", "", new int[]{70, 120, 130, 165, 130, 85}, "Dark", "Bad Dreams", "Darkranite");
        put("emboar", "", new int[]{110, 148, 75, 110, 110, 75}, "Fire/Fighting", "Mold Breaker", "Emboarite");
        put("excadrill", "", new int[]{110, 165, 100, 65, 65, 103}, "Ground/Steel", "Piercing Drill", "Excadrite");
        put("audino", "", new int[]{103, 60, 126, 80, 126, 50}, "Normal/Fairy", "Healer", "Audinite");
        put("scolipede", "", new int[]{60, 140, 149, 75, 99, 62}, "Bug/Poison", "Shell Armor", "Scolipite");
        put("scrafty", "", new int[]{65, 130, 135, 55, 135, 68}, "Dark/Fighting", "Intimidate", "Scraftinite");
        put("eelektross", "", new int[]{85, 145, 80, 135, 90, 80}, "Electric", "Eelevate", "Eelektrossite");
        put("chandelure", "", new int[]{60, 75, 110, 175, 110, 90}, "Ghost/Fire", "Infiltrator", "Chandelurite");
        put("golurk", "", new int[]{89, 159, 105, 70, 105, 55}, "Ground/Ghost", "Unseen Fist", "Golurkite");
        put("chesnaught", "", new int[]{88, 137, 172, 74, 115, 44}, "Grass/Fighting", "Bulletproof", "Chesnaughtite");
        put("delphox", "", new int[]{75, 69, 72, 159, 125, 134}, "Fire/Psychic", "Levitate", "Delphoxite");
        put("greninja", "", new int[]{72, 125, 77, 133, 81, 142}, "Water/Dark", "Protean", "Greninjite");
        put("pyroar", "", new int[]{86, 88, 92, 129, 86, 126}, "Fire/Normal", "Fire Mane", "Pyroarite");
        put("floette", "", new int[]{74, 85, 87, 155, 148, 102}, "Fairy", "Fairy Aura", "Floettite");
        put("meowsticm", "", new int[]{74, 48, 76, 143, 101, 124}, "Psychic", "Trace", "Meowsticite");
        put("meowsticf", "", new int[]{74, 48, 76, 143, 101, 124}, "Psychic", "Trace", "Meowsticite");
        put("malamar", "", new int[]{86, 102, 88, 98, 120, 88}, "Dark/Psychic", "Contrary", "Malamarite");
        put("barbaracle", "", new int[]{72, 140, 130, 64, 106, 88}, "Rock/Fighting", "Tough Claws", "Barbaracite");
        put("dragalge", "", new int[]{65, 85, 105, 132, 163, 44}, "Poison/Dragon", "Regenerator", "Dragalgite");
        put("hawlucha", "", new int[]{78, 137, 100, 74, 93, 118}, "Fighting/Flying", "No Guard", "Hawluchanite");
        put("zygarde", "", new int[]{216, 70, 91, 216, 85, 100}, "Dragon/Ground", "Aura Break", "Zygardite");
        put("diancie", "", new int[]{50, 160, 110, 160, 110, 110}, "Rock/Fairy", "Magic Bounce", "Diancite");
        put("crabominable", "", new int[]{97, 157, 122, 62, 107, 33}, "Fighting/Ice", "Iron Fist", "Crabominite");
        put("golisopod", "", new int[]{75, 150, 175, 70, 120, 40}, "Bug/Steel", "Tough Claws", "Golisopite");
        put("drampa", "", new int[]{78, 85, 110, 160, 116, 36}, "Normal/Dragon", "Berserk", "Drampanite");
        put("magearna", "", new int[]{80, 125, 115, 170, 115, 95}, "Steel/Fairy", "Soul-Heart", "Magearnite");
        put("magearnaoriginal", "", new int[]{80, 125, 115, 170, 115, 95}, "Steel/Fairy", "Soul-Heart", "Magearnite");
        put("zeraora", "", new int[]{88, 157, 75, 147, 80, 153}, "Electric", "Volt Absorb", "Zeraorite");
        put("falinks", "", new int[]{65, 135, 135, 70, 65, 100}, "Fighting", "Defiant", "Falinksite");
        put("scovillain", "", new int[]{65, 138, 85, 138, 85, 75}, "Grass/Fire", "Spicy Spray", "Scovillainite");
        put("glimmora", "", new int[]{83, 90, 105, 150, 96, 101}, "Rock/Poison", "Adaptability", "Glimmoranite");
        put("tatsugiricurly", "", new int[]{68, 65, 90, 135, 125, 92}, "Dragon/Water", "Commander", "Tatsugirinite");
        put("tatsugiridroopy", "", new int[]{68, 65, 90, 135, 125, 92}, "Dragon/Water", "Commander", "Tatsugirinite");
        put("tatsugiristretchy", "", new int[]{68, 65, 90, 135, 125, 92}, "Dragon/Water", "Commander", "Tatsugirinite");
        put("baxcalibur", "", new int[]{115, 175, 117, 105, 101, 87}, "Dragon/Ice", "Thermal Exchange", "Baxcalibrite");
        put("crucibelle", "", new int[]{106, 135, 75, 91, 125, 108}, "Rock/Poison", "Magic Guard", "Crucibellite");
    }

    public record Entry(int[] stats, String types, String ability, String stone) {
    }
}
