# MelaReady

> **Prepare. Practise. Coordinate.**  
> A spacious, native Android application and zero-dependency local backend for volunteer disaster mitigation preparation, venue training, and accountable gathering coordination.

**Version**: 0.4.0  
**Hackathon Theme**: Disaster Mitigation  
**Target Platform**: Android 8.0+ (API 26+) · Kotlin + Jetpack Compose  
**Backend**: Python 3.9+ (Standard Library) · SQLite  
**License**: MIT License  

---

## 1. Problem Statement & Solution Overview

### The Challenge
During major public gatherings, religious melas, cultural festivals, and disaster mitigation drills, volunteer response teams face severe operational challenges:
- **Communication Breakdown**: Unverified rumors cause panic and misdirected volunteer effort.
- **Lack of Accountable Follow-up**: Incident reports sent in casual messaging chats lack clear ownership, location accuracy, or resolution status.
- **Privacy & Safety Risks**: Volunteers often expose personal phone numbers publicly to request help.
- **Connectivity Dependencies**: Cloud-only emergency platforms fail when local cellular data networks become congested.

### The MelaReady Solution
MelaReady provides a **two-tier architecture**:
1. **Offline-First Native Android App**: Works completely offline out-of-the-box for venue preparation, 18-question bilingual training drills, checklists, local member rosters, and structured Help Now messaging.
2. **Local Connected Team Workspace**: A lightweight Python backend served over a trusted local Wi-Fi LAN (e.g., laptop hot-spot) enabling account registration, 24-hour invite-based event joining, lead-assigned tasks, incident reports, 5-minute expiring location snapshots, and short Push-to-Talk voice clips.

---

## 2. Key Features

- **First-Launch Account Gate**: Welcomes volunteers with an intuitive gateway offering *"Sign in / Create account"* or *"Continue offline"*. Offline training features require no login.
- **Prominent Live Team Access**: Instant access to connected workspace operations from the Home screen and Team screen.
- **Health Check & Server Connection Guard**: Performs asynchronous health checks (`GET /api/health`) before activating the connected WebView container, featuring expandable advanced connection settings and offline recovery options.
- **Authentication & Session Persistence**: Clean Sign In and Create Account modes with email normalization, regex validation, 8+ character password enforcement, password confirmation, opaque 12-hour session tokens, and server-side revocation on logout.
- **Tabbed Connected Workspace**:
  - **Operations**: Event details, 24-hour renewable invite codes, task lifecycle (`OPEN` -> `ACKNOWLEDGED` -> `DONE`), report lifecycle (`SAVED` -> `ACKNOWLEDGED` -> `ASSIGNED` -> `RESOLVED`), and location snapshot sharing.
  - **Members**: Joined member roster with lead assignment controls for roles and zones.
  - **Voice**: Push-to-Talk voice clips with live recording timer (max 12s, 250 KB limit), status badges, and audio playback.
  - **Profile**: Display name, opt-in phone privacy controls, JPEG profile photo, and secure logout.
- **5-Minute Expiring Location Snapshots**: Explicit, one-tap location sharing with accuracy and captured age. Location snapshots automatically expire after 5 minutes and can be manually removed at any time.
- **Push-to-Talk Voice Clips**: Asynchronous team voice messaging for joined event members. Clips automatically clean up after 24 hours.
- **Bilingual Interface**: Full English and Hindi support across native Compose UI and web workspace.

---

## 3. Technology Stack

- **Android Client**:
  - Language: Kotlin 2.2.21
  - UI Framework: Jetpack Compose (Material 3)
  - Navigation & Architecture: Jetpack ViewModel (`AppState`), Android Keystore
  - Security: AES-256-GCM encrypted local storage (`LocalStore`)
  - Target SDK: 36 (Android 16) / Min SDK: 26 (Android 8.0)
- **Backend Server**:
  - Language: Python 3.9+ (Standard Library `http.server`, `sqlite3`, `hashlib`, `hmac`)
  - Database: SQLite (`melaready_demo.sqlite3`)
  - Auth: PBKDF2 HMAC-SHA256 password hashing (600,000 rounds), SHA-256 session token hashing

---

## 4. Project Structure

```text
MelaReady/
├── app/                        # Android Native Application
│   ├── src/main/java/com/tripathitechnologies/melaready/
│   │   ├── MainActivity.kt     # Main Activity, Location & Mic Permissions
│   │   ├── AppState.kt         # ViewModel and Application State
│   │   ├── data/LocalStore.kt  # AES-GCM Encrypted Local Store
│   │   ├── domain/Models.kt    # Domain Models, Drills & Training Bank
│   │   └── ui/MelaApp.kt       # Jetpack Compose UI & Screen Views
│   └── src/test/               # Android Unit Tests
├── server/                     # Python Backend Server
│   ├── server.py               # HTTP REST API & Static Web Server
│   ├── test_server.py          # Python Backend Test Suite
│   └── web/index.html          # Connected Team Web Application
├── docs/                       # Project Documentation
│   ├── TECHNICAL_DESIGN.md     # Architecture & Mermaid Diagrams
│   ├── TESTING.md              # Test Strategy & Coverage
│   ├── PRODUCT_AND_DESIGN.md   # Design Principles & Theme
│   ├── VALIDATION.md           # Validation Checklist
│   └── THIRD_PARTY_NOTICES.md  # Open-Source Licenses
├── sample-data/                # Fictional Demonstration Data
│   ├── README.md
│   └── fictional_demo.json
├── .env.example                # Environment Variable Reference
├── .gitignore                  # Git Exclusion Rules
├── CONTRIBUTING.md             # Contribution Guidelines
├── SECURITY.md                 # Security & Vulnerability Policy
├── CODE_OF_CONDUCT.md          # Code of Conduct
├── LICENSE                     # MIT License
└── README.md                   # Repository Documentation
```

