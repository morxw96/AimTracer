package de.aimtracer.android

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class L10nTest {
    @Test
    fun englishTranslatesFixedAndDynamicAppText() {
        withLocale(Locale.ENGLISH) {
            assertEquals("Settings", L10n.text("Kalibrierung"))
            assertEquals("Dry fire", L10n.text("Trockentraining"))
            assertEquals("Packet 27", L10n.text("Paket 27"))
            assertEquals(
                "End session • Free training",
                L10n.text("Session beenden • Freies Training")
            )
            assertEquals("299 points", L10n.text("299 Ringe"))
        }
    }

    @Test
    fun germanKeepsSourceText() {
        withLocale(Locale.GERMAN) {
            assertEquals("Kalibrierung", L10n.text("Kalibrierung"))
            assertEquals("Paket 27", L10n.text("Paket 27"))
        }
    }

    private fun withLocale(locale: Locale, block: () -> Unit) {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(locale)
            block()
        } finally {
            Locale.setDefault(previous)
        }
    }
}
