package com.nabd.news

import android.view.WindowManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import androidx.lifecycle.ViewModelProvider
import org.junit.Rule
import org.junit.Test

class UiSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun arabicNavigationAndNoteEditorWork() {
        lateinit var vm:NabdViewModel
        compose.activityRule.scenario.onActivity{vm=ViewModelProvider(it)[NabdViewModel::class.java]}
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
        compose.waitUntil(30000){vm.notes.value.any{it.title=="ملاحظة اختبار الواجهة"&&it.body=="نص عربي يبقى محليًا"}}
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("ملاحظة اختبار الواجهة"))
        compose.onNodeWithText("ملاحظة اختبار الواجهة").assertIsDisplayed().performClick()
        compose.waitUntil(30000) { compose.onAllNodesWithText("نص عربي يبقى محليًا").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("نص عربي يبقى محليًا").assertExists()
    }
}
