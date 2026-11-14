package com.example.securenotes

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.securenotes.activities.DashboardActivity
import com.example.securenotes.activities.NoteDetailActivity
import com.example.securenotes.activities.NoteEditorActivity
import com.example.securenotes.database.DatabaseContract
import com.example.securenotes.database.DatabaseHelper
import com.example.securenotes.models.IdeaStage
import com.example.securenotes.models.User
import com.example.securenotes.repositories.NoteRepository
import com.example.securenotes.repositories.UserRepository
import com.example.securenotes.utils.Constants
import com.example.securenotes.utils.SessionManager
import com.example.securenotes.utils.ThemeUtils
import org.hamcrest.Matchers.containsString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore

@RunWith(AndroidJUnit4::class)
class IdeaJourneyUiTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val suffix = System.nanoTime()
    private val email = "journey-ui-$suffix@example.test"

    private lateinit var helper: DatabaseHelper
    private lateinit var notes: NoteRepository
    private lateinit var session: SessionManager
    private var userId = -1L
    private var noteId = -1L
    private var detailScenario: ActivityScenario<NoteDetailActivity>? = null
    private var dashboardScenario: ActivityScenario<DashboardActivity>? = null

    @Before
    fun setUp() {
        session = SessionManager(context)
        session.clearSessionImmediately()
        helper = DatabaseHelper(context)
        val users = UserRepository(helper)
        userId = users.registerUser(
            User(
                fullName = "Journey QA",
                email = email,
                passwordHash = "hash",
                passwordSalt = "salt"
            )
        )
        session.saveUserSession(userId, email, "Journey QA")
        notes = NoteRepository(helper)
        noteId = notes.createNote(userId, "Marketplace Concept", "Platform idea notes")
    }

    @After
    fun tearDown() {
        detailScenario?.close()
        dashboardScenario?.close()
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
    fun noteDetailDisplaysIdeaJourneyAndUpdatesStage() {
        launchDetail(noteId)
        waitUntilDisplayed(withId(R.id.btnChangeIdeaStage))
        onView(withId(R.id.tvIdeaJourneyStageBadge)).check(matches(withText(containsString("Captured"))))

        onView(withId(R.id.btnChangeIdeaStage)).perform(scrollTo(), click())
        waitUntilDisplayed(withText("Exploring"))
        onView(withText("Exploring")).perform(click())

        waitUntilDisplayed(withId(R.id.tvIdeaJourneyStageBadge))
        onView(withId(R.id.tvIdeaJourneyStageBadge)).check(matches(withText(containsString("Exploring"))))

        val updatedNote = notes.getNote(userId, noteId)
        assertEquals(IdeaStage.EXPLORING, updatedNote?.ideaStage)
    }

    @Test
    fun dashboardDisplaysStageCountsAndNavigatesToFilteredNotes() {
        notes.updateIdeaStage(userId, noteId, IdeaStage.DEVELOPING)

        dashboardScenario = ActivityScenario.launch(DashboardActivity::class.java)
        waitUntilDisplayed(withId(R.id.tvCountDeveloping))
        onView(withId(R.id.tvCountDeveloping)).check(matches(withText("1")))
        onView(withId(R.id.tvCountExploring)).check(matches(withText("0")))

        onView(withId(R.id.cardStageDeveloping)).perform(scrollTo(), click())
        waitUntilDisplayed(withText("Marketplace Concept"))
    }

    @Test
    fun ideaJourneyDisplaysInDarkTheme() {
        ThemeUtils.setThemeMode(Constants.THEME_MODE_DARK)
        launchDetail(noteId)
        waitUntilDisplayed(withId(R.id.btnChangeIdeaStage))
        onView(withId(R.id.tvIdeaJourneyStageBadge)).check(matches(isDisplayed()))
        onView(withId(R.id.stageIndicatorCaptured)).check(matches(isDisplayed()))
    }

    private fun launchDetail(id: Long) {
        val intent = Intent(context, NoteDetailActivity::class.java)
            .putExtra(NoteEditorActivity.EXTRA_NOTE_ID, id)
        detailScenario = ActivityScenario.launch(intent)
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
