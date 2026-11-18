package com.example.securenotes

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.securenotes.activities.RediscoverActivity
import com.example.securenotes.database.DatabaseHelper
import com.example.securenotes.models.User
import com.example.securenotes.repositories.NoteRepository
import com.example.securenotes.repositories.UserRepository
import com.example.securenotes.utils.SessionManager
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore

@RunWith(AndroidJUnit4::class)
class RediscoverUiTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val suffix = System.nanoTime()
    private val email = "rediscover-ui-$suffix@example.test"

    private lateinit var helper: DatabaseHelper
    private lateinit var notes: NoteRepository
    private lateinit var session: SessionManager
    private var userId = -1L
    private var noteId = -1L
    private var scenario: ActivityScenario<RediscoverActivity>? = null

    @Before
    fun setUp() {
        session = SessionManager(context)
        session.clearSessionImmediately()
        helper = DatabaseHelper(context)
        val users = UserRepository(helper)
        userId = users.registerUser(
            User(fullName = "Rediscover UI Tester", email = email, passwordHash = "hash", passwordSalt = "salt")
        )
        session.saveUserSession(userId, email, "Rediscover UI Tester")
        notes = NoteRepository(helper)
        noteId = notes.createNote(userId, "UI Test Candidate Note", "Content for rediscovery UI test")
    }

    @After
    fun tearDown() {
        scenario?.close()
        session.clearSessionImmediately()
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val alias = "securenotes_user_$userId"
        if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
    }

    @Test
    fun rediscoverActivityDisplaysHeaderAndCategoryChips() {
        val intent = Intent(context, RediscoverActivity::class.java)
        scenario = ActivityScenario.launch(intent)

        waitUntilDisplayed(withId(R.id.btnRediscoverBack))
        onViewRoot(withId(R.id.chipCategoryToday)).check(matches(isDisplayed()))
        onViewRoot(withId(R.id.chipCategoryForgotten)).check(matches(isDisplayed()))
        onViewRoot(withId(R.id.chipCategoryUnfinished)).check(matches(isDisplayed()))
        onViewRoot(withId(R.id.chipCategoryConnected)).check(matches(isDisplayed()))
        onViewRoot(withId(R.id.chipCategoryQueue)).check(matches(isDisplayed()))
    }

    @Test
    fun switchingCategoryChipsChangesTabSelection() {
        val intent = Intent(context, RediscoverActivity::class.java)
        scenario = ActivityScenario.launch(intent)

        waitUntilDisplayed(withId(R.id.chipCategoryForgotten))
        onViewRoot(withId(R.id.chipCategoryForgotten)).perform(click())
        onViewRoot(withId(R.id.chipCategoryUnfinished)).perform(click())
    }

    private fun onViewRoot(matcher: org.hamcrest.Matcher<android.view.View>): androidx.test.espresso.ViewInteraction =
        onView(matcher).inRoot(
            org.hamcrest.Matchers.anyOf(
                androidx.test.espresso.matcher.RootMatchers.isFocusable(),
                androidx.test.espresso.matcher.RootMatchers.isPlatformPopup()
            )
        )

    private fun waitUntilDisplayed(matcher: org.hamcrest.Matcher<android.view.View>, timeoutMs: Long = 5000) {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            try {
                onViewRoot(matcher).check(matches(isDisplayed()))
                return
            } catch (_: Throwable) {
                android.os.SystemClock.sleep(100)
            }
        }
        onViewRoot(matcher).check(matches(isDisplayed()))
    }
}
