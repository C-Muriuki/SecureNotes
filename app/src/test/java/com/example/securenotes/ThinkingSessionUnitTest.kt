package com.example.securenotes

import com.example.securenotes.models.ThinkingResponse
import com.example.securenotes.models.ThinkingSession
import com.example.securenotes.repositories.ThinkingTemplateRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ThinkingSessionUnitTest {

    @Test
    fun frameworkTemplatesAreDefinedCorrectly() {
        val frameworks = ThinkingTemplateRepository.getFrameworks()
        assertEquals(5, frameworks.size)

        val fiveWhys = ThinkingTemplateRepository.getFramework("FIVE_WHYS")
        assertNotNull(fiveWhys)
        assertEquals(5, fiveWhys?.steps?.size)
        assertEquals("WHY_1", fiveWhys?.steps?.first()?.id)
        assertEquals("WHY_5", fiveWhys?.steps?.last()?.id)

        val swot = ThinkingTemplateRepository.getFramework("SWOT")
        assertNotNull(swot)
        assertEquals(4, swot?.steps?.size)

        val prosCons = ThinkingTemplateRepository.getFramework("PROS_CONS")
        assertNotNull(prosCons)
        assertEquals(5, prosCons?.steps?.size)

        val problemSolution = ThinkingTemplateRepository.getFramework("PROBLEM_SOLUTION")
        assertNotNull(problemSolution)
        assertEquals(7, problemSolution?.steps?.size)

        val ideaExpansion = ThinkingTemplateRepository.getFramework("IDEA_EXPANSION")
        assertNotNull(ideaExpansion)
        assertEquals(7, ideaExpansion?.steps?.size)
    }

    @Test
    fun thinkingSessionAndResponseModelConstruction() {
        val response = ThinkingResponse(
            id = 1L,
            sessionId = 10L,
            ownerUserId = 100L,
            stepId = "WHY_1",
            response = "Software bug caused systemic failure."
        )

        val session = ThinkingSession(
            id = 10L,
            ownerUserId = 100L,
            sourceNoteId = 50L,
            frameworkId = "FIVE_WHYS",
            status = 0,
            responses = listOf(response)
        )

        assertEquals(10L, session.id)
        assertEquals(100L, session.ownerUserId)
        assertEquals(50L, session.sourceNoteId)
        assertEquals("FIVE_WHYS", session.frameworkId)
        assertEquals(1, session.responses.size)
        assertEquals("WHY_1", session.responses[0].stepId)
        assertEquals("Software bug caused systemic failure.", session.responses[0].response)
    }
}
