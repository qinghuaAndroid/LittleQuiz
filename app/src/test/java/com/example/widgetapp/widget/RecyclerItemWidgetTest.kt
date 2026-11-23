package com.example.widgetapp.widget

import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecyclerItemWidgetTest {

    @Test
    fun rebind_recyclesThePreviousBindingBeforeBindingAgain() {
        val widget = RecordingItemWidget()

        widget.dispatchBind(position = 1, payloads = emptyList())
        widget.dispatchBind(position = 2, payloads = emptyList())

        assertEquals(listOf(1, 2), widget.boundPositions)
        assertEquals(listOf(1), widget.recycledPositions)
        assertTrue(widget.isBound)
        assertEquals(2, widget.boundPosition)
    }

    @Test
    fun recycle_beforeBind_isIgnored() {
        val widget = RecordingItemWidget()

        widget.dispatchRecycle()

        assertTrue(widget.recycledPositions.isEmpty())
        assertFalse(widget.isBound)
    }

    private class RecordingItemWidget : RecyclerItemWidget() {
        override val root: View
            get() = error("The root View is not needed by these lifecycle-dispatch tests.")

        val boundPositions = mutableListOf<Int>()
        val recycledPositions = mutableListOf<Int>()

        override fun onBind(position: Int, payloads: List<Any>) {
            boundPositions += position
        }

        override fun onRecycle() {
            recycledPositions += boundPosition
        }
    }
}
