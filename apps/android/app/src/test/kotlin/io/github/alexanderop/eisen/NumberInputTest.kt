package io.github.alexanderop.eisen

import io.github.alexanderop.eisen.domain.Equipment
import io.github.alexanderop.eisen.domain.WeightUnit
import io.github.alexanderop.eisen.domain.parseTypedNumber
import io.github.alexanderop.eisen.domain.snapTo
import io.github.alexanderop.eisen.domain.typedIncrementSnap
import io.github.alexanderop.eisen.domain.typedValue
import io.github.alexanderop.eisen.domain.typedWeightSnap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NumberInputTest {
    private val inf = Double.POSITIVE_INFINITY

    private fun typed(text: String, snap: Double, min: Double = 0.0, max: Double = inf) = typedValue(text, min, max, snap)

    @Test fun `reads a dot and a comma as the decimal point`() {
        assertEquals(82.5, typed("82.5", 2.5)!!, 0.0)
        assertEquals(82.5, typed("82,5", 2.5)!!, 0.0)
        assertEquals(0.25, typed(",25", 0.25)!!, 0.0)
        assertEquals(80.0, typed("80.", 2.5)!!, 0.0)
    }

    @Test fun `ignores spaces around the number`() {
        assertEquals(90.0, typed(" 90 ", 5.0)!!, 0.0)
    }

    @Test fun `empty or garbage text gives no value`() {
        for (text in listOf("", " ", ".", ",", "-", "abc", "8a", "1.2.3", "1,2,3", "82.5kg", "1:30", "1e3", "--5")) {
            assertNull("\"$text\"", typed(text, 1.0))
            assertNull("\"$text\"", parseTypedNumber(text))
        }
    }

    @Test fun `negative numbers end up at the minimum`() {
        assertEquals(2.5, typed("-5", 2.5, min = 2.5)!!, 0.0)
        assertEquals(0.0, typed("-0,5", 0.5)!!, 0.0)
    }

    @Test fun `keeps the value within min and max`() {
        assertEquals(10.0, typed("12", 1.0, min = 1.0, max = 10.0)!!, 0.0)
        assertEquals(1.0, typed("0", 1.0, min = 1.0, max = 10.0)!!, 0.0)
        assertEquals(15.0, typed("5", 5.0, min = 15.0)!!, 0.0)
        assertEquals(2.5, typed("1", 1.25, min = 2.5)!!, 0.0)
    }

    @Test fun `snaps to a 2_5 grid`() {
        assertEquals(82.5, typed("83", 2.5)!!, 0.0)
        assertEquals(85.0, typed("83.75", 2.5)!!, 0.0)
        assertEquals(80.0, typed("81.2", 2.5)!!, 0.0)
    }

    @Test fun `snaps to a 1_25 grid`() {
        assertEquals(81.25, typed("81", 1.25)!!, 0.0)
        assertEquals(81.25, typed("81,3", 1.25)!!, 0.0)
        assertEquals(1.25, typed("1.3", 1.25)!!, 0.0)
    }

    @Test fun `snaps to a 0_5 and a 1 grid`() {
        assertEquals(1.5, typed("1.3", 0.5)!!, 0.0)
        assertEquals(2.0, typed("1.75", 0.5)!!, 0.0)
        assertEquals(12.0, typed("12.4", 1.0)!!, 0.0)
        assertEquals(13.0, typed("12.5", 1.0)!!, 0.0)
    }

    @Test fun `values already on the grid stay as they are`() {
        for ((text, snap) in listOf("82.5" to 2.5, "81.25" to 1.25, "1.5" to 0.5, "7" to 1.0, "90" to 5.0, "0" to 2.5)) {
            assertEquals(text.toDouble(), typed(text, snap)!!, 0.0)
        }
    }

    @Test fun `snapping is safe from floating point drift`() {
        assertEquals(0.3, snapTo(0.1 + 0.2, 0.1), 0.0)
        assertEquals(0.3, typed("0.3", 0.1)!!, 0.0)
        assertEquals(0.7, typed("0.7", 0.1)!!, 0.0)
        assertEquals(102.3, typed("102.3", 0.1)!!, 0.0)
        assertEquals(1.1, snapTo(0.55 * 2, 0.05), 0.0)
        assertEquals(82.5, snapTo(80.0 + 2.5, 2.5), 0.0)
    }

    @Test fun `a grid of zero leaves the number alone`() {
        assertEquals(81.3, typed("81.3", 0.0)!!, 0.0)
    }

    @Test fun `typed weights keep what the plates or increments allow`() {
        assertEquals(1.25, typedWeightSnap(Equipment.BARBELL, WeightUnit.KG), 0.0)
        assertEquals(1.25, typedWeightSnap(Equipment.MACHINE, WeightUnit.KG), 0.0)
        assertEquals(2.5, typedWeightSnap(Equipment.BARBELL, WeightUnit.LB), 0.0)
        assertEquals(0.5, typedWeightSnap(Equipment.WEIGHT, WeightUnit.KG), 0.0)
        assertEquals(0.25, typedIncrementSnap(WeightUnit.KG), 0.0)
        assertEquals(1.25, typed("1.25", typedIncrementSnap(WeightUnit.KG), min = 0.5)!!, 0.0)
    }
}
