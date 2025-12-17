# GT New Horizons - Early Game FAQ

*A concise guide to commonly missed mechanics and GTNH-specific changes. Information sourced from official quest data.*

---

## Survival Basics (Changed from Vanilla)

### Night is Brutal
Unlike vanilla Minecraft, **nights are pitch black** with no ambient light. Infernal mobs spawn everywhere. Your first priority is food and shelter before sunset.

### Food & Hunger
- **Eating the same food repeatedly reduces its effectiveness.** Vary your diet.
- Raw potatoes can be poisonous. Cook them.
- Berry bushes provide early food and can be planted as defensive walls.
- Combine berries into "Berry Medley" for better saturation.
- Plant apple tree saplings (oak sapling + apple) for sustainable food.

### Animals Explode
Killing animals with regular weapons can cause them to explode. **Use a GregTech Knife or Butchery Knife** (flint + stick) to prevent this and get proper drops. The Butchery Knife also has built-in Looting.

### Special Mobs to Watch For
- **Witch Spiders** (red X on back): Reflect ranged damage. Use melee only.
- **Mother Spiders**: Spawn infernal baby spiders on death.
- **Drowning Creepers**: Trap you in cobble filled with water and silverfish.
- **Ender Creepers**: Teleport you randomly, possibly underground.
- **Vampire Pigmen**: Require wooden sword (stake) or magic to damage.

---

## Crafting & Tools

### Flint Doesn't Drop from Gravel
Craft flint manually: look it up in NEI. This is your first surprise.

### Chests Require Flint
Vanilla chest recipe is changed: 4 wood + 4 planks + 1 flint.

### Paper Requires Wood Pulp
No more sugarcane → paper. You need wood pulp + water, or use a mortar to grind sugarcane into chad and press it.

### Vanilla Bows Are Assembler-Only
Make a **Tinker's Construct bow** instead. Wood limbs + string bowstring works fine early. Bone arrows have good durability; use flint tips for repairability.

### Tinker's Construct Changes
- The smeltery **does NOT double ores** in GTNH.
- Use the Crafting Station (holds items in grid) and place it next to a vanilla chest to see chest contents while crafting.

---

## Ore Generation & Mining

### Veins, Not Random Ores
GregTech generates ores in **veins on a 3-chunk grid**. The center of each vein is at chunk coordinates where both X and Z equal 3N+1 (e.g., 4,7 or 10,16 or -2,4).

### Finding Ore Chunks
- The **InGame Info mod** (top-left corner) shows your current chunk and whether it's an "Ore Chunk."
- Press **F9 three times** to toggle the NEI ore vein overlay, which marks centers blue.
- Use the **Ore Finder Wand** (craftable with Magnetic Iron Rod) to locate specific ores within 60 blocks vertically.

### Common Early Veins
- **Copper**: Y 5-60, appears as Copper Ore, Chalcopyrite, or Malachite. Often with Iron and Pyrite.
- **Lignite/Coal**: Essential fuel sources. Coal can become Coal Coke (superior fuel).

---

## GregTech Machines & Power

### Rain Damages Machines
Electric machines exposed to rain will **start fires**, which can cascade into explosions. Always cover your machines or build indoors.

### Voltage Explosions
Sending voltage **higher than a machine's tier** causes it to explode. Sending more amps than needed is safe.

### Cable Loss
Every amp traveling through cable loses EU per block (shown in cable tooltip). **Cables are better than wires.** For your first Electric Blast Furnace, consider placing generators directly adjacent to minimize loss.

### Multiblock Power Loss
If a **multiblock machine loses power mid-recipe**, you lose your input items. Singleblock machines just restart progress without losing items.

### GT6-Style Pipes
- Pipes **don't auto-connect** by default. Use a wrench to manually connect sides.
- Right-click the center of a pipe face to toggle connections (X = enabled).
- **Hot pipes (steam) damage players.** Cover them with plates or planks.
- Shift+RMB with wrench adds a shutter to prevent input on that side.

