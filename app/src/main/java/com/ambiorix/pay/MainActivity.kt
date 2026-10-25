                                packageName = "com.nequi.MobileApp",
                                timestampMillis = System.currentTimeMillis(),
                                senderName = sender,
                                channel = channel ?: "Bre-B"
                            )
                            currentPayment = payment
                            soundAnnouncer.announce(
                                amount = amount,
                                playSound = settings.soundEnabled,
                                speakVoice = settings.ttsEnabled,
                                includeThankYou = settings.includeThankYou,
                                senderName = sender
                            )
                        }
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
