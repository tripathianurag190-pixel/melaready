# Manual device checks — pending

These are not recorded as passed. The build environment has no Android emulator or attached phone.

- [ ] Install the APK on Android 8/API 26 and a current supported Android device/emulator.
- [ ] Confirm a fresh install starts empty and the sample appears only after an explicit tap.
- [ ] Create an event; reject blank fields, nonnumeric attendance, zero and values over 1,000,000.
- [ ] Add/remove members; verify long names and Hindi names wrap correctly.
- [ ] Reopen the app after process termination; event, roster, checks and practice progress survive.
- [ ] Rotate during forms and each screen; check for lost data, blocked actions and crashes.
- [ ] Complete training, test wrong-answer feedback and restart.
- [ ] Add a drill note; test all transitions and the terminal resolved state.
- [ ] Test location permission denied, approximate only, precise, revoked and device location disabled.
- [ ] Get a real device fix; verify accuracy and increasing age. Confirm no fix is promised indoors.
- [ ] Put the app in the background during a location request; ensure updates stop.
- [ ] Clear a location snapshot; confirm it does not persist after process death.
- [ ] Test English/Hindi, 200% font size, narrow screens, screen reader labels and system bars.
- [ ] Confirm microphone permission appears only after tapping the shared-team microphone control.
- [ ] Record a short voice clip; verify auto-stop, upload and playback on the second joined phone.
- [ ] Deny/revoke microphone permission; confirm the app explains the unavailable state and does not pretend to share audio.
- [ ] Create two accounts, join by the 24-hour invite, restart both apps and verify login/session behaviour.
- [ ] Verify private phone is hidden, opt-in phone is visible, and profile photo picker asks for no broad storage permission.
- [ ] Share a fresh location explicitly; confirm age/accuracy, five-minute expiry and manual removal on phone 2.
- [ ] Open external official links; verify the browser opens to the intended official site.
- [ ] Cancel and confirm data deletion; verify only the app's local workspace is affected.
- [ ] Inspect app-private preferences on a debug test device to confirm ciphertext, not readable roster text.
- [ ] Perform an encryption round-trip and corrupted-storage test on a device; verify failure blocks accidental overwriting.
- [ ] Have a qualified trainer review the draft scenario before presenting it as training material.

Device, OS version, observed result and date should be recorded for each completed run. Do not tick a check based only on code review.
