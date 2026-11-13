# BeezzCraft

Fabric client mod for Minecraft **26.3**: **Alt + right click** on an item in the inventory or at a crafting table opens
an octagon wheel of everything you can craft from it.

- **Inner ring**: what you can craft right now. **Grey**: missing resources.
- Rings of 8, 16, 24 and 32 buttons (the outer rings split each octagon side in 2, 3 and 4), 80 per page; more go to
  pages (the «◀ 1/2 ▶» switch above the wheel or the mouse wheel).
- Variants of one thing (a fence of each wood, a banner of each colour) are one button with three dots under it: it
  shows a variant you can make, the mouse wheel over it switches to the others.
- Order: popular things and what you craft often first; tiers of a tool in a row (wooden, stone, copper, iron, golden,
  diamond). Sticks at a crafting table: 112 recipes fit in 64 buttons on one page.
- **Hover** a sector: a card with the result, the ingredients as icons (green tick = enough, red = how many are
  missing) and the clicks drawn as mouse and key pictures.
- **Click** crafts once, **Ctrl + click** crafts 10 times, **Shift + click** crafts as many as possible.
- The inventory shows 2×2 recipes, a crafting table shows 3×3.
- **⚙ gear** in the top left corner of the inventory: switches for the craft queue (off by default), grey recipes and
  the crafting sound. Under it the craft queue is listed: each item, how many, whether it waits for resources, and ✕
  to remove it.
- **Craft queue**: with the queue switched on, clicking a grey recipe queues it. It is crafted by itself as soon as the
  items are in your inventory, but only while nothing or your own inventory is open (never with a chest open).
  3×3 recipes wait for a crafting table. While playing, a small card in the top left corner shows what waits. Some servers forbid automatic
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
