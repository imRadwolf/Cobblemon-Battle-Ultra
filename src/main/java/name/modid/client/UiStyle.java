package name.modid.client;

import java.util.Locale;
import net.minecraft.class_332;

public abstract class UiStyle {
    public static final String[] NAMES = new String[]{"Classic", "Glass", "Cards", "Pixel (GBA)", "Holo HUD"};
    private static final UiStyle[] STYLES = new UiStyle[]{
        new UiStyle.Classic(), new UiStyle.Glass(), new UiStyle.Cards(), new UiStyle.Pixel(), new UiStyle.Holo()
    };

    public static UiStyle current() {
        int i = ExtrasPlusConfig.get().uiStyle;
        return STYLES[Math.max(0, Math.min(STYLES.length - 1, i))];
    }

    public abstract void frame(class_332 var1, int var2, int var3, int var4, int var5, int var6, float var7);

    public abstract void section(class_332 var1, int var2, int var3, int var4, int var5, int var6, UiStyle.Accent var7, float var8);

    public abstract void moveChip(class_332 var1, int var2, int var3, int var4, int var5, int var6, UiStyle.Chip var7, boolean var8, float var9);

    public abstract void pill(class_332 var1, int var2, int var3, int var4, int var5, int var6, float var7);

    public int text(int argb) {
        return argb;
    }

    public boolean shadow() {
        return true;
    }

    public int dim() {
        return -2012212200;
    }

    public String label(String s) {
        return s;
    }

    public int pillText(int pillColor) {
        return -1;
    }

    static int op(int color, float opacity) {
        return BattleOpacityHelper.applyOpacity(color, opacity);
    }

    static int a(int rgb, int alpha) {
        return alpha << 24 | rgb & 16777215;
    }

    static int mix(int a, int b, float t) {
        int ar = a >> 16 & 0xFF;
        int ag = a >> 8 & 0xFF;
        int ab = a & 0xFF;
        int br = b >> 16 & 0xFF;
        int bg = b >> 8 & 0xFF;
        int bb = b & 0xFF;
        return (int)(ar + (br - ar) * t) << 16 | (int)(ag + (bg - ag) * t) << 8 | (int)(ab + (bb - ab) * t);
    }

    static int dark(int rgb, float f) {
        return (int)((rgb >> 16 & 0xFF) * f) << 16 | (int)((rgb >> 8 & 0xFF) * f) << 8 | (int)((rgb & 0xFF) * f);
    }

    static float lum(int rgb) {
        return (0.299F * (rgb >> 16 & 0xFF) + 0.587F * (rgb >> 8 & 0xFF) + 0.114F * (rgb & 0xFF)) / 255.0F;
    }

    static void fill(class_332 g, int x1, int y1, int x2, int y2, int argb) {
        if (x2 > x1 && y2 > y1) {
            g.method_25294(x1, y1, x2, y2, argb);
        }
    }

    static void grad(class_332 g, int x1, int y1, int x2, int y2, int top, int bottom) {
        if (x2 > x1 && y2 > y1) {
            g.method_25296(x1, y1, x2, y2, top, bottom);
        }
    }

    static void outline(class_332 g, int x1, int y1, int x2, int y2, int argb) {
        fill(g, x1, y1, x2, y1 + 1, argb);
        fill(g, x1, y2 - 1, x2, y2, argb);
        fill(g, x1, y1 + 1, x1 + 1, y2 - 1, argb);
        fill(g, x2 - 1, y1 + 1, x2, y2 - 1, argb);
    }

    static void rounded(class_332 g, int x1, int y1, int x2, int y2, int argb) {
        fill(g, x1 + 1, y1, x2 - 1, y1 + 1, argb);
        fill(g, x1, y1 + 1, x2, y2 - 1, argb);
        fill(g, x1 + 1, y2 - 1, x2 - 1, y2, argb);
    }

    static void roundedOutline(class_332 g, int x1, int y1, int x2, int y2, int argb) {
        fill(g, x1 + 1, y1, x2 - 1, y1 + 1, argb);
        fill(g, x1 + 1, y2 - 1, x2 - 1, y2, argb);
        fill(g, x1, y1 + 1, x1 + 1, y2 - 1, argb);
        fill(g, x2 - 1, y1 + 1, x2, y2 - 1, argb);
    }

