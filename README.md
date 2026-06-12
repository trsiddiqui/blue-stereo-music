# Blue Stereo Music

Blue Stereo Music is a native Android music player built for landscape car head units.
It scans local device storage for audio, plays songs directly from the Android media
library, and presents the controls as a premium in-car media cockpit.

## Features implemented

- Local MediaStore scan for popular audio formats including MP3, M4A, AAC, WAV, FLAC, OGG, OPUS, WMA, AMR, MP4, M4B, MIDI, AIF, AIFF, and APE entries exposed by Android.
- Native local playback with play/pause, previous, next, 10-second rewind, 10-second forward, seek/progress, shuffle, repeat all, and repeat one.
- Favourites persisted on device, a large favourite button on the main player, and a Play Favourites flow.
- Random playlist generation from the scanned local library.
- Library, Favourites, and Queue browser with search.
- 10-band equalizer UI connected to Android `Equalizer`, plus bass, mid, and treble cabin controls.
- Loudness enhancer and Max output mode. Max output raises the music stream to the device maximum and enables the loudness enhancer.
- Landscape-first luxury car UI with large touch targets and high-contrast typography.

## Build APK

1. Open this repository in Android Studio.
2. Let Gradle sync and install Android SDK 35 if prompted.
3. Build > Build Bundle(s) / APK(s) > Build APK(s).
4. Install the generated APK on the Android car stereo/head unit.

From a configured command line, run:

```powershell
gradle assembleDebug
```

## Permissions

The app requests audio-library access so it can scan songs in local device storage:

- Android 13 and newer: `READ_MEDIA_AUDIO`
- Android 12L and older: `READ_EXTERNAL_STORAGE`

The app also requests `MODIFY_AUDIO_SETTINGS` for max-output audio behavior.

## Notes

- Android only exposes media that is indexed by MediaStore. If files do not appear, confirm they are in a normal music/media folder and that the head unit has indexed them.
- EQ and loudness effects depend on the audio effect support provided by the device firmware. Some low-cost head units may ignore individual audio-effect controls.
