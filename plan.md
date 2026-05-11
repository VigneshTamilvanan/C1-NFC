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
  "$GRADLE_BIN" assembleDebug --no-daemon -p /Users/admin/Desktop/C1-NFC
```
Requires JDK 17 (JDK 25 incompatible with AGP). JDK 17 at `/Library/Java/JavaVirtualMachines/temurin-17.jdk`.

## Phase 2 — SDK integration ✅ DONE

- [x] `EzeAPI.initialize()` with appKey, merchantName, userName, appMode `EZETAP_DEMO`
- [x] `EzeAPI.pay()` with amount, reference, customer fields
- [x] `onActivityResult` handler logging all intent extras + pretty-printed JSON
- [x] Package whitelisted by Razorpay — `com.test.mvinfc`
- [x] Service App login working after manual login via `com.ezetap.service.demo`
- [x] NFC reader confirmed active — `PAYMENT_FAILED: Card payment failed due to PIN timeout` = card detected, EMV started
- [x] Auto-reinit on session loss — app recovers silently on `SESSION_TIMED_OUT`

**Key files:**
- `app/src/main/java/com/test/mvinfc/MainActivity.java`
- `app/src/main/res/layout/activity_main.xml`
- Package name: `com.test.mvinfc`

## Phase 3 — POC: A2 Transit Tap-to-Pay ✅ DONE

### Chosen approach: A2 — Tap = instant payment

User boards bus → conductor has trip pre-filled → passenger taps phone (Google Pay/PhonePe) on PAX A99 → Razorpay charges fare → backend issues ticket → confirmation on POS screen.

### ETM App screens

```
Screen 1 — Trip Setup (conductor fills once)
┌─────────────────────┐
│ Route:  [__________]│  ← Spinner (5 Chennai GTFS routes)
│ Source: [__________]│  ← Spinner (stops auto-populated)
│ Dest:   [__________]│  ← Spinner (stops auto-populated)
│ Fare:   [__________]│
│                     │
│   [START COLLECTION]│
└─────────────────────┘

Screen 2 — Tap Screen (shown to passenger)
┌─────────────────────┐
│ Route 21C           │
│ Koyambedu → Adyar   │
│ Fare: ₹25           │
│                     │
│  TAP PHONE TO PAY   │
│  [   PAY NOW   ]    │
└─────────────────────┘

Screen 3 — Result
┌─────────────────────┐
│ ✅ TICKET ISSUED    │
│ Ticket #: TK-00123  │
│ Txn: rzp_xxxx       │
│                     │
│  [NEXT PASSENGER]   │
└─────────────────────┘
```

### Routes (from Chennai GTFS)

| Route | From | To |
|-------|------|----|
| 21 | Royapuram | Guindy |
| 15 | Island Ground | Koyambedu |
| M70 | Koyambedu | Thiruvanmiyur |
| 47 | Besant Nagar | ICF |
| 70 | Avadi | Tambaram |

### NFC tap status

- Card insert (chip): ✅ Full flow works, PIN prompt appears
- NFC contactless (phone tap): ⚠️ Razorpay demo merchant does not show contactless option — awaiting Razorpay to enable contactless payment method for `MOVING_TECH_INNOVATIONS` demo account

**Samsung SM-G781B NFC verified:** NFC ON, Google Pay HCE active (`TpHceService`), Visa AID registered — phone is ready to tap once Razorpay enables contactless.

## Phase 4 — Backend + Dashboard ✅ DONE

### Stack
- **Backend:** Node.js + Express 5
- **Database:** PostgreSQL via Supabase (session pooler, port 5432)
- **Dashboard:** Single-page HTML + vanilla JS
- **Hosting:** Render — `https://movingtech-etm.onrender.com`

### API endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/auth/login` | — | Returns JWT token |
| POST | `/api/transit/tap` | — | ETM app posts payment result, returns ticketNo |
| GET | `/api/transit/tickets` | JWT | Paginated ticket list |
| GET | `/api/transit/stats` | JWT | Today + all-time counts and revenue |