    public static enum Accent {
        NONE,
        LEFT,
        TOP;
    }

    static final class Cards extends UiStyle {
        @Override
        public void frame(class_332 g, int x1, int y1, int x2, int y2, int accent, float op) {
            rounded(g, x1 + 2, y1 + 2, x2 + 2, y2 + 2, op(1879048192, op));
            rounded(g, x1, y1, x2, y2, op(-199088077, op));
            roundedOutline(g, x1, y1, x2, y2, op(-12827306, op));
            fill(g, x1 + 2, y1 + 1, x2 - 2, y1 + 2, op(822083583, op));
            if (accent != 0) {
                fill(g, x1 + 3, y1, x2 - 3, y1 + 1, op(0xFF000000 | accent, op));
            }
        }

        @Override
        public void section(class_332 g, int x1, int y1, int x2, int y2, int accent, UiStyle.Accent where, float op) {
            rounded(g, x1, y1, x2, y2, op(-15196890, op));
            fill(g, x1 + 1, y1, x2 - 1, y1 + 1, op(419430399, op));
            if (accent != 0) {
                if (where == UiStyle.Accent.LEFT) {
                    rounded(g, x1, y1 + 1, x1 + 2, y2 - 1, op(0xFF000000 | accent, op));
                } else if (where == UiStyle.Accent.TOP) {
                    rounded(g, x1 + 2, y1, x2 - 2, y1 + 2, op(0xFF000000 | accent, op));
                }
            }
        }

        @Override
        public void moveChip(class_332 g, int x1, int y1, int x2, int y2, int type, UiStyle.Chip kind, boolean best, float op) {
            switch (kind) {
                case LIKELY:
                    rounded(g, x1, y1, x2, y2, op(a(dark(type, 0.28F), 200), op));
                    roundedOutline(g, x1, y1, x2, y2, op(a(type, 140), op));
                    break;
                case MEMORY:
                    rounded(g, x1, y1, x2, y2, op(a(mix(dark(type, 0.4F), dark(13084415, 0.45F), 0.6F), 235), op));
                    roundedOutline(g, x1, y1, x2, y2, op(-3692801, op));
                    break;
                case EMPTY:
                    roundedOutline(g, x1, y1, x2, y2, op(1627389951, op));
                    break;
                default:
                    rounded(g, x1, y1, x2, y2, op(0xFF000000 | dark(type, 0.82F), op));
                    grad(g, x1 + 1, y1 + (y2 - y1) / 2, x2 - 1, y2 - 1, op(a(dark(type, 0.82F), 0), op), op(a(dark(type, 0.5F), 255), op));
                    fill(g, x1 + 1, y1, x2 - 1, y1 + 1, op(a(mix(type, 16777215, 0.6F), 210), op));
                    fill(g, x1 + 1, y2 - 1, x2 - 1, y2, op(a(dark(type, 0.35F), 255), op));
            }

            if (best) {
                roundedOutline(g, x1 - 1, y1 - 1, x2 + 1, y2 + 1, op(-10496, op));
            }
        }

        @Override
        public void pill(class_332 g, int x1, int y1, int x2, int y2, int color, float op) {
            rounded(g, x1, y1, x2, y2, op(0xFF000000 | dark(color & 16777215, 0.42F), op));
            roundedOutline(g, x1, y1, x2, y2, op(color, op));
        }
    }

    public static enum Chip {
        REVEALED,
        LIKELY,
        MEMORY,
        EMPTY;
    }

    static final class Classic extends UiStyle {
        @Override
        public void frame(class_332 g, int x1, int y1, int x2, int y2, int accent, float op) {
            BattleUiThemePalette.Theme theme = BattleUiThemePalette.getActiveBattleLogTheme();
            int border = op(0xFF000000 | mix(theme.borderHighlight(), theme.panelBgGradient(), 0.25F), op);
            fill(g, x1, y1, x2, y2, border);
            grad(g, x1 + 1, y1, x2 - 1, y2 - 1, op(a(theme.panelBgGradient(), 232), op), op(a(theme.panelBgBottom(), 236), op));
            fill(g, x1 + 2, y2 - 2, x2 - 2, y2 - 1, op(a(theme.panelBgBottom(), 210), op));
        }

