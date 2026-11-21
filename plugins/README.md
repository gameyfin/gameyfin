# Local plugin development

Use JDK 25 for Gameyfin and Gradle. From the Gameyfin repository root, clone the plugin repositories you want to work on
into `plugins/`. Their names start with `plugin-`.

```sh
git clone https://github.com/gameyfin/plugin-steam-metadata.git plugins/plugin-steam-metadata
```

Each checkout has its own Git history. Gameyfin's Git ignore rules exclude the checkouts.

## IntelliJ IDEA

1. Open Gameyfin as a Gradle project. In the Gradle tool window, unlink any old copy of the plugin. Use **Link Gradle
   Project** to link the `build.gradle.kts` inside the checkout under `plugins/`.
2. Open the plugin source from that checkout. Set a breakpoint on an executable line with **Suspend** set to **All**.
3. Debug the shared **Gameyfin** configuration. It builds the local plugins before launch. Trigger the plugin action you
   want to debug. For Steam Metadata, you can set a breakpoint in `SteamPlugin.stop()` and disable the running plugin in
   **Administration → Plugins**.

**Rebuild plugins** reruns plugin preparation. **Rebuild all** rebuilds Gameyfin and prepares the local plugins. Restart
Gameyfin after rebuilding so PF4J loads the new bytecode.

## Terminal and other IDEs

Run these commands from the Gameyfin repository root. On Windows, use `gradlew.bat` for the Gradle commands and set the
environment variables in your shell.

```sh
./gradlew -PpluginDevDir=plugins buildDevPlugins
./gradlew -PpluginDevDir=plugins clean build -x test
```

The first command prepares plugins only. The second also rebuilds Gameyfin and skips tests, matching **Rebuild all**.

To run Gameyfin with local plugins, set a development `APP_KEY` and keep the same key across runs that reuse the same
data. For a fresh local instance, you can generate a key with OpenSSL.

```sh
export APP_KEY="$(openssl rand -base64 16)"
export APP_URL=http://localhost:8080
export SPRING_PROFILES_ACTIVE=dev
./gradlew -PpluginDevDir=plugins :app:bootRun
```

`bootRun` prepares the plugins and sets PF4J to development mode. To debug, use the same environment variables and start
Gameyfin with this command.

```sh
./gradlew -PpluginDevDir=plugins :app:bootRun --debug-jvm
```

The JVM waits for a debugger on `localhost:5005`. Open the plugin checkout as a source project in your IDE, set your
breakpoints, then attach its remote JVM debugger to that port.

## Which plugin API is used?

`-PpluginDevDir=plugins` tells Gradle to include the cloned builds and substitute Gameyfin's local `:plugin-api` project
for `org.gameyfin:plugin-api`. Building from a plugin's own checkout or running its standalone Gradle tasks in the IDE
uses the released API version declared by that plugin.

To check one plugin after an API change or inspect its compile dependency, run the task through Gameyfin's Gradle build.

```sh
./gradlew -PpluginDevDir=plugins :plugin-steam-metadata:compileKotlin
./gradlew -PpluginDevDir=plugins :plugin-steam-metadata:dependencyInsight \
  --dependency org.gameyfin:plugin-api --configuration compileClasspath
```

The dependency report should show `project ':plugin-api'`. If a breakpoint misses, check the plugin path in the Gameyfin
log. PF4J can select an installed copy with the same plugin ID. It should load the development copy from your checkout's
`build/` directory.