### Dashboard features
- Login page (username: `mtc_admin`)
- Stats cards: tickets today, revenue today, total tickets, total revenue
- Ticket table with route/date filter + pagination
- Auto-refresh every 10s

### Key files
- `backend/src/index.js` — Express server
- `backend/src/auth.js` — JWT login
- `backend/src/tickets.js` — tap + tickets + stats routes
- `backend/src/db.js` — Supabase pg pool
- `backend/src/schema.sql` — DB schema
- `backend/src/seed.js` — admin user seed
- `dashboard/index.html` — full dashboard SPA

### Data flow
```
PAX ETM app (EzeAPI.pay)
        ↓
Razorpay processes payment → onActivityResult RESULT_OK
        ↓
POST https://movingtech-etm.onrender.com/api/transit/tap
  { txnId, route, source, destination, fare, timestamp }
        ↓
Backend inserts ticket → returns { ticketNo }
        ↓
Screen 3 shows ✅ TICKET ISSUED + ticketNo
        ↓
Dashboard auto-refreshes → ticket appears in live feed
```

## Phase 5 — Contactless NFC enablement ⏳ PENDING

- [ ] Email Razorpay (ticket #18906124) to enable contactless payment method for demo merchant
- [ ] Test Google Pay HCE tap end-to-end with Samsung SM-G781B
- [ ] Confirm ticket issued via NFC tap flow

## Phase 6 — Production readiness ⏳ PENDING

- [ ] Switch from demo credentials to production app key
- [ ] Generate production signing keystore
- [ ] Test with actual HCE phone (Google Pay tap) end-to-end
- [ ] Confirm Razorpay Service App provisioned on all target devices via PAXSTORE
- [ ] Conductor login / auth flow
- [ ] Change dashboard password after go-live

---

## What works / what doesn't

| Item | Status | Notes |
|------|--------|-------|
| Gradle build | ✅ | JDK 17 required, Gradle 8.14.3 |
| AAR wired | ✅ | `app/libs/` + `fileTree` dep |
| EzeAPI.initialize() | ✅ | Returns success, local config |
| Service App login | ✅ | Manual login with `1411001148` / `123456Q` |
| NFC reader active | ✅ | Card detected, EMV contactless started |
| EzeAPI.pay() chip insert | ✅ | Full flow works, PIN prompt appears |
| EzeAPI.pay() contactless | ⚠️ | No contactless option in Razorpay demo UI — merchant config |
| Auto-reinit on session loss | ✅ | Recovers silently, re-enables PAY NOW |
| Backend POST | ✅ | Posts to Render, ticket stored in Supabase |
| Dashboard login | ✅ | JWT auth, mtc_admin |
| Dashboard ticket feed | ✅ | Live table, auto-refresh 10s |
| Dashboard stats | ✅ | Today + all-time revenue/tickets |

## Architecture

```
[ETM app — com.test.mvinfc on PAX A99]
  Screen 1: Conductor fills route/fare (GTFS dropdowns)
  Screen 2: Passenger taps phone / inserts card
  Screen 3: Ticket confirmation
       |
  EzeAPI (ezetapandroidsdk-3.9.aar)
       |
  com.ezetap.service.demo (cert-whitelisted, owns PICC)
       |
  PiccManager → NXP PN5190 → reads Google Pay HCE tap
       |
  Ezetap/Razorpay backend (charges fare)
       |
  onActivityResult → POST to movingtech-etm.onrender.com
       |
  Render (Node.js) → Supabase PostgreSQL
       |
  Dashboard: https://movingtech-etm.onrender.com
```

## Key constraint

NFC access and Razorpay payment processing are coupled. Raw card data not exposed — only transaction result. For raw PICC access with custom payment gateway (future), pursue PAX cert whitelisting (Option 1/2 in CLAUDE.md).
