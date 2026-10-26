package com.nabd.news

import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId

data class ModelPrice(val inputPerMillion: Double, val outputPerMillion: Double) {
    fun cost(input: Long, output: Long) = (input * inputPerMillion + output * outputPerMillion) / 1_000_000.0
}
object AiPolicy {
    const val MAX_OUTPUT = 2000
    fun context(messages: List<Pair<String, String>>, maxChars: Int = 12000): List<Pair<String, String>> {
        var remaining = maxChars
        return messages.asReversed().mapNotNull { (role, body) ->
            if (remaining <= 0) null else {
                val text = body.takeLast(remaining)
                remaining -= text.length
                role to text
            }
        }.asReversed().dropWhile { it.first != "user" }
    }
    fun hash(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
    fun periodStarts(now: Long): Pair<Long, Long> {
        val day = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
        val zone = ZoneId.systemDefault()
        return day.atStartOfDay(zone).toInstant().toEpochMilli() to day.withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli()
    }
    fun budgetError(daily: Double, monthly: Double, spentDay: Double, spentMonth: Double, reserve: Double?, unknown: Boolean): String? {
        if (daily <= 0 && monthly <= 0) return null
        if (reserve == null || unknown) return "حد الميزانية مفعّل، لكن التسعير أو تكلفة طلب سابق غير معروفة. اضبط التسعير أو راجع سجل الاستخدام قبل المتابعة."
        if (daily > 0 && spentDay + reserve > daily) return "بلغت الميزانية اليومية المحلية أو لا تكفي لهذا الطلب."
        if (monthly > 0 && spentMonth + reserve > monthly) return "بلغت الميزانية الشهرية المحلية أو لا تكفي لهذا الطلب."
        return null
    }
}
