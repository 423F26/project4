package com.example.liftingapp.data

import java.security.SecureRandom

/*
 * Team join codes. Plain Kotlin, so it's unit tested (see JoinCodeTest).
 *
 * 8 characters from a 31-character alphabet is about 850 billion possible codes, far too many to
 * guess (a 6-digit number would be one million). Letters and digits that look alike (0/O, 1/I/L)
 * are left out so a code read off a whiteboard or a text still works.
 */
object JoinCode {
    const val LENGTH = 8
    const val ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"

    private val random = SecureRandom()

    fun generate(): String = buildString(LENGTH) {
        repeat(LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
    }

    /**
     * What the person typed, as a code to look up: upper case, with spaces and dashes removed.
     * Returns null unless that leaves exactly [LENGTH] characters from [ALPHABET], so a typo is
     * caught before anything is looked up.
     */
    fun normalize(input: String): String? {
        val cleaned = input.uppercase().filterNot { it == ' ' || it == '-' || it == '\n' || it == '\t' }
        if (cleaned.length != LENGTH) return null
        return cleaned.takeIf { code -> code.all { it in ALPHABET } }
    }

    /** "K7QXM4RT" -> "K7QX-M4RT", easier to read aloud. */
    fun display(code: String): String =
        if (code.length == LENGTH) "${code.substring(0, 4)}-${code.substring(4)}" else code
}
