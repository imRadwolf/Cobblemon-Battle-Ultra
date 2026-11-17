package name.modid.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;

public final class ExtrasPlusConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "cobblemon-battle-extras-plus.json";
    private static ExtrasPlusConfig instance;
    public boolean pinnedPanelsEnabled = true;
    public boolean pinnedPlayerPanel = true;
    public boolean pinnedOpponentPanel = true;
    public boolean pinnedAvoidSideStrips = true;
    public float pinnedPanelScale = 1.0F;
    public boolean ultraTiles = true;
    public boolean smoothFont = true;
    public int pinnedOffsetX = 0;
    public int pinnedOffsetY = 0;
    public boolean pinnedHoverTooltips = true;
    public boolean showPossibleAbilities = true;
    public boolean teamPreviewCapture = true;
    public int teamPreviewMaxAgeSeconds = 300;
    public boolean teamPreviewAllowNonPlayers = false;
    public boolean teamPreviewOverrideServerData = false;
    public boolean portraitsInsteadOfBalls = true;
    public boolean showMoveContact = true;
    public boolean hideChatInBattleMenu = true;
    public boolean refinedDamagePreview = true;
    public boolean damagePreviewBreakdown = true;
    public boolean buildInference = true;
    public boolean showLikelyOpponentInfo = true;
    public boolean showIncomingDamage = true;
    public boolean switchThreatBox = true;
    public boolean contactWarnings = true;
    public boolean moveOrderLine = true;
    public boolean moveAdvisor = true;
    public boolean modifierChips = true;
    public boolean opponentMemory = true;
    public int uiStyle = 0;
    public List<String> disabledFormats = new ArrayList<>();
    public boolean volatileBadges = true;
    public boolean switchAdvisor = true;
    public boolean opponentPpTracking = true;
    public boolean battleRecap = true;
    public int recapSeconds = 25;
    public boolean attachedPanelStyle = true;
    public boolean fieldHudEnabled = true;
    public float fieldHudScale = 1.0F;
    public int fieldHudOffsetY = 0;

    private ExtrasPlusConfig() {
    }

    public static ExtrasPlusConfig get() {
        if (instance == null) {
            load();
        }

        return instance;
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("cobblemon-battle-extras-plus.json");
    }

    public static void load() {
        ExtrasPlusConfig loaded = null;

        try {
            Path p = path();
            if (Files.exists(p)) {
                try (Reader reader = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                    loaded = (ExtrasPlusConfig)GSON.fromJson(reader, ExtrasPlusConfig.class);
                }
            }
        } catch (Throwable var7) {
        }

        instance = loaded != null ? loaded : new ExtrasPlusConfig();
        instance.sanitize();
        if (loaded == null) {
            save();
        }
    }

    public static void save() {
        try {
            ExtrasPlusConfig cfg = get();
            cfg.sanitize();
            Path p = path();
            Files.createDirectories(p.getParent());

            try (Writer writer = Files.newBufferedWriter(p, StandardCharsets.UTF_8)) {
                GSON.toJson(cfg, writer);
            }
        } catch (Throwable var7) {
        }
    }

    private void sanitize() {
        if (!(this.pinnedPanelScale >= 0.5F) || this.pinnedPanelScale > 1.5F) {
            this.pinnedPanelScale = Math.max(0.5F, Math.min(1.5F, Float.isNaN(this.pinnedPanelScale) ? 1.0F : this.pinnedPanelScale));
        }

        this.pinnedOffsetX = Math.max(-300, Math.min(300, this.pinnedOffsetX));
        this.pinnedOffsetY = Math.max(-300, Math.min(300, this.pinnedOffsetY));
        this.teamPreviewMaxAgeSeconds = Math.max(30, Math.min(3600, this.teamPreviewMaxAgeSeconds));
        if (!(this.fieldHudScale >= 0.6F) || this.fieldHudScale > 2.0F) {
            this.fieldHudScale = 1.0F;
        }
    }
}
