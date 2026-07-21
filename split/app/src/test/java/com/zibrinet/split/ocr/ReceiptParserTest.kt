package com.zibrinet.split.ocr

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptParserTest {

    private fun dateMillis(year: Int, month: Int, day: Int): Long =
        LocalDate.of(year, month, day).atStartOfDay(ZoneId.systemDefault())
            .toInstant().toEpochMilli()

    @Test
    fun `thai style receipt with baht total`() {
        val text = """
            7-Eleven
            สาขาสุขุมวิท
            21/07/2026 14:32
            น้ำดื่ม 2 x 10.00
            ขนมปัง 25.50
            รวมทั้งสิ้น 45.50 บาท
            เงินสด 100.00
            เงินทอน 54.50
        """.trimIndent()

        val parsed = ReceiptParser.parse(text, "THB")
        assertEquals(4550L, parsed.amountMinor)
        assertEquals("THB", parsed.currency)
        assertEquals(dateMillis(2026, 7, 21), parsed.dateMillis)
        assertEquals("7-Eleven", parsed.merchant)
    }

    @Test
    fun `european receipt with comma decimals`() {
        val text = """
            Trattoria da Mario
            Roma, Via del Corso 12
            15.03.2026
            Pasta 12,50
            Vino 18,00
            Totale € 30,50
        """.trimIndent()

        val parsed = ReceiptParser.parse(text, "THB")
        assertEquals(3050L, parsed.amountMinor)
        assertEquals("EUR", parsed.currency)
        assertEquals(dateMillis(2026, 3, 15), parsed.dateMillis)
        assertEquals("Trattoria da Mario", parsed.merchant)
    }

    @Test
    fun `thousands separators in both styles`() {
        assertEquals(123456L, ReceiptParser.parseAmountToken("1,234.56", "EUR"))
        assertEquals(123456L, ReceiptParser.parseAmountToken("1.234,56", "EUR"))
        assertEquals(123400L, ReceiptParser.parseAmountToken("1.234", "EUR"))
        assertEquals(123400L, ReceiptParser.parseAmountToken("1,234", "EUR"))
        assertEquals(1256L, ReceiptParser.parseAmountToken("12,56", "EUR"))
        assertEquals(1256L, ReceiptParser.parseAmountToken("12.56", "EUR"))
        assertEquals(1234567L, ReceiptParser.parseAmountToken("12,345.67", "EUR"))
    }

    @Test
    fun `keyword amount preferred over larger unrelated number`() {
        val text = """
            Some Shop
            Member 90210
            Amount due 250.00
            Cash 300.00
        """.trimIndent()

        val parsed = ReceiptParser.parse(text, "THB")
        assertEquals(25000L, parsed.amountMinor)
    }

    @Test
    fun `total on line below keyword is found`() {
        val text = """
            My Cafe
            GRAND TOTAL
            1,250.00
        """.trimIndent()

        val parsed = ReceiptParser.parse(text, "THB")
        assertEquals(125000L, parsed.amountMinor)
    }

    @Test
    fun `subtotal is not mistaken for total`() {
        val text = """
            Shop
            Subtotal 90.00
            VAT 7.00
            Total 97.00
        """.trimIndent()

        val parsed = ReceiptParser.parse(text, "THB")
        assertEquals(9700L, parsed.amountMinor)
    }

    @Test
    fun `email screenshot with iso date and eur`() {
        val text = """
            Your booking is confirmed
            Booking.com
            Check-in 2026-08-02
            Total EUR 214.30
        """.trimIndent()

        val parsed = ReceiptParser.parse(text, "THB")
        assertEquals(21430L, parsed.amountMinor)
        assertEquals("EUR", parsed.currency)
        assertEquals(dateMillis(2026, 8, 2), parsed.dateMillis)
    }

    @Test
    fun `garbage text finds nothing but does not crash`() {
        val parsed = ReceiptParser.parse("~~~ ??? !!!", "THB")
        assertNull(parsed.amountMinor)
        assertNull(parsed.dateMillis)
        assertTrue(!parsed.foundAnything)
    }

    @Test
    fun `empty text returns empty result`() {
        val parsed = ReceiptParser.parse("", "THB")
        assertTrue(!parsed.foundAnything)
    }

    @Test
    fun `phone numbers are not amounts`() {
        val text = """
            Big Shop
            Tel 02-123-4567
            Total 55.00
        """.trimIndent()

        val parsed = ReceiptParser.parse(text, "THB")
        assertEquals(5500L, parsed.amountMinor)
    }

    @Test
    fun `no keyword falls back to largest plausible number`() {
        val text = """
            Corner Store
            Coffee 45.00
            Cake 120.00
        """.trimIndent()

        val parsed = ReceiptParser.parse(text, "THB")
        assertEquals(12000L, parsed.amountMinor)
    }

    @Test
    fun `thai buddhist era year converts to gregorian`() {
        val text = """
            Lotus Express
            21/07/2569 15:04
            รวม 180.00
        """.trimIndent()

        val parsed = ReceiptParser.parse(text, "THB")
        assertEquals(dateMillis(2026, 7, 21), parsed.dateMillis)
        assertEquals(18000L, parsed.amountMinor)
    }

    @Test
    fun `us month first date parses when day first is invalid`() {
        val text = """
            Online Store
            07/21/2026
            Total 99.99
        """.trimIndent()

        val parsed = ReceiptParser.parse(text, "THB")
        assertEquals(dateMillis(2026, 7, 21), parsed.dateMillis)
    }

    @Test
    fun `receipt id never beats the total`() {
        val text = """
            Mega Mart
            No 8641259
            Total 320.00
        """.trimIndent()

        val parsed = ReceiptParser.parse(text, "THB")
        assertEquals(32000L, parsed.amountMinor)
    }

    @Test
    fun `times are not amounts`() {
        val text = """
            Corner Cafe
            12/06/2026 09:45
            Total 60.00
        """.trimIndent()

        val parsed = ReceiptParser.parse(text, "THB")
        assertEquals(6000L, parsed.amountMinor)
        assertEquals(dateMillis(2026, 6, 12), parsed.dateMillis)
    }

    @Test
    fun `decimal-bearing number beats bigger bare integer without keywords`() {
        val text = """
            Some Shop
            Order 55512
            Coffee 85.00
        """.trimIndent()

        val parsed = ReceiptParser.parse(text, "THB")
        assertEquals(8500L, parsed.amountMinor)
    }

    @Test
    fun `zero decimal currency keeps integer amounts`() {
        val text = """
            Tokyo Ramen
            Total ¥1,200
        """.trimIndent()

        val parsed = ReceiptParser.parse(text, "THB")
        assertEquals("JPY", parsed.currency)
        assertEquals(1200L, parsed.amountMinor)
    }
}
