package com.nabd.news

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

/** Navigation is separate from live source availability and real translation quality. */
class WorldNavigationTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun worldIsInsideNabdAndHasSourcesArchivesAndTranslationSettings() {
        compose.onNodeWithText("نبض العالم • صحافة العالم، بلغتك").performScrollTo().performClick()
        compose.onNodeWithText("نبض العالم").assertIsDisplayed()
        compose.onNodeWithText("الصحف").performClick()
        compose.onNodeWithText("صحف من العالم").assertIsDisplayed()
        compose.onNodeWithText("المحفوظات").performClick()
        compose.onNodeWithText("صحافة العالم، بلغتك.").assertIsDisplayed()
        compose.onNodeWithText("الترجمة").performClick()
        compose.onNodeWithText("ترجمة هادئة، تحت سيطرتك").assertIsDisplayed()
        compose.onNodeWithContentDescription("رجوع").performClick()
        compose.onNodeWithText("أقرب إلى الحدث").assertIsDisplayed()
    }
}
