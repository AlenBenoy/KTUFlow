---
name: Java KTU Scholar structure
description: Explains the Java project layout in java-apps/ and how desktop/Android share ktu-core.
---

## Rule
ktu-core is a pure Java 17 library (no Android APIs). Desktop uses it directly via Gradle composite include. Android uses it via ktu-core.jar copied to ktu-android/app/libs/.

**Why:** Gradle cannot include an Android project as a subproject of a Java-only root. The JAR copy approach avoids a separate Maven repo while keeping the logic shared.

**How to apply:** After changing ktu-core, run `./gradlew :ktu-core:jar` then copy `ktu-core/build/libs/ktu-core.jar` → `ktu-android/app/libs/` before building the Android project.

## Desktop build command
```
cd java-apps && ./gradlew :ktu-desktop:shadowJar
java -jar ktu-desktop/build/libs/ktu-desktop-all.jar
```

## Android project
ktu-android/ is a separate Android Studio project (its own settings.gradle). Open it in Android Studio, put ktu-core.jar in app/libs/, then Build → Generate APK.

## Passwords encryption
Both desktop and Android encrypt credentials with AES-256-GCM + PBKDF2. Desktop uses OS username as master password; Android uses Android ID from Settings.Secure. Neither stores plaintext anywhere.

## Demo data fallback
KtuPortalService, EzygoService, AnnouncementService all catch any exception and return hardcoded demo data. This means the app is always usable even when the portals are down.
