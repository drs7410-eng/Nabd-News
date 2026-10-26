package com.nabd.news

/** Candidate publishers are NOT installed feeds. Only a successful device fetch can verify a feed. */
object WorldCatalog {
    data class Entry(val publisher:WorldPublisher,val feed:String="",val evidence:String="")
    private fun p(id:String,name:String,arabic:String,site:String,country:String,language:String="en")=WorldPublisher(id,name,arabic,site,country,language,
        rightsNote="مرشح للفحص؛ لم تُثبت أذونات الحفظ والترجمة والاستخراج. وجود RSS أو اشتراك لا يمنح إذن معالجة تلقائيًا.")
    val entries=listOf(
        Entry(p("nyt","The New York Times","نيويورك تايمز","https://www.nytimes.com/","الولايات المتحدة"),"https://rss.nytimes.com/services/xml/rss/nyt/World.xml","https://rss.nytimes.com/"),
        Entry(p("wsj","The Wall Street Journal","وول ستريت جورنال","https://www.wsj.com/","الولايات المتحدة")),
        Entry(p("washington","The Washington Post","واشنطن بوست","https://www.washingtonpost.com/","الولايات المتحدة")),
        Entry(p("bloomberg","Bloomberg","بلومبرغ","https://www.bloomberg.com/","الولايات المتحدة")),
        Entry(p("ap","Associated Press","أسوشيتد برس","https://apnews.com/","الولايات المتحدة")),
        Entry(p("cnn-int","CNN International","سي إن إن الدولية","https://edition.cnn.com/","الولايات المتحدة")),
        Entry(p("ft","Financial Times","فايننشال تايمز","https://www.ft.com/","بريطانيا")),
        Entry(p("guardian-world","The Guardian","الغارديان","https://www.theguardian.com/","بريطانيا").copy(permission="restricted",allowCache=true,
            termsUrl="https://www.theguardian.com/help/terms-of-service",rightsNote="RSS متاح للاستخدام الشخصي وفق شروطه؛ الشروط تقيّد المعالجة بتقنيات AI. الترجمة والاستخراج الآلي غير مفعّلين هنا."),"https://www.theguardian.com/world/rss","https://www.theguardian.com/help/feeds"),
        Entry(p("times","The Times","ذا تايمز","https://www.thetimes.com/","بريطانيا")),
        Entry(p("telegraph","The Telegraph","التلغراف","https://www.telegraph.co.uk/","بريطانيا")),
        Entry(p("bbc-world","BBC News","بي بي سي نيوز","https://www.bbc.com/news/","بريطانيا"),"https://feeds.bbci.co.uk/news/world/rss.xml","https://support.bbc.co.uk/platform/feeds/NewsFeeds.htm"),
        Entry(p("independent","The Independent","إندبندنت","https://www.independent.co.uk/","بريطانيا")),
        Entry(p("reuters","Reuters","رويترز","https://www.reuters.com/","دولي")),
        Entry(p("dw-world","Deutsche Welle","دويتشه فيله","https://www.dw.com/","ألمانيا"),"https://rss.dw.com/xml/rss-en-all","https://corporate.dw.com/en/rss-feeds/a-68693346"),
        Entry(p("france24","France 24","فرانس 24","https://www.france24.com/en/","فرنسا")),
        Entry(p("lemonde","Le Monde","لوموند","https://www.lemonde.fr/","فرنسا","fr"),"https://www.lemonde.fr/rss/une.xml","https://www.lemonde.fr/le-monde-et-vous/article/2025/07/14/les-flux-rss-du-monde-fr_5498778_3237.html"),
        Entry(p("euronews","Euronews","يورونيوز","https://www.euronews.com/","أوروبا")),
        Entry(p("elpais","El País","إل باييس","https://elpais.com/","إسبانيا","es")),
        Entry(p("spiegel","Der Spiegel","دير شبيغل","https://www.spiegel.de/","ألمانيا","de")),
        Entry(p("nikkei","Nikkei Asia","نيكي آسيا","https://asia.nikkei.com/","اليابان")),
        Entry(p("japantimes","The Japan Times","جابان تايمز","https://www.japantimes.co.jp/","اليابان")),
        Entry(p("scmp","South China Morning Post","ساوث تشاينا مورنينغ بوست","https://www.scmp.com/","هونغ كونغ")),
        Entry(p("straits","The Straits Times","ستريتس تايمز","https://www.straitstimes.com/","سنغافورة")),
        Entry(p("ajenglish","Al Jazeera English","الجزيرة الإنجليزية","https://www.aljazeera.com/","قطر")),
        Entry(p("koreaherald","The Korea Herald","كوريا هيرالد","https://www.koreaherald.com/","كوريا الجنوبية")),
        Entry(p("globalvoices","Global Voices","أصوات عالمية","https://globalvoices.org/","دولي").copy(permission="recorded",allowCache=true,allowLocal=true,allowCloud=true,allowHtml=true,
            termsUrl="https://globalvoices.org/about/global-voices-attribution-policy/",rightsNote="نصوص Global Voices الأصلية CC BY 3.0 ما لم يُذكر غير ذلك؛ يلزم اسم الكاتب ورابط الأصل والترخيص وبيان الترجمة الآلية. الصور ومواد الأطراف الثالثة مستبعدة. سياسة AI التحريرية تخص مساهمي الموقع ولا تُعد اعتمادًا لهذه الترجمة."),"https://globalvoices.org/feed/","https://globalvoices.org/about/"),
        Entry(p("nasa-world","NASA News","أخبار ناسا","https://www.nasa.gov/","الولايات المتحدة").copy(termsUrl="https://www.nasa.gov/nasa-brand-center/images-and-media/",rightsNote="محتوى حكومي متاح وفق إرشادات NASA مع استثناء مواد الأطراف الثالثة؛ افحص مادة المقال قبل تسجيل الأذونات. ليست صحيفة عامة."))
    )
}
object WorldRights {
    fun needsAuthor(website:String)=PublisherScope.samePublisher("https://globalvoices.org/",website)
    fun knownRestricted(website:String)=PublisherScope.samePublisher("https://www.theguardian.com/",website)
    fun localBlock(p:WorldPublisher,meta:WorldArticle):String = when {
        knownRestricted(p.website)->"شروط هذه الصحيفة تقيّد المعالجة الآلية؛ تنزيل النموذج لا يزيل هذا القيد"
        meta.restriction.isNotBlank()->meta.restriction
        !p.allowCache || !p.allowLocal || p.permission!="recorded"->"إذن الترجمة المحلية لهذه الصحيفة لم يسجل بعد؛ افتح إعداد الصحيفة وأكمل الأذونات"
        needsAuthor(p.website)&&meta.author.isBlank()->"نسبة الكاتب غير متاحة؛ أعد فحص متن المصدر قبل الترجمة"
        else->""
    }
    fun local(p:WorldPublisher,meta:WorldArticle)=!knownRestricted(p.website) && meta.restriction.isBlank() && p.allowCache && p.allowLocal && p.permission=="recorded" && (!needsAuthor(p.website) || meta.author.isNotBlank())
    fun cloud(p:WorldPublisher,meta:WorldArticle)=!knownRestricted(p.website) && meta.restriction.isBlank() && p.allowCache && p.allowCloud && p.permission=="recorded" && (!needsAuthor(p.website) || meta.author.isNotBlank())
    suspend fun requireCloud(dao:NabdDao,ids:List<String>) {
        ids.forEach { id -> dao.worldArticle(id)?.let{meta -> val p=dao.worldPublisher(meta.publisherId);require(p!=null && cloud(p,meta)){"لم يُسجل إذن إرسال محتوى ${p?.name?:"هذا المصدر"} إلى AI سحابي. لم يُرسل الطلب."}} }
    }
}
