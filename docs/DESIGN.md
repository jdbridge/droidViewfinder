# Design notes

This document records how droidViewfinder works, the decisions behind it, and what has been verified on
real hardware. For building and installing, see the [README](../README.md).

## What the app does

Two screens:

1. **Source list.** Lists the USB Video Class (UVC) devices that are attached: USB-C and HDMI capture
   cards and webcams. The phone's built-in cameras are not listed yet (see the roadmap below).
2. **Viewer.** Shows the live picture full screen with the correct aspect ratio (letterboxed, never
   stretched), in both orientations and in both postures of a foldable phone. Three linked drop-downs
   along the bottom choose the mode the device offers: **Type** (MJPEG or uncompressed),
   **Resolution** and **FPS**. Other controls:
   - A back arrow at the top left, or the system Back gesture, returns to the source list.
   - A two-finger pinch zooms up to 8×, a drag pans while zoomed, and a double tap returns to the full
     view.
   - The overlay controls fade out after 5 seconds and come back on a double tap. They stay visible
     while a drop-down is open.
   - A reset button resets the USB device and reopens it in the same mode.
   - The last mode chosen for each device is remembered and restored the next time it is opened.

## Talking to a USB video device

| | Camera2 (platform API) | UVC library over `UsbManager` (libuvc) |
|---|---|---|
| Device support | External cameras through Camera2 are optional for device makers ([AOSP: external USB cameras](https://source.android.com/docs/core/camera/external-usb-cameras)), so support varies by phone. | Needs only USB host mode. Does not depend on the external-camera HAL. |
| Format choice | Not exposed. `StreamConfigurationMap` offers `YUV_420_888`, `JPEG` and others, and the HAL picks the UVC format. | Exposed: frame sizes, frame rates and formats (MJPEG, YUYV, NV12). |
| Cost | No native code. | Prebuilt native libraries (`libusb`, `libuvc`) inside the library. |

**Decision:** USB sources use [UVCAndroid](https://github.com/shiyinghan/UVCAndroid)
(`com.herohan:UVCAndroid:1.0.13`, Apache-2.0, on Maven Central). Only the UVC route allows choosing the
format, and it works whether or not the phone's Camera2 implementation supports external cameras. The
built-in cameras are planned to use Camera2, behind a common `VideoSource` interface.

The alternative considered was AUSBC, the maintained fork of `jiangdongguo/AndroidUSBCamera`
(distributed through JitPack).

## Facts that shape the design

- **Permissions.** For apps targeting API 28 and later, opening a UVC device needs both the USB device
  permission and `CAMERA` ([UsbManager](https://developer.android.com/reference/android/hardware/usb/UsbManager)).
  The app asks for `CAMERA` on first launch. UVCAndroid shows the system USB permission dialog per device.
- **Minimal permissions.** UVCAndroid's manifest also asks for storage, audio and foreground-service
  permissions for its recording features. The app does not record, so its manifest removes them
  (`tools:node="remove"`). The merged manifest asks only for `CAMERA`.
- **Duplicates (planned).** If a phone also exposes a UVC device through Camera2
  (`LENS_FACING_EXTERNAL`), it would be listed twice. Rule: list Camera2 external cameras only when no
  UVC device is attached.
- **Folding and rotation.** Folding, unfolding and rotating change the window size and would normally
  recreate the activity, which drops the camera stream. The activity handles
  `orientation|screenSize|smallestScreenSize|screenLayout|keyboardHidden|density` itself, so the stream
  survives.
- **Full screen.** Edge to edge. The viewer hides the system bars with `WindowInsetsControllerCompat`,
  keeps the screen on, and draws into the display cutout (`windowLayoutInDisplayCutoutMode=always`).
- **Aspect ratio.** The picture box is sized with `Modifier.aspectRatio(width / height)` from the
  selected mode and centred on black, so it is the largest picture of the right ratio that fits.
- **Zoom detail.** The `SurfaceView` buffer is fixed to the camera's own frame size
  (`holder.setFixedSize`) and the compositor scales it, so a zoomed picture shows the camera's real
  detail instead of an upscaled screen-sized copy.
- **16 KB pages.** Google Play requires apps that target API 35 or later and ship native libraries to be
  16 KB aligned from 2027-02-01 ([page sizes](https://developer.android.com/guide/practices/page-sizes)).
  As checked on 2026-10-06, UVCAndroid 1.0.13's arm64 libraries are 16 KB aligned and the debug APK passes
  `zipalign -c -P 16 -v 4`.
- **ABI.** The build packages only `arm64-v8a`. UVCAndroid ships four ABIs (about 25 MB of native
  libraries), and current phones are arm64.
- **Power and cables.** A USB-A device needs a USB-C OTG adapter. Some capture cards need more bus power
  than a phone supplies.

## Mode handling

- The drop-downs are built from what the device reports (`Size.type`, width, height and frame-rate list).
- They are linked. Changing Type keeps the resolution if the new type offers it, otherwise it takes the
  largest. Changing Type or Resolution keeps the frame rate if available, otherwise it takes the fastest.
- Applying a mode stops the preview, sets the new size and starts the preview again.
- `Size.type` is the UVC descriptor subtype: 4 and 5 are shown as "Uncompressed", 6 and 7 as "MJPEG".
- The last mode per device is stored in the app's `SharedPreferences` (`last_modes`), keyed by
  `vendor:product:serial` (the device's USB serial number, when it has one). Two identical devices with
  different serial numbers therefore keep separate choices.

## USB reset

The reset button releases the camera and then attempts a port-level reset through the hidden
`UsbDeviceConnection.resetDevice()` method, called by reflection on a second connection. It then reopens
the device with a new `CameraHelper` in the same mode. If the hidden method is not available, releasing
and reopening the device acts as a soft reset. If the device has not reopened after about 8 seconds, the
app returns to the source list.

`resetDevice()` is not a public API. A future Android release may block it, in which case only the soft
reset remains.

## Verified on hardware

Tested on a Pixel 11 Pro Fold (Android 17, arm64, 4 KB pages) with one
capture card: "USB3.0 Video", USB ID `345f:2130` (MacroSilicon), carrying an HDMI desktop signal.

- The card is listed, and the `CAMERA` and USB permission flows work.
- MJPEG at 1920×1080 @ 30 fps shows an undistorted, letterboxed picture on the inner display.
- On this card `Size.type` is 7 for MJPEG. An uncompressed mode has not been observed yet. The card
  advertises unusual resolutions (UVCAndroid's default first mode was MJPEG 1920×2160 @ 60), so the
  Resolution list is long.
- The port-level reset returned `true` three times in a row, and the card reopened in the same mode each
  time.
- The remembered mode is restored when the device reopens after a reset. Reopening the device from the
  source list and unplugging it while it is in use were reported to work, but were not observed over
  adb.
- Harmless log lines seen so far: an `AudioSystem ... missing perms` warning (the library probes an
  audio input, and `RECORD_AUDIO` is removed on purpose) and SELinux `avc: denied` lines for
  `/dev/bus/usb` and the `uvcvideo` debugfs directory, which the app does not need.

Not yet verified: other capture cards and webcams, a full list of modes, folding and unfolding while
streaming, and zoom and pan on a live picture beyond basic use.

## Testing tips

- On a phone with two displays, `adb exec-out screencap -d <display-id> -p` captures a specific display.
  `adb shell dumpsys SurfaceFlinger --display-id` lists the IDs.
- A double tap made from two `adb shell input tap` commands is too slow unless the first one is sent in
  the background: `input tap x y & sleep 0.12; input tap x y`.
- The app logs under the tag `droidViewfinder` (`adb logcat -s droidViewfinder`).

## Roadmap

1. **Built-in cameras** through Camera2, behind a common `VideoSource` interface, with the duplicate
   rule above. `SpikeViewModel` is then renamed and split.
2. **Source list.** USB attach and detach broadcasts for hot-plug, and a clearer empty state.
3. **Mode labels.** Plain-language labels per format once more devices' format subtypes are known, and
   disabling combinations the device does not offer.
4. **Viewer.** A message when the device is unplugged while viewing, and a hint that a double tap shows
   the controls.
5. **Foldables.** Test folding and unfolding mid-stream, and list layouts per window size class.
6. **Tests.** Unit tests for the pure parts: mode sorting and grouping, the aspect-ratio and zoom-clamp
   maths, and the duplicate rule.
7. **Release build.** A release signing configuration, R8, and an Android App Bundle, if the app is
   published beyond sideloading.

Out of scope for now: recording, snapshots, audio, exposure controls, and several cameras at once.
