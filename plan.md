# Integration Plan — PAX A99 NFC via Razorpay Android SDK

## Goal

Passenger phone (HCE/NFC) → tap PAX A99 ETM device → Razorpay Android SDK reads card → backend wallet debit → confirmation on POS screen.

---

## Credentials (demo environment)

| Key | Value |
|-----|-------|
| App Key | `f249e904-1935-4d7a-be63-2a318d6145e7` |
| Username (Login) | `1411001148` |
| Password | `123456Q` |
| Username (API) | `1411001149` |
| Org Code | `MOVING_TECH_INNOVATIONS` |
| Device Serial | `3170049436` |
| Ticket | `#18906124` (Shivam Paliwal, Razorpay Solutions) |

Demo environment: transactions not charged. Test with VISA or Mastercard only.

---

## SDK

Android SDK (AAR): `ezetapandroidsdk-3.9.aar` (in `app/libs/`)
GitHub: https://github.com/ezetap/android-payments-sdk/tree/master/release/native-sdk-release/3.9
Integration guide: page 47 of RazorpayPOS_P2P_SDK_DQR.pdf

---

## Phase 1 — Environment setup ✅ DONE

- [x] Download AAR — placed at `app/libs/ezetapandroidsdk-3.9.aar`
- [x] Migrate project from raw `aapt`/`d8` to Gradle
- [x] Add AAR as local `fileTree` dependency in `app/build.gradle`
- [x] Confirm Razorpay Service Apps installed on device:
  - `com.ezetap.service.demo` v2.0 ✅
  - `com.ezetap.service.prod` v2.0 ✅
  - `com.razorpay.pos` v10.18.232_DEMO ✅

**Build command:**
```bash
GRADLE_BIN=$(find "$HOME/.gradle/wrapper/dists/gradle-8.14.3-bin" -name "gradle" -type f | head -1)
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home \
  "$GRADLE_BIN" assembleDebug --no-daemon -p /path/to/nfc-tiny
```
Requires JDK 17 (JDK 25 incompatible with AGP). JDK 17 at `/Library/Java/JavaVirtualMachines/temurin-17.jdk`.

## Phase 2 — SDK integration ✅ DONE

- [x] `EzeAPI.initialize()` with appKey, merchantName, userName, appMode `EZETAP_DEMO`
- [x] `EzeAPI.pay()` with amount, reference, customer fields
- [x] `onActivityResult` handler logging all intent extras + pretty-printed JSON
- [x] Two-button UI: INIT → PAY, with amount input

**Key files:**
- `app/src/main/java/com/test/mvinfc/MainActivity.java`
- `app/src/main/res/layout/activity_main.xml`
- Package name: `com.test.mvinfc`

## Phase 3 — First NFC tap test 🔴 BLOCKED

- [x] APK built and sideloaded (`adb install`)
- [x] `EzeAPI.initialize()` succeeds — returns `{"status":"success","result":{"message":"Initialize device successful."}}`
- [ ] `EzeAPI.pay()` fails — `SESSION_TIMED_OUT / Login failed`

**Root cause:** Credentials `1411001148` / `123456Q` rejected by Ezetap demo server. Confirmed by manual login attempt on device — "Invalid credentials. Verify your credentials, login again, or contact your supervisor."

**Status:** Awaiting Razorpay to whitelist package `com.test.mvinfc` and activate demo credentials.
Razorpay requested: package name + POS app version → sent `com.test.mvinfc` + version `10.18.232_DEMO`.

## Phase 4 — Backend integration ⏳ PENDING

- [ ] On successful tap, POST transaction data to backend wallet server
- [ ] Implement idempotency (retry-safe transaction reference)
- [ ] Debit wallet and return confirmation
- [ ] Display confirmation on POS screen

## Phase 5 — Production readiness ⏳ PENDING

- [ ] Switch from demo credentials to production app key
- [ ] Generate production signing keystore
- [ ] Test with actual HCE (phone tap) end-to-end
- [ ] Confirm Razorpay Service App provisioned on all target devices via PAXSTORE

---

## What works / what doesn't

| Item | Status | Notes |
|------|--------|-------|
| Gradle build | ✅ | JDK 17 required, Gradle 8.14.3 |
| AAR wired | ✅ | `app/libs/` + `fileTree` dep |
| EzeAPI.initialize() | ✅ | Returns success in ~1s (local, no server call) |
| EzeAPI.pay() | ❌ | `SESSION_TIMED_OUT` — credentials not active |
| Manual Service App login | ❌ | "Invalid credentials" on device |

## Architecture

```
[ETM app — com.test.mvinfc on PAX A99]
       |
  EzeAPI (ezetapandroidsdk-3.9.aar)
       |
  com.ezetap.service.demo (cert-whitelisted, owns PICC)
       |
  PiccManager → NXP PN5190 → reads HCE/card tap
       |
  Ezetap/Razorpay demo backend
       |
  onActivityResult → your app → display result
```

## Key constraint

NFC access and Razorpay payment processing are coupled. Raw card data not exposed — only transaction result. For raw PICC access with a custom payment gateway, pursue PAX cert whitelisting (Option 1/2 in CLAUDE.md).
