package com.ambiorix.pay.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.ambiorix.pay.data.Payment
import com.ambiorix.pay.data.PaymentRepository
import com.ambiorix.pay.data.PaymentSource
import com.ambiorix.pay.data.SettingsRepository
import com.ambiorix.pay.notification.NotificationPaymentEvents
import com.ambiorix.pay.pos.NotificationState
import com.ambiorix.pay.pos.PosRepository

/**
 * Receptor de SMS de Android para:
 * 1. Coordinación P2P entre locales (Heladería <-> Pizzería) mediante protocolo AMBIPAY.
 * 2. Notificaciones bancarias de Nequi Negocios (conciliación con reservas, pagos tardíos y pagos sin reserva).
 */
class AmbiorixSmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val appContext = context.applicationContext
        val settings = SettingsRepository.getInstance(appContext).getSettings()
        if (!settings.isServiceEnabled || !settings.smsPaymentEnabled) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val sender = messages.firstOrNull()?.originatingAddress
        val fullBody = messages.joinToString(separator = "") { it.messageBody.orEmpty() }.trim()
        if (fullBody.isEmpty()) return

        val posRepository = PosRepository.getInstance(appContext)
        val ts = messages.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()

        // Procesar comando interno AMBIPAY o notificación bancaria Nequi
        val bankNotif = posRepository.processNequiBankSms(
            rawBody = fullBody,
            originatingAddress = sender,
            timestamp = ts
        )

        // IMPORTANTE (Secciones 8, 12, 13):
        // Solo anunciar como pago propio confirmado cuando la notificación se haya vinculado
        // inequívocamente a una reserva activa de ESTE local (LINKED_TO_SALE).
        // Nunca sonar como venta confirmada si está UNASSIGNED, REQUIRES_REVIEW, RECONCILIATION_PENDING o MATCH_CANDIDATE del otro local.
        if (bankNotif != null && bankNotif.state == NotificationState.LINKED_TO_SALE) {
            val payment = Payment(
                amount = bankNotif.amount,
                appName = "Nequi SMS",
                packageName = "com.nequi.MobileApp",
                timestampMillis = bankNotif.timestamp,
                senderName = bankNotif.senderName,
                channel = bankNotif.channel ?: "Nequi",
                source = PaymentSource.SMS
            )
            PaymentRepository.getInstance(appContext).recordPayment(payment)
            NotificationPaymentEvents.emitRealPayment(payment)
        }
    }
}