---

## 5. System Requirements & Setup

### Prerequisites
- **Android Studio**: Ladybug / 2024.2+ or higher with **JDK 17** configured.
- **Android SDK**: API Level 36 and Build Tools 36.0.0.
- **Python**: Python 3.9 or higher (standard library only; no `pip` packages required).

### Android Studio Setup
1. Open Android Studio and choose **Open**, selecting the root `MelaReady` folder.
2. In **Settings > Build, Execution, Deployment > Build Tools > Gradle**, set Gradle JDK to **Java 17**.
3. Allow Gradle to sync. Select an Android device or emulator and press **Run**.

### Python Backend Setup
From the project root directory in Terminal:
```bash
python server/server.py --host 0.0.0.0 --port 8080
```

### Server Connection URLs
- **Android Emulator**: `http://10.0.2.2:8080`
- **Physical Devices**: Connect laptop and phones to the same Wi-Fi hotspot. Obtain laptop IPv4 address via `ipconfig` (Windows) or `ifconfig` (macOS/Linux) and use e.g. `http://192.168.1.8:8080`.
- **Windows Firewall**: Allow **Private network** access if prompted.

---

## 6. Environment Variables

The current local pilot backend runs out-of-the-box without requiring secret environment variables. If deploying to external infrastructure, refer to [.env.example](file:///.env.example) for variable names:
```bash
SERVER_HOST=0.0.0.0
SERVER_PORT=8080
# DATABASE_URL=sqlite:///melaready_demo.sqlite3
# SECRET_KEY=your-placeholder-secret-key-here
```
> [!IMPORTANT]
> Never commit real `.env` files or secret keys to version control.

---

## 7. Two-Device Testing Guide

1. **Start Backend**: Run `python server/server.py --host 0.0.0.0 --port 8080`.
2. **Phone 1 (Team Lead)**:
   - Tap **Sign in / Create account**, register Account 1.
   - Enter Event Name `"Kumbh Volunteer Zone A"` and Venue `"Haridwar Gate 2"`, tap **Create Event**.
   - Note the generated 6-character Invite Code (e.g., `A1B2C3`).
3. **Phone 2 (Volunteer)**:
   - Register Account 2, enter invite code `A1B2C3`, tap **Join**.
4. **Task & Report Coordination**:
   - Phone 1 assigns a task to Phone 2. Phone 2 advances status (`OPEN` -> `ACKNOWLEDGED` -> `DONE`).
   - Phone 2 submits a report. Phone 1 advances status (`SAVED` -> `ACKNOWLEDGED` -> `ASSIGNED` -> `RESOLVED`).
5. **Location Snapshot**:
   - Phone 1 taps **Share Snapshot**. Phone 2 receives location snapshot with 5-minute expiry.
6. **Push-to-Talk Voice Clip**:
   - Phone 1 opens **Voice Clips** tab, holds mic / taps **Start speaking**, records up to 12s, taps **Stop & share**.
   - Phone 2 taps **▶ Play Clip** to hear audio.

---

## 8. Verification & Test Commands

### Backend Automated Test Suite
```bash
python -m unittest -v server/test_server.py
```

### Android Build, Unit Tests & Lint (Windows)
```cmd
gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

### Android Build, Unit Tests & Lint (macOS / Linux)
```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

---

## 9. Privacy, Product Safety & Boundaries

> [!IMPORTANT]
> **Product Safety Statement**:
> MelaReady does not replace 112, NDMA/SACHET, police, medical responders or official control rooms.

- **Push-to-Talk Clarification**: Push-to-Talk in MelaReady currently refers to **short asynchronous team voice updates** (max 12s, < 250 KB), not a continuous live walkie-talkie audio call or offline analog radio.
- **Explicit Location Sharing**: Location is shared strictly on demand via 5-minute expiring snapshots. There is no background GPS tracking or continuous location history.
- **On-Demand Microphone**: Microphone access is requested only when recording a voice clip and MediaStream tracks are released immediately after recording.
- **Data Privacy**: No credentials, session tokens, passwords, real phone numbers, or private audio files are tracked or committed in this repository.

---

## 10. UN Sustainable Development Goals (SDGs) Alignment

- **SDG 11: Sustainable Cities & Communities (Target 11.5)**: Strengthens community disaster resilience and reduces operational confusion during large-scale public gatherings.
- **SDG 3: Good Health & Well-being (Target 3.d)**: Improves early warning, volunteer preparation, and coordinated local incident follow-up.

---

## 11. Hackathon Prototype Disclaimer & License

This repository is an open-source prototype developed for hackathon demonstration. It is licensed under the [MIT License](file:///LICENSE). Third-party components retain their respective licenses; see [docs/THIRD_PARTY_NOTICES.md](file:///docs/THIRD_PARTY_NOTICES.md).
