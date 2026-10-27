package com.nabd.news

import java.text.NumberFormat
import java.util.Locale

/** Never invent elapsed time when publisher omitted its date or supplied a future date. */
fun newsAge(time:Long,now:Long=System.currentTimeMillis()):String {
    if(time<=0)return "وقت النشر غير متاح"
    if(time>now+60000)return "موعد نشر لاحق"
    val minutes=((now-time).coerceAtLeast(0)/60000)
    val n=NumberFormat.getIntegerInstance(Locale.forLanguageTag("ar")).apply{isGroupingUsed=false}
    return when {
        minutes<1->"الآن"
        minutes==1L->"منذ دقيقة"
        minutes==2L->"منذ دقيقتين"
        minutes<60->"منذ ${n.format(minutes)} دقيقة"
        minutes<120->"منذ ساعة"
        minutes<180->"منذ ساعتين"
        minutes<1440->"منذ ${n.format(minutes/60)} ساعة"
        minutes<2880->"منذ يوم"
        minutes<4320->"منذ يومين"
        else->"منذ ${n.format(minutes/1440)} يومًا"
    }
}
