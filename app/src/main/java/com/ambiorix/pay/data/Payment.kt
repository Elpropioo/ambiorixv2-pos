package com.ambiorix.pay.data

import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Origen de la transacción detectada.
 */
enum class PaymentSource {
    NOTIFICATION, // Notificación Push de Android
    SMS,          // Mensaje de texto SMS (ej. Nequi 890806)
    SIMULATED     // Prueba manual desde la app
}

/**
 * Estado interno de la app.
 * La app NUNCA afirma que el banco confirmó el pago.
 * El estado interno es PAYMENT_DETECTED y la interfaz dice "PAGO RECIBIDO".
 */
enum class PaymentStatus {
    IDLE,
    PAYMENT_DETECTED
}

/**
 * Representa una transacción detectada a través del NotificationListenerService o del receptor de SMS.
 */
data class Payment(
    val id: String = UUID.randomUUID().toString(),
    val amount: Long,
    val appName: String,
    val packageName: String,
    val timestampMillis: Long = System.currentTimeMillis(),
    val senderName: String? = null,              // Nombre completo del remitente del pago (ej. SANTIAGO ALEJANDRO MORGAN AVILA)
    val channel: String? = null,                 // Canal o red de transferencia (ej. Bre-B, Nequi)
    val receiverBusiness: String? = null,        // Nombre del negocio receptor extraído (ej. TECNICABLES)
    val transactionDateTimeText: String? = null, // Fecha y hora textual de la notificación (ej. 19/10/2026 a las 07:11 p.m.)
    val source: PaymentSource = PaymentSource.NOTIFICATION
) {
    val formattedAmount: String
        get() {
            val format = NumberFormat.getCurrencyInstance(Locale("es", "CO"))
            format.maximumFractionDigits = 0
            return format.format(amount)
        }

    val formattedTime: String
        get() {
            val sdf = SimpleDateFormat("hh:mm:ss a", Locale.getDefault())
            return sdf.format(Date(timestampMillis))
        }

    val formattedDate: String
        get() {
            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            return sdf.format(Date(timestampMillis))
        }
}
