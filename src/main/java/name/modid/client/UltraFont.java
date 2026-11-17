package name.modid.client;

import net.minecraft.class_2561;
import net.minecraft.class_2583;
import net.minecraft.class_2960;
import net.minecraft.class_327;
import net.minecraft.class_332;
import net.minecraft.class_5250;

public final class UltraFont {
    public static final class_2960 ID = class_2960.method_60655("cobblemon-battle-extras", "ultra");
    private static final class_2583 STYLE = class_2583.field_24360.method_27704(ID);

    private UltraFont() {
    }

    public static boolean on() {
        return ExtrasPlusConfig.get().smoothFont;
    }

    public static class_5250 literal(String s) {
        return class_2561.method_43470(s).method_10862(STYLE);
    }

    public static void draw(class_332 gui, class_327 font, String s, int x, int y, int color, boolean shadow) {
        if (on()) {
            gui.method_51439(font, literal(s), x, y, color, shadow);
        } else {
            gui.method_51433(font, s, x, y, color, shadow);
        }
    }

    public static int width(class_327 font, String s) {
        return s == null ? 0 : (on() ? font.method_27525(literal(s)) : font.method_1727(s));
    }
}
