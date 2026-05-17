# Parental Control Android Application

A secure, lightweight native Android application engineered for parental device control, application locking, and settings protection. Uses a zero-latency WindowManager overlay, instant Settings blocking, and Device Admin anti-uninstall to create an impenetrable lock system.

## Visual Identity

<p align="center">
  <img src="images/parental_control_app_icon.png" width="22%" alt="Launcher Icon" />
  <img src="images/1.jpeg" width="22%" alt="Dashboard Screen" />
  <img src="images/2.jpeg" width="22%" alt="App Blocker Screen" />
  <img src="images/3.jpeg" width="22%" alt="Permissions Setup" />
</p>

---

## How It Works

The app uses a three-layer protection model that operates entirely on-device with no network dependency:

### Layer 1: Instant Settings & Installer Blocking
When the child opens **Settings** or the **Package Installer**, the accessibility service detects it and instantly fires `GLOBAL_ACTION_BACK`. This unwinds their navigation stack before they can tap anything, making it impossible to reach the "Uninstall" or "Disable Accessibility" screens. No overlay, no delay, no brute-force window.

### Layer 2: Zero-Latency PIN Overlay for Blocked Apps
When a blocked app (e.g., YouTube, Games) is opened, a pre-inflated `WindowManager` overlay draws a full-screen PIN lock in under 10 milliseconds. Because it's drawn by the accessibility service itself (not as a separate Activity), it cannot be swiped away from the Recent Apps menu. Correct PIN entry unlocks the app for the current session with a 15-second grace period for parent access.

### Layer 3: Device Admin Anti-Uninstall
The app registers as a Device Administrator, which prevents standard uninstallation. Combined with the Settings blocking (Layer 1), the child cannot deactivate this protection.

### Protection Shield Toggle
A master on/off switch in the dashboard lets parents temporarily disable all blocking without touching Android Accessibility settings. When OFF, the service runs but blocks nothing. When ON, full protection is active.

---

## Key Capabilities

### Protection Shield Toggle
* **One-Tap Control**: Enable or disable all blocking from the dashboard instantly.
* **State Persistence**: Toggle state survives app restarts and screen reboots.
* **Visual Feedback**: Green status text when active, red when disabled.

### Zero-Latency WindowManager Overlay
* **Pre-Inflated View**: The PIN screen is built in memory when the service starts, not when an app opens.
* **TYPE_ACCESSIBILITY_OVERLAY**: Draws above all other windows without requiring the "Display over other apps" permission prompt.
* **Auto-Relock**: Screen sleep clears all unlocked sessions automatically.

### Installed Package Discovery (Android 11+ Visibility)
* Resolves package visibility sandbox restrictions on API level 30+.
* Declares `QUERY_ALL_PACKAGES` permission to scan and list all installed third-party apps for blocking.

### Time-Based Scheduling
* Set custom blocked time windows per app (e.g., block games 8 AM – 3 PM on school days).
* Supports overnight ranges (e.g., 9 PM – 7 AM).
* Overlap detection prevents conflicting schedules.

### Secure Offline Keystore Generation
* Automated generator utility builds self-signed production keys locally using OpenSSL or Java Keytool.
* Dynamic Gradle integration loads credentials from isolated properties only if present.

---

## Security Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    App Launch Flow                       │
├─────────────────────────────────────────────────────────┤
│ App opens → PinActivity (MODE_UNLOCK) → Dashboard        │
│ (PIN required every time the app is launched)             │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│                  Protection Flow                         │
├─────────────────────────────────────────────────────────┤
│ Settings/Installer opened → GLOBAL_ACTION_BACK (instant) │
│ Blocked app opened → PIN overlay → Unlock → 15s grace    │
│ Protection Shield OFF → All blocking disabled             │
│ Screen off → All sessions cleared                         │
│ Back button on overlay → Home screen                      │
└─────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────┐
│                  Anti-Uninstall Flow                     │
├─────────────────────────────────────────────────────────┤
│ Device Admin active → Uninstall button grayed out        │
│ Settings blocked → Cannot reach Device Admin settings    │
│ Accessibility blocked → Cannot disable the service       │
└─────────────────────────────────────────────────────────┘
```

---

## Directory Architecture

* `app/` - Primary application module containing Kotlin sources, XML layouts, and assets.
* `images/` - Visual assets, launcher mockups, and screenshots.
* `artifacts/` - Output target for generated debug and release APK packages (ignored by Git).
* `.github/workflows/` - CI/CD workflow definitions for GitHub Actions.
* `build.sh` - Sandboxed build script running compilations inside isolated Docker containers.
* `generate_keystore.py` - Automation utility generating keystores, properties files, and base64 strings.

---

## First-Time Setup

1. **Install the APK** on the child's device via ADB or direct transfer.
2. **Open the app** — you'll be prompted to create a 6-digit PIN.
3. **Enable Accessibility Service** — tap the "Service Node" badge in the dashboard, find "Parental Control" in the list, and toggle it on.
4. **Enable Device Admin** — tap the "Uninstall Lock" badge and confirm the system prompt.
5. **Toggle Protection Shield ON** — use the switch in the dashboard to activate all blocking.

> **Important**: Both Accessibility and Device Admin must be enabled for full protection. The dashboard shows green indicators when both are active.

---

## Local Compilation and Build Pipeline

All compilations run inside an isolated Docker container, eliminating the need for a local JDK or Android SDK.

### Prerequisites
* Docker engine installed and running.
* ADB command-line utility for device installation.

### 1. Compile Unsigned Debug Package
```bash
./build.sh
```
* **Output**: `artifacts/app-debug.apk`

### 2. Compile Signed Release Package
```bash
./generate_keystore.py
./build.sh --release
```
* **Output**: `artifacts/app-release.apk`

---

## Secure Keystore Setup and Git Protection

### Automated Credentials Utility
```bash
./generate_keystore.py
```
Generates a secure password, creates the keystore, builds `keystore.properties`, and outputs the Base64 string for CI/CD.

### Git Security
`release.keystore` and `keystore.properties` are excluded from Git tracking via `.gitignore`. Never commit these files.

---

## Continuous Integration and Deployment (GitHub Actions)

A pre-configured CI/CD workflow at `.github/workflows/android.yml` compiles and signs production APKs on every push.

### Required Repository Secrets

| Secret Name | Value Source | Description |
| :--- | :--- | :--- |
| `KEYSTORE_BASE64` | `generate_keystore.py` output | Base64 string of the binary keystore. |
| `KEYSTORE_PASSWORD` | `generate_keystore.py` output | Master keystore password. |
| `KEY_ALIAS` | `parentalcontrol-alias` | Key alias within the keystore. |
| `KEY_PASSWORD` | `generate_keystore.py` output | Key-specific password. |

Signed APKs are published to the **Releases** section (mapped to the `latest` tag).

---

## Physical Device Installation

```bash
# Debug APK
adb install artifacts/app-debug.apk

# Signed Release APK
adb install artifacts/app-release.apk
```

---

## PIN Recovery (Forgot PIN)

Clear the app data via ADB to reset the PIN:

```bash
adb shell pm clear com.parentalcontrol
```

This wipes the stored PIN hash, blocklist, and all settings. The next launch prompts you to create a new 6-digit PIN.

**Note:** This also resets the Accessibility permission. Re-enable it:
1. Go to **Settings → Accessibility → Parental Control**
2. Toggle the service back on
3. Confirm in the system dialog

---

## Package Name

`com.parentalcontrol`
