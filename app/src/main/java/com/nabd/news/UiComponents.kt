package com.nabd.news

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class NabdPalette(val ink:Color,val panel:Color,val accent:Color,val mint:Color,val muted:Color)
private val DarkNabd=NabdPalette(Color(0xFF0B0F16),Color(0xFF151B25),Color(0xFFFFB35C),Color(0xFF76D9B1),Color(0xFF9CA6B5))
private val LightNabd=NabdPalette(Color(0xFFF3F5F9),Color.White,Color(0xFF895015),Color(0xFF126348),Color(0xFF536174))
private val LocalNabdPalette=staticCompositionLocalOf { DarkNabd }
val Ink:Color @Composable get()=LocalNabdPalette.current.ink
val Panel:Color @Composable get()=LocalNabdPalette.current.panel
val Accent:Color @Composable get()=LocalNabdPalette.current.accent
val Mint:Color @Composable get()=LocalNabdPalette.current.mint
val Muted:Color @Composable get()=LocalNabdPalette.current.muted
@Composable fun NabdTheme(mode:String="dark",content: @Composable () -> Unit) {
    val dark=when(mode){"light"->false;"system"->isSystemInDarkTheme();else->true}
    val p=if(dark)DarkNabd else LightNabd
    CompositionLocalProvider(LocalNabdPalette provides p) {
        MaterialTheme(colorScheme=if(dark)darkColorScheme(primary=p.accent,onPrimary=p.ink,secondary=p.mint,background=p.ink,surface=p.panel,surfaceVariant=Color(0xFF202735),onBackground=Color(0xFFF4F4F5),onSurface=Color(0xFFF4F4F5),outlineVariant=Color(0xFF293242))
            else lightColorScheme(primary=p.accent,onPrimary=Color.White,secondary=p.mint,background=p.ink,surface=p.panel,surfaceVariant=Color(0xFFE7ECF4),onBackground=Color(0xFF172131),onSurface=Color(0xFF172131),outlineVariant=Color(0xFFCCD5E2)),
            shapes=Shapes(small=RoundedCornerShape(12.dp),medium=RoundedCornerShape(20.dp),large=RoundedCornerShape(28.dp)),content=content)
    }
}

