# Prioritised Correction Roadmap

This roadmap is based on the comprehensive audit. It does not apply fixes; the audit request asked to present findings before beginning remediation.

## Phase 1 — Resolve data protection policy (Critical gate)

**Work**
- Decide whether standard Android backup should include any app state.
- If backups are disabled/excluded, configure both pre-Android 12 full backup rules and Android 12+ data extraction rules, including device transfer.
- Decide how a user restores manual ciphertext backups when Android Keystore keys cannot be transferred. Either define a safe user-controlled key-wrapping/export design or clearly state the backup is only useful on the original install.
- Update privacy/security copy and backup UI to match actual behavior.

**Evidence to collect**
- Verify manifest and both rule resources.
- Trigger backup/restore on API 30 and API 31+ emulator.
- Inspect backup contents and restore behavior; confirm no session state is restored unexpectedly and note key availability outcome is explicit.

## Phase 2 — Remove main-thread cryptographic/database work (High)

**Work**
- Move PBKDF2, SQLite and AES operations to an IO executor/dispatcher.
- Add lifecycle-aware loading/result state, prevent duplicate submissions and cancel safely on Activity destruction.
- Move SearchActivity query work off main and debounce text changes.
- Avoid repeated full decrypts/counts and use DiffUtil/ListAdapter.

**Evidence to collect**
- Unit tests for repository/use-case behavior.
- UI tests for loading, save errors and search.
- Measure main-thread frame stalls and search/login latency with representative large data.

## Phase 3 — Complete feature reachability and correct behavior (High/Medium)

**Work**
- Query pinned dashboard notes independently of the recent five.
- Make search clear control functional and expose loading/error/empty/retry states.
- Connect or remove onboarding and detail destinations; ensure delete, pin and share controls have real behavior.
- Add first-request POST_NOTIFICATIONS rationale and test denial fallback.
- Make editor save status reflect operation result; verify Snackbar Undo restoration failures.
- Revisit tab navigation stack behavior.

**Evidence to collect**
- Espresso tests for registration/login, dashboard, note create/edit/delete/undo, search, settings and theme.
- Instrumented account-isolation and repository tests executed on emulator.

## Phase 4 — Harden crypto and database upgrades (Medium)

**Work**
- Separate Keystore key creation from loading; never create a new key while decrypting.
- Add versioned authenticated associated data binding account ID, note ID and field.
- Store algorithm/iteration/format metadata needed for future password hash migrations.
- Define a tested DB migration from each old schema; do not silently leave inaccessible legacy notes.

**Evidence to collect**
- Tamper, field-swap, wrong-user, missing-key and migration tests.
- Verify raw SQLite title/content columns remain ciphertext after create/update and after process restart.

## Phase 5 — Meet UI/UX and accessibility requirements (Medium/Low)

**Work**
- Replace shield/robot identity with approved note/privacy mark and horizontal wordmark; update launcher/splash variants.
- Align exact light palette and semantic dark colors with the design system.
- Implement custom Canvas empty-state illustration, plus two restrained animations that respect system animation settings.
- Complete onboarding steps, spacing/type scale, screen-reader semantics, contrast and adaptive layout.

**Evidence to collect**
- Screenshot checks of every screen in light and dark on phone/tablet, portrait/landscape.
- TalkBack, font-scale, keyboard and touch-target checks.
- Verify custom view resize and reduced-motion behavior.

## Phase 6 — Release readiness (High)

**Work**
- Align versionCode/versionName and displayed version.
- Configure signing from a local untracked properties file or CI secret store; ignore keystore and signing files in VCS.
- Keep the keystore backup in a separate secure location and maintain redundant encrypted backups.
- Build release APK/AAB, verify signatures, install on clean and upgrade paths.

**Evidence to collect**
- Successful Gradle release package task.
- apksigner verification and bundle validation.
- Install/sign-in/note/encryption/theme/backup/permission/network smoke tests against the release artifact.
- Document key-loss and restore behavior and retain a release checklist.

## Exit criteria

Do not declare the app production-ready until Phase 1 policy is settled, high-priority main-thread work is moved, instrumented and UI tests run on devices, and a signed release artifact has been installed and smoke-tested.
