# Changelog

All notable changes to this project are documented in this file. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project uses
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.0] - 2026-11-05

First public release.

### Added

- Source list of attached USB Video Class (UVC) devices, with camera and USB permission flows.
- Full-screen, aspect-correct viewer that keeps streaming through fold, unfold and rotation.
- Linked Type, Resolution and FPS drop-downs built from the modes the device reports.
- Pinch zoom (up to 8×), pan, double-tap reset, and controls that fade after 5 seconds.
- Remembered mode per device (vendor, product and serial number).
- USB reset button: a port-level reset where available, otherwise close and reopen.
- Docker build container with the JDK, the Android SDK and adb.

[Unreleased]: https://github.com/jdbridge/droidViewfinder/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/jdbridge/droidViewfinder/releases/tag/v0.1.0
