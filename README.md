# gamemode

A client-side Fabric mod for Minecraft: Java Edition that lets you change your game mode on your client without asking the server.

`/gamemode <mode> @c` and the client game mode switcher (F3 + F5) change only the game mode your client uses, whatever your permissions are. Fly around in client creative, look through walls in client spectator, then switch back, while the server keeps your real game mode.

## Warning

**This mod is disabled in multiplayer by default. To use it in multiplayer, change the multiplayer mode in its settings.**

A client game mode is not a server game mode. The mod changes the game mode your client uses and nothing else. The server, including the integrated server of a singleplayer world, keeps your real game mode and checks everything your client does. This mod does not try to bypass any check or anti-cheat.

### Multiplayer

- Servers that run anti-cheat systems may detect what your client does in a client game mode, such as flying or moving through blocks. In some multiplayer modes and on some servers, this can get you set back, kicked, or banned.
- Using the mod may break a server's rules. Check each server's rules before you enable it there. Use it in multiplayer at your own risk.
- Vanilla servers with `allow-flight` turned off kick players who fly for a few seconds. Flying in client creative or client spectator on such a server gets you kicked with "Flying is not enabled on this server".

### What the server still decides

- Moving through blocks in client spectator works only on your client. The server keeps your player out of the blocks and sets you back.
- Breaking blocks instantly and taking items from the creative inventory in client creative happen only on your client. The server corrects them, usually at once: the blocks come back and the items disappear. Placing blocks works as in your real game mode.
- You still take damage, get hungry and can die as in your real game mode. The client shows creative's or spectator's HUD, so you may not see your health.
- Other players see your real game mode, and so does the player list.

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) and [Fabric API](https://modrinth.com/mod/fabric-api).
2. Download the jar from this repository's [Releases](https://github.com/urntt/gamemode/releases) page. Each release supports a single Minecraft version, shown after the `+` in its version number. For example, `1.0.0+26.3` is for Minecraft 26.3.
3. Put the jar into your `.minecraft/mods` folder.

[Mod Menu](https://modrinth.com/mod/modmenu) and [OneConfig](https://modrinth.com/mod/oneconfig) are optional. Mod Menu opens the mod's configuration screen from its mod list. With OneConfig installed, you can also manage the settings in OneConfig (see [Settings](#settings)).

## Usage

The mod is on by default in singleplayer and disabled on multiplayer servers. While a client game mode is active, the client uses it; otherwise the client uses the server's game mode, as in vanilla.

### Commands

`@c` stands for "client": the command runs on your client and never reaches the server. Every other `/gamemode` command, such as `/gamemode creative` or `/gamemode creative @s`, is sent to the server as usual.

| Command | Effect |
| --- | --- |
| `/gamemode <mode> @c` | Uses `survival`, `creative`, `adventure` or `spectator` as your client game mode. Choosing the server's game mode ends the client game mode. |
| `/gamemode @c` | Shows your client and server game modes, with clickable game modes. It changes nothing and works everywhere, even where client game modes are disabled. |

### Client game mode switcher

Hold F3 and press F5 to open the client game mode switcher. It looks and works like vanilla's game mode switcher (F3 + F4): press F5 again to select the next game mode, or point at one with the mouse, and release F3 to switch. The selected game mode shows whether choosing it sets a client game mode or returns to the server's game mode.

F5 alone still changes the camera perspective, and F3 + F4 is still vanilla's switcher, which needs permission and changes your real game mode.

### When the client game mode ends

A client game mode lasts until you choose the server's game mode again, and it is never saved. It also ends when:

- the server changes your game mode, while **Follow Server Changes** is on (the default). The chat tells you. Respawning or changing dimension in the same game mode does not end it.
- you switch to Disabled mode, turn off the mod's card in OneConfig mode, or the multiplayer settings rule out the server.
- you leave the world or the server.

### Key bindings

The key bindings are under **gamemode** in **Options → Controls → Key Binds**:

- **Client Game Mode Switcher (with F3)**, F5 by default, opens the switcher while the debug modifier key (F3) is held.
- **Open gamemode Settings**, unbound by default, opens the configuration screen. With Mod Menu installed, you can also open it from the mod list.

### Settings

Open the settings screen from Mod Menu, or bind the **Open gamemode Settings** key. The button at the top of the screen selects the mode:

| Mode | Effect |
| --- | --- |
| Disabled | Client game modes are turned off everywhere, and your client always uses the server's game mode. |
| Custom (default) | The settings on this screen apply. |
| OneConfig | The settings registered with OneConfig apply, and are changed in OneConfig. This screen shows them greyed out and links to them with **Open in OneConfig**. Turning off the mod's card in OneConfig turns client game modes off. Only offered when OneConfig is installed. |

If OneConfig mode is selected but OneConfig is later removed, the custom settings apply until you pick another mode.

| Setting | Default | Meaning |
| --- | --- | --- |
| Follow Server Changes | On | When the server changes your game mode, the client game mode ends and you use the server's new game mode. When off, the client game mode stays. |
| Multiplayer Mode | Disabled | **Disabled**: never allows client game modes on servers. **Whitelist**: allows them only on servers in the server list. **Blacklist**: allows them on all servers except those in the server list. |
| Server List | Empty | The addresses the whitelist and blacklist modes use. |

The custom settings are saved to `config/gamemode.json` as soon as you change them; OneConfig keeps its own in `config/gamemode-oneconfig.json`. A configuration file from version 1.0.0, which had an on/off state instead of modes, keeps its other settings and starts in Disabled mode if the mod was off.

The multiplayer mode is a hard limit: on a server it rules out, the client always uses the server's game mode, and the commands and the switcher only report that client game modes are disabled there. Joining another player's LAN world or a Realm counts as multiplayer.

Server list entries are compared with the address you connect to, ignoring upper and lower case. An entry without a port, such as `mc.example.com`, matches the server on any port, while an entry with a port, such as `mc.example.com:25566`, matches only that port. The server list screen marks invalid addresses in red and does not save until they are fixed or removed.

## Development

Building requires the JDK version set by `java_version` in `gradle.properties`.

Build the mod:

```bash
./gradlew build
```

The jar is written to `build/libs/`.

Run the client game tests, which start Minecraft and check what each client game mode changes on the client while the server keeps the real one, the commands, the switcher, the server-change rule, the configuration modes and screens, the key bindings, and the multiplayer rules on a local dedicated server:

```bash
./gradlew runClientGameTest
```

The game tests need a display. On a headless Linux machine, run them under Xvfb. Xvfb offers no sRGB-capable OpenGL visuals, so install Mesa's Vulkan driver (`mesa-vulkan-drivers` on Ubuntu) for the game to fall back to:

```bash
xvfb-run -a -s "-screen 0 1920x1080x24" ./gradlew runClientGameTest
```

Screenshots taken by the tests are saved to `build/run/clientGameTest/screenshots/`.

By default, the development client and the tests run without OneConfig. Add `-PoneConfigRuntime=true` to run them with OneConfig installed, which offers the OneConfig mode, and the tests check it too:

```bash
./gradlew runClientGameTest -PoneConfigRuntime=true
```

The multiplayer tests start a local dedicated server, so the test setup in `build.gradle` accepts the [Minecraft EULA](https://aka.ms/MinecraftEULA) for that test server.

## License

[MIT](LICENSE)
