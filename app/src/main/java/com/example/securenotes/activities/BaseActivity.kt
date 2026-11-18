package com.example.securenotes.activities

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

abstract class BaseActivity : AppCompatActivity() {
    private val ioExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        @Suppress("DEPRECATION")
        window.addFlags(
            android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )
        enableEdgeToEdge()
    }

    protected fun <T> runIo(task: () -> T, onComplete: (Result<T>) -> Unit): Future<*> =
        ioExecutor.submit {
            val result = runCatching(task)
            if (!Thread.currentThread().isInterrupted) {
                mainHandler.post {
                    if (!isFinishing && !isDestroyed) onComplete(result)
                }
            }
        }

    protected fun cancelIo(future: Future<*>?) {
        future?.cancel(true)
    }

    /** Reuses an existing bottom-navigation destination in this task when available. */
    protected fun navigateToTab(destination: Class<out Activity>) {
        if (javaClass == destination) return
        startActivity(
            Intent(this, destination).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        )
    }

    override fun onDestroy() {
        ioExecutor.shutdownNow()
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    /** Adds a brief press scale to a real control without delaying its click action. */
    protected fun installPressFeedback(view: View) {
        if (!ValueAnimator.areAnimatorsEnabled()) return
        var pressCanClick = false
        view.setOnTouchListener { target, event ->
            if (!target.isEnabled) return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    pressCanClick = true
                    target.isPressed = true
                    animatePressScale(target, 0.96f, 80L)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (pressCanClick && !event.isInside(target)) {
                        pressCanClick = false
                        target.isPressed = false
                        animatePressScale(target, 1f, 130L)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val shouldClick = pressCanClick && event.isInside(target)
                    pressCanClick = false
                    target.isPressed = false
                    animatePressScale(target, 1f, 130L)
                    if (shouldClick) target.performClick()
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    pressCanClick = false
                    target.isPressed = false
                    animatePressScale(target, 1f, 130L)
                    true
                }
                else -> pressCanClick
            }
        }
    }

    private fun animatePressScale(view: View, scale: Float, duration: Long) {
        view.animate().cancel()
        view.animate().scaleX(scale).scaleY(scale).setDuration(duration).start()
    }

    private fun MotionEvent.isInside(view: View): Boolean =
        x >= 0f && x < view.width && y >= 0f && y < view.height

    protected fun setupEdgeToEdgeInsets(rootView: View) {
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }
}
