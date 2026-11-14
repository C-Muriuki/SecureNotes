# Secure Notes — Phase 6 Comprehensive Testing and QA Report

**Date:** 13 November 2026  
**Scope:** Testing environment review, focused test additions, available Gradle checks, and runtime-test readiness. No production code, database schema, or security behavior was modified.

## 1. Testing strategy

Existing tests were reviewed before changes. The project already had meaningful JVM coverage for validation, password hashing, and password-strength calculation, plus Android instrumentation tests against the real SQLite, Keystore, ContentProvider, backup writer, theme preferences, and prompt parsing paths.

The gaps addressed in this phase were:

- No Espresso test class despite Espresso already being configured.
- No end-to-end UI journey connecting the actual registration, note editing, search, logout, and login screens.
- The existing database-backed registration test verified a hash, but did not explicitly inspect stored credentials for plaintext or exercise duplicate registration.

Two Espresso tests and one database instrumentation test were added. No test dependency was added. Android instrumentation tests were compiled, but could not be executed without a connected device or configured emulator.

## 2. Test environment

| Item | Result |
|---|---|
| Android module | Single `:app` module |
| Instrumentation runner | `androidx.test.runner.AndroidJUnitRunner` |
| Espresso | Already configured in `app/build.gradle.kts`; Espresso Core 3.7.0 |
| AndroidX JUnit | Already configured; 1.3.0 |
| JVM JUnit | JUnit 4.13.2 |
| Connected devices | None; `adb devices -l` listed no devices |
| Configured AVDs | None; `emulator -list-avds` returned no AVD names |
| Emulator executable | Present under `/home/nesh/Android/Sdk/emulator/` |
| Emulator system images | Android 37.x x86_64 images were present in the SDK inspection |
| Command-line SDK manager | `cmdline-tools/latest/bin/sdkmanager` was not present |

**Suggested emulator:** In Android Studio, open **Tools > Device Manager > Create Virtual Device**, choose a Pixel phone profile, select an installed **Android API 37.1 Google APIs x86_64 PS16K** image, then start the AVD. This image provides a recent Android platform for target-SDK-sensitive UI and permission behavior. If it is not available in Device Manager, install Android Emulator and the desired system image from **Tools > SDK Manager** first. Neither SDK installation nor AVD creation was performed during this phase.

## 3. Unit test results

Command: `./gradlew :app:testDebugUnitTest`

**Result: PASS — 13 tests, 0 failures, 0 skipped.**

| Test class | Tests | Coverage |
|---|---:|---|
| `ValidationUtilsTest` | 5 | Blank inputs, email formats, password rules, registration/login validation |
| `PasswordHasherTest` | 4 | Salt generation, deterministic derivation with same salt, correct and incorrect password verification |
| `PasswordStrengthCalculatorTest` | 3 | Empty, weak/fair and strong classifications |
| `ExampleUnitTest` | 1 | Template arithmetic test; low product value |

Test result XMLs were generated under `app/build/test-results/testDebugUnitTest/`.

## 4. Integration and Android instrumentation coverage

### Existing instrumentation tests

`SecureStorageInstrumentedTest` exercises real app data paths for:

- Legacy and Android 12+ Auto Backup exclusions for the database, session preferences, and manual backup directory.
- AES-GCM round-trip, tampered ciphertext, malformed ciphertext, missing Keystore key behavior, and preservation of existing stored ciphertext.
- Encrypted note values at rest, owner isolation, and persistence after reopening the database helper.
- Note repository CRUD, pinning, search, recent/count queries, and all pinned notes beyond the recent-note limit.
- ContentResolver query/CRUD, MIME types, non-exported provider behavior, and authenticated-owner scoping.
- Backup file privacy and interrupted temporary-file cleanup.
- JSON response parsing, XML offline prompt parsing, cache fallback, HTTP failure handling, and bounded streaming of oversized response bodies.
- Theme preference persistence and account-scoped mutations.
- Package-update receiver behavior.

### Added integration test

Added `registeredPasswordsPersistAsDistinctSaltedHashesAndRejectDuplicates` to `SecureStorageInstrumentedTest`. It uses `UserRepository` and the real SQLite database to check that registrations with the same password use different salts, the password is not stored in either credential column, valid verification succeeds, invalid verification fails, and the unique email constraint rejects a duplicate.

### Execution status

The instrumentation suite and UI suite **compiled**, but the integration tests above were **NOT EXECUTED** because no device or AVD was available. Their runtime assertions are not reported as passed.

## 5. Espresso UI tests

Added `SecureNotesUiTest` using existing Espresso dependencies:

| Test | Intended coverage |
|---|---|
| `loginAndRegistrationFormsRejectInvalidInput` | Login empty submission and invalid registration form remain on the expected forms |
| `registerCreateEditPinSearchDeleteLogoutAndLoginJourney` | Real account registration, dashboard, note creation, pinning, detail/editor update, search and clear, delete confirmation, logout, login, and settings theme selection |

The journey uses unique test email addresses and cleanup removes the created test database user/notes, its account Keystore alias, session, and app preferences. Its async UI checks are condition-driven with a bounded timeout; it does not assume a fixed operation duration.

**Compilation: PASS. Runtime: NOT EXECUTED** due to no connected device. Therefore the UI journey, component rendering, and lifecycle behavior remain unverified at runtime.

## 6. Security and privacy testing

