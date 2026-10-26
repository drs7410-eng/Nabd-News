package com.nabd.news

import org.junit.Assert.*
import org.junit.Test

/** Algorithm fixtures only. These are not claims of live publisher access or ML Kit language quality. */
class WorldEditorialTest {
    @Test fun protectsAndRestoresDatesNumbersQuotesUrlsAndGlossary(){
        val text="The Federal Reserve said “Revenue rose 12.5% on 2026-10-16.” See https://example.org/report/123."
        val g=WorldGlossary(original="Federal Reserve",arabic="الاحتياطي الفدرالي")
        val protected=EditorialProtection.protect(text,listOf(g),"en")
        assertFalse(protected.text.contains("12.5"));assertFalse(protected.text.contains("Federal Reserve"))
        val result=protected.restore(protected.text)
        assertTrue(result.contains("الاحتياطي الفدرالي"));assertTrue(result.contains("https://example.org/report/123."))
        EditorialProtection.validate(text,result)
        assertEquals(EditorialProtection.numbers(text),EditorialProtection.numbers(result))
    }
    @Test fun refusesDroppedOrDuplicatedProtectionMarkers(){
        val p=EditorialProtection.protect("The rate is 20%",emptyList(),"en")
        assertTrue(runCatching{p.restore("لا رقم")}.isFailure)
        assertTrue(runCatching{p.restore(p.text+p.text)}.isFailure)
    }
    @Test fun blocksChangedFactsAndQuoteMarks(){
        assertTrue(runCatching{EditorialProtection.validate("The value was 100 on 2026-10-16","القيمة 200 في 2026-10-16")}.isFailure)
        assertTrue(runCatching{EditorialProtection.validate("She said “yes”","قالت نعم")}.isFailure)
        EditorialProtection.validate("Value 12%","القيمة ١٢٪")
    }
    @Test fun glossaryUsesWholeTermsAndSpecificDomainOverridesGeneral(){
        val general=WorldGlossary(original="bond",arabic="رابطة",updated=20)
        val economics=WorldGlossary(original="bond",arabic="سند",domain="الاقتصاد والأسواق",updated=10)
        val p=EditorialProtection.protect("bond and bonding",listOf(general,economics),"en")
        val result=p.restore(p.text)
        assertTrue(result.startsWith("سند"));assertTrue(result.endsWith("bonding"))
        assertNotEquals(EditorialProtection.glossaryHash(listOf(general)),EditorialProtection.glossaryHash(listOf(economics)))
    }
    @Test fun longParagraphsAreChunkedLosslesslyWithoutBreakingSurrogates(){
        val text=("A real sentence boundary. " + "🌍".repeat(70)).repeat(60)
        val chunks=WorldTextParts.chunks(text)
        assertTrue(chunks.size>1);assertEquals(text,chunks.joinToString(""))
        assertTrue(chunks.all{it.length<=1200&&!Character.isHighSurrogate(it.last())})
    }
    @Test fun structuredBlocksKeepHeadingImagesListsAndLinkPositions(){
        val a=Article(title="Title",body="summary",summary="summary",fullContent="intro\nheading\nlist",url="https://publisher.test/a",source="Source",contentOrigin="article_page",contentBlocks=ContentCodec.blocks(listOf(
            ArticleBlock(text="Intro with link",links=listOf(ArticleLink(11,15,"https://publisher.test/link"))),ArticleBlock(type="heading",text="Heading"),ArticleBlock(type="image",url="https://publisher.test/image.jpg"),ArticleBlock(type="list_item",text="List item"))))
        val pieces=worldReadingPieces(a)
        assertEquals(listOf("paragraph","heading","image","list_item"),pieces.map{it.block.type})
        assertEquals(11,pieces.first().block.links!!.single().start)
        assertTrue(WorldTextParts.article(a).any{it.slot=="block-3"})
        assertTrue(worldReadingPieces(a.copy(fullContent="")).isEmpty())
    }
    @Test fun feedDiscoveryUsesDeclaredXmlLinksAndRejectsOtherHostsOrPrivateAddresses(){
        val html="""<link rel="alternate" type="application/rss+xml" href="/feed/"><link rel="alternate" type="text/html" href="/other"><link rel="alternate" type="application/atom+xml" href="https://evil.test/feed">"""
        assertEquals(listOf("https://publisher.test/feed/"),WorldUrls.discovered(html,"https://publisher.test/"))
        listOf("https://127.0.0.1/feed","https://localhost/feed","https://a.local/feed","https://user:pass@publisher.test/feed","http://publisher.test/feed","https://publisher.test/feed?api_key=secret").forEach{assertFalse(it,WorldUrls.valid(it))}
        assertTrue(WorldUrls.samePublisher("https://edition.cnn.com/","https://rss.cnn.com/rss.xml"))
    }
    @Test fun extractsDeclaredLanguageAndAuthorWithoutInventingMetadata(){
        val xml="""<rss xmlns:dc="http://purl.org/dc/elements/1.1/"><channel><language>fr-FR</language><item><title>Test</title><link>https://publisher.test/a</link><dc:creator>Author Name</dc:creator></item></channel></rss>""".toByteArray()
        assertEquals("fr",WorldMetadata.language(xml))
        assertEquals("Author Name",WorldMetadata.authors(xml,"https://publisher.test/feed")["https://publisher.test/a"])
        assertEquals("",WorldMetadata.language("<rss><channel/></rss>".toByteArray()))
    }
    @Test fun knownRestrictionsCannotBeBypassedByAnotherLocalPublisherId(){
        val p=WorldPublisher("new-id","Custom","مخصص","https://www.theguardian.com/","UK","en",permission="recorded",allowCache=true,allowLocal=true,allowCloud=true)
        val m=WorldArticle("a",p.id,"feed","en")
        assertFalse(WorldRights.local(p,m));assertFalse(WorldRights.cloud(p,m))
    }
    @Test fun staleTranslationCannotMasqueradeAsCurrentTextOrGlossary(){
        val row=WorldTranslation("t","a","title","en",textHash=AiPolicy.hash("Original"),glossaryHash="g",engine="MLKit",engineVersion="v",original="Original",translated="ترجمة")
        assertNotNull(WorldTranslationChoice.get(listOf(row),"title","Original","en","g","MLKit","v"))
        assertNull(WorldTranslationChoice.get(listOf(row),"title","Changed","en","g"))
        assertNull(WorldTranslationChoice.get(listOf(row),"title","Original","en","changed"))
        assertNull(WorldTranslationChoice.get(listOf(row),"title","Original","en","g","MLKit","v2"))
    }
    @Test fun glossaryMetadataEditsDoNotInvalidateOutputButRelevantTermsDo() {
        val term=WorldGlossary(original="bond",arabic="سند")
        assertEquals(EditorialProtection.glossaryHash(listOf(term)),EditorialProtection.glossaryHash(listOf(term.copy(notes="ملاحظة جديدة",reference="مرجع",updated=900))))
        assertNotEquals(EditorialProtection.glossaryHash(listOf(term),"عام"),EditorialProtection.glossaryHash(listOf(term),"العلوم"))
    }
    @Test fun supportsGlossaryTermsInLanguagesWithoutSpaceSeparatedWords() {
        val term=WorldGlossary(original="人工知能",arabic="الذكاء الاصطناعي",language="ja")
        val p=EditorialProtection.protect("この人工知能モデル",listOf(term),"ja")
        assertTrue(p.restore(p.text).contains("الذكاء الاصطناعي"))
    }
    @Test fun licensedPublisherAliasesStillRequireBylineForAttribution() {
        val p=WorldPublisher("alias","Global Voices","اسم","https://fr.globalvoices.org/","دولي","fr",permission="recorded",allowCache=true,allowLocal=true,allowCloud=true)
        val meta=WorldArticle("a",p.id,"f","fr")
        assertFalse(WorldRights.local(p,meta));assertTrue(WorldRights.local(p,meta.copy(author="Writer")))
    }
    @Test fun nonCjkGlossaryTermsStillRespectWordBoundaries() {
        val term=WorldGlossary(original="علم",arabic="معرفة",language="ar")
        val p=EditorialProtection.protect("علم وتعليم",listOf(term),"ar")
        assertEquals("معرفة وتعليم",p.restore(p.text))
    }
    @Test fun localModelNeverReceivesOpaqueTokensAndNumbersQuotesTermsSurvive()=kotlinx.coroutines.runBlocking {
        val original="Revenue “rose 12.5%” on 2026-10-16 at NASA."
        val protected=EditorialProtection.protect(original,listOf(WorldGlossary(original="NASA",arabic="ناسا")),"en")
        val calls=mutableListOf<String>()
        val result=protected.locally{part->
            calls+=part;assertFalse(part.contains("NBD"));assertTrue(EditorialProtection.numbers(part).isEmpty())
            part.replace("Revenue","الإيرادات").replace("rose","ارتفعت").replace("on","في").replace("at","لدى")
        }
        assertTrue(calls.isNotEmpty());assertTrue(result.contains("ناسا"));assertTrue(result.contains("12.5%"));assertTrue(result.contains("2026-10-16"))
        EditorialProtection.validate(original,result)
    }
    @Test fun whollyProtectedTextNeedsNoInferenceAndCancellationPropagates()=kotlinx.coroutines.runBlocking {
        val p=EditorialProtection.protect("2026 “NASA”",listOf(WorldGlossary(original="NASA",arabic="ناسا")),"en")
        assertEquals("2026 “ناسا”",p.locally{error("No text requires inference")})
        assertTrue(runCatching{EditorialProtection.protect("Some words 12",emptyList(),"en").locally{throw kotlinx.coroutines.CancellationException("cancel")}}.exceptionOrNull() is kotlinx.coroutines.CancellationException)
    }
    @Test fun localGatesExplainWhyModelDownloadAloneDoesNotGrantPublisherRights(){
        val p=WorldPublisher("p","Publisher","صحيفة","https://publisher.test/","بلد","en")
        val a=WorldArticle("a","p","f","en")
        assertTrue(WorldRights.localBlock(p,a).contains("إذن"))
        assertTrue(WorldRights.localBlock(p.copy(website="https://www.theguardian.com/"),a).contains("تقيّد"))
        assertEquals("",WorldRights.localBlock(p.copy(permission="recorded",allowCache=true,allowLocal=true),a))
    }
}
