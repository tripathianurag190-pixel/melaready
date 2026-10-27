# Contributing to MelaReady

Thank you for your interest in contributing to MelaReady! This project is an open-source prototype designed for disaster mitigation preparation, volunteer practice, and local venue coordination.

---

## 1. Getting Started

1. **Clone the Repository**:
   ```bash
   git clone https://github.com/MY-USERNAME/MelaReady.git
   cd MelaReady
   ```
2. **Android Setup**:
   - Open the `MelaReady` directory in Android Studio.
   - Configure Gradle JDK **17**.
   - Let Gradle sync and verify that `:app:assembleDebug` builds cleanly.
3. **Backend Setup**:
   - Python 3.9+ (standard library only, no pip dependencies required).
   - Start local test server:
     ```bash
     python server/server.py --host 0.0.0.0 --port 8080
     ```

---

## 2. Running Automated Tests

Before submitting a pull request, ensure all backend and Android tests pass:

### Backend Tests
```bash
python -m unittest -v server/test_server.py
```

### Android Unit Tests & Lint
On Windows:
```cmd
gradlew.bat :app:testDebugUnitTest :app:lintDebug
```
On macOS/Linux:
```bash
./gradlew :app:testDebugUnitTest :app:lintDebug
```

---

## 3. Pull Request Guidelines

1. **Branch Naming**: Use descriptive branch names (e.g., `feature/voice-clip-ui`, `fix/invite-validation`).
2. **Privacy First**: Do **NOT** include real personal data, passwords, API tokens, real phone numbers, or actual GPS coordinates in issue descriptions or code commits. Use fictional demo data (e.g., `@example.test` emails).
3. **Product Safety**: Maintain accurate claims. Do not claim official government integration, continuous GPS tracking, or live walkie-talkie audio calling.
4. **Commit Messages**: Keep commit messages concise and clear.

---

## 4. Code Style

- **Android**: Follow standard Kotlin style guidelines and Jetpack Compose state management patterns.
- **Python**: Follow standard Python PEP 8 conventions. Standard library usage is preferred for zero-dependency local deployment.
