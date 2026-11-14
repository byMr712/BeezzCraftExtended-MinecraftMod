package land.beezz.craft;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a long list of recipes into a tidy wheel:
 * <ul>
 *   <li>variants of the same thing become one button: items whose names end the same way (oak_fence, poplar_fence;
 *       torch, soul_torch; wooden_pickaxe … diamond_pickaxe). No list of woods or colours, so new ones join by
 *       themselves. The mouse wheel over the button switches between them;</li>
 *   <li>the button shows the variant you crafted last, else one you can make (from the wood further up-left in the
 *       inventory), else the one you lack the least for;</li>
 *   <li>what is popular or what you craft often comes first.</li>
 * </ul>
 */
final class Groups {

    /** Material tiers, in the order they stand on the wheel. */
    private static final String[] TIERS = {"wooden", "leather", "stone", "chainmail", "copper", "iron", "golden", "diamond", "netherite"};
    /**
     * Families everybody makes all the time, most wanted first (tools before decorations). What the player crafts
     * often is added on top of this (Config.counts).
     */
    private static final List<String> POPULAR = List.of(
            "pickaxe", "axe", "sword", "shovel", "torch", "crafting_table", "chest", "furnace", "ladder", "bed", "door", "trapdoor",
            "planks", "stick", "slab", "stairs", "fence", "fence_gate", "sign", "boat", "bow", "arrow", "shield", "bucket", "hoe",
            "spear", "helmet", "chestplate", "leggings", "boots", "fishing_rod", "crossbow", "shears", "flint_and_steel", "rail",
            "powered_rail", "lever", "button", "pressure_plate", "barrel", "smoker", "blast_furnace", "campfire", "lantern",
            "bread", "cake", "banner", "carpet", "wool", "bundle");

    /** One button: the variants of one thing, with the one shown right now. */
    static final class Group {
        final List<Recipes.Option> members = new ArrayList<>();
        final String key, family;
        final int tier;
        int sel;

        Group(String key, String family, int tier) {
            this.key = key;
            this.family = family;
            this.tier = tier;
        }

        Recipes.Option current() {
            return members.get(sel);
        }

        boolean craftable() {
            for (Recipes.Option o : members) if (o.craftable) return true;
            return false;
        }

        /** Shows the next (or previous) variant. */
        void cycle(int step) {
            sel = Math.floorMod(sel + step, members.size());
        }

        /** The variant to show first: the last crafted, else one you can craft (top left wood first), else the closest. */
        void pickBest() {
            List<String> recent = Config.get().recent;
            int latest = Integer.MAX_VALUE;
            for (int i = 0; i < members.size(); i++) {
                int at = recent.indexOf(members.get(i).itemId());
                if (at >= 0 && at < latest) {
                    latest = at;
                    sel = i;
                }
            }
            if (latest != Integer.MAX_VALUE) return;
            int best = 0;
            for (int i = 1; i < members.size(); i++) {
                Recipes.Option a = members.get(i), b = members.get(best);
                boolean better = a.craftable != b.craftable ? a.craftable
                        : a.craftable ? a.rank < b.rank : Recipes.missing(a) < Recipes.missing(b);
                if (better) best = i;
            }
            sel = best;
        }
    }

    private Groups() {}

    private static String path(Recipes.Option o) {
        String id = o.itemId();
        return id.substring(id.indexOf(':') + 1);
    }

    /** -1 when the item has no material tier. */
    static int tier(String key) {
        for (int i = 0; i < TIERS.length; i++) if (key.startsWith(TIERS[i] + "_")) return i;
        return -1;
    }

    /**
     * The shortest name ending (in whole words) that another item in the list also has: oak_fence and poplar_fence →
     * "fence", torch and soul_torch → "torch". An item that shares no ending with any other stays on its own.
     */
    private static Map<String, String> families(List<Recipes.Option> options) {
        Map<String, java.util.Set<String>> owners = new HashMap<>();
        for (Recipes.Option o : options) {
            String p = path(o);
            String[] t = p.split("_");
            for (int i = 0; i < t.length; i++) {
                owners.computeIfAbsent(String.join("_", java.util.Arrays.copyOfRange(t, i, t.length)), k -> new java.util.HashSet<>()).add(p);
            }
        }
        Map<String, String> family = new HashMap<>();
        for (Recipes.Option o : options) {
            String p = path(o);
            String[] t = p.split("_");
            String f = p;
            for (int i = t.length - 1; i >= 0; i--) { // shortest ending first
                String end = String.join("_", java.util.Arrays.copyOfRange(t, i, t.length));
                if (owners.get(end).size() >= 2) {
                    f = end;
                    break;
                }
            }
            family.put(p, f);
        }
        return family;
    }

    /** Position in the popular list of the first member that is (or ends with) a popular name. */
    private static int popularity(Group g) {
        int best = POPULAR.size();
        for (Recipes.Option o : g.members) {
            String p = path(o);
            for (int i = 0; i < best; i++) {
                String n = POPULAR.get(i);
                if (p.equals(n) || p.endsWith("_" + n)) best = i;
            }
        }
        return best;
    }

    /** Groups the recipes and orders the buttons. Call after Recipes.refresh so the best variants can be picked. */
    static List<Group> build(List<Recipes.Option> options) {
        Map<String, Group> byKey = new LinkedHashMap<>();
        Map<String, String> families = families(options);
        for (Recipes.Option o : options) {
            String family = families.get(path(o));
            Group g = byKey.computeIfAbsent(family, k -> new Group(k, k, -1));
            g.members.add(o);
        }
        List<Group> groups = new ArrayList<>(byKey.values());
        for (Group g : groups) {
            // tiers in order (wooden → diamond), then the woods / colours by name
            // the plain one first (torch before soul_torch), then tiers in order (wooden → diamond), then by name
            g.members.sort(Comparator.<Recipes.Option>comparingInt(o -> path(o).equals(g.family) ? 0 : 1)
                    .thenComparingInt(o -> tier(path(o))).thenComparing(o -> path(o)));
            g.pickBest();
        }
        // a button is as wanted as its members: any craftable, how often you made them, how popular they are
        Map<String, Integer> counts = Config.get().counts;
        groups.sort(Comparator.<Group>comparingInt(g -> g.craftable() ? 0 : 1)
                .thenComparingInt(g -> -g.members.stream().mapToInt(o -> counts.getOrDefault(o.itemId(), 0)).sum())
                .thenComparingInt(Groups::popularity)
                .thenComparing(g -> g.family));
        return groups;
    }
}
