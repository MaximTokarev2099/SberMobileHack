package com.sbermobilehack.magicball

import org.junit.Assert.*
import org.junit.Test

class AnswerTest {
    @Test fun excludesPreviousAnswer() {
        repeat(100) { assertEquals("Второй", nextAnswer(listOf("Первый", "Второй"), "Первый")) }
    }
    @Test fun allowsSingleAnswer() {
        assertEquals("Один", nextAnswer(listOf("Один"), "Один"))
    }
    @Test fun emptyListHasFallback() {
        assertTrue(nextAnswer(emptyList(), "").isNotBlank())
    }
}
