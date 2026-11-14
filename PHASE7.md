# Phase 7: Native Android Components

## Backup service

Settings starts `.services.NoteBackupService` with an explicit Intent and the authenticated session's user ID. It is a **started foreground service**, declared `exported=false` with the `dataSync` foreground-service type. It immediately posts an ongoing notification, then exports only the active account's encrypted SQLite fields on a worker thread.

The output is a JSON file under `filesDir/backups/`. It contains encrypted title/body values, their AES-GCM IVs, and note metadata. The writer does not decrypt notes. It writes to a temporary file, syncs, and renames only after success; caught failures remove the temporary file. A later attempt also removes abandoned temporary files.

The operation is a short, user initiated local copy, so it uses the required Service demonstration and does not need durable scheduled work. For a future long-running or deferrable backup, use WorkManager. A `dataSync` foreground service is still subject to Android's platform time limits.

On Android 13+, Settings requests `POST_NOTIFICATIONS` with an explanation. Denial does not block backup: the service still runs and Settings reports the result when the user returns. If notification access was permanently denied, Settings offers the app's system settings page. The service stops itself when finished and returns `START_NOT_STICKY`.

## Broadcast receiver

`AppUpdateReceiver` is a manifest-declared, non-exported receiver for protected `ACTION_MY_PACKAGE_REPLACED`. An app update invalidates the saved login session so the account must authenticate again. `onReceive()` performs only a synchronous local preference update and returns; it does not start a service or do network/database work. The receiver's process may be created to deliver the event and is not kept alive after `onReceive()`.

The prior `ACTION_SCREEN_OFF` manifest registration was removed because Android documents that event as dynamically registered only.

## Telephony information

The Account screen calls `getSystemService(TELEPHONY_SERVICE)`, checks for `FEATURE_TELEPHONY_RADIO_ACCESS` (or the older general telephony feature before API 33), then displays only `TelephonyManager.getNetworkOperatorName()`. The API reference requires radio-access hardware for this method; it does not require `READ_PHONE_STATE` or another runtime permission. No phone-state permission is requested. Devices without radio access, devices without an active operator, and restricted/empty results display an unavailable label. No identifiers, SIM serials, or phone numbers are read.

## Secure ContentProvider

Contract: `NotesContract`; authority: `com.example.securenotes.provider`.

- Collection URI: `content://com.example.securenotes.provider/notes`
- Item URI: `content://com.example.securenotes.provider/notes/{note_id}`
- MIME types: `vnd.android.cursor.dir/vnd.com.example.securenotes.provider.note` and `vnd.android.cursor.item/vnd.com.example.securenotes.provider.note`

The provider derives owner ID from the app's current SessionManager session. It ignores caller-supplied selection clauses, scopes every query/update/delete to that owner, validates projections against an allowlist, and returns encrypted fields only. Inserts accept encrypted columns and force the active user ID. The provider is `android:exported="false"` and URI grants are disabled. Same-app code demonstrates access with `ContentResolver` through `NotesProviderClient`.

## Networking

The dashboard fetches `https://dummyjson.com/quotes/random` over HTTPS using `HttpURLConnection`, 4-second connect and 5-second read timeouts, and parses the `quote` and `author` JSON fields. It sends no account information or note data and uses no API key. Network work runs on a single worker executor; the UI is updated on the main thread.

If offline or the service fails, the app uses its last quote from SharedPreferences, then parses a prompt from `res/xml/offline_prompts.xml` with `XmlPullParser`. The prompt card shows loading and online/offline state. Note storage and editing do not depend on this request.

## Intents and component access

Internal navigation and backup launch use explicit Intents. The launcher is the only exported activity. The provider, backup service, and app-update receiver are non-exported. There is no external provider URI grant.

The note-detail share control remains disabled. A proposed implicit `ACTION_SEND` implementation that would place decrypted note contents in an unrestricted external chooser was rejected by automatic approval review. No note plaintext is currently sent to another app.

## Verification

Automated instrumentation coverage is in `SecureStorageInstrumentedTest` for ciphertext-only backup values, ContentResolver access, provider isolation, receiver session handling, JSON/XML parsing, offline cache behavior, AES-GCM, and SQLite persistence/CRUD. The prompt HTTP parser is tested with a successful and an unavailable HTTP response.

Manual device checks still needed:
1. Run `connectedDebugAndroidTest` on an emulator with Android 13+ and confirm runtime notification permission approval and denial flows.
2. Trigger backup from Settings; inspect the app-private JSON file and confirm no note title/body plaintext appears.
3. Update/reinstall the app to observe the package-replaced receiver clear the login session.
4. Open Account on a cellular emulator/device and on a Wi-Fi-only emulator; check operator value and unavailable state.

Build status for this implementation: unit tests, app assembly, and instrumentation-test compilation pass. The connected instrumentation run could not run in this environment because no Android device/emulator was connected.
