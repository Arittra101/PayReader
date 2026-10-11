# PayReader

PayReader is an Android app built in Kotlin that listens for incoming bKash payment SMS messages, parses transaction details, stores them locally, and uploads valid records to Supabase for later review and processing.

It is designed for a single dedicated Android device used by an operator to monitor payment alerts without a full login system or multi-user workflow.

## What it does

- Detects incoming bKash receive SMS messages
- Filters and parses transaction fields such as TrxID, amount, sender, and timestamp
- Saves every parsed message locally in Room before uploading
- Recovers missed SMS messages with an inbox catch-up scan
- Uploads pending records through WorkManager using Supabase
- Shows transaction status in a Compose-based single-screen dashboard
- Handles retry and background sync reliability for a dedicated device setup

## Key features

- SMS capture via manifest declared broadcast receiver
- Local-first storage with Room
- Background uploads with WorkManager and retry policy
- Catch-up scan for missed messages after force-stop, reboot, or app restarts
- Foreground keep-alive service for better reliability on Android devices
- Jetpack Compose UI with a compact status bar and transaction tabs
- Supabase integration using Ktor and anon-key access with RLS-friendly design

## Tech stack

- Kotlin
- Android SDK / Jetpack Compose
- Room database
- Koin for dependency injection
- Ktor for Supabase API calls
- WorkManager for background processing
- Android SMS permissions and system services

## Project structure

```text
PayReader/
├── app/
│   ├── src/main/java/com/example/noticereader/
│   ├── src/main/res/
│   ├── src/test/
│   ├── build.gradle.kts
│   └── proguard-rules.pro
├── gradle/
├── build.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
├── settings.gradle.kts
├── keystore.properties.example
├── requirment.md
├── TECHNICAL_OVERVIEW.md
├── PHASE5_RUNBOOK.md
└── README.md
```

## App flow

1. The app listens for incoming bKash SMS messages.
2. The message is filtered and parsed.
3. A valid transaction is saved to the local database as the source of truth.
4. A background upload worker sends it to Supabase when connectivity is available.
5. If the app missed any messages while idle, the catch-up scan scans the inbox and imports them later.

## Setup

### 1. Prerequisites

- Android Studio
- JDK 11+
- Android SDK with API level 26+ support
- A Supabase project with a table for transaction records

### 2. Local configuration

Create a `local.properties` file at the project root with your Supabase values:

```properties
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_ANON_KEY=your-anon-key
```

This file is intentionally local-only and should not be committed.

### 3. Build and run

```bash
./gradlew assembleDebug
```

Then run the app from Android Studio on a physical Android device.

### 4. Release signing

For release builds, copy the example file and fill in the keystore details:

```bash
cp keystore.properties.example keystore.properties
```

Then update the values with your signing certificate information.

## Important device requirements

This app is intended for a dedicated Android device and is not a general consumer app. Device setup matters for reliability:

- Battery optimization disabled for the app
- Autostart enabled on supported OEM devices
- Unlock the phone after reboot if needed
- Open the app once after install so it is not left in stopped state

## Security notes

- The app uses the Supabase anon key, not the service role key.
- Row Level Security should be configured so the anon role can insert records but not read or delete them.
- Payment messages are treated as signals, not proof of payment; the app is a capture tool rather than an authorization system.

## Notes

This repository also contains project planning and technical guidance documents:

- `requirment.md` — product requirements and implementation rules
- `TECHNICAL_OVERVIEW.md` — architecture and engineering guidance
- `PHASE5_RUNBOOK.md` — operational and testing instructions

## License

This project does not currently declare a repository license. Add one if you intend to distribute or publish the code.

## Status

This repo is a Kotlin Android application focused on bKash SMS transaction extraction and backend synchronization for a single-device operational workflow.
