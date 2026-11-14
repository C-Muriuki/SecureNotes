package com.example.securenotes

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.res.XmlResourceParser
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.securenotes.database.DatabaseContract
import com.example.securenotes.database.DatabaseHelper
import com.example.securenotes.models.User
import com.example.securenotes.networking.WritingPrompt
import com.example.securenotes.networking.WritingPromptRepository
import com.example.securenotes.providers.NotesContract
import com.example.securenotes.receivers.AppUpdateReceiver
import com.example.securenotes.repositories.NoteRepository
import com.example.securenotes.repositories.NotesProviderClient
import com.example.securenotes.repositories.UserRepository
import com.example.securenotes.security.EncryptionManager
import com.example.securenotes.security.PasswordHasher
import com.example.securenotes.services.NoteBackupFileWriter
import com.example.securenotes.utils.Constants
import com.example.securenotes.utils.SessionManager
import com.example.securenotes.utils.ThemeUtils
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore

@RunWith(AndroidJUnit4::class)
class SecureStorageInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var helper: DatabaseHelper
    private lateinit var notes: NoteRepository
    private lateinit var sessions: SessionManager
    private var firstUser = -1L
    private var secondUser = -1L

    @Before fun setUp() {
        context.getSharedPreferences("writing_prompt_cache", Context.MODE_PRIVATE).edit().clear().commit()
        helper = DatabaseHelper(context)
        val users = UserRepository(helper)
        val suffix = System.nanoTime()
        firstUser = users.registerUser(User(fullName = "First", email = "first-$suffix@local.test", passwordHash = "hash", passwordSalt = "salt"))
        secondUser = users.registerUser(User(fullName = "Second", email = "second-$suffix@local.test", passwordHash = "hash", passwordSalt = "salt"))
        sessions = SessionManager(context)
        sessions.saveUserSession(firstUser, "first-$suffix@local.test", "First")
        notes = NoteRepository(helper)
    }

    @After fun tearDown() {
        sessions.clearSession()
        helper.close()
        context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun autoBackupRulesExcludeNotesSessionAndManualBackups() {
        val legacyExclusions = readBackupExclusions(R.xml.backup_rules).toSet()
        assertEquals(
            setOf(
                "database" to ".",
                "sharedpref" to "secure_notes_session_prefs.xml",
                "file" to "backups/"
            ),
            legacyExclusions
        )

        val extractionExclusions = readBackupExclusions(R.xml.data_extraction_rules)
        assertEquals(6, extractionExclusions.size)
        assertEquals(2, extractionExclusions.count { it == ("database" to ".") })
        assertEquals(2, extractionExclusions.count { it == ("sharedpref" to "secure_notes_session_prefs.xml") })
        assertEquals(2, extractionExclusions.count { it == ("file" to "backups/") })
    }

    @Test fun aesGcm_roundTripsAndRejectsTampering() {
        val manager = EncryptionManager()
        val encrypted = manager.encrypt(firstUser, "private note body")
        assertNotEquals("private note body", encrypted.ciphertext)
        assertEquals("private note body", manager.decrypt(firstUser, encrypted.ciphertext, encrypted.iv))
        val tampered = encrypted.ciphertext.dropLast(2) + if (encrypted.ciphertext.endsWith("AA")) "BB" else "AA"
        try {
            manager.decrypt(firstUser, tampered, encrypted.iv)
            fail("Modified ciphertext must fail authentication")
        } catch (_: EncryptionManager.NoteDecryptionException) { }

        try {
            manager.decrypt(firstUser, "not-base64", encrypted.iv)
            fail("Malformed ciphertext must be rejected")
        } catch (_: EncryptionManager.NoteDecryptionException) { }
    }

    @Test fun missingKeystoreKeyDoesNotRecreateKeyOrChangeStoredCiphertext() {
        val noteId = notes.createNote(firstUser, "Protected title", "Protected body")
        val before = storedCiphertext(noteId)
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val alias = "securenotes_user_$firstUser"
        assertTrue(keyStore.containsAlias(alias))

        keyStore.deleteEntry(alias)

        try {
            notes.getNote(firstUser, noteId)
            fail("Reading with a missing key must report key unavailability")
        } catch (_: EncryptionManager.NoteKeyUnavailableException) { }

        try {
            notes.updateNote(firstUser, noteId, "Replacement title", "Replacement body", false)
            fail("Updating with a missing key must be rejected")
        } catch (_: EncryptionManager.NoteKeyUnavailableException) { }

        try {
            notes.createNote(firstUser, "Another note", "Another body")
            fail("Creating beside encrypted rows with a missing key must be rejected")
        } catch (_: EncryptionManager.NoteKeyUnavailableException) { }

        assertFalse("Note operations must not create a replacement key", keyStore.containsAlias(alias))
        assertEquals("Encrypted database values must remain untouched", before, storedCiphertext(noteId))
    }

    @Test fun notesAreEncryptedOwnerScopedAndPersistAcrossHelperRestart() {
        val id = notes.createNote(firstUser, "Secret title", "Secret body", pinned = true)
        assertTrue(id > 0)
        val cursor = helper.readableDatabase.query(DatabaseContract.NoteTable.TABLE_NAME,
            arrayOf(DatabaseContract.NoteTable.COLUMN_TITLE, DatabaseContract.NoteTable.COLUMN_CONTENT),
            "${DatabaseContract.NoteTable.COLUMN_ID} = ? AND ${DatabaseContract.NoteTable.COLUMN_USER_ID} = ?",
            arrayOf(id.toString(), firstUser.toString()), null, null, null)
        cursor.use {
            assertTrue(it.moveToFirst())
            assertNotEquals("Secret title", it.getString(0))
            assertNotEquals("Secret body", it.getString(1))
            assertFalse(it.getString(0).contains("Secret"))
            assertFalse(it.getString(1).contains("Secret"))
        }
        assertNull(notes.getNote(secondUser, id))
        helper.close()
        helper = DatabaseHelper(context)
        notes = NoteRepository(helper)
        val loaded = notes.getNote(firstUser, id)
        assertEquals("Secret title", loaded?.title)
        assertEquals("Secret body", loaded?.content)
        assertTrue("Pin state must survive reopening SQLite", loaded?.isPinned == true)
    }

    @Test fun registeredPasswordsPersistAsDistinctSaltedHashesAndRejectDuplicates() {
        val suffix = System.nanoTime()
        val email = "auth-$suffix@local.test"
        val password = "S3cure!Password".toCharArray()
        val users = UserRepository(helper)
        val firstSalt = PasswordHasher.generateSalt()
        val firstHash = try {
            PasswordHasher.hashPassword(password, firstSalt)
        } finally {
            password.fill(Char.MIN_VALUE)
        }
        val firstId = users.registerUser(
            User(fullName = "Auth Test", email = email, passwordHash = firstHash, passwordSalt = firstSalt)
        )
        assertTrue(firstId > 0)

        val secondPassword = "S3cure!Password".toCharArray()
        val secondSalt = PasswordHasher.generateSalt()
        val secondHash = try {
            PasswordHasher.hashPassword(secondPassword, secondSalt)
        } finally {
            secondPassword.fill(Char.MIN_VALUE)
        }
        assertNotEquals("Each registration must use a fresh salt", firstSalt, secondSalt)
        assertTrue(users.registerUser(
            User(fullName = "Duplicate", email = email, passwordHash = secondHash, passwordSalt = secondSalt)
        ) < 0)

        helper.readableDatabase.query(
            DatabaseContract.UserTable.TABLE_NAME,
            arrayOf(DatabaseContract.UserTable.COLUMN_PASSWORD_HASH, DatabaseContract.UserTable.COLUMN_PASSWORD_SALT),
            "${DatabaseContract.UserTable.COLUMN_USER_ID} = ?",
            arrayOf(firstId.toString()),
            null,
            null,
            null
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertNotEquals("S3cure!Password", cursor.getString(0))
            assertNotEquals("S3cure!Password", cursor.getString(1))
            assertTrue(PasswordHasher.verifyPassword("S3cure!Password".toCharArray(), cursor.getString(0), cursor.getString(1)))
            assertFalse(PasswordHasher.verifyPassword("incorrect".toCharArray(), cursor.getString(0), cursor.getString(1)))
        }
    }

    @Test fun registrationLoginSearchAndDashboardQueriesUseExpectedData() {
        val suffix = System.nanoTime()
        val password = "P@ssword123".toCharArray()
        val salt = PasswordHasher.generateSalt()
        val hash = try {
            PasswordHasher.hashPassword(password, salt)
        } finally {
            password.fill(Char.MIN_VALUE)
        }
        val authRepository = UserRepository(helper)
        val userId = authRepository.registerUser(
            User(fullName = "Performance User", email = "performance-$suffix@local.test", passwordHash = hash, passwordSalt = salt)
        )
        assertTrue(userId > 0)
        val stored = authRepository.getUserByEmail("performance-$suffix@local.test")
        assertNotNull(stored)
        assertTrue(PasswordHasher.verifyPassword("P@ssword123".toCharArray(), stored!!.passwordHash, stored.passwordSalt))
        assertFalse(PasswordHasher.verifyPassword("WrongPassword1".toCharArray(), stored.passwordHash, stored.passwordSalt))

        notes.createNote(firstUser, "Needle one", "searchable body", pinned = true)
        notes.createNote(firstUser, "Needle two", "another body")
        notes.createNote(firstUser, "Other", "unrelated")
        assertEquals(3, notes.countNotes(firstUser))
        assertEquals(2, notes.getRecentNotes(firstUser, 2).size)
        assertEquals(1, notes.getNotes(firstUser, pinned = true, limit = 1).size)
        assertEquals(2, notes.getNotes(firstUser, search = "needle").size)
        assertEquals(1, notes.getNotes(firstUser, search = "searchable").size)
    }

    @Test fun dashboardPinnedNotesAreNotLimitedAndRemainAccountScoped() {
        repeat(8) { index ->
            notes.createNote(firstUser, "Pinned $index", "Body $index", pinned = true)
        }
        notes.createNote(secondUser, "Other user's pinned note", "Private", pinned = true)

        val pinnedForFirstUser = notes.getNotes(firstUser, pinned = true)

        assertEquals(8, pinnedForFirstUser.size)
        assertTrue(pinnedForFirstUser.all { it.userId == firstUser && it.isPinned })
        assertTrue(pinnedForFirstUser.zipWithNext().all { (newer, older) -> newer.updatedAt >= older.updatedAt })
    }

    @Test fun notesSupportCreateReadUpdatePinAndDelete() {
        val id = notes.createNote(firstUser, "Before", "Original")
        assertTrue(id > 0)
        assertEquals("Before", notes.getNote(firstUser, id)?.title)
        assertTrue(notes.updateNote(firstUser, id, "After", "Updated body", false))
        assertEquals("Updated body", notes.getNote(firstUser, id)?.content)
        assertTrue(notes.setPinned(firstUser, id, true))
        assertTrue(notes.getNote(firstUser, id)?.isPinned == true)
        assertFalse("Another account cannot change this note's pin state", notes.setPinned(secondUser, id, false))
        assertTrue(notes.setPinned(firstUser, id, false))
        assertFalse(notes.getNote(firstUser, id)?.isPinned == true)
        assertTrue(notes.setPinned(firstUser, id, true))
        assertTrue(notes.deleteNote(firstUser, id))
        assertNull(notes.getNote(firstUser, id))
    }

    @Test fun providerResolverReturnsOnlyOwnedCiphertextRows() {
        val id = notes.createNote(firstUser, "Resolver secret", "Provider body")
        val cursor = NotesProviderClient.queryOwnedNotes(context)
        assertNotNull(cursor)
        cursor!!.use {
            assertTrue(it.moveToFirst())
            assertEquals(id, it.getLong(it.getColumnIndexOrThrow(NotesContract.COLUMN_NOTE_ID)))
            assertFalse(it.getString(it.getColumnIndexOrThrow(NotesContract.COLUMN_ENCRYPTED_TITLE)).contains("Resolver secret"))
            assertFalse(it.getString(it.getColumnIndexOrThrow(NotesContract.COLUMN_ENCRYPTED_CONTENT)).contains("Provider body"))
        }
    }

    @Test fun providerResolverSupportsEncryptedCrudAndOwnerScoping() {
        notes.createNote(firstUser, "My private note", "First account only")
        notes.createNote(secondUser, "Other account note", "Must not be visible")
        val resolver = context.contentResolver
        val providerInfo = context.packageManager.resolveContentProvider(NotesContract.AUTHORITY, 0)
        assertNotNull(providerInfo)
        assertFalse(providerInfo!!.exported)
        assertFalse(providerInfo.grantUriPermissions)
        assertEquals(NotesContract.MIME_TYPE_NOTES, resolver.getType(NotesContract.CONTENT_URI))

        // Caller selection is ignored; provider scope remains the active authenticated owner.
        resolver.query(
            NotesContract.CONTENT_URI,
            arrayOf(NotesContract.COLUMN_USER_ID),
            "${NotesContract.COLUMN_USER_ID} = ?",
            arrayOf(secondUser.toString()),
            null
        )!!.use { cursor ->
            assertEquals(1, cursor.count)
            assertTrue(cursor.moveToFirst())
            assertEquals(firstUser, cursor.getLong(0))
        }

        val values = ContentValues().apply {
            put(NotesContract.COLUMN_USER_ID, secondUser)
            put(NotesContract.COLUMN_ENCRYPTED_TITLE, "cipher-title")
            put(NotesContract.COLUMN_TITLE_IV, "title-iv")
            put(NotesContract.COLUMN_ENCRYPTED_CONTENT, "cipher-body")
            put(NotesContract.COLUMN_CONTENT_IV, "content-iv")
            put(NotesContract.COLUMN_IS_PINNED, 0)
        }
        val insertedUri = resolver.insert(NotesContract.CONTENT_URI, values)
        assertNotNull(insertedUri)
        val itemUri = ContentUris.withAppendedId(NotesContract.CONTENT_URI, ContentUris.parseId(insertedUri!!))
        assertEquals(NotesContract.MIME_TYPE_NOTE, resolver.getType(itemUri))
        resolver.query(itemUri, null, null, null, null)!!.use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(firstUser, cursor.getLong(cursor.getColumnIndexOrThrow(NotesContract.COLUMN_USER_ID)))
            assertEquals("cipher-body", cursor.getString(cursor.getColumnIndexOrThrow(NotesContract.COLUMN_ENCRYPTED_CONTENT)))
        }

        val update = ContentValues().apply {
            put(NotesContract.COLUMN_ENCRYPTED_TITLE, "cipher-title-updated")
            put(NotesContract.COLUMN_UPDATED_AT, System.currentTimeMillis())
        }
        assertEquals(1, resolver.update(itemUri, update, null, null))
        assertEquals(1, resolver.delete(itemUri, null, null))
        resolver.query(itemUri, arrayOf(NotesContract.COLUMN_NOTE_ID), null, null, null)!!.use {
            assertEquals(0, it.count)
        }
    }

    @Test fun backupWriterRemovesPartialFileWhenInterrupted() {
        notes.createNote(firstUser, "Private", "Interrupted export")
        Thread.currentThread().interrupt()
        val interrupted = try {
            NoteBackupFileWriter().writeBackup(context, helper, firstUser)
            false
        } catch (_: InterruptedException) {
            true
        } finally {
            Thread.interrupted()
        }
        assertTrue(interrupted)
        val backupDirectory = java.io.File(context.filesDir, NoteBackupFileWriter.BACKUP_DIRECTORY)
        assertTrue(backupDirectory.listFiles { _, name -> name.endsWith(".tmp") }.orEmpty().isEmpty())
    }

    @Test fun backupFileContainsCiphertextOnlyAndIsPrivate() {
        notes.createNote(firstUser, "Backup plaintext marker", "Backup content marker")
        val backup = NoteBackupFileWriter().writeBackup(context, helper, firstUser)
        assertTrue(backup.canonicalPath.startsWith(context.filesDir.canonicalPath))
        val json = backup.readText()
        assertTrue(json.contains("encryptedContent"))
        assertFalse(json.contains("Backup plaintext marker"))
        assertFalse(json.contains("Backup content marker"))
        backup.delete()
    }

    @Test fun packageReplacementReceiverClearsSessionWithoutStartingWork() {
        assertTrue(sessions.isLoggedIn())
        AppUpdateReceiver().onReceive(context, Intent(Intent.ACTION_MY_PACKAGE_REPLACED))
        assertFalse(sessions.isLoggedIn())
    }

    @Test fun quoteHttpJsonXmlAndOfflineCacheAreHandled() {
        val response = FakeHttpConnection(
            URL("https://example.invalid/quote"),
            200,
            """{"quote":"Keep writing","author":"A. Writer"}"""
        )
        val parsed = WritingPromptRepository.parseHttpResponse(response)
        assertEquals("Keep writing", parsed.text)
        assertEquals("A. Writer", parsed.author)
        assertFalse(parsed.isOffline)

        val xmlFallback = WritingPromptRepository.offlinePrompt(context)
        assertTrue(xmlFallback.isOffline)
        assertTrue(xmlFallback.text.isNotBlank())

        WritingPromptRepository.cachePrompt(context, WritingPrompt("Cached inspiration", "Local author", false))
        val cached = WritingPromptRepository.offlinePrompt(context)
        assertEquals("Cached inspiration", cached.text)
        assertTrue(cached.isOffline)
    }

    @Test fun quoteResponseLimitStopsReadingBeforeTheEntireBody() {
        val oversizedQuote = "x".repeat(WritingPromptRepository.MAX_RESPONSE_CHARS * 4)
        val body = """{"quote":"$oversizedQuote","author":"A. Writer"}"""
        val response = FakeHttpConnection(URL("https://example.invalid/large"), 200, body)
        try {
            WritingPromptRepository.parseHttpResponse(response)
            fail("An oversized response must be rejected")
        } catch (_: IllegalArgumentException) { }
        assertTrue(response.bytesRead < body.toByteArray(Charsets.UTF_8).size)
    }

    @Test fun quoteHttpFailureFallsBackToCachedPrompt() {
        WritingPromptRepository.cachePrompt(context, WritingPrompt("Previously cached", null, false))
        val unavailable = FakeHttpConnection(URL("https://example.invalid/quote"), 503, "")
        try {
            WritingPromptRepository.parseHttpResponse(unavailable)
            fail("Non-success HTTP response must be rejected")
        } catch (_: IllegalStateException) { }
        assertEquals("Previously cached", WritingPromptRepository.offlinePrompt(context).text)
    }

    @Test fun themeChoicePersistsAndAccountIsolationAppliesToMutations() {
        val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(Constants.KEY_THEME_MODE, Constants.THEME_MODE_DARK).commit()
        ThemeUtils.applySavedTheme(context)
        assertEquals(Constants.THEME_MODE_DARK, prefs.getInt(Constants.KEY_THEME_MODE, -1))
        val id = notes.createNote(firstUser, "Title", "Body")
        assertFalse(notes.updateNote(secondUser, id, "Other", "Other", false))
        assertFalse(notes.deleteNote(secondUser, id))
        assertEquals(1, notes.getNotes(firstUser).size)
        assertTrue(notes.setPinned(firstUser, id, true))
        assertTrue(notes.getNotes(firstUser, pinned = true).single().isPinned)
    }

    private fun readBackupExclusions(resourceId: Int): List<Pair<String, String>> {
        val parser: XmlResourceParser = context.resources.getXml(resourceId)
        val result = mutableListOf<Pair<String, String>>()
        try {
            var event = parser.eventType
            while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (event == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "exclude") {
                    result += parser.getAttributeValue(null, "domain") to
                        parser.getAttributeValue(null, "path")
                }
                event = parser.next()
            }
        } finally {
            parser.close()
        }
        return result
    }

    private fun storedCiphertext(noteId: Long): Pair<String, String> {
        val columns = arrayOf(DatabaseContract.NoteTable.COLUMN_TITLE, DatabaseContract.NoteTable.COLUMN_CONTENT)
        return helper.readableDatabase.query(
            DatabaseContract.NoteTable.TABLE_NAME,
            columns,
            "${DatabaseContract.NoteTable.COLUMN_ID} = ? AND ${DatabaseContract.NoteTable.COLUMN_USER_ID} = ?",
            arrayOf(noteId.toString(), firstUser.toString()),
            null,
            null,
            null
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getString(0) to cursor.getString(1)
        }
    }

    private class FakeHttpConnection(
        url: URL,
        private val status: Int,
        body: String
    ) : HttpURLConnection(url) {
        private val trackedInput = CountingInputStream(body.toByteArray(Charsets.UTF_8))
        val bytesRead: Int get() = trackedInput.bytesRead

        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy(): Boolean = false
        override fun getResponseCode(): Int = status
        override fun getInputStream() = trackedInput
    }

    private class CountingInputStream(bytes: ByteArray) : FilterInputStream(ByteArrayInputStream(bytes)) {
        var bytesRead: Int = 0
            private set

        override fun read(): Int = super.read().also { if (it >= 0) bytesRead++ }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            super.read(buffer, offset, length).also { if (it > 0) bytesRead += it }
    }
}
