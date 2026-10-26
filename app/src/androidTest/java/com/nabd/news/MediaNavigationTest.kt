package com.nabd.news

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

/** Navigation only; does not pretend to verify live X or third-party playback. */
class MediaNavigationTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun videoAndXAreInsideNabdWithOnePreferencesEntry() {
        compose.onNodeWithContentDescription("الفيديو ومتابعة X").performClick()
        compose.onNodeWithText("شاهد وتابع").assertIsDisplayed()
        compose.onNodeWithText("المفضلة").performClick()
        compose.onNodeWithText("مفضلة المقاطع").assertIsDisplayed()
        compose.onNodeWithText("متابعة X").performClick()
        compose.onNodeWithText("منشورات وحسابات X").assertIsDisplayed()
        compose.onNodeWithContentDescription("تفضيلات الفيديو ومصادره").performClick()
        compose.onNodeWithText("تجربة مشاهدة تناسبك").assertIsDisplayed()
        compose.onNodeWithText("حفظ وإغلاق").performClick()
        compose.onNodeWithContentDescription("رجوع").performClick()
        compose.onNodeWithContentDescription("الفيديو ومتابعة X").assertIsDisplayed()
    }
}
