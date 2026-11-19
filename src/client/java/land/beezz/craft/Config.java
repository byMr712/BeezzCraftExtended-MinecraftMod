package land.beezz.craft;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** config/beezzcraft.json: the switches behind the gear and the recently crafted items (shown first). */
final class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Config instance;

    /**
     * The craft queue: a grey recipe clicked now is crafted by itself as soon as the items are in the inventory.
     * Off by default: some servers treat automatic crafting as a cheat.
     */
    boolean queue = false;
    /** Show the recipes that lack resources (grey) or only the ones that can be crafted now. */
    boolean showMissing = true;
    /** Click sound when something is crafted. */
    boolean sound = true;
    /** Show what the craft queue still needs (in the inventory and on the screen while playing). */
    boolean showNeeds = true;
    /** How many crafts Ctrl + click makes. */
    int ctrlCount = 10;
    /** Result item ids, the latest first: a button opens on the variant crafted last. */
    List<String> recent = new ArrayList<>();
    /** How many times each item was crafted: the often made ones come first. */
    java.util.Map<String, Integer> counts = new java.util.HashMap<>();

    static Config get() {
        if (instance == null) instance = load();
        return instance;
    }

    private static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("beezzcraft.json");
    }

    private static Config load() {
        try {
            if (Files.exists(file())) {
                Config c = GSON.fromJson(Files.readString(file(), StandardCharsets.UTF_8), Config.class);
                if (c != null) {
                    if (c.recent == null) c.recent = new ArrayList<>();
                    if (c.counts == null) c.counts = new java.util.HashMap<>();
                    if (c.ctrlCount < 2) c.ctrlCount = 10;
                    return c;
                }
            }
        } catch (IOException | RuntimeException e) {
            BeezzCraft.LOG.warn("BeezzCraft: cannot read {}: {}", file(), e.toString());
        }
        return new Config();
    }

    void save() {
        try {
            Files.writeString(file(), GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (IOException e) {
            BeezzCraft.LOG.warn("BeezzCraft: cannot save {}: {}", file(), e.toString());
        }
    }

    void crafted(String itemId) {
        recent.remove(itemId);
        recent.addFirst(itemId);
        while (recent.size() > 64) recent.removeLast();
        counts.merge(itemId, 1, Integer::sum);
        save();
    }
}
