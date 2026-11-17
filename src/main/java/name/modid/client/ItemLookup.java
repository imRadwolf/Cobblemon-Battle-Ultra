package name.modid.client;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_7923;

public final class ItemLookup {
    private static final Map<String, class_1792> BY_NAME = new HashMap<>();
    private static boolean built = false;

    private ItemLookup() {
    }

    private static String norm(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    public static class_1799 byDisplayName(String displayName) {
        if (displayName != null && !displayName.isBlank()) {
            try {
                if (!built) {
                    built = true;

                    for (class_1792 item : class_7923.field_41178) {
                        try {
                            String key = norm(item.method_7848().getString());
                            if (!key.isEmpty()) {
                                boolean cobblemon = "cobblemon".equals(class_7923.field_41178.method_10221(item).method_12836());
                                if (cobblemon || !BY_NAME.containsKey(key)) {
                                    BY_NAME.put(key, item);
                                }
                            }
                        } catch (Throwable var5) {
                        }
                    }
                }

                class_1792 item = BY_NAME.get(norm(displayName));
                return item != null ? new class_1799(item) : null;
            } catch (Throwable var6) {
                return null;
            }
        } else {
            return null;
        }
    }
}
