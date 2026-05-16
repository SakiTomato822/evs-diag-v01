# EVS Diag V0.1

Root-only Android diagnostic app for validating EVS/AVM frame capture on automotive Android head units.

## Repository Description

EVS Diag V0.1 is a small Android diagnostic tool designed to test whether frames from an automotive EVS/AVM rendering path can be captured, hashed, converted to PNG, and reproduced consistently.

The current target environment is a rooted/userdebug Android-based head unit where the EVS HAL process exposes AVM-related DMA buffer file descriptors through `/proc/<pid>/fd`.

## Current Scope

- Locate the EVS HAL process.
- Detect `video*cif` usage from the target process.
- Enumerate DMA buffer file descriptors owned by the EVS HAL process.
- Capture a small number of raw frames from selected DMA buffer descriptors.
- Compute SHA-256 hashes for captured frames.
- Convert captured UYVY frames to PNG.
- Preview captured PNG files inside the app.
- Save raw frames, PNG previews, and a text summary for later inspection.

## Non-Goals

- This is not a DVR implementation.
- This does not perform continuous video recording.
- This does not replace or modify the factory EVS HAL.
- This does not change system configuration.
- This does not attempt to access raw multi-camera sensor streams.
- This does not bypass Android security on production devices.

## Default Capture Parameters

- Target process: `android.hardware.automotive.evs@1.0-service`
- Frame format: `UYVY`
- Width: `1920`
- Height: `896`
- Frame count: `10`
- Interval: `500 ms`

## Output

Each diagnostic session writes files under the app-specific external storage directory:

```text
Android/data/com.lynk.evsdiag/files/evs_diag_v01/<timestamp>/
```

Session contents:

```text
raw/          Captured raw frame buffers
png/          PNG previews converted from raw frames
summary.txt   Capture metadata, hashes, and basic frame checks
```

## Build

Open the project with Android Studio and build the `app` module.

The repository also includes a GitHub Actions workflow:

```text
.github/workflows/build-debug-apk.yml
```

To build from GitHub:

1. Open the repository on GitHub.
2. Go to `Actions`.
3. Run `Build Debug APK`.
4. Download the generated APK artifact.

## Runtime Requirements

- Android head unit with root access.
- `su` available to the app process.
- EVS/AVM must be active before starting capture.
- The target EVS process must expose readable DMA buffer descriptors through `/proc/<pid>/fd`.

## Safety Notes

This tool is intended for diagnostics on owned test devices only. It captures only a small number of frames and does not intentionally modify system files, kill processes, replace vendor binaries, or change vehicle configuration.
