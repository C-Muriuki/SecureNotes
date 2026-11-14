# Secure Notes — Phase 5 CAT 3 Assignment Compliance Matrix

**Review date:** 13 November 2026  
**Scope:** Existing CAT 3 requirements as recorded in requirements-compliance-matrix.md and SECURE_NOTES_COMPREHENSIVE_APPLICATION_AUDIT_REPORT.md, updated for work through Phase 5. No separate original assignment handout was present in the project.  
**Verification limit:** No emulator or physical device was connected. Instrumented test source was compiled but not executed.

**Status meanings:** PASS = implementation and relevant behavior verified; PARTIAL = implementation exists and is connected, but a requirement or runtime verification remains; FAIL = missing or incorrect; NOT TESTED = runtime/hardware behavior could not be exercised; NOT APPLICABLE = genuinely irrelevant.

| # | Requirement | Current implementation and evidence | Connected to app | Test evidence | Status and remaining work |
|---:|---|---|---|---|---|
| 1 | Android Studio environment and project anatomy | Single :app Kotlin/XML module; organized feature packages, resources, Gradle catalog and test source sets | Yes | Debug build and lint pass | **PARTIAL** — project works; developer-facing architecture/setup guide remains absent and several unused foundation files remain. |
| 2 | SQLite authentication | DatabaseHelper, UserRepository, users schema and unique email constraint | Yes | Unit validation/hash tests pass; DB tests are instrumented only | **PARTIAL** — run registration/login/database integration tests on device. |
| 3 | Input validation and salted password hashing | ValidationUtils, PasswordHasher, per-account salts and PBKDF2 verification | Yes | JVM validation and password-hasher tests pass | **PASS** for local implementation and unit-tested behavior. |
| 4 | Activities, Fragments and navigation | Activities, explicit navigation, Splash-to-onboarding/login route, dashboard/list/search/detail/editor flows | Yes | No connected UI test execution | **PARTIAL** — device-test navigation/back-stack behavior. |
| 5 | Explicit and implicit Intents | Internal navigation uses explicit Intents; NoteDetailActivity.shareSelectedNote() uses ACTION_SEND and chooser for the loaded note | Yes | No sharing UI/device test | **PARTIAL** — verify chooser and recipient payload on a device. |
| 6 | Application and Activity Context | Repositories/services normalize long-lived work to application context; Activities use Activity context for UI | Yes | Static code review | **PASS** for inspected call sites. |
| 7 | Android Service | Started, non-bound foreground NoteBackupService; exports owner-scoped ciphertext via worker executor; local backup remains tied to device Keystore; temporary file cleanup on interruption | Settings action starts it | Instrumented writer test exists; added interruption cleanup test compiled; Service itself not run | **PARTIAL** — service lifecycle and notification behavior need device verification. |
| 8 | BroadcastReceiver | Manifest AppUpdateReceiver, non-exported, handles package replacement by clearing session; onReceive() is short | System update event | Receiver test source exists but was not executed | **PARTIAL** — verify delivery during an app update on device. |
| 9 | Runtime permissions and rationale | Only runtime permission is POST_NOTIFICATIONS; backup works without it. Settings now displays an explanation before the first Android permission prompt and offers app settings after repeated denial | Backup setting | Not device-tested | **PARTIAL** — verify allow, deny, repeated denial and settings recovery on Android 13+. |
| 10 | XML layout types | ConstraintLayout and LinearLayout are widely used; RelativeLayout is used in pinned-note item; scroll containers and RecyclerView are present | Yes | Resource linking/debug build pass | **PASS** for structural requirement. |
| 11 | Accessibility | Touch-size token is 48dp; controls generally have descriptions; custom strength view announces its current level | Yes | Static review only | **PARTIAL** — TalkBack, contrast, font scaling and touch checks require a device. |
| 12 | Custom View and onDraw() | PasswordStrengthMeter now extends View, draws 3 Canvas/Paint segments, adapts to theme/density/size, and updates from registration password input | Registration screen | Strength calculator JVM tests pass; drawing not rendered on device | **PARTIAL** — implementation is connected; verify visual/accessibility behavior at runtime. |
| 13 | At least two purposeful animations | Reduced-motion-aware FAB press scale in BaseActivity.installPressFeedback(); first note-row appearance uses a short alpha/translation animation in NotesAdapter | Dashboard/list FABs and note list | Debug build/lint pass; no device motion review | **PARTIAL** — code paths are connected; verify touch and row motion on device. |
| 14 | RecyclerView and custom Adapter | Notes use RecyclerView, NotesAdapter/ListAdapter with DiffUtil and item layouts; pinned and recent sections use their adapters | Dashboard and notes list | Repository tests are instrumented, not run | **PARTIAL** — verify scrolling and item rendering on device. |
| 15 | Notes CRUD | Repository supports create/read/update/delete, pin, search, filters, sorting, recent notes and owner scoping; editor/detail/list are connected | Yes | Instrumented CRUD and persistence tests exist but were not executed | **PARTIAL** — run account-scoped SQLite flows on device. |
| 16 | SharedPreferences | Theme choice, session and prompt cache are persisted; ThemeUtils applies saved mode | Settings/startup | Instrumented persistence source exists; unit/build checks pass | **PARTIAL** — restart persistence needs device verification. |
| 17 | Light, dark and system themes | Approved Phase 4 palette resources, AppCompatDelegate and saved theme selection | All XML screens use theme resources | Build/lint pass; no screenshots available | **PARTIAL** — visual consistency and immediate switching were not device-verified. |
| 18 | Privacy statement | Privacy/security screen explains local storage, Keystore/AES, account data and network quote behavior; Phase 1 chose local-only backup and excludes database/session/manual backup from Android Auto Backup | Settings link | Static review | **PARTIAL** — no runtime screen check; portable recovery remains intentionally unsupported. |
| 19 | Android resource management | Shared strings, colors, dimensions, day/night and tablet dimension resources; Phase 4 replaced default launcher branding | Yes | Resource linking and lint pass | **PARTIAL** — some code messages and non-brand values remain inline; resource audit is not exhaustive. |
| 20 | ContentProvider and ContentResolver | NotesContract, UriMatcher, collection/item MIME types, CRUD methods, authenticated-owner scoping, ciphertext-only columns, non-exported manifest provider, same-app Resolver client | Provider client | Added Resolver CRUD, owner-scope and non-exported assertions; test source compiled but did not run | **PARTIAL** — execute provider tests; outside-app denial remains supported by manifest but not runtime-tested. |
| 21 | HTTP API communication | HTTPS HttpURLConnection, worker executor, connectivity check, timeouts, cache and offline fallback; body now stops at the response limit while streaming | Dashboard writing prompt | Parser and offline tests exist; oversized streaming test added and compiled | **PARTIAL** — successful live request/offline behavior were not executed here. |
| 22 | JSON and XML parsing | JSONObject parses remote quote; XmlPullParser reads local prompt resource | Dashboard fallback | Instrumented JSON/XML tests exist but were not executed | **PARTIAL** — run parser tests on device. |
| 23 | TelephonyManager | Account screen reads only operator name after radio feature check; no phone permission requested; unavailable states handled | Account details | No cellular/Wi-Fi-only hardware available | **NOT TESTED** — source is connected; verify hardware and SIM states on devices. |
| 24 | APK/AAB signing and versioning | Debug version configured; no release signing credentials/configuration or signed artifact established | Release path incomplete | No release signature test | **FAIL** — signing and version alignment remain release work. |
| 25 | JUnit and Espresso | 13 JVM tests pass; Espresso dependency exists, but no Espresso UI test class was found | Unit tests connected; UI tests absent | :app:testDebugUnitTest: 13 passed; Android test Kotlin compilation passes | **PARTIAL** — add and execute Espresso flows when a device is available. |
| 26 | AES encryption and Android Keystore | AES-GCM encryption, account-specific Keystore keys, encrypted SQLite fields, and missing-key safeguards from Phase 1 | Notes repository | Instrumented encryption/tamper/key tests exist but were not executed | **PARTIAL** — execute Keystore and raw-storage tests on emulator/device. |
| 27 | Secure coding | Parameterized SQLite access, non-exported provider/service/receiver, scoped notes, ciphertext backup, no plaintext note export in provider; Auto Backup excludes notes/session/manual backups | Across app | Lint/build pass; security instrumentation not executed | **PARTIAL** — runtime security checks and review of remaining IPC/data lifecycle are outstanding. |
| 28 | Integration and final testing | Unit tests, Android-test source, build and lint tasks exist | Connected integration paths in source | Unit tests pass; Android test compilation passes; connected test reports no devices | **PARTIAL** — run integration/UI/manual suite on emulator and cellular-capable hardware. |

## Phase 5 changes recorded

- Registration now contains an actively updated Canvas password-strength view. It is informational; ValidationUtils remains the acceptance rule.
- Note-row entrance and FAB press animations are connected to actual UI events and honor the system animator setting.
- The notification rationale precedes the first permission request; backup remains available after denial.
- Prompt responses are rejected during streaming once the configured character cap is exceeded.
- Provider CRUD/isolation and interrupted backup cleanup tests were added.
- The empty, unreferenced AutoLockService was reviewed and left unchanged. It is not the CAT Service implementation; the user-triggered encrypted backup service fulfills that requirement. No inactivity-lock behavior is claimed.

## Verification performed

- :app:testDebugUnitTest: **13 passed, 0 skipped, 0 failed**.
- :app:compileDebugAndroidTestKotlin: **passed**.
- :app:lintDebug: **passed**.
- :app:assembleDebug: **passed**.
- :app:connectedDebugAndroidTest: **not executed** — Gradle reported **No connected devices**.
- Emulator screenshots, permission dialogs, backup Service lifecycle, provider runtime CRUD, telephony hardware behavior, animations and Canvas rendering were not observed.
