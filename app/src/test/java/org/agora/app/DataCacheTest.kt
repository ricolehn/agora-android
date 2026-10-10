package org.agora.app

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.robolectric.RobolectricTestRunner
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.agora.app.data.AppData
import org.agora.app.data.local.DataCache
import org.agora.app.data.model.AgoraEvent
import org.agora.app.data.model.FeeSettings
import org.agora.app.data.model.MentoringThread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The start data kept on the device: written and read back, only for the same server and account. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class DataCacheTest {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true; explicitNulls = false }

    @Test
    fun roundTripForSameAccountOnly() = runBlocking {
        val cache = DataCache(ApplicationProvider.getApplicationContext(), json)
        val data = AppData(
            loaded = true,
            fees = FeeSettings(mapOf("vollverdiener" to 12.5)),
            events = listOf(AgoraEvent(id = "e1", title = "Sommerfest", date = "2026-10-17", endDate = "2026-10-19")),
            threads = listOf(MentoringThread(id = "t1", unreadCount = 2)),
            aiEnabled = true
        )
        cache.save("https://a.example", "u1", data)
        assertEquals(data, cache.load("https://a.example", "u1"))
        assertNull(cache.load("https://a.example", "u2"))
        assertNull(cache.load("https://b.example", "u1"))
        cache.clear()
        assertNull(cache.load("https://a.example", "u1"))
    }
}
