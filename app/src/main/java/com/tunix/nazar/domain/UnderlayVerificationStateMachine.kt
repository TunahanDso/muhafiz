package com.tunix.nazar.domain

enum class ProtectionMode {
    MONITORING,
    BLOCKED,
    VERIFY_PENDING,
    VERIFYING_UNDERLAY
}

enum class UnderlayEvidence {
    CLEAN,
    RISKY,
    UNCERTAIN,
    SECURE,
    ERROR
}

data class UnderlayTarget(
    val windowId: Int,
    val packageName: String
)

data class UnderlayVerificationToken(
    val sessionGeneration: Long,
    val verificationGeneration: Long,
    val target: UnderlayTarget
)

data class UnderlayDecision(
    val accepted: Boolean,
    val cleanThresholdReached: Boolean,
    val mode: ProtectionMode,
    val cleanSamples: Int
)

/**
 * Pure Kotlin state machine for the blocked-screen verification path.
 *
 * It never decides that the overlay should actually be hidden. Instead it
 * reports when sufficient current clean evidence exists; ScreenCaptureService
 * remains the authority that applies hold timing and changes the visible UI.
 */
class UnderlayVerificationStateMachine(
    private val requiredCleanSamples: Int = DEFAULT_REQUIRED_CLEAN_SAMPLES
) {

    init {
        require(requiredCleanSamples >= 1)
    }

    var mode: ProtectionMode = ProtectionMode.MONITORING
        private set

    var sessionGeneration: Long = NO_SESSION
        private set

    var verificationGeneration: Long = 0L
        private set

    var currentTarget: UnderlayTarget? = null
        private set

    var cleanSamples: Int = 0
        private set

    fun startSession(generation: Long) {
        sessionGeneration = generation
        verificationGeneration = 0L
        mode = ProtectionMode.MONITORING
        resetEvidence()
    }

    fun stopSession() {
        verificationGeneration++
        sessionGeneration = NO_SESSION
        mode = ProtectionMode.MONITORING
        resetEvidence()
    }

    fun block(generation: Long): Boolean {
        if (generation != sessionGeneration) {
            return false
        }

        verificationGeneration++
        mode = ProtectionMode.BLOCKED
        resetEvidence()
        return true
    }

    fun markVerificationPending(
        generation: Long,
        requestedVerificationGeneration: Long
    ): Boolean {
        if (
            generation != sessionGeneration ||
            mode == ProtectionMode.MONITORING ||
            requestedVerificationGeneration <= verificationGeneration
        ) {
            return false
        }

        verificationGeneration = requestedVerificationGeneration
        mode = ProtectionMode.VERIFY_PENDING
        resetEvidence()
        return true
    }

    fun beginVerification(
        token: UnderlayVerificationToken
    ): Boolean {
        if (
            token.sessionGeneration != sessionGeneration ||
            token.verificationGeneration != verificationGeneration ||
            mode == ProtectionMode.MONITORING
        ) {
            return false
        }

        if (currentTarget != token.target) {
            cleanSamples = 0
        }

        currentTarget = token.target
        mode = ProtectionMode.VERIFYING_UNDERLAY
        return true
    }

    fun recordEvidence(
        token: UnderlayVerificationToken,
        evidence: UnderlayEvidence
    ): UnderlayDecision {
        if (!isCurrent(token)) {
            return currentDecision(accepted = false)
        }

        when (evidence) {
            UnderlayEvidence.CLEAN -> {
                cleanSamples++
                mode = ProtectionMode.VERIFYING_UNDERLAY
            }

            UnderlayEvidence.RISKY,
            UnderlayEvidence.UNCERTAIN,
            UnderlayEvidence.SECURE,
            UnderlayEvidence.ERROR -> {
                cleanSamples = 0
                mode = ProtectionMode.BLOCKED
                currentTarget = null
            }
        }

        return currentDecision(
            accepted = true,
            cleanThresholdReached =
                evidence == UnderlayEvidence.CLEAN &&
                        cleanSamples >= requiredCleanSamples
        )
    }

    fun confirmMonitoring(
        token: UnderlayVerificationToken
    ): Boolean {
        if (
            !isCurrent(token) ||
            cleanSamples < requiredCleanSamples
        ) {
            return false
        }

        mode = ProtectionMode.MONITORING
        resetEvidence()
        return true
    }

    fun forceMonitoring(generation: Long): Boolean {
        if (generation != sessionGeneration) {
            return false
        }

        verificationGeneration++
        mode = ProtectionMode.MONITORING
        resetEvidence()
        return true
    }

    fun forceBlocked(generation: Long): Boolean {
        if (generation != sessionGeneration) {
            return false
        }

        verificationGeneration++
        mode = ProtectionMode.BLOCKED
        resetEvidence()
        return true
    }

    private fun isCurrent(
        token: UnderlayVerificationToken
    ): Boolean {
        return token.sessionGeneration == sessionGeneration &&
                token.verificationGeneration == verificationGeneration &&
                currentTarget == token.target &&
                mode == ProtectionMode.VERIFYING_UNDERLAY
    }

    private fun resetEvidence() {
        cleanSamples = 0
        currentTarget = null
    }

    private fun currentDecision(
        accepted: Boolean,
        cleanThresholdReached: Boolean = false
    ): UnderlayDecision {
        return UnderlayDecision(
            accepted = accepted,
            cleanThresholdReached = cleanThresholdReached,
            mode = mode,
            cleanSamples = cleanSamples
        )
    }

    companion object {
        const val DEFAULT_REQUIRED_CLEAN_SAMPLES = 3
        private const val NO_SESSION = -1L
    }
}
