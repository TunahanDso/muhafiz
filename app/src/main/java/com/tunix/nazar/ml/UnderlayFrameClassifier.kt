package com.tunix.nazar.ml

import android.graphics.Bitmap
import com.tunix.nazar.domain.UnderlayEvidence

/**
 * Conservative verifier used only to prove that an already-blocked underlying
 * window is safe enough to reveal again.
 *
 * It does not change the normal Muhafiz detection thresholds. Auto-unblock is
 * deliberately stricter: every sampled region must look strongly SFW, while
 * any trusted adult-like region keeps the protection blocked.
 */
class UnderlayFrameClassifier(
    private val interpreter: BinaryNsfwInterpreter
) {

    fun classify(bitmap: Bitmap): UnderlayEvidence {
        if (
            bitmap.isRecycled ||
            bitmap.width <= 0 ||
            bitmap.height <= 0 ||
            !interpreter.isReady()
        ) {
            return UnderlayEvidence.ERROR
        }

        val specs =
            if (bitmap.width >= bitmap.height) {
                LANDSCAPE_PATCH_SPECS
            } else {
                PORTRAIT_PATCH_SPECS
            }

        var sawAnyReadyResult = false
        var everyRegionStronglyClean = true

        for (spec in specs) {
            val sample =
                createPatchBitmap(
                    bitmap = bitmap,
                    patch = spec
                ) ?: return UnderlayEvidence.ERROR

            try {
                val result =
                    interpreter.classify(sample)

                if (!result.isReady) {
                    return UnderlayEvidence.ERROR
                }

                sawAnyReadyResult = true

                if (
                    result.nsfwScore >= FRAME_NSFW_RISK_THRESHOLD &&
                    result.sfwScore <= FRAME_SFW_MAX_FOR_RISK
                ) {
                    return UnderlayEvidence.RISKY
                }

                if (
                    result.nsfwScore >= PATCH_TRUSTED_NSFW_THRESHOLD &&
                    result.sfwScore <= PATCH_MAX_SFW_FOR_TRUSTED_NSFW
                ) {
                    return UnderlayEvidence.RISKY
                }

                val stronglyClean =
                    result.nsfwScore <= UNDERLAY_CLEAN_MAX_NSFW &&
                            result.sfwScore >= UNDERLAY_CLEAN_MIN_SFW

                if (!stronglyClean) {
                    everyRegionStronglyClean = false
                }
            } finally {
                if (
                    sample !== bitmap &&
                    !sample.isRecycled
                ) {
                    sample.recycle()
                }
            }
        }

        return when {
            !sawAnyReadyResult ->
                UnderlayEvidence.ERROR

            everyRegionStronglyClean ->
                UnderlayEvidence.CLEAN

            else ->
                UnderlayEvidence.UNCERTAIN
        }
    }

    private fun createPatchBitmap(
        bitmap: Bitmap,
        patch: PatchSpec
    ): Bitmap? {
        if (patch.isFullFrame) {
            return bitmap
        }

        val left =
            (bitmap.width * patch.leftRatio)
                .toInt()
                .coerceIn(
                    0,
                    bitmap.width - 1
                )

        val top =
            (bitmap.height * patch.topRatio)
                .toInt()
                .coerceIn(
                    0,
                    bitmap.height - 1
                )

        val right =
            (bitmap.width * patch.rightRatio)
                .toInt()
                .coerceIn(
                    left + 1,
                    bitmap.width
                )

        val bottom =
            (bitmap.height * patch.bottomRatio)
                .toInt()
                .coerceIn(
                    top + 1,
                    bitmap.height
                )

        val width =
            right - left

        val height =
            bottom - top

        if (
            width <= 0 ||
            height <= 0
        ) {
            return null
        }

        return try {
            Bitmap.createBitmap(
                bitmap,
                left,
                top,
                width,
                height
            )
        } catch (_: Exception) {
            null
        }
    }

    private data class PatchSpec(
        val leftRatio: Float,
        val topRatio: Float,
        val rightRatio: Float,
        val bottomRatio: Float
    ) {
        val isFullFrame: Boolean
            get() =
                leftRatio == 0f &&
                        topRatio == 0f &&
                        rightRatio == 1f &&
                        bottomRatio == 1f
    }

    companion object {
        /*
         * These are the existing normal-risk / trusted-patch thresholds.
         * They are intentionally not relaxed for automatic unblock.
         */
        private const val FRAME_NSFW_RISK_THRESHOLD = 0.86f
        private const val FRAME_SFW_MAX_FOR_RISK = 0.24f
        private const val PATCH_TRUSTED_NSFW_THRESHOLD = 0.88f
        private const val PATCH_MAX_SFW_FOR_TRUSTED_NSFW = 0.22f

        /*
         * Separate, stricter proof threshold for revealing a previously
         * blocked window. This does not modify the normal detector.
         */
        private const val UNDERLAY_CLEAN_MAX_NSFW = 0.12f
        private const val UNDERLAY_CLEAN_MIN_SFW = 0.88f

        private val PORTRAIT_PATCH_SPECS =
            listOf(
                PatchSpec(0f, 0f, 1f, 1f),
                PatchSpec(0.04f, 0.22f, 0.96f, 0.78f),
                PatchSpec(0.04f, 0.34f, 0.96f, 0.92f),
                PatchSpec(0.08f, 0.30f, 0.92f, 0.74f),
                PatchSpec(0.08f, 0.42f, 0.92f, 0.90f),
                PatchSpec(0.14f, 0.25f, 0.86f, 0.76f),
                PatchSpec(0.14f, 0.42f, 0.86f, 0.96f)
            )

        private val LANDSCAPE_PATCH_SPECS =
            listOf(
                PatchSpec(0f, 0f, 1f, 1f),
                PatchSpec(0.16f, 0.08f, 0.84f, 0.92f),
                PatchSpec(0.22f, 0.12f, 0.78f, 0.88f),
                PatchSpec(0.02f, 0.12f, 0.52f, 0.88f),
                PatchSpec(0.48f, 0.12f, 0.98f, 0.88f),
                PatchSpec(0.18f, 0.02f, 0.82f, 0.58f),
                PatchSpec(0.18f, 0.42f, 0.82f, 0.98f),
                PatchSpec(0.30f, 0.08f, 0.70f, 0.92f)
            )
    }
}
