package name.modid.client;

import com.cobblemon.mod.common.client.battle.ClientBattlePokemon;
import com.cobblemon.mod.common.pokemon.FormData;
import com.cobblemon.mod.common.pokemon.Pokemon;
import com.cobblemon.mod.common.pokemon.Species;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.class_1799;

public final class PanelData {
    public boolean playerSide;
    /** Drawn for a benched Pokemon (team strip panel): no move advice for the active Pokemon. */
    public boolean bench;
    public String abilityText = "???";
    public String abilityId;
    public int abilityColor = 14342874;
    public String itemText;
    public class_1799 itemStack;
    public boolean itemKnown;
    public boolean itemConsumed;
    public boolean itemLost;
    public String itemLostReason;
    public boolean abilitySuppressed;
    public String abilityNote;
    public final String[] statLabels = new String[]{"ATK", "DEF", "SPA", "SPD", "SPE"};
    public final String[] statValues = new String[]{"?", "?", "?", "?", "?"};
    public final int[] statColors = new int[]{15790320, 15790320, 15790320, 15790320, 15790320};
    public final List<PanelData.MoveRow> moves = new ArrayList<>();
    public final List<String> types = new ArrayList<>();
    public ClientBattlePokemon battlePokemon;
    public Pokemon partyPokemon;
    public Species species;
    public FormData form;
    public int level = 100;
    public BuildInference.Info inference;
    public ThreatCalc.Report threats;
    public List<ModifierChips.Chip> modifiers = List.of();

    public record MoveRow(String name, String id, String typeName, String ppText, int ppColor, boolean known) {
    }
}
