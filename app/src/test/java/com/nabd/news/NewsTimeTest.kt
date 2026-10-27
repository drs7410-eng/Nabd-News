package com.nabd.news

import org.junit.Assert.*
import org.junit.Test
import java.util.*

class NewsTimeTest {
    private val now=2000000000000L
    @Test fun elapsedTimeUsesActualPublisherDateAndArabicUnits() {
        assertEquals("الآن",newsAge(now,now))
        assertEquals("منذ دقيقة",newsAge(now-60000,now))
        assertEquals("منذ دقيقتين",newsAge(now-120000,now))
        assertEquals("منذ ١٢ دقيقة",newsAge(now-12*60000,now))
        assertEquals("منذ ساعتين",newsAge(now-2*3600000,now))
        assertEquals("منذ يومين",newsAge(now-2*86400000,now))
        assertEquals("وقت النشر غير متاح",newsAge(0,now))
        assertEquals("موعد نشر لاحق",newsAge(now+3600000,now))
    }
    @Test fun absoluteTimeUsesTwelveHourClockAndExplicitPeriod() {
        val cal=Calendar.getInstance().apply { set(2026,9,27,14,5,0) }
        val afternoon=dateText(cal.timeInMillis)
        assertTrue(afternoon,afternoon.contains("مساءً"));assertTrue(afternoon,afternoon.contains("٢:٠٥"));assertFalse(afternoon.contains("١٤:"))
        cal.set(Calendar.HOUR_OF_DAY,0)
        val midnight=dateText(cal.timeInMillis)
        assertTrue(midnight.contains("صباحًا"));assertTrue(midnight.contains("١٢:٠٥"))
    }
}
