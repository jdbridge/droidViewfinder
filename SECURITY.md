# Security policy

## Supported versions

Only the latest release and the `main` branch receive fixes.

## Reporting a vulnerability

Please **do not** open a public issue for security problems.

Report them privately through GitHub: open the repository's **Security** tab and choose
**Report a vulnerability**. This creates a private advisory that only the maintainer can see. Include
the affected version or commit, the device and Android version, steps to reproduce, and the impact
you expect.

You should receive a reply within 14 days. Once a fix is available, the advisory is published with
credit to the reporter, unless you ask not to be named.

## Scope

The app talks to USB devices that the user selects and has no network access of its own. Relevant
reports include anything that lets a USB device, another app or a crafted input crash the app in an
exploitable way, read data it should not, or escalate privileges. Vulnerabilities in third-party
libraries (for example UVCAndroid, libusb or libuvc) should also be reported upstream.
