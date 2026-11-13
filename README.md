# BeezzCraft

Fabric client mod for Minecraft **26.3**: **Alt + right click** on an item in the inventory or at a crafting table opens
an octagon wheel of everything you can craft from it.

- **Inner ring**: what you can craft right now. **Grey**: missing resources.
- Up to 8 recipes per ring, up to 3 rings; more go to pages (mouse wheel).
- **Hover** a sector: the recipe, how many of each item it needs and how many you have; missing items are red.
- **Click** crafts once, **Ctrl + click** crafts 10 times, **Shift + click** crafts as many as possible.
- The inventory shows 2×2 recipes, a crafting table shows 3×3.
- **⚙ gear**: the craft queue (off by default), showing grey recipes, the crafting sound, clearing the queue.
- **Craft queue**: with the queue switched on, clicking a grey recipe queues it. It is crafted by itself as soon as the
  items are in your inventory, but only while nothing or your own inventory is open (never with a chest open).
  3×3 recipes wait for a crafting table. What waits is shown in the top left corner. Some servers forbid automatic
  crafting, which is why it is off by default.

Crafting goes through the vanilla recipe book: the server lays the recipe out from your inventory, like clicking a
recipe in the recipe book. So the mod works on vanilla servers and knows only the recipes you have unlocked.

Russian and English.

## Build and test

```
JAVA_HOME=<JDK 25> ./gradlew build runClientGameTest
```

`build/libs/beezzcraft-<version>+26.3.jar` is the mod (needs Fabric API).

`runClientGameTest` starts a real client and world, then walks through the mod:
- the wheel and a tooltip;
- click, Shift and Ctrl;
- the gear and the craft queue (torches made by themselves once coal arrives);
- an iron pickaxe at a crafting table.

Screenshots are saved to `build/run/clientGameTest/screenshots`. `BEEZZCRAFT_LANG=en_us` gives English ones.

The wheel textures come from `tools/make_textures.py`. If you change the geometry, change it there and in `Wheel.java`.

## License

MIT
