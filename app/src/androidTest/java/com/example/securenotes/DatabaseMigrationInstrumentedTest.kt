package com.example.securenotes

import android.content.ContentValues
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.securenotes.database.DatabaseContract
import com.example.securenotes.database.DatabaseHelper
import com.example.securenotes.security.EncryptionManager
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@RunWith(AndroidJUnit4::class)
class DatabaseMigrationInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val disposableDatabases = mutableSetOf<String>()
    private val disposableKeyAliases = mutableSetOf<String>()

    @After
    fun removeDisposableDatabases() {
        disposableDatabases.forEach(context::deleteDatabase)
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        disposableKeyAliases.forEach { alias ->
            if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
        }
    }

    @Test
    fun representativeVersion3FixtureMigratesAndPreservesExistingRows() {
        val name = disposableName("fixture")
        createVersion3Fixture(name)
        val before = openRaw(name).use(::snapshot)

        DatabaseHelper(context, name).use { helper ->
            val db = helper.writableDatabase
            assertEquals(4, db.version)
            assertSnapshotEquals(before, snapshot(db))
            assertAddedColumnsHaveSafeDefaults(db)
            assertV4ObjectsExist(db)
            assertEquals(1, countNotes(db, FIXTURE_OWNER_ONE, true))
            assertEquals(1, countNotes(db, FIXTURE_OWNER_ONE, false))
            assertEquals(1, countNotes(db, FIXTURE_OWNER_TWO, true))
            assertEquals(0, db.rawQuery("PRAGMA foreign_key_check", null).use { it.count })
            db.beginTransaction()
            try {
                assertConnectionConstraintsAndCascade(db)
            } finally {
                // Constraint checks are rolled back so the fixture remains a stable baseline.
                db.endTransaction()
            }
        }

        DatabaseHelper(context, name).use { reopened ->
            assertEquals("Reopening must not rerun or damage the migration", 4, reopened.readableDatabase.version)
            assertSnapshotEquals(before, snapshot(reopened.readableDatabase))
        }
    }

    @Test
    fun encryptedSyntheticVersion3FixtureMigratesWithoutChangingCiphertextOrKeys() {
        val name = disposableName("encrypted_fixture")
        val ownerOne = 9_200_000_000_000_001L
        val ownerTwo = 9_200_000_000_000_002L
        val keyAliases = mapOf(
            ownerOne to createTestOnlyKeyAlias(ownerOne),
            ownerTwo to createTestOnlyKeyAlias(ownerTwo)
        )
        createEncryptedVersion3Fixture(name, ownerOne, ownerTwo, keyAliases)
        val before = openRaw(name).use(::snapshot)
        val keyStoreBefore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        keyAliases.values.forEach { assertTrue("The test-only key must exist before migration", keyStoreBefore.containsAlias(it)) }

        DatabaseHelper(context, name).use { helper ->
            val db = helper.writableDatabase
            assertEquals(4, db.version)
            assertSnapshotEquals(before, snapshot(db))
            assertAddedColumnsHaveSafeDefaults(db)
            assertV4ObjectsExist(db)
            assertEquals(2, rowCount(db, DatabaseContract.UserTable.TABLE_NAME))
            assertEquals(3, rowCount(db, DatabaseContract.NoteTable.TABLE_NAME))
            assertEquals(0, db.rawQuery("PRAGMA foreign_key_check", null).use { it.count })

            val keyStoreAfter = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            keyAliases.forEach { (ownerId, alias) ->
                assertTrue("Migration must not replace or remove a test-only key", keyStoreAfter.containsAlias(alias))
                val key = keyStoreAfter.getKey(alias, null) as SecretKey
                db.query(
                    DatabaseContract.NoteTable.TABLE_NAME,
                    arrayOf(
                        DatabaseContract.NoteTable.COLUMN_ID,
                        DatabaseContract.NoteTable.COLUMN_USER_ID,
                        DatabaseContract.NoteTable.COLUMN_TITLE,
                        DatabaseContract.NoteTable.COLUMN_TITLE_IV,
                        DatabaseContract.NoteTable.COLUMN_CONTENT,
                        DatabaseContract.NoteTable.COLUMN_CONTENT_IV
                    ),
                    "${DatabaseContract.NoteTable.COLUMN_USER_ID} = ?",
                    arrayOf(ownerId.toString()),
                    null,
                    null,
                    DatabaseContract.NoteTable.COLUMN_ID
                ).use { cursor ->
                    var verified = 0
                    while (cursor.moveToNext()) {
                        val noteId = cursor.getLong(0)
                        assertEquals(ownerId, cursor.getLong(1))
                        assertEquals(
                            "Synthetic title $noteId",
                            decryptTestValue(key, cursor.getString(2), cursor.getString(3))
                        )
                        assertEquals(
                            "Synthetic content $noteId",
                            decryptTestValue(key, cursor.getString(4), cursor.getString(5))
                        )
                        verified++
                    }
                    assertTrue("Each synthetic owner must have encrypted notes verified", verified > 0)
                }
            }
        }

        DatabaseHelper(context, name).use { reopened ->
            assertEquals(4, reopened.readableDatabase.version)
            assertSnapshotEquals(before, snapshot(reopened.readableDatabase))
        }
    }

    @Test
    fun migrationFailureDuringTableCreationRollsBackAndRetrySucceeds() {
        assertFailureRollsBackAndRetrySucceeds(
            blockerSetup = "CREATE TABLE ${DatabaseContract.NoteConnectionTable.TABLE_NAME} (collision TEXT)",
            blockerCleanup = "DROP TABLE ${DatabaseContract.NoteConnectionTable.TABLE_NAME}"
        )
    }

    @Test
    fun migrationFailureDuringIndexCreationRollsBackTablesAndColumnsAndRetrySucceeds() {
        assertFailureRollsBackAndRetrySucceeds(
            blockerSetup = """
                CREATE TABLE migration_index_blocker (value TEXT);
                CREATE INDEX ${DatabaseContract.Index.NOTES_OWNER_IDEA_STAGE} ON migration_index_blocker(value)
            """.trimIndent(),
            blockerCleanup = """
                DROP INDEX ${DatabaseContract.Index.NOTES_OWNER_IDEA_STAGE};
                DROP TABLE migration_index_blocker
            """.trimIndent()
        )
    }

    @Test
    fun actualVersion3DatabaseCopyMigratesAndPreservesAllStoredValues() {
        val copiedName = disposableName("actual_v3_copy")
        val sourceFile = context.getDatabasePath(DatabaseContract.DATABASE_NAME)
        assumeTrue("The installed application database is unavailable", sourceFile.isFile)

        val sourceDb = SQLiteDatabase.openDatabase(
            sourceFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY
        )
        val originalVersion: Int
        val originalSnapshot: Snapshot
        val userCount: Int
        val noteCount: Int
        try {
            originalVersion = sourceDb.version
            assumeTrue("The device database is not version 3", originalVersion == 3)
            val journal = File(sourceFile.path + "-journal")
            val wal = File(sourceFile.path + "-wal")
            assumeTrue(
                "A live SQLite sidecar prevents a safe raw copy; close the app and retry",
                (!journal.exists() || journal.length() == 0L) &&
                    (!wal.exists() || wal.length() == 0L)
            )
            originalSnapshot = snapshot(sourceDb)
            userCount = rowCount(sourceDb, DatabaseContract.UserTable.TABLE_NAME)
            noteCount = rowCount(sourceDb, DatabaseContract.NoteTable.TABLE_NAME)
            assumeTrue(
                "The real database has no user or note rows to validate",
                userCount >= 1 && noteCount >= 1
            )
        } finally {
            sourceDb.close()
        }

        // A real user database is only read here. All migration writes target this disposable copy.
        sourceFile.copyTo(context.getDatabasePath(copiedName), overwrite = true)
        DatabaseHelper(context, copiedName).use { helper ->
            val migrated = helper.writableDatabase
            assertEquals(4, migrated.version)
            assertSnapshotEquals(originalSnapshot, snapshot(migrated))
            assertAddedColumnsHaveSafeDefaults(migrated)
            assertV4ObjectsExist(migrated)
            assertEquals(0, migrated.rawQuery("PRAGMA foreign_key_check", null).use { it.count })
        }

        val sourceAfter = SQLiteDatabase.openDatabase(sourceFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        try {
            assertEquals("The original device database must remain at v3", originalVersion, sourceAfter.version)
            assertSnapshotEquals(originalSnapshot, snapshot(sourceAfter))
        } finally {
            sourceAfter.close()
        }

    }

    @Test
    fun actualVersion3CopyEncryptedNotesRemainReadableWithOriginalKeystoreKeys() {
        val copiedName = disposableName("actual_v3_decrypt_copy")
        val sourceFile = context.getDatabasePath(DatabaseContract.DATABASE_NAME)
        assumeTrue("The installed application database is unavailable", sourceFile.isFile)

        val source = SQLiteDatabase.openDatabase(sourceFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        val sourceVersion: Int
        val sourceSnapshot: Snapshot
        try {
            sourceVersion = source.version
            assumeTrue("The device database is not version 3", sourceVersion == 3)
            val noteCount = rowCount(source, DatabaseContract.NoteTable.TABLE_NAME)
            assumeTrue("The device database contains no encrypted notes", noteCount > 0)
            val owners = mutableSetOf<Long>()
            source.query(
                DatabaseContract.NoteTable.TABLE_NAME,
                arrayOf(DatabaseContract.NoteTable.COLUMN_USER_ID),
                null,
                null,
                null,
                null,
                null
            ).use { cursor -> while (cursor.moveToNext()) owners += cursor.getLong(0) }
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            assumeTrue(
                "A matching original Keystore key is unavailable for at least one stored owner",
                owners.all { keyStore.containsAlias("securenotes_user_$it") }
            )
            val journal = File(sourceFile.path + "-journal")
            val wal = File(sourceFile.path + "-wal")
            assumeTrue(
                "A live SQLite sidecar prevents a safe raw copy; close the app and retry",
                (!journal.exists() || journal.length() == 0L) &&
                    (!wal.exists() || wal.length() == 0L)
            )
            sourceSnapshot = snapshot(source)
        } finally {
            source.close()
        }

        sourceFile.copyTo(context.getDatabasePath(copiedName), overwrite = true)
        DatabaseHelper(context, copiedName).use { helper ->
            val db = helper.readableDatabase
            assertEquals(4, db.version)
            assertSnapshotEquals(sourceSnapshot, snapshot(db))
            val manager = EncryptionManager()
            db.query(
                DatabaseContract.NoteTable.TABLE_NAME,
                arrayOf(
                    DatabaseContract.NoteTable.COLUMN_USER_ID,
                    DatabaseContract.NoteTable.COLUMN_TITLE,
                    DatabaseContract.NoteTable.COLUMN_TITLE_IV,
                    DatabaseContract.NoteTable.COLUMN_CONTENT,
                    DatabaseContract.NoteTable.COLUMN_CONTENT_IV
                ),
                null,
                null,
                null,
                null,
                DatabaseContract.NoteTable.COLUMN_ID
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                do {
                    val ownerId = cursor.getLong(0)
                    // Plaintext is deliberately kept inside this assertion path and is never logged.
                    val title = manager.decrypt(ownerId, cursor.getString(1), cursor.getString(2))
                    val content = manager.decrypt(ownerId, cursor.getString(3), cursor.getString(4))
                    assertNotNull(title)
                    assertNotNull(content)
                } while (cursor.moveToNext())
            }
        }

        val original = SQLiteDatabase.openDatabase(sourceFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        try {
            assertEquals("The original must remain at its starting version", sourceVersion, original.version)
            assertSnapshotEquals(sourceSnapshot, snapshot(original))
        } finally {
            original.close()
        }
    }

    private fun assertFailureRollsBackAndRetrySucceeds(blockerSetup: String, blockerCleanup: String) {
        val name = disposableName("rollback")
        createVersion3Fixture(name)
        val before = openRaw(name).use(::snapshot)
        openRaw(name).use { db -> blockerSetup.split(';').map(String::trim).filter(String::isNotEmpty).forEach(db::execSQL) }

        val failedHelper = DatabaseHelper(context, name)
        try {
            failedHelper.writableDatabase
            fail("The deliberately conflicting schema object should fail the upgrade")
        } catch (_: SQLiteException) {
            // SQLiteOpenHelper must roll back every preceding migration statement.
        } finally {
            failedHelper.close()
        }

        openRaw(name).use { db ->
            assertEquals("Failed upgrade must not advance user_version", 3, db.version)
            assertFalse(hasColumn(db, DatabaseContract.NoteTable.TABLE_NAME, DatabaseContract.NoteTable.COLUMN_IS_IDEA))
            val connectionTableIsTheDeliberateBlocker =
                blockerSetup.contains("CREATE TABLE ${DatabaseContract.NoteConnectionTable.TABLE_NAME}")
            assertEquals(
                "Rollback must retain only the pre-existing blocker, not a partial migration table",
                connectionTableIsTheDeliberateBlocker,
                tableExists(db, DatabaseContract.NoteConnectionTable.TABLE_NAME)
            )
            assertFalse(tableExists(db, DatabaseContract.NoteReviewQueue.TABLE_NAME))
            assertSnapshotEquals(before, snapshot(db))
            blockerCleanup.split(';').map(String::trim).filter(String::isNotEmpty).forEach(db::execSQL)
        }

        DatabaseHelper(context, name).use { retry ->
            assertEquals(4, retry.writableDatabase.version)
            assertAddedColumnsHaveSafeDefaults(retry.readableDatabase)
            assertV4ObjectsExist(retry.readableDatabase)
            assertSnapshotEquals(before, snapshot(retry.readableDatabase))
        }
    }

    private fun createTestOnlyKeyAlias(ownerId: Long): String {
        val alias = "securenotes_stage2_test_${System.nanoTime()}_$ownerId"
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        assertFalse("Refusing to reuse an existing Keystore alias", keyStore.containsAlias(alias))
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        generator.generateKey()
        disposableKeyAliases += alias
        return alias
    }

    private fun encryptTestValue(key: SecretKey, plaintext: String): Pair<String, String> {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val ciphertext = Base64.encodeToString(cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        return ciphertext to iv
    }

    private fun decryptTestValue(key: SecretKey, ciphertext: String, iv: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            key,
            GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP))
        )
        return cipher.doFinal(Base64.decode(ciphertext, Base64.NO_WRAP)).toString(Charsets.UTF_8)
    }

    private fun createEncryptedVersion3Fixture(
        name: String,
        ownerOne: Long,
        ownerTwo: Long,
        keyAliases: Map<Long, String>
    ) {
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        file.delete()
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val db = SQLiteDatabase.openOrCreateDatabase(file.absolutePath, null)
        try {
            db.setForeignKeyConstraintsEnabled(true)
            db.execSQL(DatabaseContract.UserTable.CREATE_TABLE)
            db.execSQL(
                """CREATE TABLE notes (
                    note_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id INTEGER NOT NULL,
                    encrypted_title TEXT NOT NULL,
                    title_iv TEXT NOT NULL,
                    encrypted_content TEXT NOT NULL,
                    content_iv TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    is_pinned INTEGER NOT NULL DEFAULT 0,
                    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
                )"""
            )
            db.insertOrThrow(DatabaseContract.UserTable.TABLE_NAME, null, userValues(ownerOne, "synthetic-one"))
            db.insertOrThrow(DatabaseContract.UserTable.TABLE_NAME, null, userValues(ownerTwo, "synthetic-two"))
            listOf(
                Triple(301L, ownerOne, true),
                Triple(302L, ownerOne, false),
                Triple(401L, ownerTwo, true)
            ).forEach { (noteId, ownerId, pinned) ->
                val key = keyStore.getKey(keyAliases.getValue(ownerId), null) as SecretKey
                val (encryptedTitle, titleIv) = encryptTestValue(key, "Synthetic title $noteId")
                val (encryptedContent, contentIv) = encryptTestValue(key, "Synthetic content $noteId")
                val values = ContentValues().apply {
                    put(DatabaseContract.NoteTable.COLUMN_ID, noteId)
                    put(DatabaseContract.NoteTable.COLUMN_USER_ID, ownerId)
                    put(DatabaseContract.NoteTable.COLUMN_TITLE, encryptedTitle)
                    put(DatabaseContract.NoteTable.COLUMN_TITLE_IV, titleIv)
                    put(DatabaseContract.NoteTable.COLUMN_CONTENT, encryptedContent)
                    put(DatabaseContract.NoteTable.COLUMN_CONTENT_IV, contentIv)
                    put(DatabaseContract.NoteTable.COLUMN_CREATED_AT, 1_700_000_100_000L + noteId)
                    put(DatabaseContract.NoteTable.COLUMN_UPDATED_AT, 1_700_000_200_000L + noteId)
                    put(DatabaseContract.NoteTable.COLUMN_IS_PINNED, if (pinned) 1 else 0)
                }
                db.insertOrThrow(DatabaseContract.NoteTable.TABLE_NAME, null, values)
            }
            db.version = 3
        } finally {
            db.close()
        }
    }

    private fun createVersion3Fixture(name: String) {
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        file.delete()
        val db = SQLiteDatabase.openOrCreateDatabase(file.absolutePath, null)
        try {
            db.setForeignKeyConstraintsEnabled(true)
            db.execSQL(DatabaseContract.UserTable.CREATE_TABLE)
            db.execSQL(
                """CREATE TABLE notes (
                    note_id INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_id INTEGER NOT NULL,
                    encrypted_title TEXT NOT NULL,
                    title_iv TEXT NOT NULL,
                    encrypted_content TEXT NOT NULL,
                    content_iv TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL,
                    is_pinned INTEGER NOT NULL DEFAULT 0,
                    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
                )"""
            )
            db.insertOrThrow(DatabaseContract.UserTable.TABLE_NAME, null, userValues(FIXTURE_OWNER_ONE, "first"))
            db.insertOrThrow(DatabaseContract.UserTable.TABLE_NAME, null, userValues(FIXTURE_OWNER_TWO, "second"))
            db.insertOrThrow(DatabaseContract.NoteTable.TABLE_NAME, null, noteValues(101, FIXTURE_OWNER_ONE, true))
            db.insertOrThrow(DatabaseContract.NoteTable.TABLE_NAME, null, noteValues(102, FIXTURE_OWNER_ONE, false))
            db.insertOrThrow(DatabaseContract.NoteTable.TABLE_NAME, null, noteValues(202, FIXTURE_OWNER_TWO, true))
            db.version = 3
        } finally {
            db.close()
        }
    }

    private fun userValues(id: Long, suffix: String) = ContentValues().apply {
        put(DatabaseContract.UserTable.COLUMN_USER_ID, id)
        put(DatabaseContract.UserTable.COLUMN_FULL_NAME, "Fixture $suffix")
        put(DatabaseContract.UserTable.COLUMN_EMAIL, "$suffix@example.invalid")
        put(DatabaseContract.UserTable.COLUMN_PASSWORD_HASH, "fixture-hash-$suffix")
        put(DatabaseContract.UserTable.COLUMN_PASSWORD_SALT, "fixture-salt-$suffix")
        put(DatabaseContract.UserTable.COLUMN_CREATED_AT, 1_700_000_000_000L + id)
    }

    private fun noteValues(id: Long, owner: Long, pinned: Boolean) = ContentValues().apply {
        put(DatabaseContract.NoteTable.COLUMN_ID, id)
        put(DatabaseContract.NoteTable.COLUMN_USER_ID, owner)
        put(DatabaseContract.NoteTable.COLUMN_TITLE, "Q0lQSEVSX1RJVExFXyR7aWR9")
        put(DatabaseContract.NoteTable.COLUMN_TITLE_IV, "VElUTEVfSVZfJHtpZH0=")
        put(DatabaseContract.NoteTable.COLUMN_CONTENT, "Q0lQSEVSX0NPTlRFTlRfJHtpZH0=")
        put(DatabaseContract.NoteTable.COLUMN_CONTENT_IV, "Q09OVEVOVF9JVl8ke2lkfQ==")
        put(DatabaseContract.NoteTable.COLUMN_CREATED_AT, 1_700_000_100_000L + id)
        put(DatabaseContract.NoteTable.COLUMN_UPDATED_AT, 1_700_000_200_000L + id)
        put(DatabaseContract.NoteTable.COLUMN_IS_PINNED, if (pinned) 1 else 0)
    }

    private fun assertConnectionConstraintsAndCascade(db: SQLiteDatabase) {
        val valid = ContentValues().apply {
            put(DatabaseContract.NoteConnectionTable.COLUMN_OWNER_ID, FIXTURE_OWNER_ONE)
            put(DatabaseContract.NoteConnectionTable.COLUMN_SOURCE_NOTE_ID, 101)
            put(DatabaseContract.NoteConnectionTable.COLUMN_TARGET_NOTE_ID, 102)
            put(DatabaseContract.NoteConnectionTable.COLUMN_CREATED_AT, 1_700_000_300_000L)
        }
        val connectionId = db.insertOrThrow(DatabaseContract.NoteConnectionTable.TABLE_NAME, null, valid)
        assertTrue(connectionId >= 0)
        assertConstraintFailure { db.insertOrThrow(DatabaseContract.NoteConnectionTable.TABLE_NAME, null, valid) }

        val selfLink = ContentValues(valid).apply {
            put(DatabaseContract.NoteConnectionTable.COLUMN_TARGET_NOTE_ID, 101)
        }
        assertConstraintFailure { db.insertOrThrow(DatabaseContract.NoteConnectionTable.TABLE_NAME, null, selfLink) }

        val crossOwnerLink = ContentValues(valid).apply {
            put(DatabaseContract.NoteConnectionTable.COLUMN_TARGET_NOTE_ID, 202)
        }
        assertConstraintFailure { db.insertOrThrow(DatabaseContract.NoteConnectionTable.TABLE_NAME, null, crossOwnerLink) }

        assertEquals(
            1,
            db.delete(
                DatabaseContract.NoteTable.TABLE_NAME,
                "${DatabaseContract.NoteTable.COLUMN_ID} = ? AND ${DatabaseContract.NoteTable.COLUMN_USER_ID} = ?",
                arrayOf("101", FIXTURE_OWNER_ONE.toString())
            )
        )
        assertEquals(0, rowCount(db, DatabaseContract.NoteConnectionTable.TABLE_NAME))
    }

    private fun assertConstraintFailure(operation: () -> Unit) {
        try {
            operation()
            fail("The database constraint should reject this row")
        } catch (_: SQLiteConstraintException) {
            // Expected: schema constraints protect ownership and canonical pairs.
        }
    }

    private fun assertAddedColumnsHaveSafeDefaults(db: SQLiteDatabase) {
        db.query(
            DatabaseContract.NoteTable.TABLE_NAME,
            arrayOf(
                DatabaseContract.NoteTable.COLUMN_IS_IDEA,
                DatabaseContract.NoteTable.COLUMN_IDEA_STAGE,
                DatabaseContract.NoteTable.COLUMN_LAST_OPENED_AT
            ),
            null,
            null,
            null,
            null,
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                assertEquals(0, cursor.getInt(0))
                assertEquals(0, cursor.getInt(1))
                assertTrue(cursor.isNull(2))
            }
        }
    }

    private fun assertV4ObjectsExist(db: SQLiteDatabase) {
        assertTrue(hasColumn(db, DatabaseContract.NoteTable.TABLE_NAME, DatabaseContract.NoteTable.COLUMN_IS_IDEA))
        assertTrue(hasColumn(db, DatabaseContract.NoteTable.TABLE_NAME, DatabaseContract.NoteTable.COLUMN_IDEA_STAGE))
        assertTrue(hasColumn(db, DatabaseContract.NoteTable.TABLE_NAME, DatabaseContract.NoteTable.COLUMN_LAST_OPENED_AT))
        listOf(
            DatabaseContract.NoteConnectionTable.TABLE_NAME,
            DatabaseContract.NoteReviewQueue.TABLE_NAME
        ).forEach { assertTrue("Missing table $it", tableExists(db, it)) }
        listOf(
            DatabaseContract.Index.NOTES_OWNER_NOTE,
            DatabaseContract.Index.NOTES_OWNER_IDEA_STAGE,
            DatabaseContract.Index.CONNECTIONS_OWNER_TARGET,
            DatabaseContract.Index.REVIEW_OWNER_STATUS_DUE
        ).forEach { assertTrue("Missing index $it", indexExists(db, it)) }
    }

    private fun snapshot(db: SQLiteDatabase) = Snapshot(
        users = rowDigests(
            db,
            DatabaseContract.UserTable.TABLE_NAME,
            arrayOf(
                DatabaseContract.UserTable.COLUMN_USER_ID,
                DatabaseContract.UserTable.COLUMN_FULL_NAME,
                DatabaseContract.UserTable.COLUMN_EMAIL,
                DatabaseContract.UserTable.COLUMN_PASSWORD_HASH,
                DatabaseContract.UserTable.COLUMN_PASSWORD_SALT,
                DatabaseContract.UserTable.COLUMN_CREATED_AT
            ),
            DatabaseContract.UserTable.COLUMN_USER_ID
        ),
        notes = rowDigests(
            db,
            DatabaseContract.NoteTable.TABLE_NAME,
            arrayOf(
                DatabaseContract.NoteTable.COLUMN_ID,
                DatabaseContract.NoteTable.COLUMN_USER_ID,
                DatabaseContract.NoteTable.COLUMN_TITLE,
                DatabaseContract.NoteTable.COLUMN_TITLE_IV,
                DatabaseContract.NoteTable.COLUMN_CONTENT,
                DatabaseContract.NoteTable.COLUMN_CONTENT_IV,
                DatabaseContract.NoteTable.COLUMN_CREATED_AT,
                DatabaseContract.NoteTable.COLUMN_UPDATED_AT,
                DatabaseContract.NoteTable.COLUMN_IS_PINNED
            ),
            DatabaseContract.NoteTable.COLUMN_ID
        )
    )

    private fun rowDigests(db: SQLiteDatabase, table: String, columns: Array<String>, orderBy: String): List<String> {
        val digests = mutableListOf<String>()
        db.query(table, columns, null, null, null, null, orderBy).use { cursor ->
            while (cursor.moveToNext()) {
                val digest = MessageDigest.getInstance("SHA-256")
                for (index in columns.indices) {
                    if (cursor.isNull(index)) {
                        digest.update(byteArrayOf(0))
                    } else {
                        digest.update(byteArrayOf(1))
                        val bytes = when (cursor.getType(index)) {
                            1 -> cursor.getLong(index).toString().toByteArray()
                            2 -> cursor.getDouble(index).toString().toByteArray()
                            3 -> cursor.getString(index).toByteArray(Charsets.UTF_8)
                            4 -> cursor.getBlob(index)
                            else -> byteArrayOf()
                        }
                        digest.update(bytes.size.toString().toByteArray())
                        digest.update(byteArrayOf(0))
                        digest.update(bytes)
                    }
                }
                digests += digest.digest().joinToString("") { byte -> "%02x".format(byte) }
            }
        }
        return digests
    }

    private fun assertSnapshotEquals(expected: Snapshot, actual: Snapshot) {
        assertTrue("User rows or authentication fields changed", expected.users == actual.users)
        assertTrue("Note rows, ciphertext, IVs, ownership, timestamps or pin states changed", expected.notes == actual.notes)
    }

    private fun openRaw(name: String): SQLiteDatabase =
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).absolutePath, null, SQLiteDatabase.OPEN_READWRITE)

    private fun rowCount(db: SQLiteDatabase, table: String): Int =
        db.rawQuery("SELECT COUNT(*) FROM $table", null).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    private fun countNotes(db: SQLiteDatabase, ownerId: Long, pinned: Boolean): Int =
        db.query(
            DatabaseContract.NoteTable.TABLE_NAME,
            arrayOf(DatabaseContract.NoteTable.COLUMN_ID),
            "${DatabaseContract.NoteTable.COLUMN_USER_ID} = ? AND " +
                "${DatabaseContract.NoteTable.COLUMN_IS_PINNED} = ?",
            arrayOf(ownerId.toString(), if (pinned) "1" else "0"),
            null,
            null,
            null
        ).use { it.count }

    private fun hasColumn(db: SQLiteDatabase, table: String, column: String): Boolean =
        db.rawQuery("PRAGMA table_info($table)", null).use { cursor ->
            generateSequence { if (cursor.moveToNext()) cursor.getString(1) else null }.any { it == column }
        }

    private fun tableExists(db: SQLiteDatabase, table: String): Boolean =
        schemaObjectExists(db, "table", table)

    private fun indexExists(db: SQLiteDatabase, index: String): Boolean =
        schemaObjectExists(db, "index", index)

    private fun schemaObjectExists(db: SQLiteDatabase, type: String, name: String): Boolean =
        db.query(
            "sqlite_master",
            arrayOf("name"),
            "type = ? AND name = ?",
            arrayOf(type, name),
            null,
            null,
            null
        ).use { it.moveToFirst() }

    private fun disposableName(prefix: String): String =
        "stage2_${prefix}_${System.nanoTime()}.db".also(disposableDatabases::add)

    private data class Snapshot(val users: List<String>, val notes: List<String>)

    companion object {
        private const val FIXTURE_OWNER_ONE = 9_100_000_000_000_000_001L
        private const val FIXTURE_OWNER_TWO = 9_100_000_000_000_000_002L
    }
}
