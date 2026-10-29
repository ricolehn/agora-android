package org.agora.app

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isFinite
import androidx.compose.ui.unit.IntSize
import org.agora.app.ui.media.ZoomState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoomStateTest {
    private fun zoomedIn() = ZoomState().apply {
        box = IntSize(1000, 2000)
        zoomTo(2.5f, Offset(300f, 400f))
    }

    @Test
    fun `zooming keeps the spot under the fingers where it is`() {
        val zoom = zoomedIn()
        assertEquals(2.5f, zoom.scale)
        // A spot shows at middle + spot * scale + offset: (300, 400) is 200 left of and 600 above the middle
        // (500, 1000), so the offset is (-200, -600) - (-200, -600) * 2.5
        assertEquals(Offset(300f, 900f), zoom.offset)
    }

    @Test
    fun `lifting the last finger (no centroid) leaves the picture where it is`() {
        val zoom = zoomedIn()
        val before = zoom.offset
        // What the gesture reported when the last finger went up: no centroid at all
        zoom.zoomTo(zoom.scale, Offset.Unspecified)
        assertTrue(zoom.offset.isFinite)
        assertEquals(before, zoom.offset)
        assertEquals(2.5f, zoom.scale)
    }

    @Test
    fun `an unusable pan or scale changes nothing`() {
        val zoom = zoomedIn()
        val before = zoom.offset
        zoom.zoomTo(zoom.scale, Offset(10f, 10f), Offset(Float.NaN, Float.NaN))
        zoom.zoomTo(Float.NaN, Offset(10f, 10f))
        assertEquals(before, zoom.offset)
        assertEquals(2.5f, zoom.scale)
    }
}
