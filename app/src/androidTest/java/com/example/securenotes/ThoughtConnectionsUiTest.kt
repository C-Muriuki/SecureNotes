package com.example.securenotes

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.securenotes.activities.NoteDetailActivity
import com.example.securenotes.activities.NoteEditorActivity
import com.example.securenotes.database.DatabaseContract
import com.example.securenotes.database.DatabaseHelper
import com.example.securenotes.models.CreateConnectionResult
import com.example.securenotes.models.User
import com.example.securenotes.repositories.NoteRepository
import com.example.securenotes.repositories.UserRepository
import com.example.securenotes.utils.Constants
import com.example.securenotes.utils.SessionManager
import com.example.securenotes.utils.ThemeUtils
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore

@RunWith(AndroidJUnit4::class)
class ThoughtConnectionsUiTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val suffix = System.nanoTime()
    private val email = "connections-ui-$suffix@example.test"

    private lateinit var helper: DatabaseHelper
    private lateinit var notes: NoteRepository
    private lateinit var session: SessionManager
    private var userId = -1L
    private var sourceNoteId = -1L
    private var candidateNoteId = -1L
    private var alreadyConnectedId = -1L
    private var scenario: ActivityScenario<NoteDetailActivity>? = null

    @Before
    fun setUp() {
        session = SessionManager(context)
        session.clearSessionImmediately()
        helper = DatabaseHelper(context)
        val users = UserRepository(helper)
        userId = users.registerUser(
            User(
                fullName = "Connections QA",
                email = email,
                passwordHash = "hash",
                passwordSalt = "salt"
            )
        )
        session.saveUserSession(userId, email, "Connections QA")
        notes = NoteRepository(helper)

        sourceNoteId = notes.createNote(userId, "Business idea", "Marketplace concept")
        candidateNoteId = notes.createNote(userId, "Meeting notes", "Investor follow-up")
        alreadyConnectedId = notes.createNote(userId, "Market research", "Audience notes")
        assertEquals(
            CreateConnectionResult.CREATED,
            notes.connectNotes(userId, sourceNoteId, alreadyConnectedId)
        )
    }

    @After
    fun tearDown() {
        scenario?.close()
        session.clearSessionImmediately()
        context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        ThemeUtils.setThemeMode(Constants.THEME_MODE_SYSTEM)
        try {
            helper.writableDatabase.delete(
                DatabaseContract.NoteTable.TABLE_NAME,
                "${DatabaseContract.NoteTable.COLUMN_USER_ID} = ?",
                arrayOf(userId.toString())
            )
            helper.writableDatabase.delete(
                DatabaseContract.UserTable.TABLE_NAME,
                "${DatabaseContract.UserTable.COLUMN_USER_ID} = ?",
                arrayOf(userId.toString())
            )
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val alias = "securenotes_user_$userId"
            if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
        } finally {
            helper.close()
        }
    }

    @Test
    fun relatedThoughtsEmptyStatePickerSearchConnectOpenAndRemove() {
        assertTrue(notes.removeConnection(userId, sourceNoteId, alreadyConnectedId))

        launchDetail(sourceNoteId)
        waitUntilDisplayed(withId(R.id.tvRelatedThoughtsEmpty))
        onViewRoot(withId(R.id.btnAddThoughtConnection)).perform(scrollTo(), click())

        waitUntilDisplayed(withId(R.id.etConnectionSearch))
        onViewRoot(withId(R.id.etConnectionSearch)).perform(replaceText("Market"), closeSoftKeyboard())
        waitUntilDisplayed(withText("Market research"))
        onViewRoot(withText("Meeting notes")).check(doesNotExist())

        onViewRoot(withId(R.id.etConnectionSearch)).perform(replaceText("Meeting"), closeSoftKeyboard())
        waitUntilDisplayed(withText("Meeting notes"))
        onViewRoot(withText("Market research")).check(doesNotExist())

        onViewRoot(withId(R.id.btnConnectCandidate)).perform(click())
        waitUntilDisplayed(withText(R.string.thought_connections_confirm_title))
        onViewRoot(withText(R.string.action_connect)).perform(click())

        waitUntilDisplayed(withText("Meeting notes"))
        onViewRoot(withId(R.id.rvRelatedThoughts)).perform(scrollTo()).check(matches(isDisplayed()))
        onViewRoot(withId(R.id.tvRelatedThoughtsEmpty)).check(matches(not(isDisplayed())))

        onViewRoot(withText("Meeting notes")).perform(scrollTo(), click())
        waitUntilDisplayed(withText("Investor follow-up"))
        waitUntilDisplayed(withText(R.string.note_detail_related_thoughts))

        onViewRoot(withId(R.id.btnDetailBack)).perform(click())
        waitUntilDisplayed(withText("Meeting notes"))
        onViewRoot(withId(R.id.btnRemoveThoughtConnection)).perform(scrollTo(), click())
        waitUntilDisplayed(withText(R.string.thought_connections_remove_title))
        onViewRoot(withText(R.string.action_remove)).perform(click())
        waitUntilDisplayed(withId(R.id.tvRelatedThoughtsEmpty))
    }

    @Test
    fun pickerExcludesAlreadyConnectedNotes() {
        launchDetail(sourceNoteId)
        waitUntilDisplayed(withText("Market research"))
        onViewRoot(withId(R.id.btnAddThoughtConnection)).perform(scrollTo(), click())
        waitUntilDisplayed(withId(R.id.etConnectionSearch))
        waitUntilDisplayed(withText("Meeting notes"))
        onViewRoot(withText("Market research")).check(doesNotExist())
    }

    @Test
    fun relatedThoughtsRemainVisibleInDarkTheme() {
        ThemeUtils.setThemeMode(Constants.THEME_MODE_DARK)
        launchDetail(sourceNoteId)
        waitUntilDisplayed(withText("Market research"))
        onViewRoot(withId(R.id.rvRelatedThoughts)).perform(scrollTo()).check(matches(isDisplayed()))
        onViewRoot(withText(R.string.note_detail_related_thoughts)).perform(scrollTo()).check(matches(isDisplayed()))
    }

    private fun onViewRoot(matcher: org.hamcrest.Matcher<android.view.View>): androidx.test.espresso.ViewInteraction =
        onView(matcher).inRoot(
            org.hamcrest.Matchers.anyOf(
                androidx.test.espresso.matcher.RootMatchers.isFocusable(),
                androidx.test.espresso.matcher.RootMatchers.isPlatformPopup(),
                androidx.test.espresso.matcher.RootMatchers.isDialog()
            )
        )

    private fun launchDetail(noteId: Long) {
        val intent = Intent(context, NoteDetailActivity::class.java)
            .putExtra(NoteEditorActivity.EXTRA_NOTE_ID, noteId)
        scenario = ActivityScenario.launch(intent)
    }

    private fun waitUntilDisplayed(matcher: org.hamcrest.Matcher<android.view.View>, timeoutMs: Long = 10_000) {
        waitUntil(timeoutMs) {
            try {
                onView(matcher).check(matches(isDisplayed()))
            } catch (_: Throwable) {
                onView(matcher).perform(scrollTo()).check(matches(isDisplayed()))
            }
            true
        }
    }

    private fun waitUntil(timeoutMs: Long, assertion: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        var lastError: Throwable? = null
        while (SystemClock.uptimeMillis() < deadline) {
            try {
                if (assertion()) return
            } catch (error: Throwable) {
                lastError = error
            }
            SystemClock.sleep(50)
        }
        throw AssertionError("UI condition was not met within $timeoutMs ms", lastError)
    }
}
