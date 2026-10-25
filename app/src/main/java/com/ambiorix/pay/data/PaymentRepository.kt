package com.ambiorix.pay.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class PaymentRepository(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _paymentsFlow = MutableStateFlow<List<Payment>>(emptyList())
    val paymentsFlow: StateFlow<List<Payment>> = _paymentsFlow.asStateFlow()

    init {
        _paymentsFlow.value = loadPayments()
    }

    @Synchronized
    fun recordPayment(payment: Payment) {
        val current = _paymentsFlow.value.toMutableList()
        current.add(0, payment)
        // Guardar máximo los últimos 100 pagos para mantener bajo uso de memoria y disco
        val trimmed = if (current.size > 100) current.take(100) else current
        _paymentsFlow.value = trimmed
        savePayments(trimmed)
    }

    @Synchronized
    fun clearHistory() {
        _paymentsFlow.value = emptyList()
        prefs.edit().remove(KEY_PAYMENTS_JSON).apply()
    }

    private fun loadPayments(): List<Payment> {
        val jsonStr = prefs.getString(KEY_PAYMENTS_JSON, null) ?: return emptyList()
        val list = mutableListOf<Payment>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val sourceStr = obj.optString("source", "NOTIFICATION")
                val sourceEnum = try {
                    PaymentSource.valueOf(sourceStr)
                } catch (e: Exception) {
                    PaymentSource.NOTIFICATION
                }
                list.add(
                    Payment(
                        id = obj.optString("id"),
                        amount = obj.getLong("amount"),
                        appName = obj.getString("appName"),
                        packageName = obj.getString("packageName"),
                        timestampMillis = obj.getLong("timestampMillis"),
                        senderName = if (obj.has("senderName")) obj.optString("senderName").takeIf { it.isNotBlank() } else null,
                        channel = if (obj.has("channel")) obj.optString("channel").takeIf { it.isNotBlank() } else null,
                        receiverBusiness = if (obj.has("receiverBusiness")) obj.optString("receiverBusiness").takeIf { it.isNotBlank() } else null,
                        transactionDateTimeText = if (obj.has("transactionDateTimeText")) obj.optString("transactionDateTimeText").takeIf { it.isNotBlank() } else null,
                        source = sourceEnum
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    private fun savePayments(payments: List<Payment>) {
        try {
            val array = JSONArray()
            for (p in payments) {
                val obj = JSONObject().apply {
                    put("id", p.id)
                    put("amount", p.amount)
                    put("appName", p.appName)
                    put("packageName", p.packageName)
                    put("timestampMillis", p.timestampMillis)
                    if (p.senderName != null) put("senderName", p.senderName)
                    if (p.channel != null) put("channel", p.channel)
                    if (p.receiverBusiness != null) put("receiverBusiness", p.receiverBusiness)
                    if (p.transactionDateTimeText != null) put("transactionDateTimeText", p.transactionDateTimeText)
                    put("source", p.source.name)
                }
                array.put(obj)
            }
            prefs.edit().putString(KEY_PAYMENTS_JSON, array.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    companion object {
        private const val PREFS_NAME = "ambiorix_pay_history"
        private const val KEY_PAYMENTS_JSON = "payments_json"

        @Volatile
        private var INSTANCE: PaymentRepository? = null

        fun getInstance(context: Context): PaymentRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PaymentRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
