# NFC Test — PAX A99 POS

## Project summary

Minimal Android APK to probe NFC/PICC accessibility on a Razorpay-managed PAX A99 POS device connected via USB.

Built without Gradle — raw `aapt` + `d8` + `apksigner`. Build and deploy:
```bash
bash build.sh
```

## Device

| Property | Value |
|---|---|
| Model | PAX A99 |
| Serial | 3170049436 |
| OS | PayDroid 12.0.0 (V11.1.06) |
| Android | 12 |
| PICC chip | NXP PN5190 |
| PICC firmware | V105.416 |

## What we found

`NfcAdapter.getDefaultAdapter()` returns null. Not a hardware problem.

PAX deliberately excludes `android.hardware.nfc` from the declared feature list so the standard Android NFC stack (`NfcNci.apk`) never activates. The NFC libraries and APK are present on device but dormant.

The contactless reader is controlled by PAX's proprietary PICC stack:
- Runtime: `com.pax.ipp.neptune` (installed at `/data/app/`)
- Native layer: `/system/lib/libpaxapijni.so`
- Permission gate: `paxservice` (runs as root), enforces cert-based checks
- Whitelist DB: `/data/resource/internal/package_signer.xml` (root-owned, unwritable)

Any app calling `PiccManager.piccOpen()` gets `NO PERMISSION: 0x60000202` unless its signing cert is whitelisted.

## PAX PICC API (via reflection)

Neptune package: `com.pax.ipp.neptune`
Public class: `com.pax.api.PiccManager`

```java
PiccManager pm = PiccManager.getInstance();
pm.piccOpen();                              // power on reader — no args
pm.piccDetect(byte type);                  // → PiccCardInfo{CardType, SerialInfo, CID, Other}
pm.piccClose();                            // power off
pm.piccRemove(byte b, byte b2);            // wait for card removal
pm.piccLight(byte on, byte mode);          // LED control
pm.piccCmdExchange(byte[] cmd, int len);   // raw command → byte[]
pm.piccIsoCommand(byte slot, APDU_SEND);   // ISO 7816 APDU → APDU_RESP
pm.m1ReadBlock(byte block);                // Mifare read → byte[]
pm.m1WriteBlock(byte block, byte[] data);
pm.m1Authority(byte, byte, byte[], byte[]); // Mifare auth
```

Load via `DexClassLoader` pointing at Neptune's APK path (resolved via `PackageManager`). See `MainActivity.java`.

## Three options to unlock PICC access

### Option 1 — Razorpay PAXSTORE whitelisting (fastest)

Razorpay manages this device through a PAXSTORE merchant account. They can push a permission grant OTA or add your app's cert fingerprint to the device whitelist without any PAX developer enrollment on your side.

**Steps:**
1. Generate a production signing keystore (don't use the auto-generated test one)
2. Extract the cert SHA-256 fingerprint: `keytool -printcert -jarfile your.apk`
3. Email Razorpay developer support with the device serial, package name, and cert fingerprint
4. Ask them to either: (a) whitelist the cert via PAXSTORE, or (b) explain their developer onboarding process

Current test cert fingerprints (replace with production cert before contacting):
```
SHA-256: F8:95:86:4B:EA:7C:5B:7B:5F:79:2D:F6:AC:DA:9B:29:6B:B8:5A:2E:30:4A:F8:7A:40:D5:40:54:DA:36:2D:D9
SHA-1:   C8:45:C7:DF:8A:38:B6:48:BA:3B:B7:69:17:CB:8E:0E:03:F6:E0:8B
```

### Option 2 — PAX PAXSTORE developer enrollment (scalable)

Register as a PAX developer directly. PAX issues a developer signing certificate. Any APK signed with this cert passes the `paxservice` permission check on any PAX device.

**Steps:**
1. Register at https://paxstore.pax.us (or the regional portal for India)
2. Complete developer verification — PAX may require business details and a use-case review
3. Receive a PAX developer signing certificate
4. Sign all APKs with this cert — PICC permission is automatically granted
5. Deploy via PAXSTORE or sideload via adb

**Pros:** Works on any PAX device, not tied to a single merchant account or device serial.
**Cons:** Slower — PAX enrollment can take days to weeks, may require NDA.

### Option 3 — Razorpay Android SDK (payment-coupled) — IN PROGRESS

Razorpay publishes a POS SDK (`RazorpayPOS_P2P_SDK`). It proxies PICC access through Razorpay's own certified Service App on the device, which is already whitelisted by PAX.

**How it works:** Your app on the POS calls the Razorpay SDK → SDK calls Razorpay's Service App (cert-whitelisted) → Service App calls `PiccManager` → reads HCE/card → Razorpay processes payment → webhook to your backend.

**Current status:**
- SDK (ezetapandroidsdk-3.9.aar) integrated into Gradle project ✅
- Package name: `com.test.mvinfc`
- `EzeAPI.initialize()` succeeds ✅
- `EzeAPI.pay()` blocked on `SESSION_TIMED_OUT` — demo credentials not yet activated by Razorpay
- Sent package name + POS app version (`10.18.232_DEMO`) to Razorpay (ticket #18906124) for whitelisting ⏳

**Decision taken:** Proceeding with Option 3 (Razorpay payment processing acceptable for this use case).

**Limitation:** NFC access and Razorpay payment processing are coupled. Raw card data not exposed. If custom payment gateway needed later, revert to Option 1 or 2.

## Target flow (bus booking)

```
Passenger phone (HCE/NFC) → tap PAX A99 → ETM app reads card UID/data → your backend wallet → debit → confirmation on POS screen
```

**Required:** Direct PICC access (Option 1 or 2). Your ETM app calls `PiccManager.piccDetect()`, reads `SerialInfo` (card UID) or issues ISO 7816 APDUs via `piccIsoCommand()` to read wallet balance/debit, then posts result to your backend.

**Razorpay SDK (Option 3) does NOT fit this flow** — it locks NFC output into Razorpay's payment pipeline, incompatible with a custom wallet backend.

First thing to check on device:
```bash
adb shell pm list packages | grep -E "eze|razorpay|ezetap"
```

## What does NOT work

| Approach | Why |
|---|---|
| `NfcAdapter.getDefaultAdapter()` | Returns null — feature flag absent |
| `Class.forName("com.pax.api.PiccManager")` | Not on app classloader — use `DexClassLoader` |
| `setprop pax.persist.permission.check 0` | Read-only system property |
| Writing to `/data/resource/internal/package_signer.xml` | Root-owned, shell has no write access |
| `adb root` | Production build — `adbd cannot run as root` |
