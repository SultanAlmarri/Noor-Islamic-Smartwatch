package com.noor.prayer.face

import kotlin.math.abs

/**
 * Where each [Slot] sits on the round face, in fractions of the radius from the centre (x to the right, y down).
 * One table for the renderer, the phone's editor (taps and drags on the preview) and the watch (taps on the face).
 */
object FaceGeometry {
    data class Box(val cx: Float, val cy: Float, val hw: Float, val hh: Float) {
        fun contains(x: Float, y: Float, pad: Float = 0f) = abs(x - cx) <= hw + pad && abs(y - cy) <= hh + pad
        val left get() = cx - hw
        val right get() = cx + hw
        val top get() = cy - hh
        val bottom get() = cy + hh
    }

    private val UPPER = Box(0f, -0.60f, 0.40f, 0.12f)
    private val TOP = Box(0f, -0.335f, 0.66f, 0.125f)
    private val CENTER = Box(0f, 0f, 0.80f, 0.20f)
    private val CENTER_NARROW = Box(0f, 0f, 0.30f, 0.20f)
    private val BOTTOM = Box(0f, 0.335f, 0.66f, 0.125f)
    private val LOWER = Box(0f, 0.60f, 0.40f, 0.12f)
    private val LEFT = Box(-0.53f, 0f, 0.20f, 0.20f)
    private val RIGHT = Box(0.53f, 0f, 0.20f, 0.20f)

    /** The slot's box in [design]: the centre narrows when something sits at its left or right. */
    fun box(slot: Slot, design: FaceDesign? = null): Box = when (slot) {
        Slot.UPPER -> UPPER
        Slot.TOP -> TOP
        Slot.CENTER -> if (design != null && (design.at(Slot.LEFT) != null || design.at(Slot.RIGHT) != null)) CENTER_NARROW else CENTER
        Slot.BOTTOM -> BOTTOM
        Slot.LOWER -> LOWER
        Slot.LEFT -> LEFT
        Slot.RIGHT -> RIGHT
    }

    /** The slot under a touch at ([x], [y]) (fractions of the radius), or null. Small slots win over the centre. */
    fun slotAt(x: Float, y: Float, design: FaceDesign? = null, pad: Float = 0.04f): Slot? {
        if (x * x + y * y > 1.05f) return null
        val order = listOf(Slot.LEFT, Slot.RIGHT, Slot.UPPER, Slot.LOWER, Slot.TOP, Slot.BOTTOM, Slot.CENTER)
        return order.firstOrNull { box(it, design).contains(x, y, pad) }
            ?: order.minByOrNull { s -> box(s, design).let { (it.cx - x) * (it.cx - x) + (it.cy - y) * (it.cy - y) } }
                ?.takeIf { s -> box(s, design).let { (it.cx - x) * (it.cx - x) + (it.cy - y) * (it.cy - y) } < 0.09f }
    }
}
