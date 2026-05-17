# 🛡️ Parental Control Android Application

A premium, secure, and modern Android Parental Control application. 

This repository is structured for native Kotlin/Java development on Android, featuring a secure **SHA-256 parent credential lock** and real-time **system accessibility service** state monitoring.

---

## 🏗️ Technical Stack & Architecture

- ☕ **Language**: Kotlin
- 🎨 **Theme**: Modern charcoal dark-mode (`#121212`) with customized glassmorphic card overlays
- 🔒 **Security**: SHA-256 PIN hashing stored locally via `SharedPreferences`
- 🤖 **Parental Service**: Dedicated `AccessibilityService` hook to monitor application focus changes

---

## 🛠️ Automated Sandbox Compiler (Docker-based)

To avoid installing Java JDK 17, Android SDK platforms, build-tools, or Gradle on your physical machine, we have packaged an **automated, self-contained compiler pipeline**.

This pipeline compiles your code in an isolated temporary Docker container (with zero local mounts or port binds), extracts the compiled APK, and tidies up after itself.

### Prerequisites
Make sure you have **Docker** installed and running on your host system:
```bash
docker --version
```

### ⚡ Compile and Extract the APK in One Command
Simply run the helper script from your terminal:
```bash
./build.sh
```

### What this script does:
1. Builds a secure, sandboxed builder image (`Dockerfile.build`) containing the exact required versions of JDK 17 and Android SDK.
2. Compiles your complete Android app inside the container and generates the debug APK.
3. Automatically extracts the `.apk` and copies it directly to your local **`artifacts`** folder:
   - File path: [`artifacts/app-debug.apk`](file:///home/r3versein/Documents/Projects/parentalcontrol/artifacts/app-debug.apk)
4. Deletes the temporary container to keep your system clean.

---

## 🔌 How to Deploy the APK to Your Android Phone

1. Enable **USB Debugging** on your phone (Settings -> About Phone -> Tap *Build Number* 7 times. Go to Developer Options -> enable **USB Debugging**).
2. Plug your phone into your computer via a USB cable.
3. Install the newly compiled APK directly onto your phone:
   ```bash
   adb install artifacts/app-debug.apk
   ```
4. Open the app on your phone and set up your master security PIN!
