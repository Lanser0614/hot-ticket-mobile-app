package com.example.hotticketapplication

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.hotticketapplication.data.HotTicketRepository
import com.example.hotticketapplication.data.SessionStore
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HotTicketFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun demoSearchTrackingAndRotation() {
        compose.onNodeWithText("Посмотреть демо").performClick()
        compose.onNodeWithText("Найти поезда").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText("Отслеживать").onFirst().performScrollTo().performClick()
        compose.onNodeWithText("Сохранить").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Мои отслеживания").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.waitForIdle()
        compose.onNodeWithText("Мои отслеживания").assertIsDisplayed()
        compose.onNodeWithText("Отключить").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Возобновить").assertExists()
        compose.onNodeWithText("Профиль").performClick()
        compose.onNodeWithText("O‘zbekcha").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Sozlamalar").assertExists()
        compose.onNodeWithText("Русский").performClick()
    }

    @Test fun rejectsInsecureServerAndEncryptsSession() {
        val store = SessionStore(compose.activity.applicationContext)
        val repository = HotTicketRepository(store)
        listOf("http://example.com", "https://user:pass@example.com", "https://example.com/path", "https://example.com?x=1").forEach { value ->
            assertThrows(IllegalArgumentException::class.java) { repository.validateServer(value) }
        }
        assertEquals("https://ticket.crosfit.uz", repository.validateServer("https://ticket.crosfit.uz/"))
        store.saveToken("test-session-secret")
        assertEquals("test-session-secret", store.readToken())
        val raw = compose.activity.getSharedPreferences("hot-ticket", 0).getString("session", "")!!
        assertFalse(raw.contains("test-session-secret"))
        store.clear()
        assertEquals("", store.readToken())
    }
}
