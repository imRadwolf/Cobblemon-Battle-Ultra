package name.modid.client;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.class_310;
import net.minecraft.class_327;
import net.minecraft.class_332;

/**
 * Panel shown when hovering a Pokemon in a team strip: same look as the info panels (header with HP bar, status and
 * types, then the usual ability / item / stats / speed / moves body), with real PP for yours and PP Max assumed for theirs.
 */
public final class TeamPanelRenderer {
    static final int W = AttachedPanelRenderer.WIDTH;
    static final int HEADER_H = 37;

    private TeamPanelRenderer() {
    }

    /** What the header shows. ratio < 0 = never seen (HP unknown). */
    public record Header(String name, int level, float ratio, int hp, int maxHp, String status, boolean fainted, String note) {
    }

    public static boolean enabled() {
        return ExtrasPlusConfig.get().attachedPanelStyle;
    }

    /**
     * Draws the panel next to the hovered strip slot.
     * slot = {x, y, w, h} of the hovered slot, strip = {x, y, w, h} of the whole strip (screen coordinates).
     */
    public static void render(class_332 gui, PanelData data, Header header, SpeedTiers.Report speed, int[] slot, int[] strip, boolean horizontal, float progress) {
        class_310 mc = class_310.method_1551();
        class_327 font = mc.field_1772;
        float s = ExtrasPlusConfig.get().pinnedPanelScale;
        int bodyH = AttachedPanelRenderer.height(data);
        int totalH = HEADER_H - 1 + bodyH;
        int pw = Math.round(W * s);
        int ph = Math.round(totalH * s);
        int sw = mc.method_22683().method_4486();
        int sh = mc.method_22683().method_4502();
        int x;
        int y;
        if (horizontal) {
            x = slot[0] + slot[2] / 2 - pw / 2;
            y = strip[1] + strip[3] + 2;
            if (y + ph > sh - 2 && strip[1] - ph - 2 >= 2) {
                y = strip[1] - ph - 2;
            }
        } else {
            boolean leftSide = strip[0] + strip[2] / 2 < sw / 2;
            x = leftSide ? strip[0] + strip[2] + 2 : strip[0] - pw - 2;
            y = slot[1] + slot[3] / 2 - ph / 2;
        }
        x = Math.max(2, Math.min(sw - pw - 2, x));
        y = Math.max(2, Math.min(sh - ph - 2, y));
        float eased = 1.0F - (float)Math.pow(1.0F - Math.max(0.0F, Math.min(1.0F, progress)), 3.0);
        float opacity = Math.max(0.05F, eased);

        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        gui.method_51448().method_22903();
        gui.method_51448().method_46416(x, y + (1.0F - eased) * 6.0F, 5000.0F);
        gui.method_51448().method_22905(s, s, 1.0F);
        drawHeader(gui, font, data, header, opacity);
        gui.method_51448().method_22903();
        gui.method_51448().method_46416(0.0F, HEADER_H - 1, 0.0F);
        List<PinnedHoverZone> zones = new ArrayList<>();
        AttachedPanelRenderer.render(gui, data, speed, zones, opacity);
        gui.method_51448().method_22909();
        gui.method_51448().method_22909();
        RenderSystem.enableDepthTest();
    }

