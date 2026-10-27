# Validation — MelaReady 0.3.0 source handoff

Recorded 27 October 2026.

## Current automated evidence

- Python server: 4 real HTTP integration tests pass.
- Covered: two-account invite joining, shared task acknowledgement, expiring/removable location, private contact visibility, voice-clip metadata and expired-invite rejection.
- `server.py` and `test_server.py` pass Python compilation.
- Shared-workspace JavaScript passes Node syntax checking.
- Android manifest XML, source inventory and package archive structure pass static checks.

## Android build status

The v0.1 Android baseline previously passed assemble, five domain tests and lint, and the user confirmed that baseline app worked. The v0.3 Android build was not compiled in this creation environment because the Gradle 8.13 distribution was absent and network policy blocked its official download. Open v0.3 in Android Studio with JDK 17 and run:

```text
gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Do not describe v0.3 as a verified APK until these tasks and the device checklist pass.

## Configuration

- Package: `com.tripathitechnologies.melaready`
- Version: `0.3.0` / code `3`
- Minimum SDK 26; compile and target SDK 36
- Gradle 8.13, AGP 8.13.2, Kotlin 2.2.21, JDK 17
- Permissions: internet, coarse/fine location and microphone. No contacts, camera, broad storage or background-location permission.
- Profile photo uses Android's system document picker rather than storage permission.

## Device checks still required

- Install/launch, five-tab navigation, Hindi/English, rotation, large text and accessibility.
- Permission denied/allowed/revoked behaviour for location and microphone.
- Two phones: persistent login, event create/join, role assignment, task and report loops.
- Profile photo selection and contact opt-in privacy.
- Fresh location share, five-minute expiry and manual removal visible on the other phone.
- Short voice recording, auto-stop, upload, playback, microphone release and app backgrounding.
- Server unavailable/restart/reconnect, form retention and duplicate submission behaviour.
- Encrypted local-workspace persistence and corruption handling.

## Limits

The bundled server is for a trusted local hackathon trial. Server SQLite data and voice blobs are not encrypted at rest. There is no verified email, password recovery, account deletion, production HTTPS, push-notification service, backup/restore, moderation, monitoring or load test. MelaReady is not connected to government dispatch, and no real venue or authority endorses it.
