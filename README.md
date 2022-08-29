<div align="center">
  <img src="assets/Gameyfin_Logo_White_Border.svg" height="128px" width="auto" alt="Gameyfin Logo">
  <h1>Gameyfin</h1>
  <p align="center">A simple game library manager.</p>
</div>

<img />

# Overview

Name and functionality inspired by [Jellyfin](https://jellyfin.org/).

## Video

Click [this link](https://youtu.be/BSaccEm0tpo) to watch how to install and set up Gameyfin on your machine.

## Features

* Automatically scans your game library folder
* Load additional information about the games from IGDB
* Display your library in a modern web frontend
* Download games directly from your browser
* Search and filter your game library
* Offline-friendly (once the library has been scanned everything is cached locally)
* Easy to host yourself thanks to native Docker support (alternatively it's only one .jar file to run on bare metal)
* Light and dark theme

## Installation

### General

Since Gameyfin loads information from IGDB, you need to register yourself there. Follow [this guide](https://api-docs.igdb.com/#account-creation).

### Docker

1. Download the `docker-compose.example.yml` file from this repository and rename it to just `docker-compose.yml`
2. Edit the configuration values to your liking
3. Run `docker-compose up -d`

### Bare metal

1. Make sure you have a JRE or JDK with version 18 or greater installed
2. Download the latest `gameyfin.jar` and `gameyfin.properties` file from the releases page
3. Edit the config options in the `gameyfin.properties` file
4. Use the following command to start Gameyfin: `java -jar gameyfin.jar`
5. Open the address of your Gameyfin host in your browser, Gameyfin runs under port 8080 by default

## Screenshots

### Game library screen in light mode (top) and dark mode (bottom)
![Game library](assets/library_overview.png)

### Game detail view
![Game detail screen](assets/game_detail_view.png)

### Automatic library scanning
![Library scan hint](assets/scan_library.png)

### Admin interface
![Admin interface](assets/game_mappings.png)

### Manually fix incorrect mappings (with autocomplete suggestions)
![Fix mapping dialog](assets/fix_game_mapping.png)

