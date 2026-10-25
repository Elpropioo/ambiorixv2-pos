package com.ambiorix.pay.notification

import android.app.Notification
import android.content.ComponentName
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.ambiorix.pay.data.KnownApps
import com.ambiorix.pay.data.Payment
import com.ambiorix.pay.data.PaymentRepository
import com.ambiorix.pay.data.SettingsRepository

/**
 * Servicio de escucha de notificaciones de Android.
 *
 * Principios de seguridad y privacidad:
 * - NO lee extras ni textos si la app no está explícitamente autorizada en la configuración.
 * - NO imprime montos, nombres ni contenidos de notificaciones en el log del sistema (Logcat).
 * - Cero conexiones a internet.
 * - Soporta reconexión automática mediante requestRebind().
 */
class AmbiorixNotificationListener : NotificationListenerService() {

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var paymentRepository: PaymentRepository
    private val filter = PaymentNotificationFilter()

    override fun onCreate() {
        super.onCreate()
        settingsRepository = SettingsRepository.getInstance(applicationContext)
        paymentRepository = PaymentRepository.getInstance(applicationContext)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        NotificationPaymentEvents.setListenerConnected(true)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        NotificationPaymentEvents.setListenerConnected(false)
        // Intentar reconectar el servicio inmediatamente
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                requestRebind(ComponentName(this, AmbiorixNotificationListener::class.java))
            } catch (e: Exception) {
                // Falla silenciosa sin exponer datos sensibles
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val packageName = sbn.packageName ?: return

        val settings = settingsRepository.getSettings()

        // 1. REGLA DE SERVICIO ENCENDIDO / APAGADO:
        // Si el usuario apagó el servicio Ambiorix Pay, ignorar completamente sin procesar.
        if (!settings.isServiceEnabled) {
            return
        }

        // 2. REGLA DE HORARIO COMERCIAL:
        // Fuera de horario comercial no se anuncian pagos ni se generan confirmaciones.
        val isWithinHours = settings.businessHours.isWithinBusinessHours()
        if (!isWithinHours) {
            return
        }

        // 3. REGLA CRÍTICA DE PRIVACIDAD:
        // Si el paquete NO está en la lista de apps autorizadas, descartar inmediatamente
        // SIN LEER extras ni el contenido de la notificación.
        val allAuthorized = settings.enabledPackages + settings.customPackages
        val isAuthorized = allAuthorized.any { it.equals(packageName, ignoreCase = true) }
        if (!isAuthorized) {
            return
        }

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        // Extraer título y texto sin loguear sus contenidos
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val fullBody = if (!bigText.isNullOrBlank()) bigText else text

        val postTime = sbn.postTime

        // Evaluar con el filtro determinista
        val result = filter.evaluate(
            packageName = packageName,
            title = title,
            text = fullBody,
            notificationPostTimeMillis = postTime,
            authorizedPackages = allAuthorized,
            isServiceEnabled = settings.isServiceEnabled,
            isWithinBusinessHours = isWithinHours
        )

        when (result) {
            is FilterResult.Accepted -> {
                val appDisplayName = KnownApps.getDisplayName(packageName)
                val payment = Payment(
                    amount = result.amount,
                    appName = appDisplayName,
                    packageName = packageName,
                    timestampMillis = if (postTime > 0) postTime else System.currentTimeMillis()
                )

                // Guardar en el repositorio local de historial
                paymentRepository.recordPayment(payment)

                // Emitir por el canal en memoria hacia la interfaz gráfica
                NotificationPaymentEvents.emitRealPayment(payment)
            }
            is FilterResult.Rejected -> {
                // Descartada silenciosamente sin escribir montos en logs
            }
        }
    }
}
