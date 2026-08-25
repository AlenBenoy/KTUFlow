# KTU Scholar — Java Edition

Multi-module Gradle project for Windows (Swing) + Android (Java).

## Project Structure

```
java-apps/
├── ktu-core/           Pure Java shared library (models, DB, services)
├── ktu-desktop/        Java Swing + FlatLaf desktop app (Windows/macOS/Linux)
└── ktu-android/        Android Java app (API 26+)
```

## Prerequisites

- **JDK 17+** (Eclipse Temurin or Oracle)
- **Android Studio Hedgehog or newer** (for the Android app)
- **Gradle 8.6** (wrapper included)

## Building

### Desktop App (Windows)

```bash
cd java-apps
./gradlew ktu-desktop:shadowJar
# Output: ktu-desktop/build/libs/ktu-desktop-all.jar
java -jar ktu-desktop/build/libs/ktu-desktop-all.jar
```

### Android App

Open `java-apps/ktu-android/` as a project in Android Studio and run on a device or emulator.

## Architecture

```
ktu-core
  ├── model/          Data classes (Profile, Todo, SubjectResult, etc.)
  ├── db/             SQLite via JDBC (desktop) / abstracted for Android
  ├── service/        Business logic (KTU scraper, EzyGo, grade calc)
  └── util/           Crypto, date helpers

ktu-desktop
  ├── MainFrame       Tabbed sidebar navigation
  └── panels/         One panel per feature tab

ktu-android
  ├── MainActivity    Bottom nav host
  └── fragments/      One fragment per feature
```

## Notes on KTU Portal & EzyGo Scraping

- Results/attendance scraping uses Jsoup + OkHttp (same in both apps).
- KTU portal login submits a form to `https://app.ktu.edu.in`.
- EzyGo is a SPA; the service hits its REST API with a JWT token obtained via login.
- When the portal is unreachable or its HTML changes, the app shows an explicit
  sync error. It never substitutes fabricated student results or attendance.

## Credentials Storage

Passwords are encrypted with AES-256-GCM before being stored in SQLite.
The key is derived from a device-specific salt using PBKDF2.