        @Override
        public void section(class_332 g, int x1, int y1, int x2, int y2, int accent, UiStyle.Accent where, float op) {
            fill(
                g,
                x1,
                y1,
                x2,
                y2,
                op(where == UiStyle.Accent.NONE ? 1073741824 : (where == UiStyle.Accent.LEFT && y2 - y1 <= 10 ? 1426063360 : 1073741824), op)
            );
            if (accent != 0) {
                if (where == UiStyle.Accent.LEFT) {
                    fill(g, x1, y1, x1 + 1, y2, op(0xFF000000 | accent, op));
                } else if (where == UiStyle.Accent.TOP) {
                    fill(g, x1, y1, x2, y1 + 1, op(0xFF000000 | accent, op));
                }
            }
        }

        @Override
        public void moveChip(class_332 g, int x1, int y1, int x2, int y2, int type, UiStyle.Chip kind, boolean best, float op) {
            switch (kind) {
                case LIKELY:
                    fill(g, x1, y1, x2, y2, op(a(dark(type, 0.3F), 120), op));
                    fill(g, x1, y1, x1 + 2, y2, op(a(type, 150), op));
                    break;
                case MEMORY:
                    grad(g, x1, y1, x2, y2, op(a(dark(type, 0.45F), 180), op), op(a(dark(13084415, 0.25F), 180), op));
                    fill(g, x1, y1, x1 + 2, y2, op(-3692801, op));
                    break;
                case EMPTY:
                    fill(g, x1, y1, x2, y2, op(1073741824, op));
                    break;
                default:
                    grad(g, x1, y1, x2, y2, op(a(dark(type, 0.55F), 200), op), op(a(dark(type, 0.35F), 200), op));
                    fill(g, x1, y1, x1 + 2, y2, op(0xFF000000 | type, op));
            }

            if (best) {
                int gold = op(-10496, op);
                fill(g, x1, y1, x2, y1 + 1, gold);
                fill(g, x1, y2 - 1, x2, y2, gold);
                fill(g, x2 - 1, y1, x2, y2, gold);
            }
        }

        @Override
        public void pill(class_332 g, int x1, int y1, int x2, int y2, int color, float op) {
            fill(g, x1, y1, x2, y2, op(a(dark(color & 16777215, 0.33F), 230), op));
            fill(g, x1, y2 - 1, x2, y2, op(color, op));
        }
    }

    static final class Glass extends UiStyle {
        @Override
        public void frame(class_332 g, int x1, int y1, int x2, int y2, int accent, float op) {
            fill(g, x1, y1, x2, y2, op(-1710551276, op));
            fill(g, x1, y1, x2, y1 + 1, op(1090519039, op));
            if (accent != 0) {
                fill(g, x1, y1, x1 + 1, y2, op(a(accent, 200), op));
            }
        }

        @Override
        public void section(class_332 g, int x1, int y1, int x2, int y2, int accent, UiStyle.Accent where, float op) {
            fill(g, x1, y2 - 1, x2, y2, op(654311423, op));
            if (accent != 0) {
                if (where == UiStyle.Accent.TOP) {
                    fill(g, x1, y2 - 1, x1 + Math.max(6, (x2 - x1) / 3), y2, op(a(accent, 230), op));
                } else if (where == UiStyle.Accent.LEFT) {
                    fill(g, x1, y1 + 2, x1 + 1, y2 - 2, op(a(accent, 230), op));
                }
            }
        }

        @Override
        public void moveChip(class_332 g, int x1, int y1, int x2, int y2, int type, UiStyle.Chip kind, boolean best, float op) {
            int line = kind == UiStyle.Chip.MEMORY ? 13084415 : type;
            int alpha = kind == UiStyle.Chip.REVEALED ? 60 : (kind == UiStyle.Chip.EMPTY ? 0 : 28);
            if (alpha > 0) {
                grad(g, x1, y1, x2, y2, op(a(line, 0), op), op(a(line, alpha), op));
            }

            fill(g, x1, y2 - 1, x2, y2, op(a(kind == UiStyle.Chip.EMPTY ? 16777215 : line, kind == UiStyle.Chip.REVEALED ? 255 : 110), op));
            if (best) {
                fill(g, x1, y2 - 2, x2, y2, op(-10496, op));
                fill(g, x1, y1, x1 + 1, y2, op(-10496, op));
            }
        }

