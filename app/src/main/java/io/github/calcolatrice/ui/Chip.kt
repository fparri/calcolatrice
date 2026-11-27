package io.github.calcolatrice.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.widget.CompoundButton
import android.widget.TextView
import io.github.calcolatrice.R

/**
 * Chip di Material 3: etichetta in un rettangolo con angoli di 8 dp, alto 32 dp. Spento ha un bordo
 * sottile; acceso è pieno, con il segno di spunta se è un interruttore (filter chip).
 * Per TalkBack è un interruttore se [checkable], altrimenti un pulsante.
 */
@SuppressLint("ViewConstructor")
class Chip(context: Context, private val palette: Palette, private val checkable: Boolean) : TextView(context) {

    private val shape = GradientDrawable().apply { cornerRadius = context.dp(8).toFloat() }
    private val check = context.getDrawable(R.drawable.ic_check)?.mutate()
    var checked = false
        private set

    init {
        textSize = 14f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        gravity = Gravity.CENTER
        maxLines = 1
        minHeight = context.dp(32)
        isClickable = true
        isFocusable = true
        compoundDrawablePadding = context.dp(4)
        background = RippleDrawable(ColorStateList.valueOf(rippleColor(palette.onBackground)), shape, null)
        update(text = "", checked = false)
    }

    fun update(text: CharSequence, checked: Boolean) {
        this.text = text
        this.checked = checked
        val content = if (checked) palette.onSelectedContainer else palette.onBackgroundVariant
        setTextColor(content)
        if (checked) {
            shape.setColor(palette.selectedContainer)
            shape.setStroke(0, 0)
        } else {
            shape.setColor(android.graphics.Color.TRANSPARENT)
            shape.setStroke(context.dp(1), palette.outline)
        }
        val icon = if (checked && checkable) check?.apply { setTint(content) } else null
        setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null)
        val start = context.dp(if (icon != null) 8 else 12)
        setPaddingRelative(start, 0, context.dp(12), 0)
        isSelected = checked
    }

    override fun getAccessibilityClassName(): CharSequence =
        if (checkable) CompoundButton::class.java.name else android.widget.Button::class.java.name

    override fun onInitializeAccessibilityNodeInfo(info: android.view.accessibility.AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        if (checkable) {
            info.isCheckable = true
            info.isChecked = checked
        }
    }
}
