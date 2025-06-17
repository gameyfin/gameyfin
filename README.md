<div align="center">
    <a href="https://gameyfin.org">
        <img src="assets/v2/Banner.svg" width="auto" alt="Gameyfin Logo">
    </a>
    <h2>Gameyfin</h2>
    <h4>Manage your video games.</h4>
    <p>simple / fast / <a href="https://github.com/gameyfin/gameyfin/blob/main/LICENSE.md">FOSS</a></p>
</div>

> [!IMPORTANT]
> Gameyfin v2 is currently in beta stage.  
> Expect bugs and breaking changes until the `2.0.0` release.

## Overview

Name and functionality inspired by [Jellyfin](https://jellyfin.org/).

### Features

✨ Automatically scans and indexes your game libraries  
⬇️ Access your library via your web browser & download games directly from there  
👥 Share your library with friends & family  
⚛️ LAN-friendly (everything is cached locally)  
🐋 Runs in a container or any system with a JVM  
🌈 Themes (including colorblind support)  
🔌 Easily expandable with plugins  
🔒 Integrates into your SSO solution via OAuth2 / OpenID Connect  
🆓 **100% open source and free to use without any paywall.**

### Documentation

The documentation is available at [gameyfin.org](https://gameyfin.org/).

### Contribute to Gameyfin

Currently, no contribution guide is available. After the `2.0.0` release, contributions will be welcome.

### Technical Details

Gameyfin v2 is written in Kotlin and uses the following libraries/frameworks:

* Spring Boot 3 for the backend
* Vaadin Hilla & React for the frontend
* PF4J for the plugin system
* H2 database for persistence
