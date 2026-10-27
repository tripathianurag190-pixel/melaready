# MelaReady v0.4 — Backend, Login aur Device Test Guide

Naam: **MelaReady**. Matlab: mela ya gathering se pehle taiyaar team.

## 1. Android Studio

1. `MelaReady` folder Android Studio mein open karo.
2. Gradle JDK **17** select karo.
3. Sync complete hone ke baad app phone ya emulator par run karo.

## 2. Backend Server Run Karo

Android Studio Terminal mein project root se:

```text
python server/server.py --host 0.0.0.0 --port 8080
```

- **Emulator URL:** `http://10.0.2.2:8080`
- **Physical Phones:** Laptop aur phones same trusted Wi-Fi par jodein. Windows Terminal mein `ipconfig` chalakar laptop ka IPv4 address dekhein (jaise `http://192.168.1.8:8080`).
- **Windows Firewall:** Agar prompt aaye to sirf **Private network** allow karein.
- App → Live Team (ya Team → Open Live Team) → server URL daalein aur **Connect** tap karein.

## 3. Account Creation, Login aur Two-Device Test

1. **Phone 1:**
   - App start hone par **Sign in / Create account** chunein.
   - **Create Account** par tap karke Naya Account banayein (Display Name, Email, Password, Confirm Password).
   - Sign in hone ke baad **Create Event** par tap karein aur 6-character Invite Code note karein.
2. **Phone 2:**
   - Naya account banayein (**Create Account**).
   - **Join Event** mein 6-character Invite Code daalkar event se judein.
3. **Task & Report Coordination:**
   - Phone 1 (Team Lead) Phone 2 ke member ko Task assign karein.
   - Phone 2 Task ko **Acknowledge** aur **Done** karein.
   - Phone 2 Report submit karein; Phone 1 (Lead) use **Acknowledge**, **Assign** aur **Resolve** karein.
4. **Location Snapshot Sharing (5-minute expiry):**
   - App mein **Share Snapshot** tap karke location permission dein.
   - Snapshot 5 minute mein swatah expire ho jayega. Explicit **Remove** button se use turant bhi hataya ja sakta hai.
5. **Push-to-Talk Voice Clips:**
   - Voice section mein **Start speaking** tap karke ya mic button ko press-and-hold karke 5–12 second ka message bolein.
   - **Stop & share** tap karke clip bhejin. Doosre phone par **Play Clip** tap karke sunein.
6. **Microphone Permission Recovery:**
   - Microphone permission deny karne par app spasht karan dikhate hue **Try again** aur **Open app settings** ka vikalp deti hai.
   - Background recording nahi hoti. Recording ke dauran spasht visual indicator rehta hai.
7. **Logout & Session Restoration:**
   - App band karke dubara kholne par active session validate hota hai.
   - **Sign Out** karne par server-side session revoke ho jata hai aur protected data inacessible ho jata hai.

## 4. Backend Unit Tests

```text
python -m unittest -v server/test_server.py
```

Tests account registration, login/logout, session expiry, event creation/joining, lead controls, expiring location, voice clip size/MIME validation aur protected route access check karte hain.

## 5. Product Truth & Safety

- **MelaReady** volunteer teams ko task, report, 5-minute expiring location aur short voice clips ke dwara organize karne ka prototype hai.
- Yeh **112**, **NDMA/SACHET**, police, medical emergency ya official control room ka substitute nahi hai.
- Isme continuous GPS tracking ya live radio/walkie-talkie call nahi hai.
