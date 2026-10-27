package com.tripathitechnologies.melaready
import com.tripathitechnologies.melaready.domain.*
import org.junit.Assert.*
import org.junit.Test
class WorkflowTest {
    @Test fun attendanceRejectsInvalidAndOutOfRangeValues() {
        listOf("", "0", "-1", "1.5", "1000001", "999999999999").forEach { assertFalse(Workflow.validEvent("Mela", "Haryana", it)) }
    }
    @Test fun eventNeedsBothFieldsAndAcceptsAnEstimate() {
        assertTrue(Workflow.validEvent("Mela", "Haryana", "250"))
        assertFalse(Workflow.validEvent(" ", "Haryana", "250"))
        assertFalse(Workflow.validEvent("Mela", " ", "250"))
    }
    @Test fun incidentWorkflowIsOrderedAndTerminal() {
        var s = IncidentStatus.SAVED
        listOf(IncidentStatus.ACKNOWLEDGED, IncidentStatus.ASSIGNED, IncidentStatus.RESOLVED, IncidentStatus.RESOLVED).forEach { next ->
            s = Workflow.next(s); assertEquals(next, s)
        }
    }
    @Test fun trainingRejectsPartialWrongAndExtraAnswers() {
        assertTrue(Training.complete(listOf(1, 2, 1)))
        listOf(emptyList(), listOf(1, 2), listOf(0, 2, 1), listOf(1, 2, 1, 0)).forEach { assertFalse(Training.complete(it)) }
    }
    @Test fun workspaceDefaultsToUncompletedGatewayAndDefaultServerUrl() {
        val w = Workspace()
        assertFalse(w.gatewayCompleted)
        assertEquals("http://10.0.2.2:8080", w.serverUrl)
    }
    @Test fun sampleIsExplicitAndHasNoClaimedApprovals() {
        val w = Workflow.sample()
        assertTrue(w.event!!.sample); assertEquals(3, w.members.size)
        assertTrue(w.checks.isEmpty()); assertTrue(w.answers.isEmpty()); assertTrue(w.incidents.isEmpty())
    }
    @Test fun learningBankHasSixTopicsAndEighteenValidQuestions() {
        assertEquals(6, LearningBank.topics.size)
        assertEquals(18, LearningBank.topics.sumOf { it.questions.size })
        assertEquals(6, LearningBank.topics.map { it.id }.toSet().size)
        LearningBank.topics.forEach { topic ->
            assertEquals(3, topic.questions.size)
            topic.questions.forEach { q ->
                assertEquals(3, q.options.size); assertTrue(q.correct in q.options.indices)
                assertTrue(q.promptEn.isNotBlank() && q.promptHi.isNotBlank())
                assertTrue(q.whyEn.isNotBlank() && q.whyHi.isNotBlank())
            }
        }
    }
}
