package com.ambiorix.pay

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.app.NotificationManagerCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.ambiorix.pay.data.Payment
import com.ambiorix.pay.data.SettingsRepository
import com.ambiorix.pay.notification.NotificationPaymentEvents
import com.ambiorix.pay.notification.UiPaymentEvent
import com.ambiorix.pay.sound.SoundAnnouncer
import com.ambiorix.pay.ui.components.PinDialog
import com.ambiorix.pay.ui.screens.KioskMainScreen
import com.ambiorix.pay.ui.screens.PaymentSuccessScreen
import com.ambiorix.pay.ui.theme.AmbiorixPayTheme

class MainActivity : ComponentActivity() {

    private lateinit var settingsRepository: SettingsRepository
    private lateinit var soundAnnouncer: SoundAnnouncer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsRepository = SettingsRepository.getInstance(applicationContext)
        soundAnnouncer = SoundAnnouncer.getInstance(applicationContext)

        configureKioskWindowFlags()
        lockBackButton()

        setContent {
            AmbiorixPayTheme {
                val settings by settingsRepository.settingsFlow.collectAsStateWithLifecycle()
                var currentPayment by remember { mutableStateOf<Payment?>(null) }
                val paymentQueue = remember { mutableStateListOf<Payment>() }
                var showPinDialog by remember { mutableStateOf(false) }
                var isNotificationAccessGranted by remember { mutableStateOf(checkNotificationAccess()) }

                // Evaluar estado operacional diferenciando estado, permisos y horario comercial
                val isWithinSchedule = remember(settings.businessHours) {
                    settings.businessHours.isWithinBusinessHours()
                }
                val operationalStatus = remember(settings.isServiceEnabled, isNotificationAccessGranted, isWithinSchedule) {
                    when {
                        !settings.isServiceEnabled -> com.ambiorix.pay.data.OperationalStatus.DISABLED
                        !isNotificationAccessGranted -> com.ambiorix.pay.data.OperationalStatus.MISSING_PERMISSION
                        !isWithinSchedule -> com.ambiorix.pay.data.OperationalStatus.OUTSIDE_HOURS
                        else -> com.ambiorix.pay.data.OperationalStatus.OPERATIONAL
                    }
                }
                val scheduleDescription = remember(settings.businessHours) {
                    settings.businessHours.getScheduleStatusDescription()
                }

                // Función auxiliar para procesar el siguiente pago en la cola
                fun showNextPaymentIfAvailable() {
                    if (paymentQueue.isNotEmpty()) {
                        val next = paymentQueue.removeAt(0)
                        currentPayment = next
                        soundAnnouncer.announce(
                            amount = next.amount,
                            playSound = settings.soundEnabled,
                            speakVoice = settings.ttsEnabled,
                            includeThankYou = settings.includeThankYou,
                            senderName = next.senderName,
                            announceSenderName = settings.announceSenderName
                        )
                    } else {
                        currentPayment = null
                    }
                }

                // Si el usuario apaga el servicio mientras hay un aviso en pantalla,
                // detener inmediatamente el sonido/voz y regresar a la pantalla de espera
                LaunchedEffect(settings.isServiceEnabled) {
                    if (!settings.isServiceEnabled) {
                        soundAnnouncer.stop()
                        paymentQueue.clear()
                        currentPayment = null
                    }
                }

                // Monitorear pagos entrantes (reales o simulados)
                LaunchedEffect(Unit) {
                    repeatOnLifecycle(Lifecycle.State.STARTED) {
                        NotificationPaymentEvents.paymentEvents.collect { event ->
                            val payment = when (event) {
                                is UiPaymentEvent.RealPayment -> {
                                    val currentSettings = settingsRepository.getSettings()
                                    if (!currentSettings.isServiceEnabled || !currentSettings.businessHours.isWithinBusinessHours()) {
                                        return@collect
                                    }
                                    event.payment
                                }
                                is UiPaymentEvent.SimulatedTestPayment -> {
                                    event.payment
                                }
                            }

                            // Gestión de cola: si ya hay una confirmación en pantalla, encolar el nuevo pago
                            // para que no se superpongan ni se pierda ninguno
                            if (currentPayment == null) {
                                currentPayment = payment
                                soundAnnouncer.announce(
                                    amount = payment.amount,
                                    playSound = settings.soundEnabled,
                                    speakVoice = settings.ttsEnabled,
                                    includeThankYou = settings.includeThankYou,
                                    senderName = payment.senderName,
                                    announceSenderName = settings.announceSenderName
                                )
                            } else {
                                paymentQueue.add(payment)
                            }
                        }
                    }
                }

                // Actualizar verificación de permisos al volver a primer plano
                DisposableEffect(Unit) {
                    val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            hideSystemBars()
                            isNotificationAccessGranted = checkNotificationAccess()
                        }
                    }
                    lifecycle.addObserver(observer)
                    onDispose {
                        lifecycle.removeObserver(observer)
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    // Pantalla Principal Kiosco con códigos QR integrados al fondo oscuro
                    KioskMainScreen(
                        settings = settings,
                        operationalStatus = operationalStatus,
                        scheduleStatusDescription = scheduleDescription,
                        onOpenSettingsClick = { showPinDialog = true },
                        onRequestNotificationAccess = { openNotificationSettings() }
                    )

                    // Transición fluida y elegante hacia la pantalla verde de confirmación
                    // Sin parpadeos ni pantallas negras intermedias
                    AnimatedVisibility(
                        visible = currentPayment != null,
                        enter = fadeIn(animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing)) +
                                scaleIn(initialScale = 0.94f, animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing)),
                        exit = fadeOut(animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)) +
                               scaleOut(targetScale = 0.94f, animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing))
                    ) {
                        currentPayment?.let { payment ->
                            PaymentSuccessScreen(
                                payment = payment,
                                durationSeconds = settings.confirmationDurationSeconds,
                                onTimeout = {
                                    showNextPaymentIfAvailable()
                                }
                            )
                        }
                    }

                    // Modal de PIN para ingresar a la configuración
                    if (showPinDialog) {
                        PinDialog(
                            correctPin = settings.pinCode,
                            onDismissRequest = { showPinDialog = false },
                            onPinSuccess = {
                                showPinDialog = false
                                startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                            }
                        )
                    }
                }
            }
        }
    }

    private fun configureKioskWindowFlags() {
        // Mantener la pantalla permanentemente encendida en el local comercial
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Encender y mostrar incluso si el dispositivo se llega a bloquear
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun lockBackButton() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Bloqueado deliberadamente para modo kiosco
            }
        })
    }

    private fun checkNotificationAccess(): Boolean {
        val enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(this)
        return enabledPackages.contains(packageName)
    }

    private fun openNotificationSettings() {
        try {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            startActivity(intent)
        } catch (e: Exception) {
            val intent = Intent(Settings.ACTION_SETTINGS)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
    }

    override fun onDestroy() {
        super.onDestroy()
        soundAnnouncer.stop()
    }
}
