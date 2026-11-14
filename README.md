# BeezzCraft

Fabric client mod for Minecraft **26.3**: **Alt + right click** on an item in the inventory or at a crafting table opens
an octagon wheel of everything you can craft from it.

- **Inner ring**: what you can craft right now. **Grey**: missing resources.
- Rings of 8, 16, 24 and 32 buttons (the outer rings split each octagon side in 2, 3 and 4), 80 per page; more go to
  pages (the «◀ 1/2 ▶» switch above the wheel or the mouse wheel).
- Variants of one thing are one button with three dots under it: items whose names end the same way (a fence of each
  wood, new woods included; torch, soul torch, copper torch), a banner of each colour, the
  tiers of a tool (wooden, stone, copper, iron, golden, diamond pickaxe). The mouse wheel over it switches variants.
  It opens on the variant you crafted last, else on one you can make (the wood further up-left in the inventory wins).
- Order: what you can make and what you craft often first, then popular things. Sticks at a crafting table: 112 recipes
  in 33 buttons.
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
- **What to get**: everything the queue still needs, added up (12 queued torches = 12 coal), as icons with how many
  are missing. Under the queue in the inventory and, while playing, under the queue card at the top left. Switch:
  «Resources to get» behind the gear.

All crafting recipes of the game are known, not only the ones the recipe book has unlocked (a torch unlocks only once
you hold a stone pickaxe): in singleplayer from the server, in multiplayer from the game's own data. Recipes the book
knows are crafted through it (the server lays them out); the others are laid out with ordinary clicks, taking the items
from the inventory top left first. Both work on vanilla servers.

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
