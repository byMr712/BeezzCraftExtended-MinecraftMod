package land.beezz.craft;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Reads the mod's lang files straight from the JAR. Fabric API's resource loader is not a dependency, so
 * Component.translatable would show raw keys on a clean Fabric install.
 */
final class Tr {
    private static String code;
    private static Map<String, String> lang = Map.of();

    private Tr() {}

    static MutableComponent t(String key, Object... args) {
        String template = raw(key);
        if (template == null) return Component.literal(key);
        Object[] values = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            Object arg = args[i];
            if (arg instanceof Component c) values[i] = c.getString();
            else if (arg instanceof ItemStack stack) values[i] = stack.getHoverName().getString();
            else values[i] = arg;
        }
        try {
            return Component.literal(String.format(Locale.ROOT, template.replace("%d", "%s"), values));
        } catch (RuntimeException e) {
            return Component.literal(template);
        }
    }

    private static Map<String, String> lang() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getLanguageManager() == null) return Map.of();
        String selected = minecraft.getLanguageManager().getSelected();
        if (selected.equals(code)) return lang;
        code = selected;
        String file = "ru_ru".equals(selected) ? "ru_ru" : "en_us";
        String path = "/assets/beezzcraft/lang/" + file + ".json";
        Map<String, String> map = new HashMap<>();
        try (InputStream in = Tr.class.getResourceAsStream(path)) {
            if (in != null) {
                JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                for (Map.Entry<String, com.google.gson.JsonElement> e : json.entrySet()) {
                    map.put(e.getKey(), e.getValue().getAsString());
                }
            }
        } catch (Exception e) {
            BeezzCraft.LOG.warn("BeezzCraft: cannot read {}: {}", path, e.toString());
        }
        lang = map;
        return map;
    }

    /** Raw translation for the vanilla Language mixin, or null when the key is not ours. */
    static String raw(String key) {
        return lang().get(key);
    }
}
