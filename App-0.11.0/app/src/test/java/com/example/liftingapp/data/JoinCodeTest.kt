package com.example.liftingapp.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JoinCodeTest {

    @Test
    fun codesAreEightCharactersFromTheAlphabet() {
        repeat(200) {
            val code = JoinCode.generate()
            assertEquals(JoinCode.LENGTH, code.length)
            assertTrue(code, code.all { it in JoinCode.ALPHABET })
        }
    }

    @Test
    fun noLookAlikeCharacters() {
        "01OIL".forEach { assertTrue("$it should not be used", it !in JoinCode.ALPHABET) }
    }

    @Test
    fun codesDontRepeat() {
        val codes = List(2000) { JoinCode.generate() }
        assertEquals(codes.size, codes.toSet().size)
    }

    @Test
    fun typedCodesAreCleanedUp() {
        assertEquals("K7QXM4RT", JoinCode.normalize("k7qx-m4rt"))
        assertEquals("K7QXM4RT", JoinCode.normalize(" K7QX M4RT "))
        assertNull(JoinCode.normalize("K7QX-M4R"))     // too short
        assertNull(JoinCode.normalize("K7QX-M4R0"))    // 0 isn't used
    }

    @Test
    fun displayAddsADash() {
        assertEquals("K7QX-M4RT", JoinCode.display("K7QXM4RT"))
    }
}
