# Moving Tech ETM — Implementation Plan

## Status Summary

| Phase | Description | Status |
|---|---|---|
| 1 | Environment setup + Gradle migration | ✅ Done |
| 2 | Razorpay Ezetap SDK integration | ✅ Done |
| 3 | ETM ticket booking flow | ✅ Done |
| 4 | Backend + basic dashboard | ✅ Done |
| 5 | Conductor login + offline mode | ✅ Done |
| 6 | Waybill system + multi-role dashboard | ✅ Done |
| 7 | QR ticket validation | ✅ Done |
| 8 | Thermal ticket printing | ✅ Done |
| 9 | PICC direct access (raw NFC) | ⏳ Blocked — cert whitelist pending |
| 10 | Production readiness | ⏳ Pending |

---

## Phase 1 — Environment Setup ✅

- Gradle project (was raw aapt/d8)
- JDK 17 required (JDK 25 breaks Gradle 8.6)
- Ezetap AAR in `app/libs/`
- Razorpay Service Apps confirmed on device

## Phase 2 — Razorpay SDK ✅

- `EzeAPI.initialize()` + `EzeAPI.pay()` working
- `onActivityResult` handles success/failure
- Package `com.test.mvinfc` whitelisted by Razorpay
- Payment mode extracted from `result.txn.paymentMode` (not hardcoded)

## Phase 3 — ETM Booking Flow ✅

- Screen 1: route fixed from waybill, src/dst spinners, fare auto-fetch, passenger stepper (1–10)
- Single tap: "TAP PAY ₹X (N pax)" → directly charges total (pax × fare)
- No intermediate screens — result toast + back to ticket screen
- Trip banner shows active trip no + route
- Total fare = per-pax × passenger count sent to Razorpay

## Phase 4 — Backend + Dashboard ✅

- Node.js/Express on Render
- Supabase PostgreSQL
- Endpoints: `/api/auth/login`, `/api/transit/tap`, `/api/transit/tickets`, `/api/transit/stats`
- `passenger_count` column in tickets table
- Multi-role dashboard (admin/depot/conductor)
- Waybill board with trip dots, live ticket counts, End Trip, Close Waybill

## Phase 5 — Conductor Login + Offline ✅

- `LoginActivity` with 4-digit PIN (SHA-256 hashed)
- 5 conductors: C001–C005
- Room SQLite for offline ticket storage
- WorkManager background sync
- TK-PENDING shown offline, syncs when network available

## Phase 6 — Waybill System ✅

- Waybill assigned by depot, fetched by conductor on login
- `activeTripId`, `activeWaybillNo`, `activeRouteNo`, `activeFleetNo` as static fields
- Restored from API on app restart (no data loss)
- Trip start/end via backend API
- Waybill status: open → active → closed

## Phase 7 — QR Ticket Validation ✅

- Embedded ZXing camera (no external app) — portrait locked
- Parses comma-separated QR payload
- fare field = rupees (not paise)
- VALID → post ticket (paymentMode=NCMC) + print
- ALREADY USED → usedFlag=1 in QR
- ALREADY SCANNED → local Room DB `scanned_qr` table check
- INVALID → malformed format

## Phase 8 — Thermal Printing ✅

- NeptuneLite IDAL → IPrinter
- `com.pax.permission.PRINTER` in manifest (sufficient — no cert whitelist needed)
- Prints after payment success (UPI/CARD) and QR validation
- MTC ticket format: header, fleet, route, journey, pax × fare = total, payment mode, timestamp
- 384px wide (58mm thermal roll)

## Phase 9 — Direct PICC Access ⏳ Blocked

NfcTestActivity working:
- ✅ NeptuneLiteUser init
- ✅ getDal() → IPicc obtained
- ✅ piccOpen() — reader powered on
- ✅ Physical card detect (EDetectMode.EMV_AB)
- ❌ iPhone NFC — requires Apple Pay active (double-click side button)
- ❌ PICC permission for ETM app — cert not whitelisted

**Pending:** Razorpay to whitelist cert SHA-256 `F8:95:86:4B...` for `com.pax.permission.PICC`
Ticket: #18906124

## Phase 10 — Production ⏳

- [ ] Production signing keystore (replace debug cert)
- [ ] Re-whitelist production cert with Razorpay/PAX
- [ ] Switch Ezetap from `EZETAP_DEMO` to production app mode
- [ ] Tamil font rendering in ticket print
- [ ] Moving.Tech QR API integration (sandbox confirmed working, token obtained)
- [ ] Dashboard export CSV
- [ ] Analytics tab in dashboard

---

## Key Files

| File | Purpose |
|---|---|
| `app/src/main/java/com/test/mvinfc/MainActivity.java` | Main ticket booking screen |
| `app/src/main/java/com/test/mvinfc/WaybillActivity.java` | Waybill + trip management |
| `app/src/main/java/com/test/mvinfc/LoginActivity.java` | Conductor PIN login |
| `app/src/main/java/com/test/mvinfc/NfcTestActivity.java` | Raw PICC test screen |
| `app/src/main/java/com/test/mvinfc/AppDatabase.java` | Room DB (v2) |
| `app/src/main/res/layout/activity_main.xml` | Ticket screen layout |
| `backend/src/tickets.js` | Tap + ticket API |
| `backend/src/waybill.js` | Waybill + trip API |
| `backend/src/auth.js` | JWT login (role/depot in token) |
| `dashboard/index.html` | Multi-role SPA dashboard |
