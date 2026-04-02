# MISS Minimal

**A distraction-free Android launcher. Text only. No icons. Pure focus.**

Built by **THE YV🖤**
&nbsp;
[![linktr.ee/yv_3000](https://img.shields.io/badge/linktree-yv__3000-39E09B?style=flat&logo=linktree&logoColor=white)](https://linktr.ee/yv_3000)
[![License: MIT](https://img.shields.io/badge/License-MIT-white.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android%208.0%2B-black.svg)](https://developer.android.com)
[![Open Source](https://img.shields.io/badge/open--source-yes-brightgreen)](https://github.com)

---

## What is MISS Minimal?

MISS Minimal is a minimalist Android home screen launcher that strips your phone down to its bare essentials. No app icons. No colorful UI. No distractions. Just text — clean, monospace, black and white.

The idea is simple: most of the time you unlock your phone, you don't actually need it. The moment you see a colorful grid of app icons, your brain starts wandering. MISS Minimal removes that trigger. You see only what you put there — five apps max on your home screen, listed as plain text.

Everything still works underneath. Your apps are all there in the drawer. Your notifications still come in. But the visual noise is gone — and with it, a surprising amount of mindless scrolling.

---

## Features

### Home Screen
- Displays current time in large, lightweight monospace font
- Date shown below in clean format
- 5 app slots — Phone and Camera fixed, 3 fully customizable
- Long press any user slot to change or remove an app
- Swipe up to open App Drawer
- FOCUS and TIME toggles at the bottom

### App Drawer
- Full list of all installed apps — text only, alphabetically sorted
- Real-time search bar at the top
- Long press any app for a context menu:
  - Add to Home Screen (max 3 user slots)
  - Set Time Limit (1 min / 5 min / 10 min / 15 min / Custom)
  - Rename app label
  - App Info
  - Uninstall
- Settings gear at the bottom-right

### Quick Settings (Swipe Down from Right)
- Toggle WiFi, Bluetooth, Mobile Data
- Toggle DND, Flashlight
- Brightness slider with Auto button
- Volume slider
- Silent / Vibration mode toggles
- Active state shown with subtle blue highlight

### Focus Mode
- **Stopwatch** — Start, Pause, Resume, Stop
- **Timer** — Scroll-wheel picker (HH:MM:SS), Start, Pause, Resume, Stop. Vibrates on completion
- **Strict Mode** — 25-minute full lockdown:
  - Silences ALL calls, messages and notifications via DND
  - Disables camera via Device Admin
  - Blocks system notification panel
  - Countdown shown fullscreen — cannot be cancelled
  - Vibrates when session ends

### App Time Limits
- Long press any app → Set Time Limit
- Choose 1 / 5 / 10 / 15 minutes or enter custom duration
- When time is up, launcher automatically brings you back to home screen

### Launcher Settings
- Toggle WiFi, Bluetooth, Mobile Data directly
- DND, Flashlight, Brightness, Volume controls
- Font size adjustment (8sp – 20sp) applied across the entire launcher
- Built by / Terms & Conditions section

### Exit
- EXIT button on home screen → takes you to Home Settings to switch back to your system launcher
- (Currently, there is an issue, so it might not work on some phones.)

---

## Screenshots

> *Add your screenshots here after uploading to the repo*

| Home Screen | App Drawer | Quick Settings | Focus Mode | Strict Mode |
|:-----------:|:----------:|:--------------:|:----------:|:-----------:|
| *soon* | *soon* | *soon* | *soon* | *soon* |

---

## Download

### Option 1 — Direct APK (Recommended for most users)

1. Go to [**Releases**](../../releases) on this GitHub page
2. Download the latest `MISS-Minimal-v1.0.apk`
3. On your Android phone:
   - Open the downloaded file
   - If prompted, allow installation from unknown sources
   - Tap Install
4. After install, press your Home button
5. Select **MISS Minimal** → tap **Always**

### Option 2 — Build from Source

Requirements:
- Android Studio (latest stable)
- Android SDK (API 26+)
- Java 17 or higher

```bash
git clone https://github.com/YOUR_USERNAME/MISS-Minimal.git
```

Open in Android Studio → wait for Gradle sync → connect your phone via USB → tap Run ▶

---

## Permissions & Why They're Needed

MISS Minimal requests several permissions. Here is exactly what each one does — nothing more, nothing less:

| Permission | Why |
|---|---|
| `QUERY_ALL_PACKAGES` | To list all your installed apps in the drawer |
| `SYSTEM_ALERT_WINDOW` | To block the system notification panel while launcher is active |
| `ACCESS_NOTIFICATION_POLICY` | To silence all calls/notifications during Strict Mode |
| `BIND_NOTIFICATION_LISTENER_SERVICE` | To read notification count per app |
| `CAMERA` | To open Camera from the home screen |
| `MODIFY_AUDIO_SETTINGS` | For volume control in quick settings |
| `WRITE_SETTINGS` | For brightness control |
| `BLUETOOTH_CONNECT` | For Bluetooth toggle |
| `RECEIVE_BOOT_COMPLETED` | So launcher starts automatically after reboot |
| `VIBRATE` | For timer completion and Strict Mode alerts |
| `BIND_DEVICE_ADMIN` | To disable camera during Strict Mode only |
| `BIND_ACCESSIBILITY_SERVICE` | To dismiss system panel when launcher is active |

**This app does not collect, transmit, or store any personal data. No analytics. No internet. No servers. Everything stays on your device.**

---

## Privacy

- Zero data collection
- No network requests
- No third-party SDKs
- No ads, ever
- Fully offline
- Open source — every line of code is visible here

---

## Tech Stack

- **Language:** Kotlin
- **Min SDK:** API 26 (Android 8.0)
- **Target SDK:** API 34 (Android 14)
- **Architecture:** Single-module Android app
- **UI:** XML layouts, ConstraintLayout, RecyclerView
- **Storage:** SharedPreferences (local only)
- **No external dependencies** beyond standard AndroidX libraries

---

## Built By

**THE YV🖤**

Designer. Builder. Minimalist.

🔗 [linktr.ee/yv_3000](https://linktr.ee/yv_3000)

---

## License

```
MIT License

Copyright (c) 2026 THE YV

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

---

*MISS Minimal — because your phone should work for you, not against you.*

YV🖤 ~ I EXPECT NOTHING FROM YOU...
