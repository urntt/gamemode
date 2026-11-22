# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html). Each version carries the targeted Minecraft version as build metadata, for example `1.0.0+26.3`.

## [Unreleased]

### Added

- A configuration mode, the first button of the settings screen: **Disabled** turns client game modes off everywhere, **Custom** (the default) applies the settings on the screen, and **OneConfig** applies the settings registered with [OneConfig](https://modrinth.com/mod/oneconfig). OneConfig is optional, and its mode is only offered while it is installed; the screen then shows OneConfig's values greyed out and links to them.

### Changed

- The settings screen only holds the mode, **Follow Server Changes**, the multiplayer mode and the server list. Outside custom mode, the settings are greyed out and show the values that apply.
- A configuration file from 1.0.0 keeps its other settings and starts in Disabled mode if the mod was turned off.

### Removed

- The **Client Game Mode** on/off setting and the **Toggle gamemode** key binding; Disabled mode replaces them.
- The singleplayer and server defaults and the **Reset on World Exit** and **Reset on Game Exit** rules.

## [1.0.0+26.3] - 2026-11-17

### Added

- `/gamemode <mode> @c` sets a client game mode that only the client uses, whatever the player's permissions are. Choosing the server's game mode ends it. `/gamemode @c` shows the client and server game modes with clickable game modes.
- A client game mode switcher that looks and works like vanilla's F3 + F4 switcher, opened with F3 + F5. Its key is configurable, and F5 alone still changes the perspective.
- The client game mode changes the client's abilities, block breaking, interaction and HUD, and the local player's own game mode, for example spectator noclip. The server keeps the real game mode, and nothing is sent to it.
- A **Follow Server Changes** setting, on by default: when the server changes the player's game mode, the client game mode ends. Respawning or changing dimension in the same game mode keeps it.
- A state for the whole mod, on by default, with a **Toggle gamemode** key binding (unbound by default), singleplayer and server defaults, and reset rules.
- Multiplayer rules: client game modes are disabled on multiplayer servers by default; a whitelist or blacklist mode with a server list allows them on chosen servers.
- A configuration screen built from vanilla widgets, opened with an **Open gamemode Settings** key binding (unbound by default) or through Mod Menu.
- English and Simplified Chinese translations.
