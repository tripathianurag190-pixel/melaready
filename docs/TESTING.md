# MelaReady Test & Quality Strategy

MelaReady includes automated test suites for both the Python backend API and the Android native domain logic, as well as a manual two-device acceptance test procedure for multi-user coordination workflows.

---

## 1. Automated Python Backend Tests

The backend test suite is located in `server/test_server.py`. It tests the HTTP API in-memory using Python's standard `unittest` library and a temporary SQLite database.

### Running Backend Tests

From the project root:
```bash
python -m unittest -v server/test_server.py
```

### Coverage Area

- **Health Endpoints**: `GET /api/health` and `GET /health` returning service metadata (`{"ok": true, "service": "MelaReady", "version": "0.4.0"}`).
- **Authentication**: Valid registration, duplicate email rejection (HTTP 409), short password rejection (HTTP 400), invalid email format rejection (HTTP 400), valid login, invalid password rejection (HTTP 401), and logout token revocation.
- **Session Lifecycle**: Expired session rejection (HTTP 401).
- **Event Management**: Event creation with name/venue validation, 6-character invite code generation, event joining with valid invite, non-existent invite rejection (HTTP 404), expired invite rejection (HTTP 410), and invalid code length rejection (HTTP 400).
- **Task & Incident Workflows**: Task creation by lead, task status advancement by assignee (`OPEN` -> `ACKNOWLEDGED` -> `DONE`), report creation, report advancement by lead (`SAVED` -> `ACKNOWLEDGED` -> `ASSIGNED` -> `RESOLVED`).
- **Privacy & Contact Visibility**: Phone number concealment when `contact_visible=0`.
- **Location Sharing**: 5-minute location snapshot posting, location snapshot removal, automatic expiry, and non-member authorization rejection (HTTP 403).
- **Push-to-Talk Voice Clips**: Voice clip upload validation, maximum size enforcement (< 250 KB, HTTP 400), MIME type restriction (`audio/webm`, `audio/ogg`, `audio/mp4`), non-member upload rejection (HTTP 403), and voice file retrieval over HTTP.

---

## 2. Automated Android Unit Tests & Lint

### Running Android Unit Tests

On Windows:
```cmd
gradlew.bat :app:testDebugUnitTest
```

On macOS/Linux:
```bash
./gradlew :app:testDebugUnitTest
```

The Android unit test suite (`app/src/test/.../WorkflowTest.kt`) verifies:
- Event input validation (attendance estimate range 1–1,000,000).
- Incident status workflow ordering and terminal states.
- 3-decision drill completion checks.
- Sample workspace initialization rules.
- 18-question learning bank structure and option validity.
- Default `Workspace` gateway state and default server URL configuration.

### Running Android Lint & Build Checks

On Windows:
```cmd
gradlew.bat :app:assembleDebug :app:lintDebug
```

On macOS/Linux:
```bash
./gradlew :app:assembleDebug :app:lintDebug
```

---

## 3. Manual Two-Phone Acceptance Test Checklist

To verify multi-device coordination over a local Wi-Fi network:

1. **Start Backend Server**:
   ```bash
   python server/server.py --host 0.0.0.0 --port 8080
   ```
2. **Connect Devices**:
   Connect two Android devices (or one emulator and one physical phone) to the same Wi-Fi network. Obtain the laptop IPv4 address (e.g. `http://192.168.1.8:8080`).
3. **Phone 1 (Team Lead)**:
   - Tap **Sign in / Create account**, register Account 1.
   - Enter event name `"Kumbh Volunteer Zone A"` and venue `"Haridwar Gate 2"`, tap **Create Event**.
   - Verify server log prints `POST /api/event/create HTTP/1.1 201`.
   - Note the generated 6-character Invite Code (e.g. `A1B2C3`).
4. **Phone 2 (Volunteer)**:
   - Tap **Sign in / Create account**, register Account 2.
   - Enter invite code `A1B2C3`, tap **Join**.
   - Verify server log prints `POST /api/event/join HTTP/1.1 200`.
5. **Task Assignment**:
   - Phone 1 creates task `"Check first aid post"` assigned to Phone 2.
   - Phone 2 sees task status `OPEN`, taps **Advance Status** -> status changes to `ACKNOWLEDGED`.
   - Phone 1 sees updated status `ACKNOWLEDGED`.
6. **Location Snapshot**:
   - Phone 1 taps **Share Snapshot**.
   - Verify server log prints `POST /api/location/share HTTP/1.1 200`.
   - Phone 2 sees Phone 1's 5-minute location snapshot with accuracy and remaining time.
7. **Push-to-Talk Voice Clip**:
   - Phone 1 opens **Voice Clips** tab, taps **Start speaking**, speaks a 5-second message, taps **Stop & share**.
   - Verify server log prints `POST /api/voice/upload HTTP/1.1 201`.
   - Phone 2 sees new clip from Phone 1, taps **▶ Play Clip**, and hears playback (`GET /api/voice/file` 200).
8. **Logout & Protected Access**:
   - Phone 2 taps **Sign Out**. Verify session is invalidated and returning to event requires signing in again.
