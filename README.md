# Archive TV

A locked-down Internet Archive browser for Android TV (Android 11+), dark UI, full remote support.

## Build
1. Install Android Studio (Hedgehog or newer; JDK 17 is bundled).
2. File > Open > select the `ArchiveTV` folder and let Gradle sync.
3. Build > Build APK(s). Output: `app/build/outputs/apk/debug/app-debug.apk`

## Install on the TV
TV: Settings > Device Preferences > About > tap "Build" 7 times, then enable Developer options > Network/USB debugging.
PC: `adb connect <TV_IP>:5555` then `adb install app-debug.apk`
(Or put the APK on a USB stick and open it with a file manager app.)

## Remote controls
- D-pad: move pointer (push at a screen edge to scroll)
- OK: click. Hold OK / Menu / Info: toolbar
- Back: page back (twice at first page to exit); closes toolbar / fullscreen video
- Play/Pause/Stop, FF/Rewind, Next/Prev: control audio/video (+-10s / +-30s)
- Fullscreen video: Left/Right seek 10s, OK play/pause, Back exits
- Channel +/- or Page Up/Down: scroll a page
- Red/Green/Yellow/Blue: Home / Reload / Toggle cursor / Search

Allowed sites: edit ALLOWED_HOSTS in MainActivity.kt.
