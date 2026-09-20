package com.personal.cameraalarm

import com.personal.cameraalarm.trigger.MatchMode
import com.personal.cameraalarm.ui.rule.RuleEditorState
import com.personal.cameraalarm.ui.rule.RuleViewModel
import org.junit.Assert.*
import org.junit.Test

class RuleValidationTest {
    @Test
    fun blankNameIsRejected() {
        val state = RuleEditorState(
            name = "   ",
            sourcePackage = "com.camera.app",
            keywordsRaw = "human"
        )
        val error = RuleViewModel.validateRule(state)
        assertNotNull(error)
        assertTrue(error!!.contains("name", ignoreCase = true))
    }

    @Test
    fun blankSourcePackageIsRejected() {
        val state = RuleEditorState(
            name = "Person Rule",
            sourcePackage = "   ",
            keywordsRaw = "human"
        )
        val error = RuleViewModel.validateRule(state)
        assertNotNull(error)
        assertTrue(error!!.contains("source", ignoreCase = true))
    }

    @Test
    fun emptyKeywordsAreRejected() {
        val state = RuleEditorState(
            name = "Person Rule",
            sourcePackage = "com.camera.app",
            keywordsRaw = "   \n   \n"
        )
        val error = RuleViewModel.validateRule(state)
        assertNotNull(error)
        assertTrue(error!!.contains("keyword", ignoreCase = true))
    }

    @Test
    fun moreThan30KeywordsRejected() {
        val keywords = (1..35).joinToString("\n") { "keyword$it" }
        val state = RuleEditorState(
            name = "Large Rule",
            sourcePackage = "com.camera.app",
            keywordsRaw = keywords
        )
        val error = RuleViewModel.validateRule(state)
        assertNotNull(error)
        assertTrue(error!!.contains("30", ignoreCase = true))
    }

    @Test
    fun keywordOver100CharsRejected() {
        val longKeyword = "a".repeat(101)
        val state = RuleEditorState(
            name = "Long Keyword Rule",
            sourcePackage = "com.camera.app",
            keywordsRaw = longKeyword
        )
        val error = RuleViewModel.validateRule(state)
        assertNotNull(error)
        assertTrue(error!!.contains("100", ignoreCase = true))
    }

    @Test
    fun validRulePassesValidation() {
        val state = RuleEditorState(
            name = "Valid Rule",
            sourcePackage = "com.camera.app",
            keywordsRaw = "  HUMAN detected  \nperson\nPhát hiện người  ",
            matchMode = MatchMode.CONTAINS_ANY,
            priority = 1
        )
        val error = RuleViewModel.validateRule(state)
        assertNull(error)
        assertEquals(3, state.normalizedKeywords.size)
        assertTrue(state.normalizedKeywords.contains("human detected"))
        assertTrue(state.normalizedKeywords.contains("person"))
        assertTrue(state.normalizedKeywords.contains("phát hiện người"))
    }

    @Test
    fun commasRemainPartOfOneKeywordBecauseCsvIsNoLongerTheEditorContract() {
        val state = RuleEditorState(
            name = "Comma Rule",
            sourcePackage = "com.camera.app",
            keywordsRaw = "motion, person, intrusion\nbreak-in"
        )
        val error = RuleViewModel.validateRule(state)
        assertNull(error)
        assertEquals(2, state.normalizedKeywords.size)
        assertTrue(state.normalizedKeywords.contains("motion, person, intrusion"))
        assertTrue(state.normalizedKeywords.contains("break-in"))
    }

    @Test
    fun shortKeywordIsRejected() {
        val state = RuleEditorState(
            name = "Short Keyword Rule",
            sourcePackage = "com.camera.app",
            keywordsRaw = "a\nmotion"
        )
        val error = RuleViewModel.validateRule(state)
        assertNotNull(error)
        assertTrue(error!!.contains("too short", ignoreCase = true))
    }

    @Test
    fun duplicateKeywordsAfterCaseAndWhitespaceNormalizationAreRejected() {
        val state = RuleEditorState(
            name = "Duplicate Rule",
            sourcePackage = "com.camera.app",
            keywordsRaw = "Phát hiện   người\n phát hiện người "
        )

        val error = RuleViewModel.validateRule(state)

        assertNotNull(error)
        assertTrue(error!!.contains("duplicate", ignoreCase = true))
    }

    @Test
    fun suggestedTemplateMatchesTheVietnameseProductContract() {
        assertEquals("Camera an ninh phổ biến", RuleViewModel.SUGGESTED_TEMPLATE_NAME)
        assertEquals(9, RuleViewModel.SUGGESTED_TEMPLATE_KEYWORDS.size)
        assertTrue(RuleViewModel.SUGGESTED_TEMPLATE_KEYWORDS.contains("phát hiện người/phương tiện"))
        assertTrue(RuleViewModel.SUGGESTED_TEMPLATE_KEYWORDS.contains("phát hiện xâm nhập"))
    }
}
