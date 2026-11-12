# MovieBox v24.0 — Direct Video Play (Gofile / MKV / MP4 / HLS / DASH) + 3-Step Animated Welcome, 5-Digit Share Code Redeem & Admin Panel

## What's new in v24.0
- **Direct Play for Gofile & Protected Stream Links (`DirectStreamResolver.kt`, `LinkProbe.kt`, `VideoExtractor.kt`, `Net.kt`, `PlayerHolder.kt`)**:
  - Ported the `VideoLinkFinder v2.2` link-probing, cookie/header injection, and stream-extraction engine directly into MovieBox's native ExoPlayer pipeline so clicking **Play** opens the player and plays immediately without opening any browser UI.
  - **Automatic Gofile `accountToken` & Cookie Injection**: Automatically fetches a Gofile guest `accountToken` (`https://api.gofile.io/accounts`) and attaches `Cookie: accountToken=<token>`, `Authorization: Bearer <token>`, `Referer: https://gofile.io/`, `Origin: https://gofile.io`, and a Chrome Mobile `User-Agent` to `OkHttpDataSource` + `ResolvingDataSource` so `https://file-*.gofile.io/download/web/.../*.mkv` and `https://gofile.io/d/...` links stream and download directly.
  - **Invisible Headless WebView Fallback**: If a stream host requires JavaScript execution to set session cookies or emit the underlying `.mkv` / `.mp4` / `.m3u8` URL, `DirectStreamResolver` resolves it silently in the background while the player buffers.
  - **DNS-over-HTTPS Fallback (`SwitchableDns`)**: Automatically falls back to Cloudflare (`1.1.1.1`), Google, and Quad9 DoH if the carrier/ISP DNS blocks a video host.
- **All v23.0 & v18.0 Features and UI 100% Preserved**:
  - 3-Step Animated Welcome Showcase (`SplashScreen.kt`), Live Views & Downloads Analytics, 5-Digit Share Code Redeem, VIP Access Gating, Encrypted `.mbx` Offline Downloads, and Full Admin Panel.
