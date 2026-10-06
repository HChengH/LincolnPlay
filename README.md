# LincolnPlay

**Wired CarPlay for the 2020 Lincoln Aviator (Chinese SYNC+ cracked head unit) — and similar Android 8 dashboards.** A port of [DiPlay](https://github.com/shihabal3amri/DiPlay) away from its BYD home: same receiver, retargeted transport, audio and cluster integration.

> Developed and verified on one car: a 2020 Aviator with an NXP MEK-MX8Q board running Android 8.1 (API 27, userdebug). Other SYNC+/Freescale units may work; BYD-specific paths remain in the tree but gate themselves off. Not affiliated with Ford or Apple.

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
