package com.ambiorix.pay.notification

import com.ambiorix.pay.data.Payment
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class UiPaymentEvent {
    data class RealPayment(val payment: Payment) : UiPaymentEvent()
    data class SimulatedTestPayment(val payment: Payment) : UiPaymentEvent()
}

/**
 * Canal en memoria (StateFlow / SharedFlow) entre el servicio NotificationListenerService
 * y las actividades de la interfaz de usuario.
 */
object NotificationPaymentEvents {

    private val _paymentEvents = MutableSharedFlow<UiPaymentEvent>(extraBufferCapacity = 10)
    val paymentEvents: SharedFlow<UiPaymentEvent> = _paymentEvents.asSharedFlow()

    private val _isListenerConnected = MutableStateFlow(false)
    val isListenerConnected: StateFlow<Boolean> = _isListenerConnected.asStateFlow()

    fun emitRealPayment(payment: Payment) {
        _paymentEvents.tryEmit(UiPaymentEvent.RealPayment(payment))
    }

    fun emitTestPayment(testAmount: Long = 35000L) {
        val testPayment = Payment(
            amount = testAmount,
            appName = "Prueba de Sistema",
            packageName = "com.ambiorix.pay.test",
            timestampMillis = System.currentTimeMillis()
        )
        _paymentEvents.tryEmit(UiPaymentEvent.SimulatedTestPayment(testPayment))
    }

    fun setListenerConnected(connected: Boolean) {
        _isListenerConnected.value = connected
    }
}
