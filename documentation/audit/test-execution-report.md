# Test Execution Report

**Execution date:** 13 November 2026  
**Environment:** Android Studio Gradle runner and command runner; no connected Android device/emulator.

| Test/task | Purpose | Expected | Actual | Status | Evidence/limitations |
|---|---|---|---|---|---|
| ValidationUtilsTest.isNotEmpty_rejectsBlankAndNull | Empty/null validation | Accept nonblank and reject blank/null | Included in unit task; aggregate task passed | PASS | app/src/test/java/com/example/securenotes/ValidationUtilsTest.kt |
| ValidationUtilsTest.emailValidation_acceptsValidAndRejectsMalformed | Email validation | Accept valid addresses, reject malformed/null | Included in unit task; aggregate task passed | PASS | Same test class |
| ValidationUtilsTest.passwordValidation_requiresLengthAndNumberOrSymbol | Password policy | Accept qualifying passwords; reject short/no digit-or-symbol | Included in unit task; aggregate task passed | PASS | Same test class |
| ValidationUtilsTest.registrationValidation_checksAllFields | Registration validation | Reject missing name/bad email/mismatched confirmation | Included in unit task; aggregate task passed | PASS | Same test class |
| ValidationUtilsTest.loginValidation_rejectsEmptyInputs | Login required inputs | Reject missing email/password | Included in unit task; aggregate task passed | PASS | Same test class |
| PasswordHasherTest.generateSalt_returnsNonEmptyUniqueHex | Salt format/uniqueness | 16-byte hex salt and distinct generated values | Included in unit task; aggregate task passed | PASS | app/src/test/java/com/example/securenotes/PasswordHasherTest.kt |
| PasswordHasherTest.hashPassword_deterministicWithSameSalt | PBKDF2 deterministic derivation | Same password+salt yields 256-bit hash | Included in unit task; aggregate task passed | PASS | Same test class |
| PasswordHasherTest.verifyPassword_correctPassword_returnsTrue | Correct password verification | Return true | Included in unit task; aggregate task passed | PASS | Same test class |
| PasswordHasherTest.verifyPassword_incorrectPassword_returnsFalse | Incorrect password rejection | Return false | Included in unit task; aggregate task passed | PASS | Same test class |
| ExampleUnitTest.addition_isCorrect | Template test | 2 + 2 equals 4 | Included in unit task; aggregate task passed | PASS | app/src/test/java/com/example/securenotes/ExampleUnitTest.kt; provides no product coverage |
| :app:testDebugUnitTest | Execute JVM suite | All local tests pass | IDE Gradle runner: 10 passed, 0 skipped, 0 failed | PASS | Actual tool task summary |
| :app:lintDebug | Static Android lint | Lint task completes without fatal findings | IDE Gradle runner: Build finished successfully | PASS | Tool did not provide issue count/details in response; do not infer zero warnings |
| :app:assembleDebug | Package debug variant | Debug APK assembles | IDE Gradle runner: Build finished successfully | PASS | Does not prove install/runtime behavior |
| IDE latest app build/run | User-provided IDE build/run signal | App build/run succeeds | IDE context states success=true | PASS signal | User/IDE context; no device UI available in this audit |
| :app:connectedDebugAndroidTest | Run instrumentation suite | Execute all instrumented assertions on device | Gradle stopped before tests with “No connected devices!” | NOT EXECUTED | Device availability failure, not assertion failure |
| ExampleInstrumentedTest.useAppContext | Verify target package context on device | Package name equals com.example.securenotes | Not executed | NOT EXECUTED | app/src/androidTest/java/com/example/securenotes/ExampleInstrumentedTest.kt |
| SecureStorageInstrumentedTest.aesGcm_roundTripsAndRejectsTampering | Keystore AES-GCM round trip and tag rejection | Correct plaintext returned; modified ciphertext rejected | Not executed | NOT EXECUTED | Test source exists in app/src/androidTest/java/com/example/securenotes/SecureStorageInstrumentedTest.kt |
| SecureStorageInstrumentedTest.notesAreEncryptedOwnerScopedAndPersistAcrossHelperRestart | Raw SQLite ciphertext, account isolation, persistence | No plaintext in DB; owner access only; survive helper restart | Not executed | NOT EXECUTED | Test source exists; no actual DB value inspection occurred during audit |
| SecureStorageInstrumentedTest.notesSupportCreateReadUpdatePinAndDelete | Repository CRUD/pin | CRUD mutations persist and are owner scoped | Not executed | NOT EXECUTED | Test source exists |
| SecureStorageInstrumentedTest.providerResolverReturnsOnlyOwnedCiphertextRows | ContentResolver/provider | Active owner rows and ciphertext only | Not executed | NOT EXECUTED | Test source exists |
| SecureStorageInstrumentedTest.backupFileContainsCiphertextOnlyAndIsPrivate | Backup output | File inside filesDir and no note plaintext marker | Not executed | NOT EXECUTED | Test source exists |
| SecureStorageInstrumentedTest.packageReplacementReceiverClearsSessionWithoutStartingWork | Receiver behavior | Session cleared after package-replaced event | Not executed | NOT EXECUTED | Direct method test source exists; does not prove Android system delivery |
| SecureStorageInstrumentedTest.quoteHttpJsonXmlAndOfflineCacheAreHandled | JSON/XML/cache parsing | Parse JSON, local XML and cached fallback | Not executed | NOT EXECUTED | Fake connection test source exists |
| SecureStorageInstrumentedTest.quoteHttpFailureFallsBackToCachedPrompt | HTTP error handling | Reject 503 and preserve cached prompt | Not executed | NOT EXECUTED | Fake connection test source exists |
| SecureStorageInstrumentedTest.themeChoicePersistsAndAccountIsolationAppliesToMutations | Preference/owner behavior | Persist mode and reject cross-account update/delete | Not executed | NOT EXECUTED | Test source exists |
| Espresso login/registration/navigation/note/settings tests | UI workflows | Render controls and complete actions | No Espresso test class found | FAIL (missing suite) | Espresso dependency exists; tests need authoring and execution |
| :app:assembleRelease | Release packaging | Build optimized release variant | IDE Gradle operation remained active without result and was stopped; no success/failure result | NOT VERIFIED | No release APK/AAB claim |
| Signed APK/AAB install and smoke tests | Deployment | Install release and validate feature/security flows | No signing config/artifact/device available | NOT EXECUTED | Release readiness gap |

## Manual checks not performed

No device/emulator was connected; therefore all of the following remain NOT EXECUTED:

- Login and registration UI, password toggle and form errors.
- Navigation/back stack, FAB, note editing/deletion/undo/search/filter.
- Light/dark/system switch and restart persistence.
- Backup start, completion notification, notification approval/denial/permanent denial.
- Package update receiver delivery.
- Cellular operator and Wi-Fi-only/no-radio behavior.
- Live HTTP response, true offline mode, cache display and airplane-mode recovery.
- TalkBack, large font, keyboard/IME, small phone, tablet, landscape.
- Release install, signature, upgrade and backup restore.

