# Android build container

A Docker image with the JDK, the Android command-line tools, the Android SDK platform and build-tools,
and `adb`. It builds and installs droidViewfinder without Android Studio or a local SDK. The image
contains no project code: Gradle, the Android Gradle Plugin (AGP) and Kotlin come from the project's own
Gradle wrapper and build files.

| Component | Version |
|---|---|
| Base image | `eclipse-temurin:25-jdk-noble` (Temurin JDK 25) |
| Android command-line tools | `15859902` (SHA-256 checked during the image build) |
| SDK platform | `platforms;android-37.0` |
| Build-tools | `build-tools;36.0.0` (the default for AGP 9.4) |
| In-container user | `dev`, UID/GID from `HOST_UID`/`HOST_GID` (default 1000) |

## Use

From the repository root:

```bash
docker compose -f build-container/compose.yaml up -d --build
```

```bash
docker exec droidviewfinder-android-build ./gradlew testDebugUnitTest assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

If your user ID is not 1000, pass it so that files written to the bind-mounted checkout are owned by you:

```bash
HOST_UID=$(id -u) HOST_GID=$(id -g) docker compose -f build-container/compose.yaml up -d --build
```

The container idles (`sleep infinity`), so later `docker exec` calls reuse a warm Gradle daemon. It is
capped at 4 CPUs and 6 GB of memory; `container-init.sh` writes a matching
`~/.gradle/gradle.properties` on first start. Change both together if you change the caps.

## Install on a phone

The service uses host networking, so `adb` in the container can reach a phone on the same LAN through
wireless debugging (Android 11 and later):

1. On the phone: **Settings → System → Developer options → Wireless debugging → Pair device with
   pairing code**.
2. Pair once: `docker exec -it droidviewfinder-android-build adb pair <phone-ip>:<pairing-port>`
3. Connect: `docker exec droidviewfinder-android-build adb connect <phone-ip>:<port>`. The port is shown
   on the Wireless debugging screen and changes after each reconnect. `adb mdns services` lists it.
4. Install: `docker exec droidviewfinder-android-build adb install -r app/build/outputs/apk/debug/app-debug.apk`

## Volumes

- `gradle-cache` → `/home/dev/.gradle`: the Gradle distribution, plugins and dependencies.
- `android-home` → `/home/dev/.android`: the adb key and the **debug signing key**
  (`debug.keystore`), generated on the first build.

Back up `debug.keystore` if you install debug builds on a device. A new key (for example after
`docker volume rm`) makes `adb install -r` fail with a signature mismatch until the app is uninstalled.
Never commit a keystore. `.gitignore` excludes `*.keystore` and `*.jks`.

## Bumping versions

- **JDK:** change `JDK_IMAGE` in the `Dockerfile` to another `eclipse-temurin` tag.
- **Command-line tools:** take the new build number and SHA-256 of the Linux package from
  <https://developer.android.com/studio#command-line-tools-only> and update `CMDLINE_TOOLS_VERSION` and
  `CMDLINE_TOOLS_SHA256`.
- **Platform / build-tools:** update `SDK_PLATFORM` and `SDK_BUILD_TOOLS`, and the `test -d` check for
  build-tools at the end of the `Dockerfile`. Keep the platform in step with `compileSdk` in
  `app/build.gradle.kts`, and the build-tools version with what AGP requires.
- **Gradle, AGP, Kotlin:** these live in the project (`gradle/wrapper/gradle-wrapper.properties`,
  `build.gradle.kts`), not in the image.

Rebuild afterwards with `docker compose -f build-container/compose.yaml up -d --build`.
