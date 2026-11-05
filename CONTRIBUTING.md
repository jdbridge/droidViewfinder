# Contributing to droidViewfinder

Thanks for considering a contribution. Bug reports, device test reports, documentation fixes and code
are all welcome.

## Before you start

- For anything larger than a small fix, open an issue first so the approach can be agreed before you
  spend time on it.
- Device reports are especially useful: which phone and Android version, which capture card or webcam
  (with its USB ID from `adb shell lsusb`, or the vendor:product the app shows), which modes it lists,
  and what worked.
- Report security problems privately, as described in [SECURITY.md](SECURITY.md), not in an issue.

## Development setup

See the [README](README.md#build) for building with Docker or with a local Android SDK. Before you open
a pull request, run:

```bash
./gradlew testDebugUnitTest assembleDebug lintDebug
```

CI runs the same tasks on every pull request.

## Pull requests

- Keep each pull request to one topic, and describe what changed and how you tested it, including the
  device and USB device you used if the change touches camera or USB behaviour.
- Follow the existing style: Kotlin official code style (`kotlin.code.style=official`), Compose for UI,
  and comments that explain *why* rather than *what*.
- Add unit tests for logic that does not need a device (mode selection, sorting, geometry).
- Do not commit build output, `local.properties`, keystores or other secrets. `.gitignore` covers the
  usual ones.
- Update [CHANGELOG.md](CHANGELOG.md) under **Unreleased** for user-visible changes, and
  [docs/DESIGN.md](docs/DESIGN.md) if you change a design decision.

## License

By contributing, you agree that your contributions are licensed under the
[Apache License, Version 2.0](LICENSE), the license of this project (see section 5 of the license).

This project follows the [Code of Conduct](CODE_OF_CONDUCT.md).
