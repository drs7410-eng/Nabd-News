package com.nabd.news

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

/** Device/emulator execution required. No third-party embed or network success is inferred. */
class SceneNavigationTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun sceneTabsRemainInsideExistingApp() {
        compose.onNodeWithText("المشهد").performClick()
        compose.onNodeWithText("نبض المشهد").assertIsDisplayed()
        compose.onNodeWithText("رصد X").performClick()
        compose.onNodeWithText("حسابات X المتابعة").assertIsDisplayed()
        compose.onNodeWithText("تغطية الصحف").performClick()
        compose.onNodeWithText("حدث واحد، زوايا متعددة").assertIsDisplayed()
        compose.onNodeWithText("التحليل الذكي").performClick()
        compose.onNodeWithText("اختر موضوعًا لفهم تطوراته").assertIsDisplayed()
        compose.onNodeWithText("الرئيسية").performClick()
        compose.onNodeWithText("أقرب إلى الحدث").assertIsDisplayed()
    }
    @Test fun sharedXLinkOpensSaveDialogWithoutOpeningBrowser() {
        compose.runOnUiThread {
            compose.activity.startActivity(Intent(compose.activity,MainActivity::class.java).apply {
                action=Intent.ACTION_SEND;type="text/plain"
                putExtra(Intent.EXTRA_TEXT,"https://x.com/BBCArabic/status/123456789")
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            })
        }
        compose.waitUntil(15000){compose.onAllNodesWithText("حفظ منشور في نبض المشهد").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("حفظ منشور في نبض المشهد").assertIsDisplayed()
        compose.onNodeWithText("إلغاء").performClick()
    }
}
