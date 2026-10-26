package com.nabd.news

import org.junit.Assert.*
import org.junit.Test

class AiPolicyTest {
    @Test fun `budget blocks unknown price and unknown previous request`() {
        assertNotNull(AiPolicy.budgetError(1.0, 10.0, 0.0, 0.0, null, false))
        assertNotNull(AiPolicy.budgetError(1.0, 10.0, 0.0, 0.0, 0.1, true))
        assertNull(AiPolicy.budgetError(0.0, 0.0, 0.0, 0.0, null, true))
    }
    @Test fun `reserves request before daily and monthly boundaries`() {
        assertNotNull(AiPolicy.budgetError(1.0, 10.0, 0.95, 2.0, 0.1, false))
        assertNotNull(AiPolicy.budgetError(1.0, 10.0, 0.0, 9.95, 0.1, false))
        assertNull(AiPolicy.budgetError(1.0, 10.0, 0.5, 2.0, 0.1, false))
    }
    @Test fun `context retains recent exchange within a hard limit`() {
        val history = listOf("user" to "old".repeat(100), "assistant" to "middle".repeat(50), "user" to "latest question")
        val result = AiPolicy.context(history, 70)
        assertTrue(result.sumOf { it.second.length } <= 70)
        assertEquals("latest question", result.last().second)
        assertFalse(result.any { it.second.contains("old") })
    }
    @Test fun `price calculation is per million tokens`() {
        assertEquals(0.005, ModelPrice(2.0, 6.0).cost(1000, 500), 0.000001)
    }
    @Test fun `cache key changes with content model and operation`() {
        val base = AiPolicy.hash("model-a|summary|content-one")
        assertEquals(base, AiPolicy.hash("model-a|summary|content-one"))
        assertNotEquals(base, AiPolicy.hash("model-b|summary|content-one"))
        assertNotEquals(base, AiPolicy.hash("model-a|verify|content-one"))
        assertNotEquals(base, AiPolicy.hash("model-a|summary|content-two"))
    }
}
