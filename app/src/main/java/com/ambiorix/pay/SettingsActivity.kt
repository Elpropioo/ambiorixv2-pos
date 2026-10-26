package com.ambiorix.pay

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ambiorix.pay.data.PaymentRepository
import com.ambiorix.pay.data.SettingsRepository
import com.ambiorix.pay.notification.NotificationPaymentEvents
import com.ambiorix.pay.sound.SoundAnnouncer
import com.ambiorix.pay.ui.screens.HistoryScreen
import com.ambiorix.pay.ui.screens.SettingsScreen
import com.ambiorix.pay.ui.theme.AmbiorixPayTheme

class SettingsActivity : ComponentActivity() {

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var paymentRepository: PaymentRepository
    private lateinit var soundAnnouncer: SoundAnnouncer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsRepository = SettingsRepository.getInstance(applicationContext)
        paymentRepository = PaymentRepository.getInstance(applicationContext)
        soundAnnouncer = SoundAnnouncer.getInstance(applicationContext)

        setContent {
            AmbiorixPayTheme {
                val settings by settingsRepository.settingsFlow.collectAsStateWithLifecycle()
                val payments by paymentRepository.paymentsFlow.collectAsStateWithLifecycle()
                val isSpanishSupported by soundAnnouncer.isSpanishSupported.collectAsStateWithLifecycle()
                val isTtsReady by soundAnnouncer.isTtsReady.collectAsStateWithLifecycle()
                val ttsStatusDetail by soundAnnouncer.ttsStatusDetail.collectAsStateWithLifecycle()
                var isNotificationAccessGranted by remember { mutableStateOf(checkNotificationAccess()) }

                // Actualizar verificación de permisos al regresar de los ajustes del sistema
                DisposableEffect(Unit) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            isNotificationAccessGranted = checkNotificationAccess()
                        }
                    }
                    lifecycle.addObserver(observer)
                    onDispose {
                        lifecycle.removeObserver(observer)
                    }
                }

                var currentSubScreen by remember { mutableStateOf("settings") }

                if (currentSubScreen == "history") {
                    HistoryScreen(
                        payments = payments,
                        onBackClick = { currentSubScreen = "settings" },
                        onClearHistory = { paymentRepository.clearHistory() }
                    )
                } else {
                    SettingsScreen(
                        settings = settings,
                        isSpanishVoiceAvailable = isSpanishSupported,
                        isTtsReady = isTtsReady,
                        ttsStatusDetail = ttsStatusDetail,
                        isNotificationAccessGranted = isNotificationAccessGranted,
                        onSettingsChanged = { transform ->
                            val prev = settingsRepository.getSettings()
                            settingsRepository.updateSettings(transform)
                            val next = settingsRepository.getSettings()
                            if ((!prev.peerStoreEnabled && next.peerStoreEnabled) || (!prev.smsPaymentEnabled && next.smsPaymentEnabled)) {
                                ensureSmsPermissionsGranted()
                            }
                        },
                        onBackClick = { finish() },
                        onOpenNotificationSettings = {
                            openNotificationAccessSettings()
                        },
                        onOpenScreenPinningSettings = {
                            openSecuritySettings()
                        },
                        onOpenTtsSettings = {
                            soundAnnouncer.openTtsSettings()
                        },
                        onOpenHistoryClick = {
                            currentSubScreen = "history"
                        },
                        onTestPaymentClick = {
                            // Emitir pago de prueba sin agregarlo al historial
                            NotificationPaymentEvents.emitTestPayment(35000L)
                            // Regresar a la pantalla principal para que se vea la pantalla verde inmediatamente
                            finish()
                        },
                        onTestVoiceClick = {
                            soundAnnouncer.speakTest(35000L, settings.includeThankYou)
                        }
                    )
                }
            }
        }
    }

    private fun ensureSmsPermissionsGranted() {
        val requiredPermissions = arrayOf(
            android.Manifest.permission.RECEIVE_SMS,
            android.Manifest.permission.SEND_SMS
        )
        val missing = requiredPermissions.filter {
            androidx.core.content.ContextCompat.checkSelfPermission(this, it) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            androidx.core.app.ActivityCompat.requestPermissions(
                this,
                missing.toTypedArray(),
                1002
            )
        }
    }

    private fun checkNotificationAccess(): Boolean {
        val enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(this)
        return enabledPackages.contains(packageName)
    }

    private fun openNotificationAccessSettings() {
        try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            startActivity(intent)
        } catch (e: Exception) {
            val intent = Intent(Settings.ACTION_SETTINGS)
            startActivity(intent)
        }
    }

    private fun openSecuritySettings() {
        try {
            val intent = Intent(Settings.ACTION_SECURITY_SETTINGS)
            startActivity(intent)
        } catch (e: Exception) {
            val intent = Intent(Settings.ACTION_SETTINGS)
            startActivity(intent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // No llamamos release() aquí para no cerrar el TTS de MainActivity
        soundAnnouncer.stop()
    }
}
