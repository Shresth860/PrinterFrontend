# BizBite Printer

Native Kotlin Android app for pairing a seller device with the BizBite Print Service, receiving jobs, and printing receipts using ESC/POS over Bluetooth SPP, USB host, or TCP/LAN. The app uses Jetpack Compose, MVVM, Room, DataStore, Retrofit/OkHttp, Hilt, WorkManager, and Firebase Cloud Messaging.

## Requirements

- JDK 17 or newer (the Android Gradle Plugin runs on JDK 17+).
- Android SDK Platform 34 and Build Tools 34.0.0.
- A backend HTTPS base URL implementing the API endpoints below.
- Android device/emulator for hardware, permission, and printer validation.

## Build and tests

The app defaults to the production Render backend for testing and release builds:

```properties
BASE_URL=https://printer-backend-2.onrender.com/
```

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat test
```

The base URL can be overridden with `-PBASE_URL=...` or the `BASE_URL` environment variable when building for another HTTPS service. Do not use a local URL for testing or release APKs.

Debug builds use the selected physical printer for test prints; no virtual printer or byte-discarding simulator is included. When upgrading an existing installation, the app removes the built-in virtual printer and its simulated jobs while preserving physical printer configurations and print history.

The app requires a valid backend pairing code. It does not provide a demo sign-in or bypass backend authentication.

## Release signing

Create a private signing keystore outside this repository. Configure either `keystore.properties` (ignored locally) or environment variables:

```properties
storeFile=C:\\secure\\bizbite-release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Equivalent environment variables are `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD`. Do not commit the keystore or signing credentials. Release builds enable R8 and resource shrinking.

## Firebase Cloud Messaging

Create an Android Firebase app for application ID `com.bizbite.printer`, enable FCM, and place the generated `google-services.json` in `app/` locally. It is intentionally not included in this repository. Without Firebase configuration, pairing and polling still work; push delivery is unavailable and the 15-second foreground poll plus 15-minute WorkManager fallback remain in use. The registration token is submitted to `PUT /api/v1/devices/me/fcm-token`; send high-priority data messages with `type=NEW_JOB`.

## Backend contract

All paths are relative to the configured HTTPS base URL:

- `GET /actuator/health`: reports backend availability in the app.
- `POST /api/v1/auth/pair`: `{ "code": "...", "deviceName": "...", "androidVersion": "...", "appVersion": "..." }`; returns `accessToken`, `refreshToken`, `deviceId`, `sellerId`, and ISO-8601 `accessTokenExpiresAt`. FCM registration is sent separately after pairing.
- `POST /api/v1/auth/refresh`: `{ "refreshToken": "..." }`; returns the same token response. The access-token expiration is parsed and stored with the encrypted credentials; a single OkHttp authenticator refresh is attempted per 401 response.
- `DELETE /api/v1/devices/me`: sent with the device bearer token before local unpair. The backend authenticates the device principal and revokes that device. Local encrypted credentials are cleared even if this request fails, and the failure is surfaced without logging credentials.
- `PUT /api/v1/devices/me/fcm-token`: `{ "fcm_token": "..." }`.
- `POST /api/v1/devices/me/heartbeat`.
- `PUT /api/v1/devices/me/printers`: printer ID, name, connection type/address/port, paper width and default flag.
- `GET /api/print-jobs/pending?printerId=...`: a JSON array of `{ "id": "...", "payload": { "blocks": [...] } }`.
- `PATCH /api/print-jobs/{id}/status`: `{ "status": "PRINTING|SUCCESS|FAILED", "errorMessage": "..." }`. `SUCCESS` is sent only after the printer write and flush succeed.
- `GET /api/print-jobs/history?page=0&size=20`: paginated print history. The app's print-history screen currently reads local Room history so it remains available offline.
- `GET /api/print-jobs/test-payload?printerId=...`: supplies the receipt blocks printed by the test-print action.
- `PATCH /api/v1/devices/me/printers/{printerId}/status`: `{ "status": "CONNECTED|DISCONNECTED" }`. Job and printer status callbacks are persisted locally and retried after network failures.
- `GET /api/v1/app/latest-release`: `version_code`, `version_name`, HTTPS `url`, `sha256` (64 hex characters), and `mandatory`.

Receipt payload `blocks` support `TEXT`, `TWO_COLUMN`, `ITEM_ROW`, `DIVIDER`, `IMAGE` (base64 image data), `QR`, `BARCODE` (`CODE128` or `EAN13`), and `FEED`. Unknown types are skipped. Non-ASCII receipt text is rendered through Android's text layout and converted to monochrome raster data.

## Printing and reliability

- Bluetooth Classic uses SPP UUID `00001101-0000-1000-8000-00805F9B34FB`, cancels device discovery before connecting, and falls back to RFCOMM channel 1 when the secure service connection fails. Its socket and output stream are reused while alive; the print service reconnects up to three times when needed. Bluetooth ACL disconnect events invalidate the socket and update connection status. BLE scanning is available alongside Classic discovery; BLE printing connects to a writable GATT characteristic and sends bounded chunks. BLE printer profiles differ by manufacturer, so validate the selected device's GATT service/characteristic behavior with that printer model. USB requires a printer exposing a bulk output endpoint and prompts for Android USB permission; LAN uses raw TCP, default port 9100.
- The foreground service processes a FIFO Room queue one job at a time, polls every 15 seconds, and starts again on boot/package replacement. WorkManager retries queue/outbox work every 15 minutes.
- Test print payloads come from the backend endpoint and are executed by the foreground service. Success is recorded only after the printer output write and flush complete without an exception; this confirms transport delivery, not a physical paper sensor acknowledgement. The history, pending count, last completed-print time, and last error come from persisted job/socket outcomes.
- Backend requests allow up to 90 seconds for responses so Render can wake from an idle period; backend availability is shown separately from device internet connectivity.
- Job status and printer configuration changes are retained locally until accepted by the service. Interrupted jobs are marked `POSSIBLE_PARTIAL` rather than silently reprinted. Verify printer output before manually reprinting these jobs.
- Bluetooth Classic/BLE scan and connection permissions are requested before an interactive test print. Android 11 and older use fine-location permission for discovery; Android 12+ uses Nearby devices permissions. Android vendor auto-start settings differ by device; the dashboard offers a shortcut when available.
- Update downloads require HTTPS and a matching SHA-256 digest before launching Android's package installer. The user must allow installation from this app in Android settings.

## Security notes

Access/refresh tokens are stored with AndroidX `EncryptedSharedPreferences` backed by Android Keystore. Receipt payloads are stored locally in Room for queue/history operation. HTTP logging is disabled, cleartext traffic is blocked, and diagnostics exports do not include tokens or receipt payloads. Treat the device and its local app data as sensitive.

## Tests

Local unit tests cover ESC/POS command bytes, QR framing, receipt line wrapping, queue idempotency/retry transitions, and durable status outbox synchronization. Physical transport behavior, camera scanning, installation, and printer-specific status bits need on-device validation with supported hardware.

