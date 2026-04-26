<p align="center">
  <img src="app/src/main/res/drawable/app_icon.png" alt="IRShark logo" width="140" />
</p>

<h1 align="center">IRShark</h1>

<p align="center">
  Android app for IR device control, testing codes from the Flipper IRDB, and building custom automation macros.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white" alt="Android" />
  <img src="https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin" />
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white" alt="Jetpack Compose" />
  <img src="https://img.shields.io/badge/minSdk-36-2E7D32" alt="minSdk 36" />
  <img src="https://img.shields.io/badge/IR-Parsed%20%2B%20Raw-0A0A0A" alt="IR parsed and raw" />
</p>

## 📱 About

IRShark is a practical IR remote toolbox for Android. It helps you:

- control devices via your phone's built-in IR blaster
- use a large profile database from Flipper IRDB
- test compatible codes step by step (IR Finder)
- save your own remotes and button layouts
- build macros (automated IR command sequences)

The project is designed as a practical-first tool: fast signal sending, clear profile navigation, and a smooth workflow for real-world hardware testing.

## 📡 Supported IR Protocols

IRShark currently encodes and transmits these parsed protocols:

- NEC
- NECext
- NEC42
- Samsung
- Samsung32
- RC5
- RC5X
- RC6
- SIRC (12-bit)
- SIRC15
- SIRC20
- Kaseikyo
- RCA
- Pioneer

It also supports RAW timing payloads.

## 🐬 Flipper IR DB

IRShark uses a bundled Flipper IRDB copy in assets:

- Community repository: https://github.com/Lucaslhm/Flipper-IRDB

This gives the app broad brand/device coverage without manual code entry.

## 🧭 App Sections

Main sections in the app:

- Home
- Universal Remote
: quickly sends common commands across multiple profiles in a category
- My Remotes
: your saved remotes, custom button mappings, favorites
- Remote DB
: browse the built-in IR profile database
- Remote Control
: control screen for a specific remote
- IR Finder
: guided workflow to find working codes based on device response
- Macros
: macro list
- Macro Editor
: create/edit block-based command sequences
- Running Macro
: live macro run screen with transmitted signal log
- Settings

## ✨ Key Features

- protocol label shown directly on remote buttons
- protocol shown in the macro transmission table during runtime
- import/export support for remote and macro JSON files
- category-based navigation with device icons
- haptic feedback and visual TX activity indicator

## 🛠️ Tech Stack

- Kotlin + Jetpack Compose
- Material 3
- Gradle Kotlin DSL
- Android ConsumerIrManager for IR transmission

## 🚀 Build & Run

Requirements:

- Android Studio (recent version with Compose support)
- Android SDK 36
- a device with an IR blaster for real transmission

Compile:

```bash
./gradlew :app:compileDebugKotlin
```

Build debug APK:

```bash
./gradlew :app:assembleDebug
```

## 🧪 Usage Notes

- some commands may need repeats or a longer press depending on device behavior
- real-world compatibility depends on the quality of your phone's IR emitter
- for reliable validation, test both decode output (e.g. with Flipper) and real target-device response

## 🗂️ Project Structure

- app
: Android app source code
- app/src/main/assets/flipper_irdb
: local copy of the Flipper IR database
- gradle, build skripty
: build configuration

## 📌 Project Status

Active development focused on protocol reliability, practical usability, and fast IR testing workflows.