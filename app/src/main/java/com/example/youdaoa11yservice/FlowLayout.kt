package com.example.youdaoa11yservice

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import kotlin.math.max

class FlowLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ViewGroup(context, attrs, defStyleAttr) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val maxWidth = if (widthMode == MeasureSpec.UNSPECIFIED) Int.MAX_VALUE else widthSize

        var x = paddingLeft
        var y = paddingTop
        var lineHeight = 0

        val available = maxWidth - paddingRight

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == View.GONE) continue

            measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, y)
            val lp = child.layoutParams as MarginLayoutParams

            val cw = child.measuredWidth + lp.leftMargin + lp.rightMargin
            val ch = child.measuredHeight + lp.topMargin + lp.bottomMargin

            if (x + cw > available && x > paddingLeft) {
                x = paddingLeft
                y += lineHeight
                lineHeight = 0
            }

            x += cw
            lineHeight = max(lineHeight, ch)
        }

        y += lineHeight + paddingBottom

        setMeasuredDimension(
            resolveSize(maxWidth, widthMeasureSpec),
            resolveSize(y, heightMeasureSpec)
        )
    }

    override fun generateLayoutParams(attrs: AttributeSet?): LayoutParams {
        return MarginLayoutParams(context, attrs)
    }

    override fun generateDefaultLayoutParams(): LayoutParams {
        return MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
    }

    override fun generateLayoutParams(p: LayoutParams?): LayoutParams {
        return MarginLayoutParams(p)
    }

    override fun checkLayoutParams(p: LayoutParams?): Boolean {
        return p is MarginLayoutParams
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val width = r - l
        val available = width - paddingRight

        var x = paddingLeft
        var y = paddingTop
        var lineHeight = 0

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == View.GONE) continue

            val lp = child.layoutParams as MarginLayoutParams
            val cw = child.measuredWidth
            val ch = child.measuredHeight

            val totalW = cw + lp.leftMargin + lp.rightMargin
            val totalH = ch + lp.topMargin + lp.bottomMargin

            if (x + totalW > available && x > paddingLeft) {
                x = paddingLeft
                y += lineHeight
                lineHeight = 0
            }

            val left = x + lp.leftMargin
            val top = y + lp.topMargin
            child.layout(left, top, left + cw, top + ch)

            x += totalW
            lineHeight = max(lineHeight, totalH)
        }
    }
}
