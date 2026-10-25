package com.ambiorix.pay.notification

import java.util.regex.Pattern

/**
 * Parser de notificaciones de pago puramente Kotlin (sin dependencias de Android).
 * Diseñado para ser 100% testeable con JUnit.
 *
 * Soporta formatos:
 * - $35.000, $ 35.000, $35,000
 * - 35.000 pesos, 35000 pesos
 * - COP 35.000, COP $35.000
 * - $1.250.000
 * - $35.000,50 (normaliza a entero 35000)
 *
 * Ignora montos que vengan después de palabras como "saldo", "disponible", "comisión".
 */
class PaymentNotificationParser {

    private val amountPrefixPattern: Pattern = Pattern.compile(
        """(?i)(?:COP\s*\$|\$|COP)\s*([0-9]{1,3}(?:[.,][0-9]{3})*(?:[.,][0-9]{1,2})?|[0-9]+)"""
    )

    private val amountSuffixPattern: Pattern = Pattern.compile(
        """(?i)([0-9]{1,3}(?:[.,][0-9]{3})*(?:[.,][0-9]{1,2})?|[0-9]+)\s*(?:pesos|cop\b)"""
    )

    /**
     * Extrae el monto del título y cuerpo de la notificación.
     * Retorna el monto normalizado en centavos/enteros (Long) o null si no se detecta ninguno válido.
     */
    fun extractAmount(title: String?, text: String?): Long? {
        val fullRaw = buildString {
            if (!title.isNullOrBlank()) append(title).append(" ")
            if (!text.isNullOrBlank()) append(text)
        }
        if (fullRaw.isBlank()) return null

        val normalized = ColombianPaymentKeywords.normalizeText(fullRaw)

        // Cortar la cadena antes de palabras como "saldo", "disponible", "comision"
        // para no confundir el saldo remanente con el pago recibido.
        val effectiveText = cutBeforeBalanceWords(normalized)

        // 1. Intentar buscar en la porción antes de saldo
        var parsed = searchAmountInText(effectiveText)
        if (parsed != null && parsed > 0) {
            return parsed
        }

        // 2. Si no se encontró nada antes de saldo (o no había palabras de saldo),
        // buscar en el texto completo como fallback
        parsed = searchAmountInText(normalized)
        return if (parsed != null && parsed > 0) parsed else null
    }

    private fun cutBeforeBalanceWords(text: String): String {
        var earliestIndex = -1
        for (keyword in ColombianPaymentKeywords.BALANCE_KEYWORDS) {
            val idx = text.indexOf(keyword)
            if (idx >= 0) {
                if (earliestIndex == -1 || idx < earliestIndex) {
                    earliestIndex = idx
                }
            }
        }
        return if (earliestIndex >= 0) {
            text.substring(0, earliestIndex)
        } else {
            text
        }
    }

    private fun searchAmountInText(text: String): Long? {
        // Primero buscar con prefijo ($ o COP)
        val prefixMatcher = amountPrefixPattern.matcher(text)
        if (prefixMatcher.find()) {
            val rawNumber = prefixMatcher.group(1)
            val parsed = normalizeRawNumber(rawNumber)
            if (parsed != null && parsed > 0) return parsed
        }

        // Segundo buscar con sufijo (pesos o cop)
        val suffixMatcher = amountSuffixPattern.matcher(text)
        if (suffixMatcher.find()) {
            val rawNumber = suffixMatcher.group(1)
            val parsed = normalizeRawNumber(rawNumber)
            if (parsed != null && parsed > 0) return parsed
        }

        return null
    }

    /**
     * Normaliza representaciones numéricas como "35.000", "35,000", "1.250.000", "35.000,50"
     * a entero Long (35000).
     */
    fun normalizeRawNumber(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        var str = raw.trim()

        // Si tiene decimales al final: coma o punto seguido de 1 o 2 dígitos (ej: ,50 o .5 o ,00)
        // OJO: Si son 3 dígitos precedidos de punto o coma (ej: .000 o ,000), son miles, no decimales.
        val decimalMatch = """[.,](\d{1,2})$""".toRegex().find(str)
        if (decimalMatch != null) {
            str = str.substring(0, decimalMatch.range.first)
        }

        // Remover todos los separadores de miles restantes (puntos, comas o espacios)
        val cleanedDigits = str.replace("""[.,\s]""".toRegex(), "")
        if (cleanedDigits.isBlank()) return null

        return try {
            cleanedDigits.toLong()
        } catch (e: NumberFormatException) {
            null
        }
    }
}
