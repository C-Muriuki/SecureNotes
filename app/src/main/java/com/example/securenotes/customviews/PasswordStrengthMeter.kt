package com.example.securenotes.customviews

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import com.example.securenotes.R
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Draws a compact, accessible password-strength hint below registration password input.
 * Strength is informative only; ValidationUtils remains the source of acceptance rules.
 */
class PasswordStrengthMeter @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    private val density = resources.displayMetrics.density
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            12f,
            resources.displayMetrics
        )
    }
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val segmentRect = RectF()
    private var strength = PasswordStrength.EMPTY

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        contentDescription = accessibilityDescription(strength)
        minimumHeight = dp(48f).roundToInt()
    }

    /** Retains only the computed level; the supplied password is not stored by this View. */
    fun setPassword(password: CharSequence?) {
        val next = PasswordStrengthCalculator.evaluate(password)
        if (next == strength) return
        strength = next
        contentDescription = accessibilityDescription(next)
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredHeight = dp(48f).roundToInt()
        setMeasuredDimension(
            resolveSize(suggestedMinimumWidth, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        textPaint.color = ContextCompat.getColor(context, R.color.colorTextSecondary)
        val label = getStringFor(strength)
        val textBaseline = paddingTop - textPaint.fontMetrics.top
        canvas.drawText(label, paddingLeft.toFloat(), textBaseline, textPaint)

        val barHeight = dp(5f)
        val gap = dp(5f)
        val left = paddingLeft.toFloat()
        val right = width - paddingRight.toFloat()
        val totalWidth = max(0f, right - left)
        val segmentWidth = max(0f, (totalWidth - gap * 2f) / 3f)
        val top = max(textBaseline + textPaint.fontMetrics.bottom + dp(4f), height - paddingBottom - barHeight)
        val bottom = min(height - paddingBottom.toFloat(), top + barHeight)
        val radius = barHeight / 2f
        val trackColor = ContextCompat.getColor(context, R.color.colorBorder)
        val filledColor = when (strength) {
            PasswordStrength.EMPTY -> trackColor
            PasswordStrength.WEAK -> ContextCompat.getColor(context, R.color.colorError)
            PasswordStrength.FAIR -> ContextCompat.getColor(context, R.color.colorWarning)
            PasswordStrength.STRONG -> ContextCompat.getColor(context, R.color.colorSuccess)
        }

        val isRtl = layoutDirection == LAYOUT_DIRECTION_RTL
        for (index in 0 until 3) {
            val logicalIndex = if (isRtl) 2 - index else index
            val segmentLeft = left + index * (segmentWidth + gap)
            segmentRect.set(segmentLeft, top, segmentLeft + segmentWidth, bottom)
            barPaint.color = if (logicalIndex < strength.filledSegments) filledColor else trackColor
            canvas.drawRoundRect(segmentRect, radius, radius, barPaint)
        }
    }

    private fun accessibilityDescription(value: PasswordStrength): String =
        context.getString(
            R.string.password_strength_accessibility,
            getStringFor(value),
            value.filledSegments
        )

    private fun getStringFor(value: PasswordStrength): String = context.getString(
        when (value) {
            PasswordStrength.EMPTY -> R.string.password_strength_none
            PasswordStrength.WEAK -> R.string.password_strength_weak
            PasswordStrength.FAIR -> R.string.password_strength_fair
            PasswordStrength.STRONG -> R.string.password_strength_strong
        }
    )

    private fun dp(value: Float): Float = value * density
}
