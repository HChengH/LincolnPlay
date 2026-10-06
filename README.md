<<<<<<< HEAD
# LincolnPlay

**Wired CarPlay for the 2020 Lincoln Aviator (Chinese SYNC+ cracked head unit) — and similar Android 8 dashboards.** A port of [DiPlay](https://github.com/shihabal3amri/DiPlay) away from its BYD home: same receiver, retargeted transport, audio and cluster integration.

> Developed and verified on one car: a 2020 Aviator with an NXP MEK-MX8Q board running Android 8.1 (API 27, userdebug). Other SYNC+/Freescale units may work; BYD-specific paths remain in the tree but gate themselves off. Not affiliated with Ford or Apple.
>
> See [DISCLAIMER.md](DISCLAIMER.md) — for technical exchange and learning only, provided as-is, no liability.

## What is verified on the car

- **Wired USB CarPlay on Android 8.1** — full bring-up on an old stack: 16 KB `UsbRequest` chunk clamp, USB re-enumeration lifecycle, replug recovery (a failed wired session forces a clean re-enumeration instead of reusing stale endpoints), stable lockdown identities plus an external pairing backup so reinstalls pair once, not every time.
- **Cluster and HUD navigation** — the AmapAuto broadcast dialect with dual old/new field spellings: arrows, road names and distances land on the factory fixed-function widgets, in factory style.
- **Steering-wheel and console keys** — next/previous track from the wheel (297/298) and the console seek keys (294/295).
- **Clear, separately adjustable navigation prompts** — the `/info` advertisement drops the 8/12 kHz PCM bits for spoken audio types, so the iPhone sends wideband (the trick the aftermarket dongles use): prompts stay clear on the guidance usage *and* keep their own volume path. Media rides lossless LPCM 48 kHz.
- **Music ducking tuned like the audio standard** — geometric gain ramps (Apple's AudioUnit `Pow` curve family) with a 120 ms attack and 220 ms release; the prompt track drains before release so arrival announcements are never clipped, and the duck lifts early enough that recovery hides behind the media buffer latency.
- **Cluster song widget** — now-playing title/artist via the `PLAY_INFO` radio dialect, Bluetooth-style. If a player ever tunnels lyric lines through the now-playing title, the same channel scrolls them unchanged.
- **Day/night that follows the light** — the ambient sensor with auto-headlamp hysteresis (night below 400 lux, day only above 1000), seeded at connect by a sunrise-equation baseline because this board exposes no system day/night signal at all (verified with wide-net sniffers).
- **60 fps option, swipe latency work** — touch moves coalesce through send bursts, the video layer composites opaque when fully covered.
- **Diagnostics everywhere** — crash safety net with a copyable report, on-screen log overlay, session export, connect-step timings in the log.

## Not yet

Wireless mode, microphone uplink (Siri/calls need a libopus encoder port — Android 8 lacks the MediaCodec one), the SurfaceView render path, parallelized startup permissions.

## Building

Same as upstream: see [docs/BUILD.md](docs/BUILD.md). The standalone APK requires external MFi runtime assets supplied through `DIPLAY_AUTH_ASSETS_DIR`; those files are **never committed** — the build rejects credential containers in the tree. Min SDK 26.

**Do not distribute built APKs publicly** — they embed the accessory identity you built with. Share source, not binaries.

## Credits

- [shihabal3amri/DiPlay](https://github.com/shihabal3amri/DiPlay) — the receiver this port carries; upstream history is merged through post-0.2.12 `main`.
- The DiAuto lineage before it, and the fork authors whose Android 8 findings (16 KB clamp, wired lifecycle) are baked into the transport.
=======
# DiPlay

**CarPlay for compatible BYD Android head units.** Wired and wireless, with the familiar DiAuto interface. Independent app: `com.shihab.diplay`.

> **BYD support scope:** These projects focus on BYD cars. They may work on other brands, but other brands are unsupported and there are no plans to add support or fix brand-specific incompatibilities.

[Download & website](https://shihabal3amri.github.io/DiPlay/) · [Release](https://github.com/shihabal3amri/DiPlay/releases/tag/v0.2.13) · [Report a problem](https://github.com/shihabal3amri/DiPlay/issues/new/choose)

![DiPlay home](site/assets/home.png)

## 0.2.13 — public preview

Install on the **car**, not the iPhone. No jailbreak, dongle, Mac, account or authentication server is required for use. Core CarPlay does not require ADB; optional dashboard, battery, wheel-speed and parked-video features do. Your head unit must permit APK installation. The APK supports Android 9+ (API 28); wireless supports Wi-Fi Direct, the car’s existing hotspot or Existing Wi-Fi / Same LAN. Android 9 Wi-Fi Direct uses a firmware-dependent legacy path with generated group credentials and unverified requested frequency; see [Android 9 Wi-Fi Direct](docs/ANDROID9_WIFI_DIRECT.md). Android 10+ verifies its negotiated group frequency.

- Wired USB and wireless CarPlay with local authentication.
- BYD HUD navigation with arrows, distance and street names on verified firmware.
- Car hotspot support, improved audio buffering and saved receive diagnostics.
- Automatic address discovery, fixed-channel Wi-Fi fallbacks and successful-configuration memory.
- Icon/text size, resolution and frame rate; applying a display change reconnects CarPlay.
- Local diagnostic export. Reports are sent only if you choose to share them.
- Separate installation alongside DiAuto. Run one projection app at a time.

This is **not an Apple-certified product**. The APK bundles an experimental accessory identity recovered from public Carlinkit firmware, not a newly provisioned MFi identity for DiPlay. A bundled private key is extractable. Acceptance after future iOS updates, reliability across head units and suitability of that identity for general distribution are unresolved. This release invites community testing; it is not a guarantee of universal compatibility.

Earlier releases were tested on the development DiLink5.1 car: live windshield guidance and street names work, Car hotspot now starts CarPlay, and Wi-Fi Direct performance is substantially improved. Occasional audio cutouts remain and are deferred to a later update. The floating-map test build was installed on the development DiLink 5.1 car; feedback led to the pinch corrections in 0.2.9. Earlier wheel-speed and video contributions were tested on a BYD Tang with DiLink 5.0 and an iPhone 15 Pro on iOS 27; wheel-speed dead reckoning in tunnels remains unverified. Broader head-unit and iOS compatibility is not guaranteed. The HUD firmware scope and cleanup limits are documented in [BYD navigation](docs/BYD_NAVIGATION.md).

## What’s new in 0.2.13

- Android 9 Wi-Fi Direct, IPv4-first hotspot endpoints, safer Auto channel ordering and wireless startup without unused NSD/USB services.
- Guarded rendered-video handoff fallback, safe wireless-to-USB switching and checked, cancellable permission setup.
- Narrow USBMUX trailer recovery that preserves valid payload replies.
- Optional live dock/split-screen areas and square-canvas rotation, plus the selected-decoder capability check and default-off experimental side panel.
- DiLink 4 casting/calibration and live cluster picture controls; checked DiLink 3 projection entry with compensation.
- Recent turn-card retention across wireless replacement, a finer dashboard map choice, battery-protocol fallback and eligible wheel-service recovery.
- The observed Siri microphone timestamp correction and TCP_NODELAY touch events, with device-specific performance limits.
- **Off by default:** independent experimental DiLink 3 call keys/dashboard calls and AAC-LC buffered music. Read their firmware/audio/restoration limits before opting in. Eligible hotspot join repair is a separately confirmed Check/Apply/Restore action.
- Clearer settings, saved-menu behavior, car-button customization and day/night-aware waiting screens.
- Traditional Chinese (Taiwan), bringing both the app and release website to seven languages.

See [0.2.13 release notes](docs/RELEASE-NOTES-0.2.13.md) and [validation](docs/VALIDATION.md) for all reviewed contributions, hardware evidence and remaining physical tests. Higher resolution and large square canvases cost more decoder/GPU work. General stutter, calls/Siri, decoder, old-iOS startup and model-specific reports remain under investigation. [0.2.12 notes](docs/RELEASE-NOTES-0.2.12.md) remain available as historical guidance.

If a problem remains, reproduce it on **0.2.13**, then use **Settings → Diagnostics → Save diagnostic report**. Android 10+ normally saves to **Downloads/DiPlay**; Android 9 uses the document picker. If unavailable, use **View report** or **Share** from the confirmation, which identifies external/private fallback storage. Review the `.txt` and add it to a matching [existing issue](https://github.com/shihabal3amri/DiPlay/issues), or [create one](https://github.com/shihabal3amri/DiPlay/issues/new/choose). Include vehicle/head-unit model, exact firmware and Android/DiLink, phone/iOS, connection backend, relevant settings, steps and failure time. Reports are shared only when you choose; never post your hotspot password.

## Documentation

[Existing Wi-Fi / Same LAN](docs/EXISTING_WIFI.md) keeps the iPhone and head unit
on an external router. See the guide for setup, build requirements and the
BYD DiLink 4.0 / Android 10 clean-install validation result.

- [Install and connect](docs/INSTALL.md)
- [Compatibility and troubleshooting](docs/COMPATIBILITY.md)
- [Smooth wireless CarPlay](docs/SMOOTH_WIRELESS.md)
- [Privacy and diagnostic reports](docs/PRIVACY.md)
- [Build from source](docs/BUILD.md)
- [Validation](docs/VALIDATION.md)
- [Release notes](CHANGELOG.md)
- [Credits and licenses](docs/THIRD_PARTY_NOTICES.md)

The app and release website are available in English, Arabic, Russian, Ukrainian, Spanish, Simplified Chinese and Traditional Chinese (Taiwan). Traditional Chinese uses Taiwan wording; the app also recognizes Hong Kong/Macao and explicit Hant selections without claiming separate regional translations. Choose the app language in Settings; on Android 13+, it stays synchronized with Android’s per-app language setting.

## Source and credits

Based on [xcertplay](https://github.com/shilapi/xcertplay), GPL-3.0. The home/settings UI and website adapt [DiAuto](https://github.com/shihabal3amri/DiAuto), AGPL-3.0; that license is included in `docs/licenses`. Preserve those notices when distributing modifications. CarPlay and its icon belong to Apple Inc.; no Apple or BYD affiliation or endorsement is implied.

This repository starts with a clean public source snapshot. Local research, tester reports and release-signing secrets are excluded. The complete source corresponding to the APK is provided with every release; experimental runtime identity assets are described separately in the build instructions and notices.

## Local release packaging

The release APK intentionally contains the experimental accessory identity. The Git repository and source archive exclude all accessory and Android signing keys; tests generate synthetic identities at runtime. Source/CI builds omit runtime identity assets by default. Local release builds explicitly select an external asset directory. Publishing the APK makes its bundled identity extractable; building locally does not preserve that identity's confidentiality.
>>>>>>> upstream-main
