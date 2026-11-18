package com.example.securenotes

import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.securenotes.activities.LoginActivity
import com.example.securenotes.database.DatabaseContract
import com.example.securenotes.database.DatabaseHelper
import com.example.securenotes.repositories.UserRepository
import com.example.securenotes.utils.Constants
import com.example.securenotes.utils.SessionManager
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore

@RunWith(AndroidJUnit4::class)
class SecureNotesUiTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val suffix = System.nanoTime()
    private val email = "ui-$suffix@example.test"
    private val password = "Str0ng!Passphrase"
    private var scenario: ActivityScenario<LoginActivity>? = null

    @Before
    fun launchLogin() {
        SessionManager(context).clearSessionImmediately()
        scenario = ActivityScenario.launch(LoginActivity::class.java)
    }

    @After
    fun cleanUp() {
        scenario?.close()
        SessionManager(context).clearSessionImmediately()
        context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        val helper = DatabaseHelper(context)
        try {
            val userId = UserRepository(helper).getUserByEmail(email)?.userId ?: return
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
            if (keyStore.containsAlias("securenotes_user_$userId")) {
                keyStore.deleteEntry("securenotes_user_$userId")
            }
        } finally {
            helper.close()
        }
    }

    @Test
    fun loginAndRegistrationFormsRejectInvalidInput() {
        onViewRoot(withId(R.id.btnLogin)).perform(click())
        onViewRoot(withId(R.id.etEmail)).check(matches(isDisplayed()))

        onViewRoot(withId(R.id.btnRegisterLink)).perform(click())
        onViewRoot(withId(R.id.btnRegisterSubmit)).perform(click())
        onViewRoot(withId(R.id.etRegFullName)).check(matches(isDisplayed()))

        onViewRoot(withId(R.id.etRegFullName)).perform(replaceText("Test User"))
        onViewRoot(withId(R.id.etRegEmail)).perform(replaceText("not-an-email"))
        onViewRoot(withId(R.id.etRegPassword)).perform(replaceText("weak"))
        onViewRoot(withId(R.id.etConfirmPassword)).perform(replaceText("different"))
        onViewRoot(withId(R.id.btnRegisterSubmit)).perform(click())
        onViewRoot(withId(R.id.etRegEmail)).check(matches(isDisplayed()))
        onViewRoot(withId(R.id.tvRegisterHeader)).check(matches(isDisplayed()))
    }

    @Test
    fun registerCreateEditPinSearchDeleteLogoutAndLoginJourney() {
        onViewRoot(withId(R.id.btnRegisterLink)).perform(click())
        onViewRoot(withId(R.id.etRegFullName)).perform(replaceText("QA User"))
        onViewRoot(withId(R.id.etRegEmail)).perform(replaceText(email))
        onViewRoot(withId(R.id.etRegPassword)).perform(replaceText(password))
        onViewRoot(withId(R.id.etConfirmPassword)).perform(replaceText(password), closeSoftKeyboard())
        onViewRoot(withId(R.id.btnRegisterSubmit)).perform(click())
        waitForView(R.id.fabCreateNote)

        onViewRoot(withId(R.id.fabCreateNote)).perform(click())
        onViewRoot(withId(R.id.etNoteTitle)).perform(replaceText("QA original"))
        onViewRoot(withId(R.id.etNoteContent)).perform(replaceText("Private QA search text"), closeSoftKeyboard())
        onViewRoot(withId(R.id.btnSaveNote)).perform(click())
        waitForText("QA original")

        onViewRoot(withId(R.id.ivPinIndicator)).perform(click())
        waitForView(R.id.tvPinnedTitle)
        onViewRoot(withId(R.id.tvPinnedTitle)).perform(click())
        waitForView(R.id.btnDetailPin)
        onViewRoot(withId(R.id.btnDetailPin)).perform(click())
        onViewRoot(withId(R.id.btnDetailPin)).check(matches(withContentDescription(R.string.action_pin_note)))
        onViewRoot(withId(R.id.btnDetailPin)).perform(click())
        onViewRoot(withId(R.id.btnDetailPin)).check(matches(withContentDescription(R.string.action_unpin_note)))
        onViewRoot(withId(R.id.btnDetailEdit)).perform(click())
        waitForText("Private QA search text")
        onViewRoot(withId(R.id.etNoteTitle)).perform(replaceText("QA edited"))
        onViewRoot(withId(R.id.btnSaveNote)).perform(click())
        waitForText("QA edited")

        onViewRoot(withId(R.id.cardSearchEntry)).perform(click())
        onViewRoot(withId(R.id.etSearchQuery)).perform(replaceText("QA edited"), closeSoftKeyboard())
        waitForText("QA edited")
        onViewRoot(withId(R.id.btnClearSearch)).check(matches(isDisplayed())).perform(click())
        onViewRoot(withId(R.id.etSearchQuery)).check(matches(withText("")))

        onViewRoot(withText("QA edited")).perform(click())
        waitForView(R.id.btnDetailDelete)
        onViewRoot(withId(R.id.btnDetailDelete)).perform(click())
        onViewRoot(withText(R.string.action_delete)).perform(click())
        waitForView(R.id.fabCreateNote)
        onViewRoot(withText("QA edited")).check(matches(not(isDisplayed())))

        onViewRoot(withId(R.id.btnProfile)).perform(click())
        onViewRoot(withId(R.id.btnLogout)).perform(click())
        waitForView(R.id.btnLogin)
        onViewRoot(withId(R.id.etEmail)).perform(replaceText(email))
        onViewRoot(withId(R.id.etPassword)).perform(replaceText(password), closeSoftKeyboard())
        onViewRoot(withId(R.id.btnLogin)).perform(click())
        waitForView(R.id.fabCreateNote)
        onViewRoot(withId(R.id.tvUserGreeting)).check(matches(isDisplayed()))

        onViewRoot(withId(R.id.btnProfile)).perform(click())
        onViewRoot(withId(R.id.cardSettingsShortcut)).perform(click())
        waitForView(R.id.rgThemeMode)
        onViewRoot(withId(R.id.rbThemeDark)).perform(click())
        onViewRoot(withId(R.id.rbThemeDark)).check(matches(isDisplayed()))
    }

    /**
     * Waits for a real UI condition instead of assuming a fixed worker/animation duration.
     * The timeout bounds failures so a broken navigation or background operation is reported.
     */
    private fun onViewRoot(matcher: org.hamcrest.Matcher<android.view.View>): androidx.test.espresso.ViewInteraction =
        onView(matcher).inRoot(
            org.hamcrest.Matchers.anyOf(
                androidx.test.espresso.matcher.RootMatchers.isFocusable(),
                androidx.test.espresso.matcher.RootMatchers.isPlatformPopup(),
                androidx.test.espresso.matcher.RootMatchers.isDialog()
            )
        )

    private fun waitForView(viewId: Int, timeoutMs: Long = 10_000) {
        waitUntil(timeoutMs) {
            onViewRoot(withId(viewId)).check(matches(isDisplayed()))
            true
        }
    }

    private fun waitForText(text: String, timeoutMs: Long = 10_000) {
        waitUntil(timeoutMs) {
            onViewRoot(withText(text)).check(matches(isDisplayed()))
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
