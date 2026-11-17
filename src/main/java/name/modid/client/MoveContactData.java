package name.modid.client;

import com.cobblemon.mod.common.api.moves.MoveTemplate;
import com.cobblemon.mod.common.api.moves.Moves;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.class_124;
import net.minecraft.class_2561;
import net.minecraft.class_5250;

public final class MoveContactData {
    private static final String RESOURCE = "/assets/cobblemon-battle-extras/data/contact_moves.txt";
    private static Set<String> contactMoves;
    private static final Map<String, String> DISPLAY_NAME_TO_ID = new HashMap<>();
    private static int displayNameCacheSize = -1;
    private static final Map<List<class_2561>, List<class_2561>> AUGMENTED = new IdentityHashMap<>();

    private MoveContactData() {
    }

    private static synchronized Set<String> contactSet() {
        if (contactMoves == null) {
            Set<String> set = new HashSet<>();

            try (InputStream in = MoveContactData.class.getResourceAsStream("/assets/cobblemon-battle-extras/data/contact_moves.txt")) {
                if (in != null) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));

                    String line;
                    while ((line = reader.readLine()) != null) {
                        String id = normalize(line);
                        if (id != null) {
                            set.add(id);
                        }
                    }
                }
            } catch (Throwable var7) {
            }

            contactMoves = set;
        }

        return contactMoves;
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        } else {
            String id = raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
            return id.isEmpty() ? null : id;
        }
    }

    public static Boolean isContact(String moveId) {
        String id = normalize(moveId);
        if (id == null) {
            return null;
        } else {
            Set<String> set = contactSet();
            if (set.isEmpty()) {
                return null;
            } else if (set.contains(id)) {
                return Boolean.TRUE;
            } else {
                try {
                    if (Moves.getByName(id) == null) {
                        return null;
                    }
                } catch (Throwable var4) {
                }

                return Boolean.FALSE;
            }
        }
    }

    public static String resolveMoveIdByDisplayName(String displayName) {
        if (displayName != null && !displayName.isBlank()) {
            String key = displayName.trim().toLowerCase(Locale.ROOT);

            try {
                List<MoveTemplate> all = Moves.all();
                if (all.size() != displayNameCacheSize) {
                    DISPLAY_NAME_TO_ID.clear();

                    for (MoveTemplate template : all) {
                        try {
                            DISPLAY_NAME_TO_ID.put(template.getDisplayName().getString().trim().toLowerCase(Locale.ROOT), template.getName());
                        } catch (Throwable var6) {
                        }
                    }

                    displayNameCacheSize = all.size();
                }
            } catch (Throwable var7) {
            }

            String id = DISPLAY_NAME_TO_ID.get(key);
            return id != null ? id : normalize(displayName);
        } else {
            return null;
        }
    }

    public static class_2561 contactLine(Boolean contact) {
        if (contact == null) {
            return null;
        } else {
            return contact
                ? class_2561.method_43470("✋ Makes contact").method_27692(class_124.field_1065)
                : class_2561.method_43470("✋ No contact").method_27692(class_124.field_1080);
        }
    }

    public static String contactText(Boolean contact) {
        if (contact == null) {
            return null;
        } else {
            return contact ? "✋ Makes contact" : "✋ No contact";
        }
    }

    public static int contactColor(Boolean contact) {
        return contact != null && contact ? -21965 : -5723992;
    }

    public static List<class_2561> withContactLine(List<class_2561> lines) {
        ExtrasPlusConfig cfg = ExtrasPlusConfig.get();
        if (lines != null && !lines.isEmpty() && (cfg.showMoveContact || cfg.damagePreviewBreakdown)) {
            List<class_2561> cached = AUGMENTED.get(lines);
            if (cached != null) {
                return cached;
            } else {
                List<class_2561> result = lines;
                boolean cacheable = true;

                try {
                    String moveName = lines.get(0).getString();
                    List<class_2561> copy = new ArrayList<>(lines);
                    boolean changed = false;
                    if (cfg.damagePreviewBreakdown && cfg.refinedDamagePreview) {
                        for (int i = 1; i < copy.size(); i++) {
                            String text = copy.get(i).getString();
                            if (text != null && text.contains("⚔")) {
                                DamageRefiner.Detail detail = DamageRefiner.detailFor(moveName, text.substring(text.indexOf("⚔")));
                                if (detail == null) {
                                    cacheable = false;
                                } else {
                                    List<class_2561> extra = breakdownLines(detail);
                                    extra.addAll(advisorLines(moveName));
                                    cacheable = false;
                                    copy.addAll(i + 1, extra);
                                    changed = true;
                                }
                                break;
                            }
                        }
                    }

                    class_2561 line = cfg.showMoveContact ? contactLine(isContact(resolveMoveIdByDisplayName(moveName))) : null;
                    if (line != null) {
                        int insertAt = Math.min(copy.size(), 2);

                        for (int ix = 1; ix < copy.size(); ix++) {
                            String text = copy.get(ix).getString();
                            String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
                            if (looksLikeStatLine(text, lower)) {
                                insertAt = ix + 1;
                            }
                        }

                        copy.add(insertAt, line);
                        changed = true;
                        String moveId = resolveMoveIdByDisplayName(moveName);
                        int at = insertAt + 1;

                        for (MoveInsights.Insight insight : new MoveInsights.Insight[]{MoveInsights.contact(moveId), MoveInsights.order(moveId)}) {
                            if (insight != null) {
                                int color = insight.color() & 16777215;
                                copy.add(at++, class_2561.method_43470(insight.text()).method_27694(style -> style.method_36139(color)));
                                cacheable = false;
                            }
                        }
                    }

                    if (changed) {
                        result = copy;
                    }
                } catch (Throwable var17) {
                }

                if (cacheable) {
                    if (AUGMENTED.size() > 64) {
                        AUGMENTED.clear();
                    }

                    AUGMENTED.put(lines, result);
                }

                return result;
            }
        } else {
            return lines;
        }
    }

    static int pctColor(int pct) {
        if (pct >= 100) {
            return 16733525;
        } else if (pct >= 50) {
            return 16755285;
        } else {
            return pct >= 25 ? 16306224 : 5635925;
        }
    }

    private static List<class_2561> breakdownLines(DamageRefiner.Detail detail) {
        List<class_2561> out = new ArrayList<>();
        out.add(
            class_2561.method_43470("  " + detail.source() + " · theoretical " + detail.theoretical())
                .method_27694(style -> style.method_36139(9079434).method_10978(true))
        );

        for (DamageRefiner.Row row : detail.rows()) {
            if (row.lo() >= 0) {
                int hi = row.hi();
                class_5250 c = class_2561.method_43470("  • ").method_27694(style -> style.method_36139(6974058));
                c.method_10852(class_2561.method_43470(row.lo() + "–" + row.hi() + "%").method_27694(style -> style.method_36139(pctColor(hi))));
                int koColor = DamageRefiner.koColor(row.ko());
                c.method_10852(class_2561.method_43470(" " + row.ko()).method_27694(style -> style.method_36139(koColor)));
                String rest = "  " + row.label() + (row.effects() != null && !row.effects().isEmpty() ? " + " + row.effects() : "");
                c.method_10852(class_2561.method_43470(rest).method_27694(style -> style.method_36139(12105912)));
                out.add(c);
            } else {
                out.add(class_2561.method_43470("  • " + row.label()).method_27694(style -> style.method_36139(12105912)));
            }
        }

        if (detail.notes() != null) {
            out.add(class_2561.method_43470("  " + detail.notes()).method_27694(style -> style.method_36139(8377599)));
        }

        return out;
    }

    private static List<class_2561> advisorLines(String moveName) {
        List<class_2561> out = new ArrayList<>();

        for (MoveAdvisor.Line line : MoveAdvisor.lines(moveName)) {
            int lc = line.leftColor() & 16777215;
            int rc = line.rightColor() & 16777215;
            out.add(
                class_2561.method_43470("  " + line.left())
                    .method_27694(style -> style.method_36139(lc))
                    .method_10852(class_2561.method_43470(line.right().isEmpty() ? "" : "  " + line.right()).method_27694(style -> style.method_36139(rc)))
            );
        }

        return out;
    }

    private static boolean looksLikeStatLine(String text, String lower) {
        if (text != null && !text.isEmpty()) {
            char first = 0;

            for (int i = 0; i < text.length(); i++) {
                char ch = text.charAt(i);
                if (!Character.isWhitespace(ch) && ch != 8226) {
                    first = ch;
                    break;
                }
            }

            return first == 9876
                || first == 9678
                || first == 9889
                || first == 9733
                || first == 10022
                || first == 9830
                || lower.contains("power:")
                || lower.contains("accuracy:")
                || lower.contains("priority:")
                || lower.contains("crit chance")
                || lower.contains("pp:")
                || lower.contains("effect:");
        } else {
            return false;
        }
    }
}
