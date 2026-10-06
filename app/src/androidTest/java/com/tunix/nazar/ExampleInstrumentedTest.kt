package com.tunix.nazar

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {

    @Test
    fun useAppContext() {
        val appContext =
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext

        /*
         * Aynı androidTest kaynağı hem production hem lab flavor
         * için derlenebilir. Bu nedenle namespace'i değil, gerçek
         * applicationId ailesini doğruluyoruz.
         */
        assertTrue(
            appContext.packageName ==
                    "com.tunix.muhafiz.guard" ||
                    appContext.packageName ==
                    "com.tunix.muhafiz.guard.test"
        )
    }
}
