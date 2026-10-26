package com.nabd.news

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable fun UsageScreen(vm: NabdViewModel) {
    val usage by vm.usage.collectAsStateWithLifecycle()
    var daily by rememberSaveable { mutableStateOf(vm.settings.get("daily", "0")) }
    var monthly by rememberSaveable { mutableStateOf(vm.settings.get("monthly", "0")) }
    var provider by rememberSaveable { mutableStateOf("OpenAI") }
    var model by rememberSaveable { mutableStateOf(vm.settings.model("OpenAI")) }
    var inputPrice by rememberSaveable(provider, model) { mutableStateOf(vm.settings.get("price_in_${provider}_$model")) }
    var outputPrice by rememberSaveable(provider, model) { mutableStateOf(vm.settings.get("price_out_${provider}_$model")) }
    val (day, month) = AiPolicy.periodStarts(System.currentTimeMillis())
    val open = usage.filter { it.provider == "OpenAI" }
    val monthUsage = open.filter { it.created >= month }
    val unpriced = monthUsage.count { it.cost == null || it.state == "reserved" }
    var reviewId by remember { mutableStateOf<Usage?>(null) }
    var actual by remember { mutableStateOf("") }
    if (reviewId != null) AlertDialog(onDismissRequest = { reviewId = null }, title = { Text("مراجعة تكلفة غير معروفة") },
        text = { Column { Text("راجع الطلب في لوحة المزود، ثم أدخل التكلفة بالدولار كما ظهرت هناك. لا تستخدم صفرًا إلا إذا تأكدت أن الطلب لم يُحتسب."); OutlinedTextField(actual, { actual = it }, label = { Text("التكلفة التي راجعتها") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)) } },
        confirmButton = { TextButton(onClick = { val v = actual.toDoubleOrNull(); if (v != null && v >= 0 && v.isFinite()) { val u = reviewId!!; reviewId = null; vm.task { vm.dao.saveUsage(u.copy(cost = v, state = "reviewed")) } } }) { Text("حفظ المراجعة") } },
        dismissButton = { TextButton(onClick = { reviewId = null }) { Text("إلغاء") } })
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { BoxCard {
            SectionTitle("استهلاكك تحت نظرك")
            Text("${open.size} طلب OpenAI مسجل محليًا", fontWeight = FontWeight.Bold)
            Caption("Tokens المعروفة: إدخال ${open.sumOf { it.inputTokens ?: 0 }} • إخراج ${open.sumOf { it.outputTokens ?: 0 }}")
            Caption("اليوم: ${money(open.filter { it.created >= day }.sumOf { it.cost ?: 0.0 })} • الشهر: ${money(monthUsage.sumOf { it.cost ?: 0.0 })}", Mint)
            if (unpriced > 0) Caption("هناك $unpriced طلب بتكلفة أو حالة تحتاج مراجعة. المجموع المعروض جزئي.", Accent)
            Caption("هذه تقديرات محلية وليست رصيد الحساب أو فاتورته. الحد المحلي لا يحمي المفتاح من استخدامه خارج هذا التطبيق.")
            LinkButton("https://platform.openai.com/usage", "الاستهلاك الفعلي لدى OpenAI")
            LinkButton("https://platform.openai.com/settings/organization/billing/overview", "الرصيد والفوترة")
        } }
        item { BoxCard {
            SectionTitle("ميزانية OpenAI المحلية", "بالدولار • 0 لتعطيل الحد • تمنع الطلبات الجديدة عند بلوغ الحد")
            OutlinedTextField(daily, { daily = it }, label = { Text("حد يومي تقديري") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(monthly, { monthly = it }, label = { Text("حد شهري تقديري") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            Button(onClick = { vm.task {
                val d = daily.toDoubleOrNull(); val m = monthly.toDoubleOrNull()
                require(d != null && m != null && d >= 0 && m >= 0 && d.isFinite() && m.isFinite()) { "أدخل مبالغ صحيحة غير سالبة" }
                vm.settings.set("daily", d.toString()); vm.settings.set("monthly", m.toString()); vm.notices.emit("حُفظت حدود الميزانية")
            } }) { Text("حفظ الميزانية") }
            Caption("يُحجز تقدير محافظ قبل التنفيذ. عند غياب التسعير أو تكلفة طلب سابق تُوقف الطلبات مع حد مفعّل حتى المراجعة. تتبع التكلفة دورات اليوم والشهر وفق توقيت الجهاز. تغيير ساعة الجهاز يؤثر على الحدود.")
        } }
        item { BoxCard {
            SectionTitle("تسعير النموذج", "أدخل السعر الرسمي بالدولار لكل مليون Token. يُربط باسم النموذج تحديدًا، ولا تُفترض أسعار قديمة أو أسعار نماذج أخرى.")
            Picker("المزود", listOf("OpenAI", "Gemini"), provider) { provider = it; model = vm.settings.model(it) }
            val models = vm.settings.get("models_$provider").lines().filter { it.isNotBlank() }
            Picker("النموذج", models, model) { model = it }
            OutlinedTextField(inputPrice, { inputPrice = it }, label = { Text("سعر مليون Token إدخال") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(outputPrice, { outputPrice = it }, label = { Text("سعر مليون Token إخراج") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            Button(onClick = { vm.task {
                val i = inputPrice.toDoubleOrNull(); val o = outputPrice.toDoubleOrNull()
                require(model.isNotBlank() && i != null && o != null && i >= 0 && o >= 0 && i.isFinite() && o.isFinite()) { "اختر نموذجًا وأدخل أسعارًا صحيحة" }
                vm.settings.set("price_in_${provider}_$model", i.toString()); vm.settings.set("price_out_${provider}_$model", o.toString()); vm.notices.emit("حُفظ التسعير لهذا النموذج")
            } }, enabled = model.isNotBlank()) { Text("حفظ الأسعار") }
            LinkButton(if (provider == "OpenAI") "https://openai.com/api/pricing/" else "https://ai.google.dev/gemini-api/docs/pricing", "التسعير الرسمي الحالي")
            Caption("تقدير النص لا يشمل رسوم البحث أو الأدوات أو خصومات الإدخال المخزن. الطلب الذي يستخدم البحث تُترك تكلفته غير معروفة حتى المراجعة.")
        } }
        item { SectionTitle("سجل الطلبات المحلي", "اختبار قائمة النماذج لا ينشئ نصًا ولا يُسجل كطلب توليد.") }
        items(usage.take(300), key = { it.id }) { u -> BoxCard {
            Text("${u.provider} • ${u.model}", fontWeight = FontWeight.Bold); Caption(dateText(u.created))
            Caption("Tokens: ${u.inputTokens ?: "؟"} / ${u.outputTokens ?: "؟"} • ${money(u.cost)}")
            Caption(when(u.state) { "unknown" -> "تكلفة غير معروفة؛ قد يكون الطلب قد احتُسب رغم الخطأ"; "reserved" -> "حجز سابق يحتاج مراجعة إذا انقطع التطبيق"; "reviewed" -> "راجعت التكلفة يدويًا"; else -> "مكتمل" })
            if (u.cost == null || u.state == "reserved") TextButton(onClick = { actual = ""; reviewId = u }) { Text("مراجعة من لوحة المزود") }
        } }
    }
}
