package dev.darn.spatiallauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GridMathTest {
    @Test fun mapsLargePortraitGridPrecisely() {
        assertEquals(0, GridMath.index(0f, 0f, 1440f, 4, 180f))
        assertEquals(7, GridMath.index(1439f, 181f, 1440f, 4, 180f))
    }
    @Test fun rejectsCoordinatesOutsideSurface() {
        assertNull(GridMath.index(-1f, 20f, 1440f, 4, 180f))
        assertNull(GridMath.index(1440f, 20f, 1440f, 4, 180f))
    }
}
