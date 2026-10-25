package com.ambiorix.pay

import com.ambiorix.pay.notification.PaymentNotificationParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class PaymentNotificationParserTest {

    private lateinit var parser: PaymentNotificationParser

    @Before
    fun setup() {
        parser = PaymentNotificationParser()
    }

    @Test
    fun testAmountWithDollarNoSpace() {
        val amount = parser.extractAmount("Nequi", "¡Te llegó plata! Te enviaron $35.000 a tu cuenta.")
        assertEquals(35000L, amount)
    }

    @Test
    fun testAmountWithDollarAndSpace() {
        val amount = parser.extractAmount("Daviplata", "Recibiste $ 35.000 exitosamente.")
        assertEquals(35000L, amount)
    }

    @Test
    fun testAmountWithCommaSeparator() {
        val amount = parser.extractAmount("Bancolombia", "Transferencia recibida por $35,000 en su cuenta.")
        assertEquals(35000L, amount)
    }

    @Test
    fun testAmountWithPesosSuffixDot() {
        val amount = parser.extractAmount("Nequi", "Te transfirieron 35.000 pesos.")
        assertEquals(35000L, amount)
    }

    @Test
    fun testAmountWithPesosSuffixClean() {
        val amount = parser.extractAmount("A la mano", "Has recibido 35000 pesos de Juan.")
        assertEquals(35000L, amount)
    }

    @Test
    fun testAmountWithCopPrefix() {
        val amount = parser.extractAmount("Transferencia", "Recibiste COP 35.000 de Carlos.")
        assertEquals(35000L, amount)
    }

    @Test
    fun testAmountWithCopAndDollar() {
        val amount = parser.extractAmount("Pago", "Entrada de dinero por COP $35.000.")
        assertEquals(35000L, amount)
    }

    @Test
    fun testAmountOverOneMillion() {
        val amount = parser.extractAmount("Bancolombia", "Has recibido $1.250.000 de Pedro Pérez.")
        assertEquals(1250000L, amount)
    }

    @Test
    fun testAmountWithCentavosDecimalsNormalizedToEntero() {
        val amount = parser.extractAmount("Bancolombia", "Recibiste $35.000,50 de María.")
        assertEquals(35000L, amount)
    }

    @Test
    fun testIgnoresBalanceAmountAfterReceivedAmount() {
        val text = "¡Te enviaron plata! Carlos te transfirió $35.000 a tu Nequi. Tu nuevo saldo disponible es $180.000."
        val amount = parser.extractAmount("Nequi", text)
        // Debe extraer el pago de 35000, ignorando completamente el saldo de 180000
        assertEquals(35000L, amount)
    }

    @Test
    fun testIgnoresCommissionAmount() {
        val text = "Recibiste $50.000 en tu cuenta. Comisión del servicio: $2.500."
        val amount = parser.extractAmount("Bancolombia", text)
        assertEquals(50000L, amount)
    }

    @Test
    fun testInvalidNotificationYieldsNull() {
        val amount = parser.extractAmount("Nequi", "Bienvenido a Nequi. Disfruta tu día.")
        assertNull(amount)
    }
}
