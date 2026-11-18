<div align="center">
    <a href="https://gameyfin.org">
        <img src="assets/v2/Banner.svg" width="auto" alt="Gameyfin Logo">
    </a>

[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=gameyfin_gameyfin&metric=sqale_rating)](https://sonarcloud.io/summary/new_code?id=gameyfin_gameyfin)
[![Reliability Rating](https://sonarcloud.io/api/project_badges/measure?project=gameyfin_gameyfin&metric=reliability_rating)](https://sonarcloud.io/summary/new_code?id=gameyfin_gameyfin)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=gameyfin_gameyfin&metric=security_rating)](https://sonarcloud.io/summary/new_code?id=gameyfin_gameyfin)
[![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=gameyfin_gameyfin&metric=vulnerabilities)](https://sonarcloud.io/summary/new_code?id=gameyfin_gameyfin)
</div>
<div align="center">
    <h2>Gameyfin</h2>
    <h4>Manage your video games.</h4>
    <p>simple / fast / <a href="https://gameyfin.org/blog/2025/12/22/why-gameyfin-is-foss/">FOSS</a></p>
</div>

## Overview

Name and functionality inspired by [Jellyfin](https://jellyfin.org/).

Gameyfin will turn your disorganized collection of video games into a beautiful, easy-to-navigate library that you can
access from any device with a web browser.  
It will automatically scan your game folders, download metadata and cover images, and present everything in a
user-friendly interface.  
Download your game files directly from the web UI, share your library with friends, and enjoy your games like never
before.

### Editions, versions, and included DLC

Gameyfin can track the store version of your games, the edition you own, and the DLC included with your copy. Link
store products to a game in its metadata editor, label editions such as Deluxe, and compare the latest store version
with the version in your library. Gameyfin can suggest DLC from names in your game library, and you can adjust the
selection or add extras the store does not list. Game details show the edition, version, and included extras.

#### Game Store Provider plugins

Stores are provided by plugins in the new **Game Store Provider** category, listed between the metadata and download
provider categories. Install plugins individually from the marketplace in Administration → Plugins.
Images contain no plugin JARs. Initial administrators may choose plugins after login or finish with none.
There is one plugin per data source:

| Plugin | Store | Provides |
|---|---|---|
| [`plugin-steamstore`](https://github.com/gameyfin/plugin-steamstore) (Steam Store) | Steam | Search, DLC |
| [`plugin-steamcmd`](https://github.com/gameyfin/plugin-steamcmd) (Steam Builds, unofficial) | Steam | Versions (branch builds) |
| [`plugin-gog`](https://github.com/gameyfin/plugin-gog) (GOG Store) | GOG | Search, versions, DLC |
| [`plugin-gogdb`](https://github.com/gameyfin/plugin-gogdb) (GOG Builds, unofficial) | GOG | Versions, edition names |
| [`plugin-epic`](https://github.com/gameyfin/plugin-epic) (Epic Games Store, egdata.app) | Epic | Search, versions, DLC |
| [`plugin-itch`](https://github.com/gameyfin/plugin-itch) (itch.io, public pages) | itch.io | Search, versions (undated upload names, no DLC or editions) |

![Game Store Provider plugins](assets/screenshots/editions-dlc/plugins-game-store-provider.png)

- **Several sources per store:** search and versions use the highest-priority source that has a result and fall back
  to the others. DLC and edition names from all sources are merged without duplicates.
- **Order:** the store dropdown follows the plugin order set in the plugin admin menu, so you can put your most used
  store first.
- **Search is optional:** plugins that cannot search (SteamCMD, GOGDB) only add data. If no enabled plugin of a store
  can search, you can still add products by ID or URL.
- **No store plugin enabled:** the **Store versions** section is hidden and the refresh task does nothing.
- **Plugin disabled:** its links stay visible read-only with cached data and are not refreshed.

#### Writing a store plugin

Store plugins are separate from metadata plugins: a plugin that provides metadata does not provide store data. A
plugin that wants to add a store ships as its own plugin implementing the `GameStoreProvider` extension point of the
plugin API. It becomes another source for its store and is combined with the others as described above. Only implement what
your source provides: `search`, `fetchVersions`, `fetchDlc` and `suggestEditions` all have empty defaults, and a
plugin that cannot search should set `supportsSearch = false`.

The Steam builds, GOGDB, Epic and itch.io plugins use unofficial services or scrape public pages. Their
repositories run weekly live checks. In a plugin checkout, run `GAMEYFIN_LIVE_TESTS=true ./gradlew cleanTest test`.

See [plugin development and rollout](docs/plugin-distribution.md) and
[single-mount container migration](docs/container-storage.md).

#### Screenshots

Open **Store versions** in the game metadata editor and choose **Add store**, then pick one of the enabled stores and
search for the game.

![Store versions section with no store linked](assets/screenshots/editions-dlc/store-versions-empty.png)

![Choosing a store](assets/screenshots/editions-dlc/store-picker.png)

A linked store has an edition and the product version to track.

![Store link with edition and version](assets/screenshots/editions-dlc/store-link-editor.png)

Each link also has an **Extras** section for DLC, edition upgrades, and bonus material that come with your copy. Use
**Detect extras** to suggest them from your files, or add extras the store does not list.

![Managing extras for a store link](assets/screenshots/editions-dlc/extras-editor.png)

Game details show the store version with an update status, plus the selected extras.

![Steam version and extras in game details](assets/screenshots/editions-dlc/details-steam-extras.png)

![Steam update available in game details](assets/screenshots/editions-dlc/details-steam-update.png)

![GOG version in game details](assets/screenshots/editions-dlc/details-gog-version.png)

### Documentation

The documentation and screenshots are available at [gameyfin.org](https://gameyfin.org/).

### Features

✨ Automatically scans and indexes your game libraries  
⬇️ Access your library via your web browser & download games directly from there  
👥 Share your library with friends & family  
⚛️ LAN-friendly (everything is cached locally - except for videos)  
🐋 Runs in a container or any system with a JVM  
🌈 Themes (including colorblind support)  
🔌 Easily expandable with plugins  
🔒 Integrates into your SSO solution via OAuth2 / OpenID Connect  
🆓 **100% open source and free to use without any paywall.**

### Contribute to Gameyfin

Contributions are welcome!  
There are no strict requirements to contribute, but please contact us first if you want to implement a new feature or
change the design of the application before you start working on it.

### Gameyfin Trademark and Attribution Notice

Gameyfin is free and open-source software licensed under the GNU Affero General Public License version 3.  
The name "Gameyfin" and the official Gameyfin logos identify the original Gameyfin project.  
Permission to use, modify, and distribute the source code does not imply official affiliation with, endorsement by, or support from the Gameyfin project.  
Modified versions and third-party applications must not misrepresent their origin or imply official endorsement by the Gameyfin project.  
Third-party projects may describe their compatibility with Gameyfin, provided that such descriptions do not mislead users regarding the project's origin or official status.  
The Gameyfin project reserves any trademark rights it holds in its name and logos.

### Acknowledgements

[![YourKit Logo](https://www.yourkit.com/images/yklogo.png)](https://www.yourkit.com/)  
Gameyfin is supported by [YourKit](https://www.yourkit.com/), the makers
of [YourKit Java Profiler](https://yourkit.com/java/profiler/), a powerful tool for profiling Java and Kotlin
applications.
