# Moving Tech ETM — PAX A99 Bus Ticketing System

Electronic Ticket Machine (ETM) app for MTC Chennai bus conductors running on a Razorpay-managed PAX A99 POS device.

## System Overview

```
Passenger (NFC phone / card) → tap PAX A99 → ETM app → Razorpay charges fare
→ ticket issued + printed → backend records → dashboard shows live data
```

## Components

| Component | Location | Tech |
|---|---|---|
| Android ETM App | `app/` | Java, Gradle, Razorpay Ezetap SDK |
| Backend API | `backend/` | Node.js, Express, PostgreSQL (Supabase) |
| Web Dashboard | `dashboard/index.html` | Vanilla JS, SPA |
| Hosting | Render | `https://movingtech-etm.onrender.com` |

---

## Use Cases Implemented

### 1. Conductor Login
- 5 conductors (C001–C005) with 4-digit PIN
- SHA-256 hashed PINs stored in SharedPreferences
- Session persists across app restarts
- Logout button on ticket screen

### 2. Waybill Assignment
- Conductor logs in → fetches today's assigned waybill from backend
- Shows waybill info: waybill no, route, fleet no, shift, trip count
- Active trip resumable after app restart (static fields restored from API)
- Trip banner shown on ticket screen

### 3. Trip Management
- Conductor starts trip → backend records start time, returns tripId
- Multiple trips per waybill (up to 8)
- End Trip button in dashboard modal
- Close Waybill when all trips done

### 4. Tap-to-Pay Ticketing (NFC / Card)
- Source + destination spinners (auto-populated from route stops)
- Fare auto-fetched from backend based on route/stops
- Passenger count stepper (1–10) — total fare = per-pax fare × count
- Single tap: "TAP PAY ₹X" → directly opens Razorpay payment
- Supports contactless NFC phones (Google Pay, PhonePe) and physical cards
- Payment mode extracted from response (CARD / UPI / WALLET)

### 5. Offline Ticket Storage + Sync
- Ticket saved to Room SQLite DB immediately (offline-safe)
- Background WorkManager sync when network available
- TK-PENDING shown if offline; syncs automatically when online

### 6. Thermal Ticket Printing
- Prints after every successful payment (NFC tap or QR)
- Format matches MTC ticket layout:
  - Header: MTC, Chennai
  - Fleet no + Trip no
  - Route + Journey (src → dst)
  - Passenger count × fare = total
  - Payment mode
  - Timestamp + footer
- Uses PAX NeptuneLite IDAL → IPrinter API
- Requires `com.pax.permission.PRINTER` in manifest

### 7. QR Ticket Validation
- "VALIDATE QR TICKET" button on ticket screen
- Opens embedded ZXing camera scanner (no external app needed)
- Portrait mode locked
- Parses QR payload: `sig,ticketId,userId,tripId,count,usedFlag,type,zone,fare,colour,timestamp,,,`
- Results:
  - **VALID** → records ticket to backend (paymentMode=NCMC), prints ticket
  - **ALREADY USED** → usedFlag=1 in QR payload
  - **ALREADY SCANNED** → same ticketId scanned before on this device (local Room DB check)
  - **INVALID** → malformed QR
- Duplicate prevention: ticketId stored in `scanned_qr` Room table

### 8. Multi-Role Web Dashboard
Three roles with scoped views:

| Role | Login | Access |
|---|---|---|
| Admin | `mtc_admin` | All depots — overview stats + all tickets |
| Depot | `depot_tambaram` | Own depot — waybill board + ticket history |
| Conductor | `conductor_web` | Own waybills only |

**Dashboard features:**
- Waybill board: card grid with status colour (open/active/closed)
- Trip dots on each waybill card
- Waybill detail modal: live ticket counts per trip (fetched client-side before trip end)
- End Trip button with confirmation
- Close Waybill button (when all trips closed)
- Ticket table with pagination
- Stats: passengers, revenue, ticket count

### 9. Direct PICC Access (NFC Test — experimental)
- `NfcTestActivity` — standalone screen for raw NFC card reads
- Uses NeptuneLite IDAL → IPicc API directly
- `piccOpen()` → `detect(EDetectMode.EMV_AB)` → reads UID/SerialInfo
- Physical cards: working
- iPhone NFC: requires Apple Pay active (double-click side button) — EMV_AB mode
- Android phones (HCE): detectable when Google Pay active
- Blocked by PAX cert whitelist until Razorpay whitelists cert for PICC permission

---

## Build

Requires JDK 17 (JDK 25 incompatible with Gradle 8.6):

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home \
  ~/.gradle/wrapper/dists/gradle-8.6-bin/afr5mpiioh2wthjmwnkmdsd5w/gradle-8.6/bin/gradle assembleDebug

adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Device

| Property | Value |
|---|---|
| Model | PAX A99 |
| Serial | 3170049436 |
| OS | PayDroid 12.0.0 |
| Android | 12 |
| PICC chip | NXP PN5190 |

## Backend Deploy

Auto-deploys to Render on `git push`. If auto-deploy fails → Manual Deploy in Render dashboard.

```bash
cd backend && npm start   # local
```

## Pending

- [ ] PICC permission whitelist for direct NFC card reads (Razorpay ticket #18906124)
- [ ] Production signing keystore
- [ ] Tamil font support in ticket printing
- [ ] Moving.Tech QR validation API integration (sandbox token working, schema confirmed)
