# MelaReady Technical Design Document

**Version**: 0.4.0  
**Project Theme**: Disaster Mitigation  
**Tagline**: Prepare · Practise · Coordinate  

---

## 1. System Overview & Architecture

MelaReady is a dual-tier native Android application paired with a lightweight, zero-dependency Python backend server (`server/server.py`). It is designed for offline-first disaster preparation, volunteer training, and local venue coordination during large public gatherings, cultural melas, and civic drills.

```mermaid
graph TD
    subgraph "Android Client (Device)"
        UI["Jetpack Compose UI (MelaApp)"]
        State["AppState (ViewModel)"]
        LocalStore["AES-GCM LocalStore (Android Keystore)"]
        WV["Android WebView Container"]
        LocationManager["Android LocationManager"]
    end

    subgraph "Local Network (Trusted LAN)"
        PythonServer["Python HTTP Server (server.py)"]
        SQLiteDB[("SQLite Database (melaready_demo.sqlite3)")]
    end

    UI --> State
    State --> LocalStore
    UI --> LocationManager
    UI --> WV
    WV -- "HTTP / REST JSON (Bearer Token)" --> PythonServer
    PythonServer --> SQLiteDB
```

---

## 2. Authentication & Session Lifecycle

Authentication relies on PBKDF2 password hashing (`600,000` iterations) and opaque 256-bit session tokens stored on the server.

```mermaid
sequenceDiagram
    autonumber
    actor User as Volunteer
    participant App as Android Client (WebView)
    participant Server as Python Backend
    participant DB as SQLite DB

    User->>App: Enter Name, Email, Password
    App->>Server: POST /api/register {email, name, password}
    Server->>Server: Hash password with PBKDF2 + salt
    Server->>DB: INSERT INTO users
    Server->>Server: Generate opaque session token
    Server->>DB: INSERT INTO sessions (token_hash, expires=12h)
    Server-->>App: 200 OK {token, user}
    App->>App: Store token in localStorage
    
    User->>App: Tap Logout
    App->>Server: POST /api/logout (Authorization: Bearer token)
    Server->>DB: DELETE FROM sessions WHERE token_hash
    Server-->>App: 200 OK {ok: true}
    App->>App: Clear token & eventId from localStorage
```

---

## 3. Event Creation & Join Workflow

Event organization uses short 6-character uppercase invite codes valid for 24 hours.

```mermaid
stateDiagram-v2
    [*] --> EventGate: User Signed In
    
    state EventGate {
        CreateEvent: Create New Connected Event
        JoinEvent: Join via 6-Char Invite
    }

    CreateEvent --> Workspace: POST /api/event/create (201 Created)
    JoinEvent --> Workspace: POST /api/event/join (200 OK)

    state Workspace {
        Operations: Tasks & Incident Reports
        Members: Joined Roster & Roles
        VoiceClips: Push-to-Talk Voice Clips
        Profile: Privacy & Logout
    }

    Workspace --> EventGate: Switch Event or Logout
```

---

## 4. Location & Voice Data Flow

Both location snapshot sharing and Push-to-Talk voice clip distribution operate strictly on an explicit, opt-in basis within active event membership.

```mermaid
sequenceDiagram
    autonumber
    actor Phone1 as Phone 1 (Member A)
    participant Client1 as Android App 1
    participant Server as Python Server
    participant Client2 as Android App 2
    actor Phone2 as Phone 2 (Member B)

    Note over Phone1,Client1: Location Sharing (5-Min Expiry)
    Phone1->>Client1: Tap "Share Snapshot"
    Client1->>Server: POST /api/location/share {event_id, lat, lng, acc, captured}
    Server->>Server: Verify event membership & validate age (<120s)
    Server-->>Client1: 200 OK {expires: now + 300s}
    Server-->>Client2: Poll snapshot via GET /api/snapshot
    Client2->>Phone2: Display 5-min expiring location pin

    Note over Phone1,Client1: Push-to-Talk Voice Clip
    Phone1->>Client1: Hold "Start speaking" (max 12s)
    Client1->>Client1: Record webm/mp4 audio (< 250 KB)
    Client1->>Server: POST /api/voice/upload {event_id, mime, audio_base64}
    Server->>Server: Validate MIME, size < 250 KB & membership
    Server-->>Client1: 201 Created {id: voice_uuid}
    Server-->>Client2: Poll snapshot includes voice_uuid
    Client2->>Server: GET /api/voice/file?id=voice_uuid (Authorization Header)
    Server-->>Client2: 200 OK (Audio Binary Stream)
    Client2->>Phone2: Playback voice clip
```

---

## 5. Security & Privacy Controls

1. **Local Data Protection**: All local workspace data (training progress, drill logs, offline event info) is stored using AES-256-GCM encryption with keys generated in the **Android Keystore**.
2. **Network Security**: Bearer tokens are submitted strictly in `Authorization: Bearer <token>` HTTP headers—never in URL query strings.
3. **No Background Tracking**: Location fixes are requested only on explicit user interaction and remain in volatile memory.
4. **On-Demand Microphone**: Microphone permissions (`RECORD_AUDIO`) are requested only when the user initiates voice clip recording. `MediaStream` audio tracks are explicitly stopped immediately after recording completes or fails.
5. **Session Expiry**: Inactive sessions automatically expire after 12 hours server-side.

---

## 6. Functional Endpoints Summary

| Endpoint | Method | Auth Required | Description |
| :--- | :--- | :--- | :--- |
| `/api/health` | `GET` | No | Returns service status and version without sensitive data |
| `/api/register` | `POST` | No | Creates new user account with hashed password |
| `/api/login` | `POST` | No | Authenticates credentials and returns opaque 12h session token |
| `/api/logout` | `POST` | Yes | Revokes current session token |
| `/api/events` | `GET` | Yes | Returns list of joined events for authenticated user |
| `/api/event/create` | `POST` | Yes | Creates new event and generates 6-character invite code |
| `/api/event/join` | `POST` | Yes | Joins user to event using valid invite code |
| `/api/snapshot` | `GET` | Yes | Fetches current members, tasks, reports, active locations, and voice clips |
| `/api/task/create` | `POST` | Yes | Team lead assigns task to joined member |
| `/api/task/advance` | `POST` | Yes | Assignee or lead advances task state (`OPEN` -> `ACKNOWLEDGED` -> `DONE`) |
| `/api/report/create` | `POST` | Yes | Creates new incident report (`SAVED`) |
| `/api/report/advance` | `POST` | Yes | Team lead advances report state (`SAVED` -> `ACKNOWLEDGED` -> `ASSIGNED` -> `RESOLVED`) |
| `/api/location/share` | `POST` | Yes | Shares 5-minute expiring location snapshot |
| `/api/location/remove` | `POST` | Yes | Immediately removes user's shared location snapshot |
| `/api/voice/upload` | `POST` | Yes | Uploads short voice clip (< 250 KB, max 12s) |
| `/api/voice/file` | `GET` | Yes | Streams voice audio binary for authorized event members |
