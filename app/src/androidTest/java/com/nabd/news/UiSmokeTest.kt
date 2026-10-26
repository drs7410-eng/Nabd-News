package com.nabd.news

import android.view.WindowManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class UiSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun arabicNavigationAndNoteEditorWork() {
        compose.onNodeWithText("أقرب إلى الحدث").assertIsDisplayed()
        compose.runOnUiThread {
            assertTrue(compose.activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
        }
        compose.onNodeWithText("الدفتر").performClick()
        compose.onNodeWithText("تدوينة جديدة").performScrollTo().performClick()
        compose.waitUntil(30000) { compose.onAllNodesWithText("عنوان التدوينة").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("عنوان التدوينة").performTextInput("ملاحظة اختبار الواجهة")
        compose.onNodeWithText("اكتب أفكارك…").performTextInput("نص عربي يبقى محليًا")
        compose.onNodeWithText("حفظ والعودة").performScrollTo().performClick()
        compose.waitUntil(30000) { compose.onAllNodesWithText("ملاحظة اختبار الواجهة").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("ملاحظة اختبار الواجهة").assertIsDisplayed().performClick()
        compose.waitUntil(30000) { compose.onAllNodesWithText("نص عربي يبقى محليًا").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("نص عربي يبقى محليًا").assertExists()
    }
}