| Check | Evidence | Status |
|---|---|---|
| Password hash and salt behavior | JVM `PasswordHasherTest`; new database instrumentation assertion | JVM tests PASS; database test compiled, NOT EXECUTED |
| Plaintext note storage | Existing raw SQLite ciphertext assertions | Test source present, NOT EXECUTED |
| AES-GCM round-trip/tamper behavior | Existing Keystore instrumentation test | Test source present, NOT EXECUTED |
| Missing key does not recreate key or rewrite ciphertext | Existing regression instrumentation test | Test source present, NOT EXECUTED |
| Account ownership | Repository and provider instrumentation assertions | Test source present, NOT EXECUTED |
| Backup privacy and interruption cleanup | Existing backup writer instrumentation assertions | Test source present, NOT EXECUTED |
| Provider external access | Manifest declares provider `exported=false`; test asserts the metadata | Static configuration inspected; runtime test NOT EXECUTED |
| Passwords in logs | No automated log-capture test found | NOT TESTED |
| Share chooser recipient payload | No automated resolver/chooser UI test found | NOT TESTED |
| Unauthorized access after logout | New UI journey covers logout then login, but does not launch every protected Activity with a forged direct Intent | PARTIAL; runtime NOT EXECUTED |
| Android Auto Backup behavior on device | Both rule files are parsed in the test suite; actual cloud transfer/restore is not exercised | PARTIAL; runtime NOT EXECUTED |

The approved local-only recovery policy remains represented in both backup rule resources. Manual backup files are separate app-private ciphertext backups and are not confused with Android Auto Backup. Cross-device recovery is still unsupported because a restored installation cannot access the original Android Keystore key.

## 7. Performance and lifecycle assessment

Static inspection confirms that authentication PBKDF2 and repository/database/encryption operations are dispatched through `BaseActivity.runIo`; its completion callback checks that the Activity is not finishing or destroyed. Dashboard note loading is off the UI thread and cancels/replaces prior loads. Search has a 300 ms debounce, loads/decrypts its collection into a screen-scoped cache, filters that cache in background work, and clears decrypted values on pause.

These are source-level findings, not runtime performance measurements. No automated test currently measures UI frame latency, cancellation timing, memory retention, large-collection behavior, or races during rapid navigation. The complete search cache can still consume memory and requires a device test with realistically large note collections before paging decisions are made.

## 8. Manual testing checklist

All items below remain **NOT EXECUTED** in this environment.

- [ ] Light theme and Midnight Graphite dark theme on login, registration, dashboard, notes, search, editor, details, account, and settings.
- [ ] Theme choice persists after process restart.
- [ ] Small phone, standard phone, large font, and landscape orientation.
- [ ] Tablet/foldable width and layout behavior.
- [ ] Registration, invalid forms, login, logout, and back-stack protection.
- [ ] Create/edit/pin/unpin/search/clear/delete and undo behavior.
- [ ] Search debounce during rapid text entry and screen pause/resume.
- [ ] Note row and FAB animations, including system animations disabled.
- [ ] Password-strength meter rendering and accessibility announcement.
- [ ] Notification permission allow, deny, and permanently-denied flows; backup remains available after denial.
- [ ] Backup completion, interrupted backup, and notification behavior.
- [ ] ContentProvider calls from the app and denial from an external application.
- [ ] Network success, unavailable network, server failure, JSON response and XML/cache fallback.
- [ ] Telephony operator available, no SIM, and Wi-Fi-only device.
- [ ] TalkBack, keyboard navigation, IME visibility, and touch target sizes.

## 9. Defects discovered and fixed

No production defect was confirmed or fixed in this testing-only phase.

The primary coverage gap discovered was the absence of Espresso UI tests. It was addressed with a UI suite, but runtime defects that those tests may reveal remain unknown until the suite is executed on a device. Existing build/lint checks did not reveal a compilation or lint regression.

## 10. Exact Gradle commands and results

| Command | Result | Details |
|---|---|---|
| `./gradlew :app:testDebugUnitTest` | **PASS** | BUILD SUCCESSFUL; 13 tests passed, 0 failed, 0 skipped |
| `./gradlew :app:compileDebugAndroidTestKotlin` | **PASS** | Existing instrumentation tests and new Espresso tests compile |
| `./gradlew :app:lintDebug` | **PASS** | BUILD SUCCESSFUL; lint report generated at `app/build/reports/lint-results-debug.html` |
| `./gradlew :app:assembleDebug` | **PASS** | BUILD SUCCESSFUL; debug APK assembled |
| `./gradlew :app:connectedDebugAndroidTest` | **NOT EXECUTED — BLOCKED** | Gradle failed task startup with `DeviceException: No connected devices!`; test methods did not run |

The connected task's Gradle failure is an environment block, not a test assertion failure.

## 11. Remaining limitations

1. New instrumentation assertions and Espresso journeys have not run on an emulator or physical device.
2. The test suite lacks automated UI coverage for all individual destinations and edge cases; the end-to-end journey covers core user workflow but does not replace focused per-screen tests.
3. Android Keystore/AES-GCM, provider IPC, service notification lifecycle, and actual backup behavior require runtime device verification.
4. Large note collection behavior, frame smoothness, Activity cancellation races, reduced-motion behavior, and memory pressure have not been measured.
5. Accessibility, theme visual consistency, screen sizes, telephony hardware cases, permissions, network-offline behavior, and share chooser behavior need manual device testing.
6. No emulator is configured. Create/start the suggested AVD and run `./gradlew :app:connectedDebugAndroidTest` to execute the Android integration and Espresso suites.

## 12. Files changed

- `app/src/androidTest/java/com/example/securenotes/SecureNotesUiTest.kt` — added invalid-form and real-UI journey tests.
- `app/src/androidTest/java/com/example/securenotes/SecureStorageInstrumentedTest.kt` — added database credential persistence, salt uniqueness, and duplicate-email test.
- `documentation/testing/PHASE6_COMPREHENSIVE_TESTING_REPORT.md` — this report.

No production implementation or Gradle dependency changed.
