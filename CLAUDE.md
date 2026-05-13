# Moving Tech ETM — PAX A99

## Project summary

Android ETM (Electronic Ticket Machine) app for MTC Chennai bus conductors. Runs on Razorpay-managed PAX A99 POS device. Handles tap-to-pay ticketing, thermal printing, QR validation, waybill management, and a multi-role web dashboard.

## Build command

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home \
  ~/.gradle/wrapper/dists/gradle-8.6-bin/afr5mpiioh2wthjmwnkmdsd5w/gradle-8.6/bin/gradle assembleDebug

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.test.mvinfc/.LoginActivity
```

JDK 17 required. JDK 25 breaks Gradle 8.6 (class file major version 69 error).

## Device

| Property | Value |
|---|---|
| Model | PAX A99 |
| Serial | 3170049436 |
| OS | PayDroid 12.0.0 (V11.1.06) |
| Android | 12 |
| PICC chip | NXP PN5190 |

## Key credentials

| Key | Value |
|---|---|
| Ezetap App Key | `f249e904-1935-4d7a-be63-2a318d6145e7` |
| Ezetap Username | `1411001148` |
| Merchant Name | `MOVING_TECH_INNOVATIONS` |
| Backend URL | `https://movingtech-etm.onrender.com` |
| Razorpay ticket | `#18906124` (Shivam Paliwal) |
| App cert SHA-256 | `F8:95:86:4B:EA:7C:5B:7B:5F:79:2D:F6:AC:DA:9B:29:6B:B8:5A:2E:30:4A:F8:7A:40:D5:40:54:DA:36:2D:D9` |

## Architecture

```
ETM App (com.test.mvinfc, PAX A99)
  ↓ EzeAPI.pay()
Ezetap Service App (cert-whitelisted) → Razorpay processes payment
  ↓ onActivityResult
POST /api/transit/tap → Render (Node.js) → Supabase PostgreSQL
  ↓
dashboard/index.html → live waybill/ticket data
```

## PAX permission model

Standard Android NFC stack disabled on PAX. Contactless reader controlled by proprietary Neptune stack:
- Runtime: `com.pax.ipp.neptune`
- Permission gate: `paxservice` (cert-based whitelist at `/data/resource/internal/package_signer.xml`)
- Manifest permissions required: `com.pax.permission.PICC`, `com.pax.permission.PRINTER`, `com.pax.permission.AIDL`
- Printer: ✅ works with manifest declaration alone
- PICC direct access: ❌ still blocked — needs cert whitelist (Razorpay ticket #18906124 open)

## NeptuneLite API usage

```java
IDAL dal = NeptuneLiteUser.getInstance().getDal(context);
IPrinter printer = dal.getPrinter();       // thermal printer
IPicc picc = dal.getPicc(EPiccType.INTERNAL);  // contactless reader
picc.open();
PiccCardInfo info = picc.detect(EDetectMode.EMV_AB);  // EMV_AB for phones/Apple Pay
picc.close();
```

Keep `dal` and `NeptuneLiteUser` as class-level fields — GC will kill the proxy if local.

## QR payload format

```
<sig>,<ticketId>,<userId>,<tripId>,<count>,<usedFlag>,<type>,<zone>,<fare_rupees>,<colour>,<timestamp>,,,
```

- `usedFlag=1` → ALREADY USED
- ticketId checked against local `scanned_qr` Room table → ALREADY SCANNED if duplicate
- fare field is rupees (not paise)

## Room DB schema (version 2)

- `tickets` — local ticket store, synced to backend
- `scanned_qr` — ticketIds scanned via QR (duplicate prevention)
- Migration 1→2 adds `scanned_qr` table

## Static fields (cross-activity state)

`WaybillActivity`:
```java
public static String activeTripId;
public static int    activeTripNo;
public static String activeWaybillNo;
public static String activeRouteNo;
public static String activeFleetNo;
```

Restored from API response on waybill load (survives app restart).

## Dashboard users (Supabase)

| Username | Password | Role |
|---|---|---|
| mtc_admin | admin@123 | admin |
| depot_tambaram | depot@123 | depot |
| conductor_web | conductor@123 | conductor |

## What does NOT work

| Approach | Why |
|---|---|
| `NfcAdapter.getDefaultAdapter()` | Returns null — feature flag absent on PAX |
| Direct PICC access without whitelist | `NO PERMISSION: 0x60000202` |
| `adb root` | Production build — blocked |
| ZXing external scanner intent | No scanner app on PAX — use embedded `zxing-android-embedded` |
| `spinnerRoute.getSelectedItem()` for route | Spinner hidden — read from `WaybillActivity.activeRouteNo` |

## Render deploy note

Free tier spins down. Auto-deploy sometimes fails — use Manual Deploy in Render dashboard if latest commit not live.