@Composable fun NewsTimestamp(time:Long) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(time) { while(true) { now=System.currentTimeMillis();kotlinx.coroutines.delay(60000) } }
    Caption("${newsAge(time,now)} • ${dateText(time)}")
}
fun dateText(time: Long) = if (time <= 0) "تاريخ النشر غير متاح" else SimpleDateFormat("dd MMM yyyy • h:mm a", Locale.forLanguageTag("ar")).apply { dateFormatSymbols = dateFormatSymbols.apply { amPmStrings = arrayOf("صباحًا", "مساءً") } }.format(Date(time))
fun money(value: Double?) = value?.let { "$" + String.format(Locale.US, "%.5f", it) } ?: "غير متاح"
@Composable fun Caption(text: String, color: Color = Muted) { Text(text, color = color, style = MaterialTheme.typography.bodySmall, lineHeight = 21.sp) }
@Composable fun SectionTitle(title: String, subtitle: String = "") {
    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    if (subtitle.isNotBlank()) Caption(subtitle)
}
@Composable fun BoxCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = Panel, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}
@Composable fun Action(label: String, icon: ImageVector = Icons.Outlined.ArrowBack, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, shape = RoundedCornerShape(14.dp)) { Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(label) }
}
@Composable fun EmptyState(title: String, body: String, icon: ImageVector = Icons.Outlined.Inbox) {
    BoxCard {
        Icon(icon, null, tint = Accent, modifier = Modifier.size(38.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Text(body, color = Muted, lineHeight = 26.sp)
    }
}
@Composable fun LinkButton(url: String, label: String = "فتح المصدر") {
    val context = LocalContext.current
    if (url.startsWith("https://") || url.startsWith("http://")) TextButton(onClick = {
        openOriginalArticle(context, url)
    }) { Icon(Icons.Outlined.OpenInNew, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(label, maxLines = 2, overflow = TextOverflow.Ellipsis) }
}
@Composable fun SearchField(value: String, onChange: (String) -> Unit, label: String = "ابحث في نبض…") {
    OutlinedTextField(value, onChange, Modifier.fillMaxWidth(), placeholder = { Text(label) }, leadingIcon = { Icon(Icons.Outlined.Search, null) },
        shape = RoundedCornerShape(16.dp), singleLine = true)
}
@OptIn(ExperimentalLayoutApi::class)
@Composable fun ChoiceRow(options: List<String>, value: String, onChange: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { options.forEach { option ->
        FilterChip(selected = value == option, onClick = { onChange(option) }, label = { Text(option) })
    } }
}
@Composable fun ArticleCard(article: Article, selected: Boolean = false, selecting: Boolean = false, onSelect: () -> Unit = {}, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(22.dp), color = Panel,
        border = BorderStroke(1.dp, if (selected) Accent else Color(0xFF242C38))) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = Accent.copy(alpha = .12f), shape = RoundedCornerShape(8.dp)) { Text(article.category, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), color = Accent, fontSize = 11.sp) }
                Spacer(Modifier.weight(1f))
                if (selecting) Checkbox(selected, { onSelect() }) else Icon(Icons.Outlined.Article, null, tint = Muted, modifier = Modifier.size(19.dp))
            }
            if (article.image.isNotBlank()) NewsImage(article.image, "صورة الخبر: ${article.title}")
            Text(article.title, fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 30.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (article.summary.ifBlank { article.body }.isNotBlank()) Text(article.summary.ifBlank { article.body }, color = Muted, maxLines = 2, lineHeight = 24.sp, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
            HorizontalDivider(color = Color(0xFF29313D))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Language, null, tint = Mint, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                Text(article.source, color = Mint, fontSize = 12.sp)
            }
            NewsTimestamp(article.published)
        }
    }
}
@Composable fun ResultContent(a: Analysis, vm: NabdViewModel, close: () -> Unit) {
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val articles by vm.articles.collectAsState()
    Column(Modifier.fillMaxSize().background(Ink).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = close) { Icon(Icons.Outlined.Close, "إغلاق") }
            Text(a.operation, Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            BoxCard { Caption("${a.provider}  •  ${a.model}", Mint); Text(a.status, color = Accent); Caption(dateText(a.created)); Caption("محفوظ محليًا • Tokens: ${a.inputTokens ?: "؟"} / ${a.outputTokens ?: "؟"} • تقدير: ${money(a.cost)}") }
            articles.filter { it.id in a.articleIds.split(',') }.forEach { article -> Caption("الخبر: ${article.title}") }
            SelectionContainer { MarkdownText(a.result) }
            SectionTitle("المصادر المرفقة")
            Caption("روابط الخبر الأصلية ليست تحققًا مستقلًا. راجع ما إذا كانت الاستشهادات تسند الادعاء فعلًا.")
            a.sources.lines().filter { it.isNotBlank() }.forEach { LinkButton(it, it) }
        }
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.saveAnswer(a); vm.task { vm.notices.emit("حُفظت الإجابة في دفتر نبض") } }, modifier = Modifier.weight(1f)) { Text("حفظ في الدفتر") }
            Action("نسخ", Icons.Outlined.ContentCopy) { clipboard.setText(androidx.compose.ui.text.AnnotatedString(a.result)) }
        }
    }
}
@Composable fun MarkdownText(text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        text.lines().forEach { line ->
            val heading = line.startsWith('#')
            val raw = line.trimStart('#').trim().let { if (it.startsWith("- ")) "• " + it.drop(2) else it }
            val formatted = androidx.compose.ui.text.buildAnnotatedString {
                var start = 0
                val pattern = Regex("\\*\\*(.+?)\\*\\*")
                pattern.findAll(raw).forEach { match ->
                    append(raw.substring(start, match.range.first))
                    pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold))
                    append(match.groupValues[1]); pop(); start = match.range.last + 1
                }
                append(raw.substring(start))
            }
            Text(formatted, fontSize = if (heading) 21.sp else 16.sp, lineHeight = 29.sp,
                fontWeight = if (heading) FontWeight.Bold else FontWeight.Normal, color = if (heading) Accent else MaterialTheme.colorScheme.onSurface)
        }
    }
}

