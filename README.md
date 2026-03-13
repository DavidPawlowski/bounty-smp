# ⚔️ Bounty SMP — Custom Weapons Plugin

A Paper/Spigot plugin for **Minecraft 1.21** that adds a bounty-hunter system and six custom weapons with unique mechanics, craftable recipes, and in-game recipe management.

![Minecraft](https://img.shields.io/badge/Minecraft-1.21-green?logo=mojang-studios)
![Paper API](https://img.shields.io/badge/Paper%20API-1.21.11-blue)
![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)
![License](https://img.shields.io/badge/License-MIT-yellow)

---

## 🎮 Features

### Bounty System
Players are assigned random bounty targets when they join. Kill your target to increase your bounty level and gain powerful effects (speed, strength, extra hearts, glowing). Get hunted down and lose it all.

### Custom Weapons

| Weapon | Base Item | Mechanic |
|--------|-----------|----------|
| **⚡ Charge Bow** | Bow | Hit players 4 times with arrows to charge. Next arrow deals **Power 20** damage. Counter resets after the charged shot. Actionbar progress + sound/particle effects on full charge. |
| **🩸 Vampire Sword** | Iron Sword | Player-only. Every 5th hit on a player deals **+4 HP bonus damage** and **heals you for 4 HP**. Actionbar blood counter + particle/sound effects. |
| **💨 Charged Mace** | Mace | Right-click to launch a Wind Charge projectile. 5-second cooldown. |
| **⚡ Thunder Trident** | Trident | Left-click a player to strike lightning on them. Works in any weather. 5-second cooldown. |
| **🪓 Stun Axe** | Diamond Axe | Right-click to stun the player you're looking at for 1 second (Slowness 255 + Weakness). 10-second cooldown. |

### Recipe Management
Every weapon has a **craftable recipe** that can be changed in-game with commands. The Charge Bow recipe is also **persisted to `config.yml`** and can be hot-reloaded.

---

## 📋 Requirements

- **Java 21** or later
- **Minecraft Server**: [Paper](https://papermc.io/) 1.21.x (also works on Spigot 1.21.x)
- **Gradle 8.x** (only needed for building — the wrapper handles this)

---

## 🔨 Building from Source

### 1. Clone the repository

```bash
git clone https://github.com/YOUR_USERNAME/bounty-smp.git
cd bounty-smp
```

### 2. Generate the Gradle wrapper

> The `gradle-wrapper.jar` is excluded from version control (it's a binary). You need to generate it once:

**If you have Gradle installed:**
```bash
gradle wrapper --gradle-version 8.5
```

**If you don't have Gradle installed:**
```bash
# macOS (Homebrew)
brew install gradle

# Windows (Chocolatey)
choco install gradle

# Linux (SDKMAN — recommended)
sdk install gradle 8.5

# Then generate the wrapper
gradle wrapper --gradle-version 8.5
```

### 3. Build the plugin

```bash
# macOS / Linux
chmod +x ./gradlew
./gradlew build

# Windows
gradlew.bat build
```

### 4. Install

Copy the compiled JAR to your server:

```bash
cp build/libs/playergamespaperplugin-0.1.0.jar /path/to/your/server/plugins/
```

Restart or reload the server. Done!

---

## 📁 Project Structure

```
bounty-smp/
├── build.gradle                  # Build configuration & Paper dependency
├── gradle.properties             # Version numbers & project metadata
├── settings.gradle               # Root project name
├── gradlew / gradlew.bat         # Gradle wrapper scripts
├── src/main/
│   ├── java/com/playergames/paper/
│   │   ├── PGPlugin.java              # Main plugin class & command handler
│   │   ├── BountyManager.java         # Bounty hunting system
│   │   ├── BountyPlayer.java          # Per-player bounty data
│   │   ├── ChargeBowManager.java      # ⚡ Charge Bow (4-hit → Power 20)
│   │   ├── VampireSwordManager.java   # 🩸 Vampire Sword (5-hit drain)
│   │   ├── ChargedMaceManager.java    # 💨 Charged Mace (wind charge)
│   │   ├── ThunderTridentManager.java # ⚡ Thunder Trident (lightning)
│   │   └── StunAxeManager.java        # 🪓 Stun Axe (freeze target)
│   └── resources/
│       ├── plugin.yml                  # Bukkit plugin descriptor
│       └── config.yml                  # Persistent recipe configuration
```

---

## 🕹️ Commands

All commands use the `/playergames` base command.

| Command | Permission | Description |
|---------|------------|-------------|
| `/playergames` | `playergames.use` | Show help and available subcommands |
| `/playergames chargebow` | `playergames.chargebow` | View current Charge Bow recipe |
| `/playergames chargebow set <ingredient> <core> <handle>` | `playergames.chargebow` | Change Charge Bow recipe (saved to config) |
| `/playergames vampire` | `playergames.vampire` | View current Vampire Sword recipe |
| `/playergames vampire set <material> <handle>` | `playergames.vampire` | Change Vampire Sword recipe |
| `/playergames mace` | `playergames.mace` | View current Charged Mace recipe |
| `/playergames mace set <ingredient> <handle>` | `playergames.mace` | Change Charged Mace recipe |
| `/playergames thundertrident` | `playergames.thundertrident` | View current Thunder Trident recipe |
| `/playergames thundertrident set <ingredient> <core> <handle>` | `playergames.thundertrident` | Change Thunder Trident recipe |
| `/playergames stunaxe` | `playergames.stunaxe` | View current Stun Axe recipe |
| `/playergames stunaxe set <ingredient> <handle>` | `playergames.stunaxe` | Change Stun Axe recipe |
| `/playergames reload` | `playergames.reload` | Reload recipes from config.yml |

### Material Names
Use [Bukkit Material names](https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/Material.html) for recipe ingredients. Examples: `DIAMOND`, `IRON_INGOT`, `BONE`, `ENDER_EYE`, `BLAZE_ROD`, `STICK`.

---

## ⚙️ Configuration

### config.yml

```yaml
# Charge Bow recipe (persisted across restarts)
chargebow:
  recipe:
    ingredient: BONE        # Outer ingredient (4 pieces)
    core: ENDER_EYE         # Center piece
    handle: STICK           # Bottom piece
```

After editing `config.yml` manually, run `/playergames reload` in-game to apply changes without restarting the server.

---

## 🔐 Permissions

| Permission | Default | Description |
|------------|---------|-------------|
| `playergames.use` | Everyone | Use the base `/playergames` command |
| `playergames.chargebow` | OP | Manage Charge Bow recipes |
| `playergames.vampire` | OP | Manage Vampire Sword recipes |
| `playergames.mace` | OP | Manage Charged Mace recipes |
| `playergames.thundertrident` | OP | Manage Thunder Trident recipes |
| `playergames.stunaxe` | OP | Manage Stun Axe recipes |
| `playergames.reload` | OP | Reload config.yml recipes |

---

## 🧪 Default Crafting Recipes

### Charge Bow
```
B . B      B = Bone
B E B      E = Ender Eye
. S .      S = Stick
```

### Vampire Sword
```
. R .      R = Redstone
R B R      B = Blaze Rod
. R .
```

### Charged Mace
```
I I I      I = Iron Ingot
. I .      S = Stick
. S .
```

### Thunder Trident
```
T T T      T = Trident
. L .      L = Lightning Rod
. S .      S = Stick
```

### Stun Axe
```
D D D      D = Diamond
. D .      S = Stick
. S .
```

---

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/my-new-weapon`)
3. Commit your changes (`git commit -m "Add new weapon"`)
4. Push to the branch (`git push origin feature/my-new-weapon`)
5. Open a Pull Request

---

## 📜 License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.

---

## 🙏 Credits

- Built with the [Paper API](https://papermc.io/)
- Developed by [PlayerGames](https://player.games)
