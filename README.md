# droidViewfinder

A simple full-screen viewer for USB video sources on Android. Plug in a UVC-class device, such as a USB-C
or HDMI capture card or a webcam. Pick its format, resolution and frame rate, and watch the picture full
screen, undistorted, with pinch zoom.

Written in Kotlin with Jetpack Compose and Material 3. USB access goes through
[UVCAndroid](https://github.com/shiyinghan/UVCAndroid).

> **Status: early (0.1.0).** It works with the one capture card it has been tested with (see
> [docs/DESIGN.md](docs/DESIGN.md#verified-on-hardware)). Built-in cameras are not supported yet. Only
> debug builds are produced.

## Features

- Lists attached USB Video Class (UVC) devices.
- Full-screen, aspect-correct preview (letterboxed, never stretched), in portrait, in landscape and on
  foldables in both postures, without dropping the stream when the phone is folded or rotated.
- Three linked drop-downs for **Type** (MJPEG or uncompressed), **Resolution** and **FPS**, built from
  the modes the device reports.
- Pinch to zoom (up to 8×), drag to pan, and double tap to reset. Controls fade out after 5 seconds and
  come back on a double tap.
- Remembers the last mode per device.
- A USB reset button that resets the device and reopens it in the same mode.
- Asks only for the `CAMERA` permission (required by Android to open a UVC device) plus the per-device
  USB permission.

## Requirements

- An arm64 Android phone or tablet with **Android 14 (API 34) or later** and USB host (OTG) support.
- A UVC-class video device. A USB-A device needs a USB-C OTG adapter, and some capture cards need more
  power than a phone's port supplies.

## Build

Toolchain: Gradle 9.8.0 (wrapper), Android Gradle Plugin 9.4.0, Kotlin 2.4.20, `compileSdk` 37,
`targetSdk` 36 and `minSdk` 34. A JDK 21 or later is required to run Gradle.

### With Docker (no local Android SDK needed)

[`build-container/`](build-container/README.md) holds a Docker image with the JDK, the Android SDK and
`adb`:

```bash
docker compose -f build-container/compose.yaml up -d --build
```

```bash
docker exec droidviewfinder-android-build ./gradlew testDebugUnitTest assembleDebug
```

### With a local Android SDK or Android Studio

Install the Android SDK platform `android-37.0` and build-tools `36.0.0`. Then point Gradle at the SDK
with `ANDROID_HOME` or a `local.properties` file (`sdk.dir=/path/to/sdk`; the file is git-ignored), and
run:

```bash
./gradlew testDebugUnitTest assembleDebug
```

Android Studio can also open the repository root directly.

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Install

Enable USB or wireless debugging on the phone, then:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The [build container README](build-container/README.md#install-on-a-phone) describes wireless pairing
from the container. To sideload the APK without `adb`, copy it to the phone and allow installs from the
app you open it with.

Debug builds are signed with the debug key of the machine that built them. An update installs over an
existing copy only if it is signed with the same key.

## Use

1. Open the app and grant the camera permission.
2. Plug in the video device and tap **Refresh** if it is not listed.
3. Tap the device and allow USB access.
4. Choose **Type**, **Resolution** and **FPS** from the bar at the bottom. Double tap to show the
   controls again after they fade.

## Known limitations

- The USB reset uses `UsbDeviceConnection.resetDevice()`, a hidden Android API called by reflection.
  When it is unavailable, the app falls back to closing and reopening the device.
- Only `arm64-v8a` is packaged.
- There is no release signing configuration. Builds are debug builds and are not suitable for Google
  Play as they are.
- No recording, snapshots or audio.

See [docs/DESIGN.md](docs/DESIGN.md) for the design, the decisions behind it, and the roadmap.

## Contributing

Issues and pull requests are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md). Please report security
issues privately as described in [SECURITY.md](SECURITY.md). This project follows a
[Code of Conduct](CODE_OF_CONDUCT.md).

## License

Copyright 2026 Jason Bridges.

Licensed under the [Apache License, Version 2.0](LICENSE). Third-party components and their licenses are
listed in [NOTICE](NOTICE).
