<div align="center">
    <a href="https://gameyfin.org">
        <img src="assets/v2/Banner.svg" width="auto" alt="Gameyfin Logo">
    </a>
    <h2>Gameyfin</h2>
    <h4>Manage your video games.</h4>
    <p>simple / fast / <a href="https://gameyfin.org/blog/2025/12/22/why-gameyfin-is-foss/">FOSS</a></p>
</div>

## Overview

Name and functionality inspired by [Jellyfin](https://jellyfin.org/).

Gameyfin will turn your disorganized collection of video games into a beautiful, easy-to-navigate library that you can access from any device with a web browser.  
It will automatically scan your game folders, download metadata and cover images, and present everything in a user-friendly interface.  
Download your game files directly from the web UI, share your library with friends, and enjoy your games like never before.  

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

### Technical Details

Gameyfin v2 is written in Kotlin and uses the following libraries/frameworks:

* Spring Boot 3 for the backend
* Vaadin Hilla & React for the frontend
* PF4J for the plugin system
* H2 database for persistence