    private static void drawHeader(class_332 gui, class_327 font, PanelData data, Header h, float opacity) {
        UiStyle style = UiStyle.current();
        style.frame(gui, 0, 0, W, HEADER_H, 0, opacity);
        // name + level
        style.section(gui, 2, 2, W - 2, 12, 0, UiStyle.Accent.NONE, opacity);
        String lv = "Lv." + h.level();
        int lw = AttachedPanelRenderer.width(font, lv, 0.6F);
        String name = AttachedPanelRenderer.trim(font, h.name(), W - 12 - lw, 0.62F);
        AttachedPanelRenderer.text(gui, font, name, 5, 5, AttachedPanelRenderer.op(0xFFFFFFFF, opacity), 0.62F);
        AttachedPanelRenderer.text(gui, font, lv, W - 5 - lw, 5, AttachedPanelRenderer.op(0xFFE8E8E8, opacity), 0.6F);
        // HP bar
        int barY = 14;
        style.section(gui, 2, barY, W - 2, barY + 10, 0, UiStyle.Accent.NONE, opacity);
        int bx1 = 4;
        int bx2 = W - 4;
        UiStyle.fill(gui, bx1, barY + 2, bx2, barY + 8, AttachedPanelRenderer.op(0xFF1A1A22, opacity));
        float r = h.ratio();
        String hpText;
        if (h.fainted()) {
            hpText = "Fainted";
        } else if (r < 0.0F) {
            UiStyle.fill(gui, bx1, barY + 2, bx2, barY + 8, AttachedPanelRenderer.op(0xFF4A4F5A, opacity));
            hpText = "100%? · not seen yet";
        } else {
            int fill = Math.round((bx2 - bx1) * r);
            if (fill > 0) {
                int c = TeamStatusTracker.hpColor(r);
                UiStyle.fill(gui, bx1, barY + 2, bx1 + fill, barY + 8, AttachedPanelRenderer.op(c, opacity));
                UiStyle.fill(gui, bx1, barY + 2, bx1 + fill, barY + 3, AttachedPanelRenderer.op(0x55FFFFFF, opacity));
            }
            int pct = r > 0.0F ? Math.max(1, Math.round(r * 100.0F)) : 0;
            hpText = h.hp() >= 0 && h.maxHp() > 0 ? h.hp() + "/" + h.maxHp() + " · " + pct + "%" : pct + "%";
        }
        int tw = AttachedPanelRenderer.width(font, hpText, 0.55F);
        int txx = bx1 + (bx2 - bx1 - tw) / 2;
        // dark outline so the text stays readable on a yellow / green bar
        for (int[] o : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            gui.method_51448().method_22903();
            gui.method_51448().method_46416(o[0] * 0.5F, o[1] * 0.5F, 0.0F);
            AttachedPanelRenderer.text(gui, font, hpText, txx, barY + 2, AttachedPanelRenderer.op(0xE0101010, opacity), 0.55F);
            gui.method_51448().method_22909();
        }
        AttachedPanelRenderer.text(gui, font, hpText, txx, barY + 2, AttachedPanelRenderer.op(0xFFFFFFFF, opacity), 0.55F);
        // types (left), status (right)
        int ty = 26;
        int tx = 3;
        for (String t : data.types) {
            if (t == null || t.isBlank()) {
                continue;
            }
            String label = t.substring(0, 1).toUpperCase() + t.substring(1);
            int c = 0xFF000000 | AttachedPanelRenderer.typeColor(t);
            int w = AttachedPanelRenderer.width(font, label, 0.45F) + 6;
            style.pill(gui, tx, ty, tx + w, ty + 8, c, opacity);
            AttachedPanelRenderer.text(gui, font, label, tx + 3, ty + 2, style.pillText(c), 0.45F);
            tx += w + 2;
        }
        String st = h.fainted() ? "fnt" : h.status();
        String label = TeamStatusTracker.statusLabel(st);
        int rx = W - 3;
        if (label != null) {
            int c = TeamStatusTracker.statusColor(st);
            int w = AttachedPanelRenderer.width(font, label, 0.5F) + 8;
            style.pill(gui, rx - w, ty - 1, rx, ty + 9, c, opacity);
            AttachedPanelRenderer.text(gui, font, label, rx - w + 4, ty + 1, 0xFFFFFFFF, 0.5F);
            rx -= w + 3;
        }
        if (h.note() != null && !h.note().isEmpty()) {
            int nw = AttachedPanelRenderer.width(font, h.note(), 0.42F);
            if (rx - nw > tx + 2) {
                AttachedPanelRenderer.text(gui, font, h.note(), rx - nw, ty + 2, AttachedPanelRenderer.op(0xFF9AA0AA, opacity), 0.42F);
            }
        }
    }

    /** Thin HP bar + status dot drawn on a strip slot (x, y = slot top-left, 25 x 23). */
    public static void drawSlotBadges(class_332 gui, float ratio, String status, boolean fainted, int x, int y, int w, int h) {
        gui.method_51448().method_22903();
        gui.method_51448().method_46416(0.0F, 0.0F, 400.0F);
        RenderSystem.enableBlend();
        int bx1 = x + 3;
        int bx2 = x + w - 3;
        int by = y + h - 4;
        UiStyle.fill(gui, bx1, by, bx2, by + 2, 0xCC10131A);
        if (!fainted && ratio >= 0.0F) {
            int fill = Math.max(ratio > 0.0F ? 1 : 0, Math.round((bx2 - bx1) * ratio));
            UiStyle.fill(gui, bx1, by, bx1 + fill, by + 2, TeamStatusTracker.hpColor(ratio));
        } else if (ratio < 0.0F && !fainted) {
            UiStyle.fill(gui, bx1, by, bx2, by + 2, 0xFF4A4F5A);
        }
        String label = fainted ? null : TeamStatusTracker.statusLabel(status);
        if (label != null) {
            class_327 font = class_310.method_1551().field_1772;
            int c = TeamStatusTracker.statusColor(status);
            int lw = AttachedPanelRenderer.width(font, label, 0.4F);
            int px2 = x + w - 2;
            int px1 = px2 - lw - 3;
            UiStyle.fill(gui, px1, y + 2, px2, y + 7, c);
            AttachedPanelRenderer.text(gui, font, label, px1 + 2, y + 3, 0xFFFFFFFF, 0.4F);
        }
        gui.method_51448().method_22909();
    }
}