        @Override
        public void pill(class_332 g, int x1, int y1, int x2, int y2, int color, float op) {
            fill(g, x1, y1, x2, y2, op(a(color, 55), op));
            fill(g, x1, y1 + 1, x1 + 1, y2 - 1, op(a(color, 255), op));
        }

        @Override
        public boolean shadow() {
            return true;
        }
    }

    static final class Holo extends UiStyle {
        private static final int CYAN = 3073791;

        private static void brackets(class_332 g, int x1, int y1, int x2, int y2, int argb, int len) {
            fill(g, x1, y1, x1 + len, y1 + 1, argb);
            fill(g, x1, y1, x1 + 1, y1 + len, argb);
            fill(g, x2 - len, y1, x2, y1 + 1, argb);
            fill(g, x2 - 1, y1, x2, y1 + len, argb);
            fill(g, x1, y2 - 1, x1 + len, y2, argb);
            fill(g, x1, y2 - len, x1 + 1, y2, argb);
            fill(g, x2 - len, y2 - 1, x2, y2, argb);
            fill(g, x2 - 1, y2 - len, x2, y2, argb);
        }

        @Override
        public void frame(class_332 g, int x1, int y1, int x2, int y2, int accent, float op) {
            int c = accent != 0 ? accent : 3073791;
            fill(g, x1, y1, x2, y2, op(-435942894, op));
            outline(g, x1, y1, x2, y2, op(a(c, 45), op));
            brackets(g, x1, y1, x2, y2, op(0xFF000000 | c, op), 5);

            for (int yy = y1 + 3; yy < y2 - 2; yy += 3) {
                fill(g, x1 + 1, yy, x2 - 1, yy + 1, op(a(c, 8), op));
            }
        }

        @Override
        public void section(class_332 g, int x1, int y1, int x2, int y2, int accent, UiStyle.Accent where, float op) {
            int c = accent != 0 ? accent : 3073791;
            outline(g, x1, y1, x2, y2, op(a(c, accent != 0 ? 90 : 50), op));
            if (accent != 0) {
                if (where == UiStyle.Accent.LEFT) {
                    fill(g, x1, y1, x1 + 1, y2, op(0xFF000000 | c, op));
                    fill(g, x1 + 1, y1, x1 + 3, y2, op(a(c, 50), op));
                } else if (where == UiStyle.Accent.TOP) {
                    fill(g, x1, y1, x2, y1 + 1, op(0xFF000000 | c, op));
                    fill(g, x1, y1 + 1, x2, y1 + 3, op(a(c, 45), op));
                }
            }
        }

        @Override
        public void moveChip(class_332 g, int x1, int y1, int x2, int y2, int type, UiStyle.Chip kind, boolean best, float op) {
            int c = kind == UiStyle.Chip.MEMORY ? 13084415 : (kind == UiStyle.Chip.EMPTY ? 5925496 : type);
            int fillAlpha = kind == UiStyle.Chip.REVEALED ? 48 : (kind == UiStyle.Chip.EMPTY ? 0 : 20);
            fill(g, x1, y1, x2 - 3, y2, op(a(c, fillAlpha), op));
            fill(g, x2 - 3, y1 + 3, x2, y2, op(a(c, fillAlpha), op));
            int line = op(a(c, kind == UiStyle.Chip.REVEALED ? 230 : 120), op);
            fill(g, x1, y1, x2 - 3, y1 + 1, line);
            fill(g, x1, y2 - 1, x2, y2, line);
            fill(g, x1, y1, x1 + 1, y2, line);
            fill(g, x2 - 1, y1 + 3, x2, y2, line);
            fill(g, x2 - 3, y1 + 1, x2 - 2, y1 + 2, line);
            fill(g, x2 - 2, y1 + 2, x2 - 1, y1 + 3, line);
            if (best) {
                brackets(g, x1 - 1, y1 - 1, x2 + 1, y2 + 1, op(-10496, op), 4);
            }
        }

