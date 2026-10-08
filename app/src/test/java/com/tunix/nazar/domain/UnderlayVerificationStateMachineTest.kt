package com.tunix.nazar.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnderlayVerificationStateMachineTest {

    private fun token(
        session: Long = 10L,
        verification: Long = 2L,
        windowId: Int = 42,
        packageName: String = "com.example.browser"
    ) = UnderlayVerificationToken(
        sessionGeneration = session,
        verificationGeneration = verification,
        target = UnderlayTarget(windowId, packageName)
    )

    private fun verifyingMachine(): Pair<UnderlayVerificationStateMachine, UnderlayVerificationToken> {
        val machine = UnderlayVerificationStateMachine(requiredCleanSamples = 3)
        machine.startSession(10L)
        machine.block(10L)
        assertTrue(machine.markVerificationPending(10L, 2L))
        val token = token()
        assertTrue(machine.beginVerification(token))
        return machine to token
    }

    @Test
    fun riskBlocksMonitoring() {
        val machine = UnderlayVerificationStateMachine()
        machine.startSession(10L)

        assertTrue(machine.block(10L))
        assertEquals(ProtectionMode.BLOCKED, machine.mode)
    }

    @Test
    fun staleCleanEvidenceIsIgnored() {
        val (machine, current) = verifyingMachine()
        val stale = current.copy(sessionGeneration = 9L)

        val decision =
            machine.recordEvidence(
                stale,
                UnderlayEvidence.CLEAN
            )

        assertFalse(decision.accepted)
        assertEquals(ProtectionMode.VERIFYING_UNDERLAY, machine.mode)
        assertEquals(0, machine.cleanSamples)
    }

    @Test
    fun secureWindowKeepsBlocked() {
        val (machine, current) = verifyingMachine()

        machine.recordEvidence(
            current,
            UnderlayEvidence.SECURE
        )

        assertEquals(ProtectionMode.BLOCKED, machine.mode)
        assertEquals(0, machine.cleanSamples)
    }

    @Test
    fun genericErrorKeepsBlocked() {
        val (machine, current) = verifyingMachine()

        machine.recordEvidence(
            current,
            UnderlayEvidence.ERROR
        )

        assertEquals(ProtectionMode.BLOCKED, machine.mode)
    }

    @Test
    fun riskyEvidenceKeepsBlocked() {
        val (machine, current) = verifyingMachine()

        machine.recordEvidence(
            current,
            UnderlayEvidence.RISKY
        )

        assertEquals(ProtectionMode.BLOCKED, machine.mode)
        assertEquals(0, machine.cleanSamples)
    }

    @Test
    fun uncertainEvidenceKeepsBlocked() {
        val (machine, current) = verifyingMachine()

        machine.recordEvidence(
            current,
            UnderlayEvidence.UNCERTAIN
        )

        assertEquals(ProtectionMode.BLOCKED, machine.mode)
        assertEquals(0, machine.cleanSamples)
    }

    @Test
    fun threeCurrentCleanSamplesReachThresholdButRequireExplicitConfirmation() {
        val (machine, current) = verifyingMachine()

        assertFalse(
            machine.recordEvidence(
                current,
                UnderlayEvidence.CLEAN
            ).cleanThresholdReached
        )
        assertFalse(
            machine.recordEvidence(
                current,
                UnderlayEvidence.CLEAN
            ).cleanThresholdReached
        )

        val third =
            machine.recordEvidence(
                current,
                UnderlayEvidence.CLEAN
            )

        assertTrue(third.cleanThresholdReached)
        assertEquals(ProtectionMode.VERIFYING_UNDERLAY, machine.mode)
        assertTrue(machine.confirmMonitoring(current))
        assertEquals(ProtectionMode.MONITORING, machine.mode)
    }

    @Test
    fun riskAfterCleanResetsSequence() {
        val (machine, current) = verifyingMachine()

        machine.recordEvidence(current, UnderlayEvidence.CLEAN)
        machine.recordEvidence(current, UnderlayEvidence.CLEAN)
        machine.recordEvidence(current, UnderlayEvidence.RISKY)

        assertEquals(0, machine.cleanSamples)
        assertEquals(ProtectionMode.BLOCKED, machine.mode)
    }

    @Test
    fun newVerificationGenerationInvalidatesOldToken() {
        val (machine, oldToken) = verifyingMachine()

        assertTrue(
            machine.markVerificationPending(
                generation = 10L,
                requestedVerificationGeneration = 3L
            )
        )

        val decision =
            machine.recordEvidence(
                oldToken,
                UnderlayEvidence.CLEAN
            )

        assertFalse(decision.accepted)
        assertEquals(0, machine.cleanSamples)
    }

    @Test
    fun targetChangeDoesNotReuseCleanHistory() {
        val (machine, firstToken) = verifyingMachine()

        machine.recordEvidence(firstToken, UnderlayEvidence.CLEAN)
        machine.recordEvidence(firstToken, UnderlayEvidence.CLEAN)

        assertTrue(
            machine.markVerificationPending(
                generation = 10L,
                requestedVerificationGeneration = 3L
            )
        )

        val secondToken =
            token(
                verification = 3L,
                windowId = 99
            )

        assertTrue(machine.beginVerification(secondToken))
        assertEquals(0, machine.cleanSamples)

        val decision =
            machine.recordEvidence(
                secondToken,
                UnderlayEvidence.CLEAN
            )

        assertFalse(decision.cleanThresholdReached)
        assertEquals(1, machine.cleanSamples)
    }

    @Test
    fun stopInvalidatesLateCallback() {
        val (machine, current) = verifyingMachine()

        machine.stopSession()

        val decision =
            machine.recordEvidence(
                current,
                UnderlayEvidence.CLEAN
            )

        assertFalse(decision.accepted)
        assertEquals(ProtectionMode.MONITORING, machine.mode)
    }
}
