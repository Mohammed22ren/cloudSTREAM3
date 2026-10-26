package com.kodastream.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    @Test
    fun versionsCompareNumerically() {
        // The one bug a string compare always has.
        assertTrue(UpdateChecker.isNewerVersion("0.10.0", "0.9.9"))
        assertFalse(UpdateChecker.isNewerVersion("0.9.9", "0.10.0"))
        assertTrue(UpdateChecker.isNewerVersion("v0.3.0", "0.2.0"))
        assertFalse(UpdateChecker.isNewerVersion("0.2.0", "0.2.0"))
        assertFalse(UpdateChecker.isNewerVersion("1.0", "1.0.0"))
    }
}
