# RIGAN Flashlight Projector

Android application project built with Kotlin and AndroidX Media3.

## Features
- Select and play a video from the phone using the Android document picker.
- Pinch-to-zoom and Zoom In/Out controls (1.0x to 3.0x) for in-app video.
- Real device flashlight toggle where supported, with runtime permission.
- Opens YouTube separately. YouTube videos are not imported or zoomed inside this app because playback, DRM, and platform restrictions may prevent that.

## Build
Open this folder in Android Studio, allow Gradle sync, then build the debug APK. It is generated at app/build/outputs/apk/debug/app-debug.apk.

GitHub Actions also builds a debug APK on pushes and uploads it as a workflow artifact.

## Important limitation
A phone flashlight LED cannot project a video image onto a wall. This app provides video playback and zoom on the phone screen and a real flashlight toggle. Actual wall projection requires external projector hardware.