        @Override
        public void pill(class_332 g, int x1, int y1, int x2, int y2, int color, float op) {
            fill(g, x1, y1, x2, y2, op(a(color, 40), op));
            outline(g, x1, y1, x2, y2, op(a(color, 220), op));
        }

        @Override
        public int text(int argb) {
            return 0xFF000000 | mix(argb & 16777215, 16777215, 0.12F);
        }

        @Override
        public String label(String s) {
            return s.toUpperCase(Locale.ROOT);
        }

        @Override
        public boolean shadow() {
            return false;
        }
    }

    static final class Pixel extends UiStyle {
        private static final int INK = 3158080;
        private static final int PAPER = 16315620;
        private static final int EDGE = 4739176;

        @Override
        public void frame(class_332 g, int x1, int y1, int x2, int y2, int accent, float op) {
            fill(g, x1, y1, x2, y2, op(-12038040, op));
            fill(g, x1 + 1, y1 + 1, x2 - 1, y2 - 1, op(-5719848, op));
            fill(g, x1 + 2, y1 + 2, x2 - 2, y2 - 2, op(-461596, op));
            if (accent != 0) {
                fill(g, x1 + 2, y1 + 2, x2 - 2, y1 + 3, op(0xFF000000 | accent, op));
            }
        }

        @Override
        public void section(class_332 g, int x1, int y1, int x2, int y2, int accent, UiStyle.Accent where, float op) {
            fill(g, x1, y1, x2, y2, op(-1646388, op));
            fill(g, x1, y2 - 1, x2, y2, op(-3620696, op));
            if (accent != 0) {
                if (where == UiStyle.Accent.LEFT) {
                    fill(g, x1, y1, x1 + 2, y2, op(0xFF000000 | dark(accent, 0.85F), op));
                } else if (where == UiStyle.Accent.TOP) {
                    fill(g, x1, y1, x2, y1 + 2, op(0xFF000000 | dark(accent, 0.85F), op));
                }
            }
        }

        @Override
        public void moveChip(class_332 g, int x1, int y1, int x2, int y2, int type, UiStyle.Chip kind, boolean best, float op) {
            int border = best ? 14721024 : (kind == UiStyle.Chip.MEMORY ? 8413376 : dark(type, 0.6F));

            int body = switch (kind) {
                case LIKELY -> mix(type, 16777215, 0.78F);
                case MEMORY -> mix(13084415, 16777215, 0.55F);
                case EMPTY -> 15657176;
                default -> mix(type, 16777215, 0.5F);
            };
            fill(g, x1, y1, x2, y2, op(0xFF000000 | (kind == UiStyle.Chip.EMPTY ? 13156520 : border), op));
            fill(g, x1 + 1, y1 + 1, x2 - 1, y2 - 1, op(0xFF000000 | body, op));
            if (best) {
                outline(g, x1 + 1, y1 + 1, x2 - 1, y2 - 1, op(-470976, op));
            }
        }

        @Override
        public void pill(class_332 g, int x1, int y1, int x2, int y2, int color, float op) {
            fill(g, x1, y1, x2, y2, op(0xFF000000 | dark(color & 16777215, 0.55F), op));
            fill(g, x1 + 1, y1 + 1, x2 - 1, y2 - 1, op(0xFF000000 | mix(color & 16777215, 16777215, 0.55F), op));
        }

        @Override
        public int text(int argb) {
            int rgb = argb & 16777215;
            float l = lum(rgb);
            if (l > 0.82F) {
                return -13619136;
            } else {
                return l > 0.45F ? 0xFF000000 | mix(dark(rgb, 0.62F), 3158080, 0.25F) : argb | 0xFF000000;
            }
        }

        @Override
        public int pillText(int pillColor) {
            return -13619136;
        }

        @Override
        public int dim() {
            return -1996950300;
        }

        @Override
        public boolean shadow() {
            return false;
        }
    }
}
