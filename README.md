# RIGAN-FLASHLIGHT-PROJECTOR
 RIGAN FLASHLIGHT PROJECTOR, engineered for Android 6.0 (API 23) through modern Android devices with lightweight XML + ViewBinding architecture, real CameraManager hardware torch control, animated futuristic UI, and an immersive Canvas-based full-screen visual projector simulation.


## Live modes and Gallery video

- **Live Color Light:** full-screen animated color patterns with a user-controlled rear LED torch toggle. The rear LED is turned off when the activity leaves the foreground.
- **Live Camera:** rear-camera live preview using CameraX, with an in-preview LED torch control where the device supports flash.
- **Gallery Video Zoom:** choose a local video through Android's document picker, play it full-screen with media controls, pinch to zoom up to 3x, and reset zoom.
- Camera access is requested only when a camera feature is opened. Gallery selection uses Android's document picker and does not request broad storage access.
- These are screen/flashlight features, not optical video projection. A phone's LED cannot project video onto a wall without separate projector optics.
- Camera preview and high screen brightness can consume battery. The app releases camera use and stops video playback when the corresponding screen leaves the foreground.
