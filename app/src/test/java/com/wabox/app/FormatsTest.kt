package com.wabox.app

import com.wabox.app.core.util.Formats
import com.wabox.app.core.util.Hashing
import com.wabox.app.ui.designsystem.WaMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class FormatsTest {
    @Test fun sizes() {
        assertEquals("512 B", Formats.size(512))
        assertEquals("2 KB", Formats.size(2048))
        assertEquals("1.5 MB", Formats.size(1_572_864))
        assertEquals("15 MB", Formats.size(15L * 1024 * 1024))
    }

    @Test fun durations() {
        assertEquals("0:15", Formats.duration(15_000))
        assertEquals("1:05", Formats.duration(64_600))
        assertEquals("1:00:00", Formats.duration(3_600_000))
    }

    @Test fun relativeTimes() {
        val now = 10_000_000_000L
        assertEquals("Just now", Formats.relative(now - 10_000, now))
        assertEquals("5m ago", Formats.relative(now - 5 * 60_000, now))
        assertEquals("3h ago", Formats.relative(now - 3 * 3_600_000, now))
    }

    @Test fun fingerprintDependsOnContentAndSize() {
        val a = Hashing.partialFingerprint(byteArrayOf(1, 2), byteArrayOf(3), 10)
        assertEquals(a, Hashing.partialFingerprint(byteArrayOf(1, 2), byteArrayOf(3), 10))
        assertNotEquals(a, Hashing.partialFingerprint(byteArrayOf(1, 2), byteArrayOf(4), 10))
        assertNotEquals(a, Hashing.partialFingerprint(byteArrayOf(1, 2), byteArrayOf(3), 11))
    }

    @Test fun springStiffnessFromResponse() {
        // (2π / 0.4)² ≈ 246.74
        assertTrue(abs(WaMotion.stiffness(0.4f) - 246.74f) < 0.1f)
    }

    @Test fun momentumProjectionMatchesAppleFormula() {
        // 1000 px/s, d = 0.998 → 1 * 0.998 / 0.002 = 499 px
        assertTrue(abs(WaMotion.project(1000f) - 499f) < 0.5f)
    }

    @Test fun rubberBandResistsProgressively() {
        val small = WaMotion.rubberBand(50f, 1000f)
        val large = WaMotion.rubberBand(500f, 1000f)
        assertTrue(small < 50f && large < 500f && large > small)
        assertTrue(large / 500f < small / 50f)
    }
}
