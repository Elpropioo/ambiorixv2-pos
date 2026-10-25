package com.ambiorix.pay.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ambiorix.pay.data.Payment
import com.ambiorix.pay.ui.theme.SuccessGreen
import com.ambiorix.pay.ui.theme.SuccessGreenDark
import kotlinx.coroutines.launch

@Composable
fun PaymentSuccessScreen(
    payment: Payment,
    durationSeconds: Int,
    onTimeout: () -> Unit
) {
    // Temporizador de cuenta regresiva para la barra inferior
    val progress = remember(payment.id) { Animatable(1.0f) }

    // Animación suave del checkmark: escala elástica elegante y fade-in progresivo
    val checkScale = remember(payment.id) { Animatable(0.4f) }
    val checkAlpha = remember(payment.id) { Animatable(0.0f) }

    // Anillo pulsante que se expande alrededor del check
    val ringScale = remember(payment.id) { Animatable(0.8f) }
    val ringAlpha = remember(payment.id) { Animatable(0.8f) }

    LaunchedEffect(payment.id) {
        // Iniciar animación del checkmark
        launch {
            checkAlpha.animateTo(
                targetValue = 1.0f,
                animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
            )
        }
        launch {
            checkScale.animateTo(
                targetValue = 1.0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
        // Iniciar expansión del anillo decorativo
        launch {
            ringScale.animateTo(
                targetValue = 1.4f,
                animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing)
            )
        }
        launch {
            ringAlpha.animateTo(
                targetValue = 0.0f,
                animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing)
            )
        }

        // Cuenta regresiva del progreso
        progress.snapTo(1.0f)
        progress.animateTo(
            targetValue = 0.0f,
            animationSpec = tween(
                durationMillis = durationSeconds * 1000,
                easing = LinearEasing
            )
        )
        onTimeout()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SuccessGreen)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // =========================================================================
            // HEADER: CHECKMARK ANIMADO Y TÍTULO "PAGO RECIBIDO"
            // =========================================================================
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 24.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(110.dp)
                ) {
                    // Anillo exterior suave en expansión
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .scale(ringScale.value)
                            .alpha(ringAlpha.value)
                            .clip(CircleShape)
                            .border(3.dp, Color.White.copy(alpha = 0.7f), CircleShape)
                    )

                    // Círculo blanco central con checkmark animado
                    Box(
                        modifier = Modifier
                            .size(86.dp)
                            .scale(checkScale.value)
                            .alpha(checkAlpha.value)
                            .clip(CircleShape)
                            .background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Confirmado",
                            tint = SuccessGreenDark,
                            modifier = Modifier.size(52.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "TRANSACCIÓN RECIBIDA",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.5.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "PAGO RECIBIDO",
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
            }

            // =========================================================================
            // MONTO GIGANTE Y ADAPTATIVO
            // =========================================================================
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                val formatted = payment.formattedAmount
                val fontSize = when {
                    formatted.length <= 8 -> 68.sp   // Ej: $35.000
                    formatted.length <= 11 -> 52.sp  // Ej: $1.250.000
                    else -> 40.sp
                }

                Text(
                    text = formatted,
                    color = Color.White,
                    fontSize = fontSize,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    lineHeight = fontSize * 1.08f,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = Color.Black.copy(alpha = 0.22f),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        text = "Detectado por ${payment.appName}",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 7.dp)
                    )
                }
            }

            // =========================================================================
            // FOOTER: HORA Y BARRA DE PROGRESO DE RETORNO A QR
            // =========================================================================
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Hora: ${payment.formattedTime}",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Barra de temporizador
                LinearProgressIndicator(
                    progress = { progress.value },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.3f),
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Volviendo a códigos QR...",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 13.sp
                )
            }
        }
    }
}
