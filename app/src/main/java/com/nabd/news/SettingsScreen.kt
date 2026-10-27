package com.nabd.news

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable fun Picker(label: String, options: List<String>, value: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Caption(label)
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Text(value.ifBlank { "اختر…" }, Modifier.weight(1f), maxLines = 2); Icon(Icons.Outlined.ExpandMore, null)
        }
        DropdownMenu(expanded, { expanded = false }, Modifier.heightIn(max = 360.dp)) {
            if (options.isEmpty()) DropdownMenuItem(text = { Text("اختبر الاتصال لتحديث القائمة") }, onClick = { expanded = false })
            options.distinct().forEach { option -> DropdownMenuItem(text = { Text(option.ifBlank { "استخدام النموذج الافتراضي" }) }, onClick = { onSelect(option); expanded = false }) }
        }
    }
}
@Composable fun SettingsScreen(vm: NabdViewModel, navigate: (String) -> Unit, export: (String) -> Unit, restore: () -> Unit) {
    var provider by rememberSaveable { mutableStateOf(vm.settings.defaultProvider()) }
    val nanoStatus by vm.nanoStatus.collectAsStateWithLifecycle()
    val configVersion by vm.configVersion.collectAsStateWithLifecycle()
    LaunchedEffect(configVersion) { provider = vm.settings.defaultProvider() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        BoxCard { Icon(Icons.Outlined.AutoAwesome, null, tint = Accent, modifier = Modifier.size(34.dp)); SectionTitle("محركات الذكاء الاصطناعي", "اختر المحرك والنموذج. تعمل الأخبار والدفتر دون تفعيل أي محرك."); Picker("المحرك الافتراضي", listOf("OpenAI", "Gemini"), provider) { provider = it; vm.settings.set("defaultProvider", it) } }
        ProviderCard(vm, "OpenAI")
        ProviderCard(vm, "Gemini")
        BoxCard {
            Text("Gemini Nano — محلي", fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Caption(nanoStatus, Mint)
            Caption("يُفحص الدعم الفعلي عبر ML Kit GenAI. اسم الجهاز وحده لا يضمن توفر AICore أو النموذج. العربية غير مدعومة رسميًا في المكتبة الحالية؛ الاختبار من شاشة المساعد تجريبي.")
            Action("فحص التوافق", Icons.Outlined.PhoneAndroid) { vm.checkNano() }
            Action("تنزيل النموذج عند توفره", Icons.Outlined.Download) { vm.downloadNano() }
        }
        BoxCard {
            SectionTitle("مساحتك المحلية")
            Action("نبض العالم", Icons.Outlined.Public) { navigate("world") }
            Action("نبض المشهد", Icons.Outlined.Insights) { navigate("scene") }
            Action("البحث الشامل", Icons.Outlined.Search) { navigate("explore") }
            Action("الفيديو ومتابعة X", Icons.Outlined.VideoLibrary) { navigate("media") }
            Action("مصادر الأخبار وX", Icons.Outlined.RssFeed) { navigate("sources") }
            Action("سجل التحليلات", Icons.Outlined.History) { navigate("history") }
            Action("المحادثات المحفوظة", Icons.Outlined.ChatBubbleOutline) { navigate("chats") }
            Action("الاستخدام والتكلفة", Icons.Outlined.Payments) { navigate("usage") }
        }
        BoxCard {
            SectionTitle("الأخبار الحديثة والتخزين")
            var days by remember { mutableIntStateOf(vm.retentionDays()) }
            Picker("مدة الاحتفاظ بالأخبار غير المحفوظة",listOf("3 أيام","7 أيام","14 يومًا","30 يومًا"),when(days){3->"3 أيام";14->"14 يومًا";30->"30 يومًا";else->"7 أيام"}) {
                days=it.substringBefore(' ').toInt();vm.settings.set("newsRetentionDays",days.toString())
            }
            Caption("التنظيف عند التشغيل والتحديث، حتى 500 خبر في المرة. تبقى المحفوظات والمقالات المرتبطة بملاحظات أو تحليلات أو محادثات أو موضوع محفوظ أو ربط يدوي، وكذلك ترجمات المتن. ترجمات العناوين والمقتطفات التلقائية مؤقتة وتنتهي مع الخبر غير المحفوظ. الأخبار بلا تاريخ تبقى حتى تتوفر سياسة لها.")
            Action("تنظيف الأخبار القديمة الآن",Icons.Outlined.CleaningServices){vm.cleanNewsCache()}
        }
        BoxCard {
            SectionTitle("التصدير والنسخ الاحتياطي")
            Text("الملف الذي تصدّره يحتوي نصوصك الشخصية بشكل مقروء. اختر مكانًا تثق به. لا يشمل المفاتيح أو إعدادات الاتصال أو سجل الميزانية.", color = Muted, lineHeight = 26.sp)
            Action("تصدير دفتر Markdown", Icons.Outlined.Description) { export("md") }
            Action("نسخة المحتوى بصيغة JSON", Icons.Outlined.Download) { export("json") }
            Action("استعادة نسخة JSON", Icons.Outlined.UploadFile, restore)
        }
        Caption("نبض • إصدار شخصي 1.0\nلا حساب سحابي ولا تحليل تلقائي في الخلفية. لا توجد خدمات إعلانات أو تقارير أعطال ترسل البيانات.")
    }
}
@Composable fun ProviderCard(vm: NabdViewModel, provider: String) {
    val s = vm.settings
    var revision by remember { mutableIntStateOf(0) }
    // Key input intentionally is not rememberSaveable: it must never enter saved instance state.
    var key by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var enabled by remember { mutableStateOf(s.enabled(provider)) }
    var model by remember { mutableStateOf(s.model(provider)) }
    val modelUpdates by vm.models.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val available = modelUpdates[provider] ?: s.get("models_$provider").lines().filter { it.isNotBlank() }
    var op by remember { mutableStateOf(operations.first()) }
    var assigned by remember(op, revision) { mutableStateOf(s.get("model_${provider}_$op")) }
    var freeConsent by remember(model) { mutableStateOf(s.get("geminiConsent_$model") == "true") }
    val keyboard = LocalSoftwareKeyboardController.current
    val tested = s.get("tested_$provider").toLongOrNull()
    var deleteConfirm by remember { mutableStateOf(false) }
    if (deleteConfirm) AlertDialog(onDismissRequest = { deleteConfirm = false }, title = { Text("حذف مفتاح $provider؟") }, confirmButton = { TextButton(onClick = { vm.task { s.deleteKey(provider); key = ""; revision++; vm.configVersion.value++; deleteConfirm = false } }) { Text("حذف المفتاح") } }, dismissButton = { TextButton(onClick = { deleteConfirm = false }) { Text("إلغاء") } })
    BoxCard {
        Row(verticalAlignment = Alignment.CenterVertically) { Text(if (provider == "OpenAI") "OpenAI API" else "Google Gemini API", Modifier.weight(1f), fontSize = 22.sp, fontWeight = FontWeight.Bold); Switch(enabled, { enabled = it; s.set("enabled_$provider", it.toString()) }) }
        val state = s.get("testState_$provider")
        Caption(if (!enabled) "معطّل" else if (!s.hasKey(provider)) "غير متصل • لا يوجد مفتاح" else when (state) {
            "connected" -> "متصل وفق آخر اختبار وصول"
            "testing" -> "جارٍ اختبار الاتصال…"
            "disconnected" -> "غير متصل • فشل الاختبار الأخير"
            else -> "مفتاح محفوظ • لم يُختبر الاتصال"
        }, if (state == "connected") Mint else Accent)
        if (tested != null) Caption("آخر اختبار ناجح: ${dateText(tested)}")
        val usage by vm.usage.collectAsStateWithLifecycle()
        val providerUsage = usage.filter { it.provider == provider }
        Caption("${providerUsage.size} طلب توليد محلي • التكاليف المعروفة: ${money(providerUsage.filter { it.state != "reserved" }.sumOf { it.cost ?: 0.0 })}")
        OutlinedTextField(key, { key = it.trim() }, Modifier.fillMaxWidth(), label = { Text(if (s.hasKey(provider)) "أدخل مفتاحًا جديدًا لاستبدال المحفوظ" else "مفتاح API الخاص بك") },
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(), singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            trailingIcon = { IconButton(onClick = { visible = !visible }) { Icon(if (visible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, "إظهار أو إخفاء المفتاح") } })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.task { s.saveKey(provider, key); key = ""; visible = false; revision++; vm.configVersion.value++; keyboard?.hide(); vm.notices.emit("حُفظ المفتاح مشفرًا على الجهاز") } }, enabled = key.isNotBlank()) { Text("حفظ المفتاح") }
            TextButton(onClick = { deleteConfirm = true }, enabled = s.hasKey(provider)) { Text("حذف المفتاح") }
        }
        Action(if (busy) "جارٍ العمل…" else "اختبار الاتصال وتحديث النماذج", Icons.Outlined.Wifi) { if (!busy) vm.test(provider) }
        Picker("النموذج الافتراضي", available, model) { model = it; s.set("model_$provider", it); revision++ }
        Caption(if (provider == "OpenAI") "قائمة الحساب قد تشمل نماذج غير مخصصة للنص. اختر نموذجًا يدعم Responses API. لا يتم تغييره تلقائيًا." else "تعرض القائمة نماذج تدعم generateContent. توفر نموذج في القائمة لا يثبت مجانية استخدامه.")
        Picker("تخصيص نموذج بحسب المهمة", operations, op) { op = it }
        Picker("نموذج «$op»", listOf("") + available, assigned) { assigned = it; s.set("model_${provider}_$op", it) }
        if (provider == "Gemini") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(freeConsent, { freeConsent = it; s.set("geminiConsent_$model", it.toString()) }, enabled = model.isNotBlank())
                Text("أؤكد أنني اخترت هذا النموذج ضمن الفئة المجانية لحسابي بعد مراجعة Google", Modifier.weight(1f), fontSize = 13.sp)
            }
            if (freeConsent) Caption("الفئة المجانية مؤكدة من المستخدم لهذا النموذج؛ غير متحققة آليًا.", Mint)
            Caption("الحصة تختلف حسب النموذج والحساب والمنطقة. لا يعرض API عدادًا موثوقًا للحد المجاني أو حالة الفوترة. التطبيق لا ينتقل إلى نموذج آخر عند نفاد الحصة. إن كان الحساب مدفوعًا فقد تفرض Google رسومًا على الطلب نفسه؛ تحقق من لوحة حسابك.")
            LinkButton("https://ai.google.dev/gemini-api/docs/rate-limits", "حدود Gemini الرسمية")
            LinkButton("https://ai.google.dev/gemini-api/docs/pricing", "النماذج والفئات المجانية والتسعير")
        }
        Caption("الاتصال المباشر من الهاتف خيار للاستخدام الشخصي، وحمايته أقل من خادم وسيط حتى مع التشفير المحلي. لا تشارك المفتاح أو التطبيق مع حسابك. تُمنع لقطات الشاشة، ولا يُدرج المفتاح في السجلات أو النسخ الاحتياطية.", Accent)
        if (provider == "OpenAI") LinkButton("https://platform.openai.com/usage", "لوحة OpenAI للاستهلاك والرصيد الفعلي")
    }
}