### Programmed Circuits
Many machines use programmed circuits to select recipes. **Click the ghost circuit icon** in machine GUIs instead of inserting physical circuits. Right-click a programmed circuit with screwdriver in inventory to program it manually.

---

## Steam Age Tips

### Solar Boilers Calcify
Simple and Advanced Solar Boilers lose efficiency over time. Use **Distilled Water** later to prevent this. Simple boilers drop to 40L/s; Advanced drop to 120L/s.

### Bronze Machine Exhaust
Bronze steam machines need their **exhaust port (orange square) unobstructed** or they won't work.

### Steel Unlocks Everything
Once you have steel, you can make the Steam Turbine to generate EU and transition to electric machines.

---

## NEI Power Features

### Essential Hotkeys
- **R** on item: Show recipes to make it
- **U** on item: Show recipes that use it
- **Backspace**: Go back to previous recipe
- **A**: Bookmark an item (appears on left side)
- **Shift+A**: Bookmark entire recipe
- **T**: Search nearby inventories (shows particles)
- **Y**: Find a placed machine in the world
- **O**: Hide NEI (for screenshots)
- **Double-click search bar**: Highlight matching items in open inventories

### Search Operators
- `@modname` - Search by mod (e.g., `@thaumcraft`)
- `space` - AND multiple terms
- `|` - OR terms
- `-` - Exclude terms
- Hold **Shift** while hovering to stop oredict cycling and show oredict names

---

## Useful Keybinds

| Key | Function |
|-----|----------|
| ~ (Tilde) | Open Questbook |
| F | Swap mainhand/offhand |
| J | Fullscreen map (JourneyMap) |
| B | Create waypoint |
| R | Sort inventory |
| NUMPAD1 | Toggle WAILA HUD |

Most keybinds are **unbound by default** to avoid conflicts. Check settings to enable what you need.

---

## Commonly Missed Features

### Offhand Slot (2.8+)
GTNH backported the modern offhand system. Press **F** to swap items. Works with backpacks and other GUI items for easier inventory management.

### Stockroom Catalog
Shift+right-click chests to track them. Right-click the catalog to see all tracked items and their locations.

### Watering Can
Speeds up crop growth significantly. Essential for farming.

### Lootbag Uptiering
Lootbags can be enchanted with Fortune for better drops, and 3 bags combine into the next tier. Check NEI for upgrade recipes and drop tables.

### Questbook Commands
- `/bq_admin default load` - Reload questbook after updates
- `/bq_admin reset all <Player>` - Reset all quests

---

## World Generation Notes

### Use Realistic World Gen (RWG)
If you see massive mushroom biomes everywhere, you're using vanilla worldgen. Regenerate your world with RWG. For servers: set `level-type=rwg` in server.properties.

### Backup Your World
ServerUtilities auto-backs up, but **JourneyMap and Thaumcraft Node Tracker data** are stored separately. Back up your `journeymap` and `tcnodetracker` folders manually.

---

## Quick Reference: What's Different from Standard Mods

| Mod | GTNH Change |
|-----|-------------|
| Tinker's Construct | Smeltery doesn't double ores |
| Forestry | Tree breeding can cause butterfly lag—breed indoors |
| IC2 Crops | Weeds spread aggressively—remove with trowel/spade |
| Thaumcraft | Many recipes gated behind GregTech progression |
| AE2 | Requires HV+ tier, heavily modified recipes |

---

## Resources

- **Discord**: https://discord.gg/gtnh
- **Wiki**: https://wiki.gtnewhorizons.com
- **Spreadsheet**: https://www.gtnewhorizons.com/spreadsheet
- **Electricity Guide (Video)**: https://www.youtube.com/watch?v=lpDPY8J-4QY

---

*Generated from GTNH quest database. For the most current information, always check in-game quests and the official wiki.*