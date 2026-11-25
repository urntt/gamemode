# AGENTS.md

Instructions for AI coding agents working in this repository. `CLAUDE.md` imports this file, so keep all agent guidance here.

## Project

`gamemode` is a client-side [Fabric](https://fabricmc.net/) mod for Minecraft: Java Edition that lets the player change their client-side game mode without asking the server: `/gamemode <mode> @c` and a client game mode switcher (F3 + F5 by default) change only the game mode the client uses, whatever the player's permissions are.

[urntt/teleport](https://github.com/urntt/teleport) is the reference for intercepting `@c` commands on the client and adding them to the server's command tree. [urntt/hiteffect](https://github.com/urntt/hiteffect) is the reference for the configuration: the configuration modes, the configuration screen and the optional OneConfig integration. [urntt/nojumpdelay](https://github.com/urntt/nojumpdelay) is the reference for the multiplayer rules and the overall project layout. Follow their style, but do not add a dependency on them.

## Project decisions

These decisions are settled. Do not deviate from them without the user's explicit approval.

### Minecraft and toolchain

- Development started on Minecraft 26.3 with Java 25. `gradle.properties` is the single source of truth for the mod, Minecraft, Fabric Loader, Loom, Fabric API, Mod Menu, OneConfig, and Java versions. `build.gradle`, `fabric.mod.json`, the mixin config, and the CI workflows read them from there; do not restate them elsewhere.
- Follow the latest official Fabric template ([FabricMC/fabric-example-mod](https://github.com/FabricMC/fabric-example-mod), also available from the [template generator](https://fabricmc.net/develop/template/)): the `net.fabricmc.fabric-loom` Gradle plugin, Mojang's official names with no `mappings` dependency, and `implementation` (not `modImplementation`) for dependencies. Do not use Yarn.
- Pin `loom_version` to a release version instead of the template's `-SNAPSHOT`, so builds are reproducible.
- Target only the latest stable (release) Minecraft version. Updates, fixes, and new features are always developed against it. Snapshots, pre-releases, and release candidates are not supported targets.
- Do not maintain older Minecraft versions and do not set up multi-version builds (no per-version branches, no Stonecutter or other preprocessors). When a new stable version is released, port the mod to it and drop the previous one.

### Identity

| Item | Value |
| --- | --- |
| Mod ID | `gamemode` |
| Display name | `gamemode` |
| Maven group | `com.urntt` |
| Base package | `com.urntt.gamemode` |
| Version format | `<SemVer>+<Minecraft version>`, for example `1.0.0+26.3` |
| License | MIT |

The mod version itself follows [Semantic Versioning](https://semver.org/); the `+<Minecraft version>` suffix is build metadata naming the Minecraft version the build targets. Version numbers start at `1.0.0`.

### Distribution

- Releases are published only as GitHub Releases. Do not publish to Modrinth, CurseForge, or any other mod platform, and do not add publishing tooling for them.
- `README.md` must clearly warn about the risks:
  - Multiplayer: a client game mode may conflict with server anti-cheat systems and server rules, and using the mod in some multiplayer modes or on some servers may get the player set back, kicked, or banned. The mod does not try to bypass any anti-cheat. It is disabled in multiplayer until the player enables it in the settings.
  - The server, including the integrated server of a singleplayer world, keeps using the player's real game mode and checks everything the client does. Vanilla servers with `allow-flight` off kick players who fly in a client game mode.

### Scope and behavior

- Client-only: `fabric.mod.json` declares `"environment": "client"`. There is no server-side component and no custom networking.
- Pure client game mode, in singleplayer and in multiplayer alike: the mod changes the game mode the client uses and nothing else. The client then behaves as the vanilla client does in that game mode, and the server, including the integrated singleplayer server, validates it as usual.
  - The mod never sends packets of its own and never tries to get around server checks.
  - What the client game mode changes: the local game mode of `MultiPlayerGameMode` (abilities such as flying and instant building, block breaking, interaction, the HUD and the creative inventory) and the game mode of the local player entity (`isSpectator()`, `isCreative()`, and so spectator noclip). The player list and what other players see stay the server's.
- The client game mode is an override on top of the game mode the server sets:
  - Choosing the server's current game mode ends the override. Ending it restores the server's game mode and the abilities the server last sent.
  - While it is active, abilities the server sends are adjusted to the client game mode; in creative, the client keeps its own flying state.
  - The setting "Follow Server Changes" (on by default) decides what happens when the server changes the player's real game mode: on, the override ends and the client uses the server's new game mode; off, the override stays. Respawns and dimension changes that keep the server's game mode never end it.
  - It is not saved. It ends when the player leaves the world or logs in again, and as soon as client game modes are turned off or the multiplayer rules rule out the current scene.
- Commands use vanilla's argument order only: `/gamemode <mode> @c` sets the client game mode, and `/gamemode @c` alone shows the client and server game modes with clickable modes. Do not add `/gamemode @c <mode>`.
  - They are intercepted on the client before they would be sent to the server, in `ClientPacketListener.sendCommand` (typed) and `sendUnattendedCommand` (clicked). Every other command, including vanilla `/gamemode` with other targets, is sent to the server unchanged. Do not register them through Fabric's client command API: Fabric executes client commands first, so a client `gamemode` would shadow vanilla `/gamemode`.
  - Their nodes are merged into the command tree the server sends, so the chat suggests and highlights them; without permission for `/gamemode`, the server sends no `gamemode` node and the mod adds its own.
- The client game mode switcher works like vanilla's F3 + F4 game mode switcher and looks the same, but applies a client game mode. Its key binding, "Client Game Mode Switcher (with F3)", is in the mod's key binding category, defaults to F5, and only acts while vanilla's debug modifier (F3) is held: it is handled together with vanilla's debug keys, so F3 + F5 does not also change the camera perspective. Pressing it again cycles the modes, and releasing the debug modifier applies the selection. Vanilla F3 + F4 stays unchanged. While the setting "Client Game Mode Switcher Key" is off, the mod ignores the key and leaves it to vanilla, so F3 + F5 changes the perspective; in Disabled mode the key stays on and only reports that client game modes are turned off.
- Configuration mode, shown as the first button of the configuration screen, as in hiteffect:
  - Disabled: client game modes are turned off everywhere, and the client always uses the server's game mode.
  - Custom (default): the mod's own settings apply.
  - OneConfig: the settings registered with OneConfig apply, and the mod's own settings are shown greyed out with the values OneConfig currently applies, next to an "Open in OneConfig" button. While the toggle on the mod's OneConfig card is off, client game modes are turned off as in Disabled mode. This mode is only offered when OneConfig is installed. If the configuration file selects it but OneConfig is missing, the mod behaves as in Custom mode and keeps the saved mode until the player changes it.
  - The settings, the same in Custom and OneConfig mode: "Client Game Mode Switcher Key" (on by default), "Follow Server Changes" (on by default), the multiplayer mode (`DISABLED` by default) and the server list (empty by default). The multiplayer mode is a dropdown and the server list a text list in OneConfig.
  - There is no separate on/off state, toggle key, default or reset rule: Disabled mode turns the mod off. A configuration file from 1.0.0, which had them, keeps its other settings and starts in Disabled mode if its state was off.
- `GameModeController` is the single owner of the multiplayer rules and the client game mode, following nojumpdelay's `JumpDelayController`. It reads the settings that apply in the configuration mode:
  - The multiplayer mode is a hard limit: `DISABLED` (the default) rules out every server, `WHITELIST` allows only servers in the server list, and `BLACKLIST` allows every server except those in it. Joining another player's LAN world or a Realm counts as multiplayer. Server list entries match like nojumpdelay's: by host (case-insensitive, after IDN conversion, and required to be a valid domain name or IP address) and by port only when the entry specifies one.
  - `availability()` is `DISABLED` while client game modes are turned off (checked first), `BLOCKED` on a ruled-out scene, and otherwise `AVAILABLE`. Setting a client game mode, by command or switcher, only reports the reason unless it is `AVAILABLE`; `/gamemode @c` alone changes nothing and works everywhere.
- Key bindings, in the mod's own category: "Client Game Mode Switcher (with F3)" (F5, used with F3) and "Open gamemode Settings" (unbound). The configuration screen also opens through Mod Menu and through the "Original menu" link on OneConfig's page.
- Settings are saved to the configuration file immediately when changed.

### Localization

- All user-facing text, including key binding names, the key binding category, chat messages, the switcher, the configuration screens, the OneConfig option titles, and command feedback, uses translation keys. Never hard-code display strings. Reuse vanilla translation keys where vanilla shows the same text (for example the game mode names `gameMode.<mode>` and `debug.gamemodes.select_next`).
- Provide translations for `en_us` and `zh_cn`, and keep both complete whenever a translation key is added or changed.

### Dependencies

- Required: Fabric Loader and Fabric API.
- Optional: Mod Menu, declared under `suggests` in `fabric.mod.json`. The mod must load and work normally without it, so Mod Menu classes may only be referenced from the Mod Menu entrypoint.
- Optional: [OneConfig](https://modrinth.com/mod/oneconfig), approved by the user and declared under `suggests`. The build only compiles against its API (`clientCompileOnly`); it is never bundled. OneConfig classes may only be referenced from the `com.urntt.gamemode.compat.oneconfig` package, which OneConfig reaches through its `oneconfig:config` entrypoint, so the mod loads and works normally without it. The rest of the mod reaches it only through `compat.OneConfigBridge`.
- Configuration is hand-written without a config library: a JSON file in the Fabric config directory, serialized with Gson (bundled with Minecraft). The configuration screens use vanilla widgets.
- Do not add other dependencies without the user's explicit approval.

### Implementation

- Language: Java only.
- Source sets: `src/main` holds only `fabric.mod.json` and the icon. All code and client resources live in `src/client`, and the client game tests live in `src/gametest`.
- The icon follows hiteffect's: the same hair clip, with a game controller whose four buttons stand for the four game modes. Its source is `art/icon.svg`. `src/main/resources/assets/gamemode/icon.png` is rendered from it at 512 x 512 pixels with a transparent background; render it again whenever the SVG changes.
- Mixins: prefer the MixinExtras injectors bundled with Fabric Loader (for example `@ModifyReturnValue`, `@WrapOperation` and `@WrapMethod`) over `@Redirect` and `@Overwrite`, to stay compatible with other mods and keep porting work small. Name handler methods `gamemode$<what>`.
- The server sets the client's game mode through `MultiPlayerGameMode.setLocalMode` in `ClientPacketListener.handleLogin`, `handleRespawn` and `handleGameEvent`, and its abilities in `handlePlayerAbilities`. These are the only places the mod reads the server's game mode from; keep the decisions about them in `GameModeController`, which has no Minecraft state, so the logic game test can check it directly.
- Fabric's `ClientPlayConnectionEvents.DISCONNECT` does not fire reliably when a singleplayer world is closed, so per-world state is also reset on login and on `JOIN`.

### Testing

- The client game tests in `src/gametest` start Minecraft and check the commands, the switcher, what the client game mode changes on the client while the server keeps the real game mode, the server-change rules, the configuration modes and screen, the key bindings, the multiplayer rules on a local dedicated server, and the saved configuration, including the upgrade of a 1.0.0 file. Keep them passing and extend them when behavior changes.
- They run twice: without OneConfig, and with `-PoneConfigRuntime=true`, which loads OneConfig and its dependencies (Fabric Language Kotlin, PolyCompose) from Modrinth's Maven into the development client. Their Modrinth version ids live in `gradle.properties` next to `oneconfig_version`; update them together. The run with OneConfig also checks the OneConfig mode.
- OneConfig stores its settings in the same config directory as the mod's own `gamemode.json` and watches its files, so its config file must keep a different name (`gamemode-oneconfig.json`).
- Inactive vanilla buttons show a greyed copy of their label, so tests find buttons by the translation key of their label, not by comparing labels.
- After porting to a new Minecraft version, run the client game tests. A successful build does not prove that the mixins still have the intended effect.
- `README.md` describes how to run them, including on a headless machine.

### CI, releases, and changelog

- GitHub Actions (`.github/workflows/build.yml`) builds the project and runs the client game tests, without and with OneConfig, on every push and pull request.
- Maintain `CHANGELOG.md` following [Keep a Changelog](https://keepachangelog.com/). Record every user-visible change under `Unreleased` in the same change that introduces it.
- `.github/workflows/release.yml` builds the mod and publishes a GitHub Release for the project version, with the jar attached and the matching `CHANGELOG.md` section as release notes. It runs when a tag `v<version>` (for example `v1.0.0+26.3`) is pushed, or when started manually on a branch, in which case it creates that tag on the branch's latest commit. It fails if a pushed tag does not match the project version, if the changelog has no section for the version, or if the release already exists.
- Release only when the user asks. To release, set `mod_version` in `gradle.properties`, rename `Unreleased` in `CHANGELOG.md` to `[<version>] - <YYYY-MM-DD>` above a new empty `Unreleased` section, commit, and push. Then start the release workflow on `main`. Claude Code cloud sessions cannot push tags, so start the workflow through the GitHub Actions API instead.

## Engineering principles

- Fix root causes, not symptoms. Diagnose the underlying cause before implementing a permanent fix. If an immediate mitigation is necessary, treat it as temporary and follow through with a root-cause fix.
- Prefer configuration-driven design for values that are expected to vary by environment, deployment, or product requirements. Avoid unexplained or duplicated magic values, but do not introduce configuration where a well-named constant is the clearer source of truth.
- Preserve a single source of truth and clear ownership for data, state, configuration, business logic, and authoritative documentation. Avoid duplicating canonical information across multiple locations.
- Do not maintain parallel legacy and replacement implementations without an explicit migration and removal plan.

## Documentation

- Keep documentation aligned with the code. When a code change affects documented behavior, APIs, architecture, configuration, workflows, or usage, update the relevant documentation in the same change.
- Keep each document's responsibility clear. For example, use `README.md` for project overview and usage, and `VISION.md` for product direction, architectural principles, or long-term decisions.
- Always specify a language identifier for fenced code blocks in Markdown.

## Language

- Communicate with the user in Chinese, including explanations, progress updates, and user-facing planning.
- Use English for development artifacts, including source code, comments, docstrings, documentation, READMEs, Git branch names, commit messages, and other deliverables intended to live in the repository.

## Git

- Do not change or override the Git author or committer identity. When an identity must be configured for commits created during the task, use:
  - Name: `urntt`
  - Email: `urntts@gmail.com`
- Do all actions on the user's behalf. Do not rewrite existing commit authorship unless explicitly requested. Do not add `Co-Authored-By` trailers or session links to commit messages or pull request descriptions.
- Develop on `main` and push directly to it. Branches and pull requests are not required.
- Because changes land on `main` without review, make sure `./gradlew build` and the client game tests pass locally before pushing.
- If a branch is used, give it a category-based prefix that reflects the purpose of the change, such as `feat/`, `fix/`, `refactor/`, `docs/`, `test/`, or `chore/`.
- Follow the [Conventional Commits](https://www.conventionalcommits.org/) specification for commit messages.
