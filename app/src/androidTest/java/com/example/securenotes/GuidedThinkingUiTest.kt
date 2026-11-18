package com.example.securenotes

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.securenotes.activities.GuidedThinkingActivity
import com.example.securenotes.database.DatabaseHelper
import com.example.securenotes.models.User
import com.example.securenotes.repositories.NoteRepository
import com.example.securenotes.repositories.UserRepository
import com.example.securenotes.utils.SessionManager
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore

@RunWith(AndroidJUnit4::class)
class GuidedThinkingUiTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val suffix = System.nanoTime()
    private val email = "guidedthinking-ui-$suffix@example.test"

    private lateinit var helper: DatabaseHelper
    private lateinit var notes: NoteRepository
    private lateinit var session: SessionManager
    private var userId = -1L
    private var scenario: ActivityScenario<GuidedThinkingActivity>? = null

    @Before
    fun setUp() {
        session = SessionManager(context)
        session.clearSessionImmediately()
        helper = DatabaseHelper(context)
        val users = UserRepository(helper)
        userId = users.registerUser(
            User(fullName = "Thinking UI Tester", email = email, passwordHash = "hash", passwordSalt = "salt")
        )
        session.saveUserSession(userId, email, "Thinking UI Tester")
        notes = NoteRepository(helper)
        notes.createNote(userId, "Seed Note", "Seed content")
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
    fun guidedThinkingActivityDisplaysFrameworks() {
        val intent = Intent(context, GuidedThinkingActivity::class.java)
        scenario = ActivityScenario.launch(intent)
        scenario?.onActivity { it.window.decorView.requestFocus() }

        waitUntilDisplayed(withId(R.id.btnGuidedThinkingBack))
        onViewRoot(withText("Five Whys")).check(matches(isDisplayed()))
        onViewRoot(withText("SWOT Analysis")).check(matches(isDisplayed()))
        onViewRoot(withText("Pros and Cons")).check(matches(isDisplayed()))
    }

    @Test
    fun selectingFrameworkLaunchesSessionRunner() {
        val intent = Intent(context, GuidedThinkingActivity::class.java)
        scenario = ActivityScenario.launch(intent)
        scenario?.onActivity { it.window.decorView.requestFocus() }

        waitUntilDisplayed(withText("Five Whys"))
        onViewRoot(withText("Five Whys")).perform(click())
        waitUntilDisplayed(withId(R.id.tvStepPromptTitle))
        onViewRoot(withText("What is the main problem?")).check(matches(isDisplayed()))
    }

    private fun onViewRoot(matcher: org.hamcrest.Matcher<android.view.View>): androidx.test.espresso.ViewInteraction =
        onView(matcher).inRoot(androidx.test.espresso.matcher.RootMatchers.withDecorView(org.hamcrest.Matchers.any(android.view.View::class.java)))

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
