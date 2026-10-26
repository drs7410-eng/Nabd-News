package com.nabd.news

/** Feed availability is checked on the user's device; these are not invented live audit results. */
data class CatalogSource(val id: String, val name: String, val website: String, val feed: String?, val reference: String, val status: String, val category: String = "العالم")
object SourceCatalog {
    val entries = listOf(
        CatalogSource("aawsat-economy", "الشرق الأوسط • الاقتصاد", "https://aawsat.com/", "https://aawsat.com/feed/economy", "https://aawsat.com/rss-feed", "منشور في دليل الناشر الرسمي؛ يُفحص قبل التفعيل", "اقتصاد"),
        CatalogSource("aawsat-gulf", "الشرق الأوسط • الخليج", "https://aawsat.com/", "https://aawsat.com/feed/gulf", "https://aawsat.com/rss-feed", "منشور في دليل الناشر الرسمي؛ يُفحص قبل التفعيل", "الخليج"),
        CatalogSource("aawsat-sport", "الشرق الأوسط • الرياضة", "https://aawsat.com/", "https://aawsat.com/feed/sport", "https://aawsat.com/rss-feed", "منشور في دليل الناشر الرسمي؛ يُفحص قبل التفعيل", "رياضة"),
        CatalogSource("aawsat-information-technology", "الشرق الأوسط • التقنية", "https://aawsat.com/", "https://aawsat.com/feed/information-technology", "https://aawsat.com/rss-feed", "منشور في دليل الناشر الرسمي؛ يُفحص قبل التفعيل", "تقنية"),
        CatalogSource("aawsat-health", "الشرق الأوسط • الصحة", "https://aawsat.com/", "https://aawsat.com/feed/health", "https://aawsat.com/rss-feed", "منشور في دليل الناشر الرسمي؛ يُفحص قبل التفعيل", "صحة وعلوم"),
        CatalogSource("aawsat-science", "الشرق الأوسط • العلوم", "https://aawsat.com/", "https://aawsat.com/feed/science", "https://aawsat.com/rss-feed", "منشور في دليل الناشر الرسمي؛ يُفحص قبل التفعيل", "صحة وعلوم"),
        CatalogSource("aawsat-culture", "الشرق الأوسط • الثقافة", "https://aawsat.com/", "https://aawsat.com/feed/culture", "https://aawsat.com/rss-feed", "منشور في دليل الناشر الرسمي؛ يُفحص قبل التفعيل", "ثقافة"),
        CatalogSource("aawsat-arab-world", "الشرق الأوسط • العالم العربي", "https://aawsat.com/", "https://aawsat.com/feed/arab-world", "https://aawsat.com/rss-feed", "منشور في دليل الناشر الرسمي؛ يُفحص قبل التفعيل", "العالم العربي"),
        CatalogSource("hadath-alhadath", "الحدث • الرئيسية", "https://www.alhadath.net/", "https://www.alhadath.net/.mrss/alhadath.xml", "https://www.alhadath.net/tools/mrss", "منشور في دليل RSS الرسمي؛ استجابة XML تحتاج فحص محتواها على الجهاز", "العالم"),
        CatalogSource("hadath-alhadath-yemen", "الحدث • اليمن", "https://www.alhadath.net/", "https://www.alhadath.net/.mrss/alhadath/yemen.xml", "https://www.alhadath.net/tools/mrss", "منشور في دليل RSS الرسمي؛ استجابة XML تحتاج فحص محتواها على الجهاز", "العالم العربي"),
        CatalogSource("hadath-alhadath-syria", "الحدث • سوريا", "https://www.alhadath.net/", "https://www.alhadath.net/.mrss/alhadath/syria.xml", "https://www.alhadath.net/tools/mrss", "منشور في دليل RSS الرسمي؛ استجابة XML تحتاج فحص محتواها على الجهاز", "العالم العربي"),
        CatalogSource("hadath-alhadath-egypt", "الحدث • مصر", "https://www.alhadath.net/", "https://www.alhadath.net/.mrss/alhadath/egypt.xml", "https://www.alhadath.net/tools/mrss", "منشور في دليل RSS الرسمي؛ استجابة XML تحتاج فحص محتواها على الجهاز", "العالم العربي"),
        CatalogSource("hadath-alhadath-maghreb", "الحدث • المغرب العربي", "https://www.alhadath.net/", "https://www.alhadath.net/.mrss/alhadath/maghreb.xml", "https://www.alhadath.net/tools/mrss", "منشور في دليل RSS الرسمي؛ استجابة XML تحتاج فحص محتواها على الجهاز", "المغرب العربي"),
        CatalogSource("aj-ar", "الجزيرة نت", "https://www.aljazeera.net/", "https://www.aljazeera.net/rss", "https://www.aljazeera.net/rss", "خلاصة عربية من نطاق الناشر؛ تُختبر على جهازك قبل التفعيل"),
        CatalogSource("sky-ar", "سكاي نيوز عربية", "https://www.skynewsarabia.com/", "https://www.skynewsarabia.com/rss.xml", "https://www.skynewsarabia.com/rss.xml", "استجاب الرابط بنوع RSS في فحص الويب؛ المحتوى يُختبر على الجهاز"),
        CatalogSource("f24-ar", "فرانس 24 عربي", "https://www.france24.com/ar/", "https://www.france24.com/ar/rss", "https://www.france24.com/ar/", "رابط خلاصة مرشح على نطاق الناشر؛ لا يُفعّل حتى ينجح فحص الجهاز"),
        CatalogSource("hespress-ar", "هسبريس", "https://www.hespress.com/", "https://www.hespress.com/feed", "https://www.hespress.com/feed", "استجاب الرابط بنوع RSS في فحص الويب؛ أخبار المغرب", "المغرب العربي"),
        CatalogSource("un-ar", "أخبار الأمم المتحدة", "https://news.un.org/ar/", "https://news.un.org/feed/subscribe/ar/news/all/rss.xml", "https://www.un.org/ar/ga/rss/index.shtml", "مصدر مؤسسي عربي؛ يلزم نجاح فحص الخلاصة على الجهاز"),
        CatalogSource("aawsat-ar", "الشرق الأوسط", "https://aawsat.com/", "https://aawsat.com/feed/news", "https://aawsat.com/rss-feed", "منشور في دليل RSS الرسمي؛ يحتاج فحصًا مباشرًا"),
        CatalogSource("rt-ar", "روسيا اليوم • RT Arabic", "https://arabic.rt.com/", "https://arabic.rt.com/rss/", "https://feeder.co/discover/c5aa2b0723/arabic-rt-com", "رابط متداول على نطاق الناشر؛ تعذر التحقق المباشر من البيئة الحالية"),
        CatalogSource("dw-ar", "DW عربية", "https://www.dw.com/ar/", "https://rss.dw.com/rdf/rss-ar-all", "https://rss.dw.com/rdf/rss-ar-all", "خلاصة عربية على نطاق DW؛ جودة المحتوى تُفحص على جهازك"),
        CatalogSource("cnbc-ar", "CNBC عربية", "https://www.cnbcarabia.com/", null, "https://www.cnbcarabia.com/", "لم أجد رابط RSS عامًا موثوقًا؛ CNBC الإنجليزية ليست بديلًا عربيًا"),
        CatalogSource("cnn-ar", "CNN بالعربية", "https://arabic.cnn.com/", "https://arabic.cnn.com/api/v1/rss/rss.xml", "https://feeder.co/discover/949259765c/arabic-cnn-com-api-v1-rss-rss-xml", "رابط مسجل في أدلة الخلاصات؛ أعادت أداة التصفح 451 في هذه البيئة"),
        CatalogSource("reuters-ar", "رويترز عربية", "https://www.reuters.com/", null, "https://www.fivefilters.org/2021/reuters-rss-feeds/", "لا توجد خلاصة عربية عامة مؤكدة؛ روابط Reuters العامة القديمة متوقفة"),
        CatalogSource("bbc-ar", "BBC نيوز عربي", "https://www.bbc.com/arabic", "https://feeds.bbci.co.uk/arabic/rss.xml", "https://github.com/bbc/world-service-rss", "رابط BBC العربي؛ يحتاج فحص جودة على جهازك")
    )
}
