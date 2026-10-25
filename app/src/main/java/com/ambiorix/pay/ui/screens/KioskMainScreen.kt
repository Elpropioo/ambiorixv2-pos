package com.ambiorix.pay.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ambiorix.pay.data.AppSettings
import com.ambiorix.pay.data.OperationalStatus
import com.ambiorix.pay.ui.theme.*
import java.io.File

@Composable
fun KioskMainScreen(
    settings: AppSettings,
    operationalStatus: OperationalStatus,
    scheduleStatusDescription: String,
    onOpenSettingsClick: () -> Unit,
    onRequestNotificationAccess: () -> Unit
) {
    // Animación de pulso continuo para "Esperando pago..."
    val infiniteTransition = rememberInfiniteTransition(label = "PulseTransition")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.22f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // =========================================================================
            // 1. BARRA SUPERIOR: LOGO / MARCA AMBIORIX Y ACCESO SEGURO A AJUSTES
            // =========================================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Logo personalizado o Texto de Marca AMBIORIX
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!settings.logoPath.isNullOrBlank() && File(settings.logoPath).exists()) {
                        AsyncImage(
                            model = File(settings.logoPath),
                            contentDescription = "Logo",
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .border(1.5.dp, GoldPrimary, RoundedCornerShape(14.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    } else {
                        Text(
                            text = "AMBIORIX",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                            color = GoldPrimary,
                            letterSpacing = 2.5.sp
                        )
                    }
                }

                // Botón discreto para ingresar a configuración protegido por PIN
                IconButton(
                    onClick = onOpenSettingsClick,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(DarkSurfaceElevated)
                        .border(1.dp, DarkBorder, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Ajustes",
                        tint = TextGrayLight,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // =========================================================================
            // 2. BANNER DE ESTADO SEGÚN OPERATIONAL STATUS
            // =========================================================================
            when (operationalStatus) {
                OperationalStatus.DISABLED -> {
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF261A0C),
                        border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenSettingsClick() }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.PowerSettingsNew,
                                contentDescription = "Apagado",
                                tint = GoldPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Servicio Ambiorix Pay en Pausa",
                                    color = TextWhite,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "Toca para ingresar y encender el servicio.",
                                    color = TextGrayLight,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
                OperationalStatus.MISSING_PERMISSION -> {
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = DangerRedBg.copy(alpha = 0.4f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DangerRed),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onRequestNotificationAccess() }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Alerta",
                                tint = DangerRedLight,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Falta Acceso a Notificaciones",
                                    color = TextWhite,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "Toca aquí para permitir que la app detecte los pagos en el sistema.",
                                    color = TextGrayLight,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
                OperationalStatus.OUTSIDE_HOURS -> {
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF142038),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3B82F6).copy(alpha = 0.6f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenSettingsClick() }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = "Horario",
                                tint = Color(0xFF60A5FA),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Fuera de Horario Comercial",
                                    color = TextWhite,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "$scheduleStatusDescription. No se anuncian pagos fuera de jornada.",
                                    color = TextGrayLight,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
                OperationalStatus.OPERATIONAL -> {
                    if (settings.businessHours.isScheduleEnabled) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = DarkSurfaceElevated,
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, DarkBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(SuccessGreen)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = scheduleStatusDescription,
                                    color = TextGrayLight,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // =========================================================================
            // 3. NOMBRE DEL NEGOCIO Y ACCIÓN "ESCANEA PARA PAGAR"
            // =========================================================================
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = settings.businessName.uppercase(),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextWhite,
                    textAlign = TextAlign.Center,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = settings.scanToPayText.uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = GoldPrimary,
                    textAlign = TextAlign.Center,
                    letterSpacing = 2.sp
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // =========================================================================
            // 4. SECCIÓN DE CÓDIGOS QR
            //    - INTEGRADO CON EL FONDO OSCURO
            //    - CERO RECUADRO BLANCO, CERO TARJETA BLANCA ARTIFICIAL
            //    - IMAGEN ORIGINAL EN SUS PROPORCIONES EXACTAS
            // =========================================================================
            val hasQr2 = settings.qr2Enabled && !settings.qr2Path.isNullOrBlank()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Código QR 1 (Principal)
                QrDisplayBox(
                    imagePath = settings.qr1Path,
                    label = if (hasQr2) "QR 1" else "CÓDIGO QR",
                    modifier = if (hasQr2) Modifier.weight(1f).padding(end = 8.dp) else Modifier.size(280.dp)
                )

                // Código QR 2 (Opcional si está habilitado)
                if (hasQr2) {
                    QrDisplayBox(
                        imagePath = settings.qr2Path,
                        label = "QR 2",
                        modifier = Modifier.weight(1f).padding(start = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // =========================================================================
            // 5. TELÉFONO DE TRANSFERENCIAS Y TEXTO ADICIONAL
            // =========================================================================
            if (settings.phoneText.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = DarkSurfaceElevated,
                    border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.45f)),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Text(
                        text = "Transferir al cel: ${settings.phoneText}",
                        color = TextWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }
            }

            if (settings.additionalText.isNotBlank()) {
                Text(
                    text = settings.additionalText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextGrayLight,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // =========================================================================
            // 6. ESTADO INFERIOR PULSANTE "ESPERANDO PAGO..."
            // =========================================================================
            when (operationalStatus) {
                OperationalStatus.OPERATIONAL -> {
                    Surface(
                        shape = RoundedCornerShape(32.dp),
                        color = DarkSurface,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            Brush.horizontalGradient(
                                listOf(
                                    GoldPrimary.copy(alpha = 0.25f),
                                    GoldPrimary.copy(alpha = 0.8f),
                                    GoldPrimary.copy(alpha = 0.25f)
                                )
                            )
                        ),
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 26.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(13.dp)
                                    .scale(pulseScale)
                                    .clip(CircleShape)
                                    .background(GoldPrimary.copy(alpha = pulseAlpha))
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                text = "Esperando pago...",
                                style = MaterialTheme.typography.titleLarge,
                                color = TextWhite,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
                OperationalStatus.DISABLED -> {
                    Surface(
                        shape = RoundedCornerShape(30.dp),
                        color = DarkSurfaceElevated,
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                        modifier = Modifier
                            .padding(bottom = 12.dp)
                            .clickable { onOpenSettingsClick() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.PowerSettingsNew,
                                contentDescription = null,
                                tint = TextGrayLight,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Servicio en Pausa",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextGrayLight,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                OperationalStatus.OUTSIDE_HOURS -> {
                    Surface(
                        shape = RoundedCornerShape(30.dp),
                        color = DarkSurfaceElevated,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3B82F6).copy(alpha = 0.4f)),
                        modifier = Modifier
                            .padding(bottom = 12.dp)
                            .clickable { onOpenSettingsClick() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = Color(0xFF60A5FA),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Fuera de Horario Comercial",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color(0xFF93C5FD),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
                OperationalStatus.MISSING_PERMISSION -> {
                    Surface(
                        shape = RoundedCornerShape(30.dp),
                        color = DangerRedBg.copy(alpha = 0.4f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DangerRed.copy(alpha = 0.7f)),
                        modifier = Modifier
                            .padding(bottom = 12.dp)
                            .clickable { onRequestNotificationAccess() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = DangerRedLight,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Falta Acceso a Notificaciones",
                                style = MaterialTheme.typography.titleMedium,
                                color = DangerRedLight,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Contenedor del código QR integrado al fondo oscuro.
 * 
 * Cumplimiento estricto del requerimiento:
 * - CERO fondo blanco artificial de interfaz.
 * - CERO tarjetas blancas ni bordes gruesos blancos.
 * - Se muestra la imagen original importada tal cual fue provista por el comercio o banco,
 *   respetando íntegramente su contenido, bordes y proporciones originales sin deformarla ni recortarla.
 */
@Composable
private fun QrDisplayBox(
    imagePath: String?,
    label: String,
    modifier: Modifier = Modifier
) {
    val fileExists = !imagePath.isNullOrBlank() && File(imagePath).exists()

    Box(
        modifier = modifier.aspectRatio(1f),
        contentAlignment = Alignment.Center
    ) {
        if (fileExists && imagePath != null) {
            // Imagen original importada: respetamos proporciones y contenido sin marcos artificiales
            AsyncImage(
                model = File(imagePath),
                contentDescription = label,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Fit
            )
        } else {
            // Placeholder oscuro de diseño cuando aún no se ha subido una fotografía del QR
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = DarkSurface,
                border = androidx.compose.foundation.BorderStroke(1.5.dp, DarkBorder),
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCode2,
                        contentDescription = "Sin QR",
                        tint = GoldPrimary.copy(alpha = 0.7f),
                        modifier = Modifier.size(68.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = label,
                        color = TextWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Configura la foto en Ajustes",
                        color = TextGrayLight,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
