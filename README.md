# Recordly Mobile (unofficial Android port)

Desktop app [Recordly](https://recordly.dev/) se inspired native Android screen recorder + editor.
Kotlin + Jetpack Compose, minSdk 29 (Android 10+).

## APK kaise banaye

### Option A: GitHub Actions (sabse aasan, kuch install nahi karna)
1. Is folder ko ek naye GitHub repo me push karo (branch `main`).
2. Repo ka **Actions** tab kholo → **Build APK** workflow chalegi (ya "Run workflow" dabao).
3. Run khatam hone par **Artifacts → RecordlyMobile-apk** download karo, zip kholo, `app-release.apk` phone me install karo
   (Settings → "Install unknown apps" allow karna padega).

### Option B: Android Studio
Folder ko Android Studio me kholo → Build → Build APK(s). `app/build/outputs/apk/release/` me APK milegi.

### Option C: Command line
JDK 17 + Android SDK + Gradle 8.9 ke saath: `gradle assembleRelease`

APK debug keystore se signed hai, sirf personal/testing use ke liye. Play Store ke liye apni keystore se sign karo.

## Features

| Feature | Status |
|---|---|
| Screen recording (MediaProjection, H.264 MP4, 720p/1080p/1440p, 30/60 fps) | Done |
| Microphone audio | Done |
| System (internal) audio | **Nahi hai** (alag AudioPlaybackCapture + muxing pipeline chahiye) |
| Webcam bubble (draggable, tap = bada/chhota, recording me dikhta hai) | Done |
| Countdown (3/5 sec) | Done |
| Tap-based auto-zoom (smooth in/out, latest tap ki taraf focus) | Done |
| Tap ripples | Done |
| Editor: trim, speed (0.5x–2x), gradient background + padding + rounded corners | Done |
| Export MP4 (Media3 Transformer) + gallery me save + share | Done |
| Library (thumbnails, share, delete) | Done |
| GIF export, `.recordly` project files, audio/webcam import | Nahi hai |

## Zaroori baatein

- **Mobile par cursor nahi hota.** Desktop Recordly ka cursor-follow zoom yaha taps par based hai.
  Taps ek Accessibility Service (`Recordly tap tracker`) se aate hain jo sirf recording ke dauran
  click events ki on-screen position note karta hai. Isko Settings → Accessibility me on karna hota hai.
  Jo apps custom touch handling (games, canvas) use karte hain unme click events nahi aate.
- Webcam bubble aur countdown ke liye "Display over other apps" permission chahiye.
- Editor preview raw video dikhata hai; zoom/background/ripples export ke baad dikhte hain.
- Ye code is environment me compile nahi ho saka (Android SDK download blocked tha). Kotlin syntax check
  pass hai, lekin pehli baar build karne par chhote API/version errors aa sakte hain. Build log bhejoge to turant fix kar dunga.
- Ye unofficial hai. Upstream Recordly ka naam/branding/code use karne se pehle unka license check kar lo,
  khaaskar agar app publish karni ho.
