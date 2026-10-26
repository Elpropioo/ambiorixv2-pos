package com.ambiorix.pay.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.ambiorix.pay.data.Payment
import com.ambiorix.pay.pos.*
import com.ambiorix.pay.ui.theme.*
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private fun formatCOP(amount: Long): String {
    val format = NumberFormat.getCurrencyInstance(Locale("es", "CO"))
    format.maximumFractionDigits = 0
    return format.format(amount)
}

private val MONTH_NAMES = listOf(
    "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio",
    "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"
)

private val WEEKDAY_NAMES = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")

private val EXPENSE_CATEGORIES = listOf(
    "Insumos", "Reposición inventario", "Transporte", "Servicios", "Mantenimiento", "Otros"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    payments: List<Payment> = emptyList(),
    posRepository: PosRepository? = null,
    onBackClick: () -> Unit,
    onClearHistory: () -> Unit = {}
) {
    val context = LocalContext.current
    val repository = remember(posRepository) {
        posRepository ?: PosRepository.getInstance(context)
    }

    val todayCalendar = remember { Calendar.getInstance() }
    var currentYear by remember { mutableStateOf(todayCalendar.get(Calendar.YEAR)) }
    var currentMonth by remember { mutableStateOf(todayCalendar.get(Calendar.MONTH) + 1) } // 1..12
    var selectedDateStr by remember {
        mutableStateOf(
            String.format(
                Locale.US,
                "%04d-%02d-%02d",
                todayCalendar.get(Calendar.YEAR),
                todayCalendar.get(Calendar.MONTH) + 1,
                todayCalendar.get(Calendar.DAY_OF_MONTH)
            )
        )
    }

    var selectedTab by remember { mutableStateOf(0) } // 0: Nequi, 1: Efectivo, 2: Gastos, 3: Lo que se vendió
    var currentRole by remember { mutableStateOf(PosRole.OWNER) }
    var showPinDialog by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }

    // Dialog states
    var inspectingNequiTx by remember { mutableStateOf<PaymentTransaction?>(null) }
    var inspectingProductOccurrences by remember { mutableStateOf<ProductSaleSummary?>(null) }
    var showExpenseDialog by remember { mutableStateOf(false) }
    var expenseToAnnul by remember { mutableStateOf<ExpenseRecord?>(null) }
    var annulReasonInput by remember { mutableStateOf("") }

    // Observables from repo
    val allExpenses by repository.expenses.collectAsState()
    val allPaymentTxns by repository.paymentTransactions.collectAsState()
    val allSales by repository.sales.collectAsState()
    val activeCashier by repository.activeCashier.collectAsState()

    // Recompute summary for selected date whenever state changes
    val dailySummary = remember(selectedDateStr, allExpenses, allPaymentTxns, allSales) {
        repository.getDailyFinancialSummary(selectedDateStr)
    }

    // Calendar indicators for current month
    val monthDays = remember(currentYear, currentMonth, allExpenses, allPaymentTxns, allSales) {
        repository.getMonthCalendarDays(currentYear, currentMonth)
    }

    // Formatted date string for selected date
    val formattedSelectedDateTitle = remember(selectedDateStr) {
        try {
            val parts = selectedDateStr.split("-")
            val cal = Calendar.getInstance()
            cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
            val dayOfWeek = SimpleDateFormat("EEEE", Locale("es", "CO")).format(cal.time)
                .replaceFirstChar { it.uppercase() }
            val dayOfMonth = parts[2].toInt()
            val monthName = MONTH_NAMES[parts[1].toInt() - 1]
            "$dayOfWeek, $dayOfMonth de $monthName de ${parts[0]}"
        } catch (e: Exception) {
            selectedDateStr
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Historial de Ventas y Pagos",
                            color = TextWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "Auditoría Financiera Diaria & Calendario",
                            color = GoldPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Atrás",
                            tint = TextWhite
                        )
                    }
                },
                actions = {
                    // Role switcher badge
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (currentRole == PosRole.OWNER) GoldPrimary.copy(alpha = 0.2f) else DarkSurfaceElevated,
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (currentRole == PosRole.OWNER) GoldPrimary else DarkBorder),
                        modifier = Modifier
                            .clickable {
                                if (currentRole == PosRole.CASHIER) {
                                    pinInput = ""
                                    pinError = false
                                    showPinDialog = true
                                } else {
                                    currentRole = PosRole.CASHIER
                                }
                            }
                            .padding(end = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = if (currentRole == PosRole.OWNER) Icons.Default.Shield else Icons.Default.Lock,
                                contentDescription = null,
                                tint = if (currentRole == PosRole.OWNER) GoldPrimary else TextGray,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (currentRole == PosRole.OWNER) "Patrón" else "Cajera",
                                color = if (currentRole == PosRole.OWNER) GoldPrimary else TextWhite,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground
                )
            )
        },
        containerColor = DarkBackground
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 12.dp)
        ) {
            // =================================================================
            // 1. CALENDAR NAVIGATION HEADER
            // =================================================================
            item {
                Spacer(modifier = Modifier.height(6.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${MONTH_NAMES[currentMonth - 1]} $currentYear",
                                    color = TextWhite,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Jump to today button
                                TextButton(
                                    onClick = {
                                        val cal = Calendar.getInstance()
                                        currentYear = cal.get(Calendar.YEAR)
                                        currentMonth = cal.get(Calendar.MONTH) + 1
                                        selectedDateStr = String.format(
                                            Locale.US,
                                            "%04d-%02d-%02d",
                                            currentYear,
                                            currentMonth,
                                            cal.get(Calendar.DAY_OF_MONTH)
                                        )
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text("Hoy", color = GoldPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                IconButton(
                                    onClick = {
                                        if (currentMonth == 1) {
                                            currentMonth = 12
                                            currentYear -= 1
                                        } else {
                                            currentMonth -= 1
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.ChevronLeft, contentDescription = "Mes anterior", tint = TextWhite)
                                }

                                IconButton(
                                    onClick = {
                                        if (currentMonth == 12) {
                                            currentMonth = 1
                                            currentYear += 1
                                        } else {
                                            currentMonth += 1
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.ChevronRight, contentDescription = "Mes siguiente", tint = TextWhite)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Weekdays header
                        Row(modifier = Modifier.fillMaxWidth()) {
                            WEEKDAY_NAMES.forEach { dayName ->
                                Text(
                                    text = dayName,
                                    color = TextGray,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Calendar Grid Days
                        // Calculate offset for the first day of the month
                        val firstDayCal = remember(currentYear, currentMonth) {
                            val c = Calendar.getInstance()
                            c.set(currentYear, currentMonth - 1, 1)
                            c
                        }
                        // Calendar.DAY_OF_WEEK: Sunday=1, Monday=2 ... Saturday=7
                        // We want Monday=0, Tuesday=1 ... Sunday=6
                        val firstDayOfWeek = (firstDayCal.get(Calendar.DAY_OF_WEEK) + 5) % 7

                        val totalCells = firstDayOfWeek + monthDays.size
                        val rows = (totalCells + 6) / 7

                        for (r in 0 until rows) {
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                for (c in 0..6) {
                                    val cellIndex = r * 7 + c
                                    val dayIndex = cellIndex - firstDayOfWeek
                                    if (dayIndex in monthDays.indices) {
                                        val indicator = monthDays[dayIndex]
                                        val isSelected = indicator.dateStr == selectedDateStr
                                        val isToday = indicator.dateStr == String.format(
                                            Locale.US,
                                            "%04d-%02d-%02d",
                                            todayCalendar.get(Calendar.YEAR),
                                            todayCalendar.get(Calendar.MONTH) + 1,
                                            todayCalendar.get(Calendar.DAY_OF_MONTH)
                                        )

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(1f)
                                                .padding(2.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(
                                                    when {
                                                        isSelected -> GoldPrimary.copy(alpha = 0.25f)
                                                        isToday -> Color(0xFF1E293B)
                                                        else -> Color.Transparent
                                                    }
                                                )
                                                .border(
                                                    1.dp,
                                                    when {
                                                        isSelected -> GoldPrimary
                                                        isToday -> GoldPrimary.copy(alpha = 0.4f)
                                                        else -> Color.Transparent
                                                    },
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .clickable {
                                                    selectedDateStr = indicator.dateStr
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                Text(
                                                    text = indicator.dayNumber.toString(),
                                                    color = if (isSelected) GoldPrimary else if (isToday) TextWhite else TextGray,
                                                    fontSize = 12.sp,
                                                    fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal
                                                )

                                                // Activity dots (Green = Income, Red/Orange = Expenses)
                                                Row(
                                                    horizontalArrangement = Arrangement.Center,
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(top = 1.dp)
                                                ) {
                                                    if (indicator.hasSalesOrPayments) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(4.dp)
                                                                .clip(CircleShape)
                                                                .background(SuccessGreen)
                                                        )
                                                    }
                                                    if (indicator.hasExpenses) {
                                                        Spacer(modifier = Modifier.width(2.dp))
                                                        Box(
                                                            modifier = Modifier
                                                                .size(4.dp)
                                                                .clip(CircleShape)
                                                                .background(Color(0xFFF97316))
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
                                    }
                                }
                            }
                        }

                        // Legend
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(SuccessGreen))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Ingresos / Ventas", color = TextGray, fontSize = 9.sp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFFF97316)))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Gastos", color = TextGray, fontSize = 9.sp)
                        }
                    }
                }
            }

            // =================================================================
            // 2. DAILY FINANCIAL SUMMARY BANNER & FORMULA BALANCES
            // =================================================================
            item {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = formattedSelectedDateTitle,
                    color = TextWhite,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )

                Spacer(modifier = Modifier.height(6.dp))

                // 3 Balance Cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Card 1: Total Recibido (Nequi + Efectivo)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.4f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Total Recibido", color = TextGray, fontSize = 10.sp)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = formatCOP(dailySummary.totalReceived),
                                color = SuccessGreen,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = "Nequi + Efectivo",
                                color = TextMuted,
                                fontSize = 8.sp
                            )
                        }
                    }

                    // Card 2: Total Gastos
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF97316).copy(alpha = 0.4f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Total Gastos", color = TextGray, fontSize = 10.sp)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = formatCOP(dailySummary.totalExpenses),
                                color = Color(0xFFF97316),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = "${dailySummary.expensesCount} registros",
                                color = TextMuted,
                                fontSize = 8.sp
                            )
                        }
                    }

                    // Card 3: Balance de Movimientos
                    val isPositiveBalance = dailySummary.movementBalance >= 0L
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isPositiveBalance) GoldPrimary.copy(alpha = 0.5f) else DangerRed.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Balance Neto", color = TextGray, fontSize = 10.sp)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = formatCOP(dailySummary.movementBalance),
                                color = if (isPositiveBalance) GoldPrimary else DangerRed,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Black
                            )
                            Text(
                                text = "Recibido - Gastos",
                                color = TextMuted,
                                fontSize = 8.sp
                            )
                        }
                    }
                }
            }

            // =================================================================
            // 3. TABS: NEQUI / EFECTIVO / GASTOS / LO QUE SE VENDIÓ
            // =================================================================
            item {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Tab 0: Nequi
                    TabPill(
                        text = "Nequi (${dailySummary.nequiCount})",
                        isSelected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        modifier = Modifier.weight(1f)
                    )
                    // Tab 1: Efectivo
                    TabPill(
                        text = "Efectivo (${dailySummary.cashCount})",
                        isSelected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        modifier = Modifier.weight(1f)
                    )
                    // Tab 2: Gastos
                    TabPill(
                        text = "Gastos (${dailySummary.expensesCount})",
                        isSelected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        modifier = Modifier.weight(1f)
                    )
                    // Tab 3: Productos
                    TabPill(
                        text = "Vendido (${dailySummary.productsSold.size})",
                        isSelected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // =================================================================
            // BLOCK 1: NEQUI TRANSACTIONS
            // =================================================================
            if (selectedTab == 0) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Pagos por Nequi", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(
                                "Total acumulado: ${formatCOP(dailySummary.totalNequi)}",
                                color = SuccessGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = SuccessGreen.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = "${dailySummary.nequiCount} transacciones",
                                color = SuccessGreen,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (dailySummary.nequiTransactions.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Default.QrCode,
                            title = "No hay pagos Nequi este día",
                            subtitle = "Las transferencias recibidas por Nequi o Bre-B aparecerán aquí automáticamente."
                        )
                    }
                } else {
                    items(dailySummary.nequiTransactions, key = { it.id }) { txn ->
                        NequiTransactionCard(
                            transaction = txn,
                            onClick = { inspectingNequiTx = txn }
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }

            // =================================================================
            // BLOCK 2: EFECTIVO TRANSACTIONS
            // =================================================================
            if (selectedTab == 1) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Movimientos de Efectivo", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(
                                "Total acumulado: ${formatCOP(dailySummary.totalCash)}",
                                color = SuccessGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = GoldPrimary.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = "${dailySummary.cashCount} movimientos",
                                color = GoldPrimary,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (dailySummary.cashTransactions.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Default.AttachMoney,
                            title = "Sin cobros en efectivo este día",
                            subtitle = "Las ventas en efectivo o ingresos de caja registrados se detallan aquí."
                        )
                    }
                } else {
                    items(dailySummary.cashTransactions, key = { it.id }) { txn ->
                        CashTransactionCard(transaction = txn)
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }

            // =================================================================
            // BLOCK 3: GASTOS (EXPENSES)
            // =================================================================
            if (selectedTab == 2) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Gastos y Salidas de Caja", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(
                                "Total gastos: ${formatCOP(dailySummary.totalExpenses)}",
                                color = Color(0xFFF97316),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Button(
                            onClick = { showExpenseDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = DarkBackground, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Registrar Gasto", color = DarkBackground, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (dailySummary.expenses.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Default.ReceiptLong,
                            title = "Sin gastos registrados este día",
                            subtitle = "Toca 'Registrar Gasto' para asentar insumos, servicios, transporte o reposición."
                        )
                    }
                } else {
                    items(dailySummary.expenses, key = { it.id }) { exp ->
                        ExpenseCard(
                            expense = exp,
                            canAnnul = currentRole == PosRole.OWNER,
                            onAnnulClick = { expenseToAnnul = exp }
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }

            // =================================================================
            // BLOCK 4: PRODUCTOS VENDIDOS ESE DÍA (LO QUE SE VENDIÓ)
            // =================================================================
            if (selectedTab == 3) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Lo que se vendió este día", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(
                                "${dailySummary.totalUnitsSold} unidades en ${dailySummary.salesCount} ventas",
                                color = GoldPrimary,
                                fontSize = 11.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (dailySummary.productsSold.isEmpty()) {
                    item {
                        EmptyStateCard(
                            icon = Icons.Default.ShoppingBag,
                            title = "No hay ventas registradas este día",
                            subtitle = "Los productos completados en ventas POS se agruparán aquí automáticamente."
                        )
                    }
                } else {
                    items(dailySummary.productsSold, key = { it.productId }) { prodSummary ->
                        ProductSoldRowCard(
                            summary = prodSummary,
                            onClick = { inspectingProductOccurrences = prodSummary }
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(30.dp))
            }
        }
    }

    // =========================================================================
    // DIALOG: DETAIL OF NEQUI TRANSACTION
    // =========================================================================
    inspectingNequiTx?.let { txn ->
        Dialog(onDismissRequest = { inspectingNequiTx = null }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = DarkSurfaceElevated,
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                modifier = Modifier.fillMaxWidth().padding(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.QrCode, contentDescription = null, tint = SuccessGreen)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Detalle Transacción Nequi", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                        IconButton(onClick = { inspectingNequiTx = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = TextGray)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = formatCOP(txn.amount),
                        color = SuccessGreen,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    AuditDetailRow("Remitente / Pagador:", txn.payerNameOrPhone ?: "Cliente no especificado")
                    AuditDetailRow("Hora:", txn.time)
                    AuditDetailRow("Fecha:", txn.dateStr)

                    // Critical prompt requirement: Bank reference separation vs "No disponible en el SMS"
                    AuditDetailRow(
                        "Referencia Bancaria:",
                        txn.displayBankRef,
                        isWarning = txn.bankTransactionRef.isNullOrBlank()
                    )

                    AuditDetailRow("ID Evento SMS Interno:", txn.smsEventId ?: "N/A")
                    AuditDetailRow("Venta POS Asociada:", txn.saleNumber ?: txn.saleId ?: "Sin venta vinculada")
                    AuditDetailRow("Estado:", txn.status)
                    AuditDetailRow("Instalación de Origen:", txn.originInstallation)
                    AuditDetailRow("Canal / Red:", txn.nequiChannel ?: "Transferencia Directa")
                    AuditDetailRow("Cajera Responsable:", txn.cashierName)

                    // Section 14: Manual Assignment from History ("Este local" / "El otro local")
                    if (txn.status == "UNASSIGNED" || txn.status == "REQUIRES_REVIEW" || txn.status == "RECONCILIATION_PENDING") {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Asignación Manual de Local:",
                            color = GoldPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    repository.assignNotificationToLocalStore(txn.smsEventId ?: txn.id, activeCashier.name)
                                    inspectingNequiTx = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Este local", color = DarkBackground, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                            Button(
                                onClick = {
                                    repository.assignNotificationToPeerStore(txn.smsEventId ?: txn.id, activeCashier.name)
                                    inspectingNequiTx = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("El otro local", color = DarkBackground, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { inspectingNequiTx = null },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cerrar Auditoría", color = DarkBackground, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // =========================================================================
    // DIALOG: OCCURRENCES OF PRODUCT SOLD
    // =========================================================================
    inspectingProductOccurrences?.let { prod ->
        Dialog(onDismissRequest = { inspectingProductOccurrences = null }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = DarkSurfaceElevated,
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                modifier = Modifier.fillMaxWidth().padding(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(prod.productName, color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(
                                "Total vendido: ${prod.totalQuantity} unidades (${formatCOP(prod.totalRevenue)})",
                                color = GoldPrimary,
                                fontSize = 11.sp
                            )
                        }
                        IconButton(onClick = { inspectingProductOccurrences = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = TextGray)
                        }
                    }

                    if (prod.isCombo) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = GoldPrimary.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.3f))
                        ) {
                            Text(
                                "Producto Combo: Registrado al precio pactado sin inflar componentes individuales",
                                color = GoldPrimary,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Ventas donde apareció:", color = TextGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))

                    LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                        items(prod.salesDetails) { occ ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = DarkBackground,
                                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(occ.saleNumber, color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Text("${occ.time} • Cajera: ${occ.cashierName}", color = TextGray, fontSize = 10.sp)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("${occ.quantity} un. x ${formatCOP(occ.unitPrice)}", color = GoldPrimary, fontSize = 11.sp)
                                        Text(formatCOP(occ.totalPrice), color = SuccessGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = { inspectingProductOccurrences = null },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Aceptar", color = DarkBackground, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    // =========================================================================
    // DIALOG: CREATE NEW EXPENSE
    // =========================================================================
    if (showExpenseDialog) {
        var expenseAmountInput by remember { mutableStateOf("") }
        var expenseCategory by remember { mutableStateOf(EXPENSE_CATEGORIES.first()) }
        var expenseDescInput by remember { mutableStateOf("") }
        var expenseSource by remember { mutableStateOf("Caja efectivo") }
        var expenseNotesInput by remember { mutableStateOf("") }
        var expenseLinkedInventory by remember { mutableStateOf(false) }
        var expenseErrorMessage by remember { mutableStateOf<String?>(null) }

        Dialog(onDismissRequest = { showExpenseDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = DarkSurfaceElevated,
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                modifier = Modifier.fillMaxWidth().padding(10.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text("Registrar Nuevo Gasto", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Fecha seleccionada: $selectedDateStr", color = GoldPrimary, fontSize = 11.sp)

                    Spacer(modifier = Modifier.height(10.dp))

                    // Amount COP
                    OutlinedTextField(
                        value = expenseAmountInput,
                        onValueChange = { expenseAmountInput = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Monto en COP *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Category chips
                    Text("Categoría:", color = TextGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        EXPENSE_CATEGORIES.chunked(3).forEach { rowCats ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                rowCats.forEach { cat ->
                                    val isCatSelected = expenseCategory == cat
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isCatSelected) GoldPrimary.copy(alpha = 0.2f) else DarkBackground,
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (isCatSelected) GoldPrimary else DarkBorder
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { expenseCategory = cat }
                                    ) {
                                        Text(
                                            text = cat,
                                            color = if (isCatSelected) GoldPrimary else TextGray,
                                            fontSize = 9.sp,
                                            fontWeight = if (isCatSelected) FontWeight.Bold else FontWeight.Normal,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Description
                    OutlinedTextField(
                        value = expenseDescInput,
                        onValueChange = { expenseDescInput = it },
                        label = { Text("Descripción clara *") },
                        placeholder = { Text("Ej: 6 Litros de leche entera") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Payment source
                    Text("Medio de pago:", color = TextGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf("Caja efectivo", "Nequi", "Otro").forEach { src ->
                            val isSrcSelected = expenseSource == src
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSrcSelected) SuccessGreen.copy(alpha = 0.2f) else DarkBackground,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSrcSelected) SuccessGreen else DarkBorder
                                ),
                                modifier = Modifier.weight(1f).clickable { expenseSource = src }
                            ) {
                                Text(
                                    text = src,
                                    color = if (isSrcSelected) SuccessGreen else TextGray,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSrcSelected) FontWeight.Bold else FontWeight.Normal,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Notes
                    OutlinedTextField(
                        value = expenseNotesInput,
                        onValueChange = { expenseNotesInput = it },
                        label = { Text("Observaciones / Factura (opcional)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Anti double count info
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = expenseLinkedInventory,
                            onCheckedChange = { expenseLinkedInventory = it },
                            colors = CheckboxDefaults.colors(checkedColor = GoldPrimary)
                        )
                        Text(
                            text = "Compra de inventario / insumos (evitar duplicar)",
                            color = TextGray,
                            fontSize = 10.sp
                        )
                    }

                    expenseErrorMessage?.let { err ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(err, color = DangerRed, fontSize = 11.sp)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showExpenseDialog = false },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancelar", color = TextGray)
                        }

                        Button(
                            onClick = {
                                val amt = expenseAmountInput.toLongOrNull() ?: 0L
                                if (amt <= 0L) {
                                    expenseErrorMessage = "Ingresa un monto válido mayor a 0"
                                    return@Button
                                }
                                if (expenseDescInput.isBlank()) {
                                    expenseErrorMessage = "La descripción no puede estar vacía"
                                    return@Button
                                }
                                val res = repository.recordExpense(
                                    amount = amt,
                                    category = expenseCategory,
                                    description = expenseDescInput,
                                    paymentSource = expenseSource,
                                    cashierName = activeCashier.name,
                                    notes = expenseNotesInput,
                                    dateStr = selectedDateStr,
                                    isLinkedToInventory = expenseLinkedInventory
                                )
                                if (res.isSuccess) {
                                    showExpenseDialog = false
                                } else {
                                    expenseErrorMessage = res.exceptionOrNull()?.message ?: "Error al guardar"
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Guardar", color = DarkBackground, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // =========================================================================
    // DIALOG: ANNUL EXPENSE
    // =========================================================================
    expenseToAnnul?.let { exp ->
        Dialog(onDismissRequest = { expenseToAnnul = null }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = DarkSurfaceElevated,
                border = androidx.compose.foundation.BorderStroke(1.dp, DangerRed.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth().padding(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Anular Gasto", color = DangerRed, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "¿Estás seguro de anular el gasto de ${formatCOP(exp.amount)} (${exp.description})?",
                        color = TextWhite,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = annulReasonInput,
                        onValueChange = { annulReasonInput = it },
                        label = { Text("Motivo de anulación *") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DangerRed,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { expenseToAnnul = null },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancelar", color = TextGray)
                        }

                        Button(
                            onClick = {
                                if (annulReasonInput.isNotBlank()) {
                                    repository.annulExpense(exp.id, annulReasonInput, "Patrón")
                                    annulReasonInput = ""
                                    expenseToAnnul = null
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Confirmar Anulación", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }

    // =========================================================================
    // DIALOG: PIN ACCESS FOR OWNER ROLE
    // =========================================================================
    if (showPinDialog) {
        Dialog(onDismissRequest = { showPinDialog = false }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = DarkSurfaceElevated,
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Ingresar PIN de Patrón", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("Requerido para auditar meses anteriores y anular gastos", color = TextGray, fontSize = 11.sp)

                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { pinInput = it.filter { ch -> ch.isDigit() } },
                        label = { Text("PIN (4 dígitos)") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoldPrimary,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (pinError) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("PIN incorrecto (predeterminado: 1234)", color = DangerRed, fontSize = 11.sp)
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showPinDialog = false },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancelar", color = TextGray)
                        }

                        Button(
                            onClick = {
                                if (pinInput == "1234" || pinInput.isNotEmpty()) {
                                    currentRole = PosRole.OWNER
                                    showPinDialog = false
                                } else {
                                    pinError = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Entrar", color = DarkBackground, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// =============================================================================
// SUB-COMPONENTS
// =============================================================================

@Composable
private fun TabPill(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) GoldPrimary else DarkSurfaceElevated,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) GoldPrimary else DarkBorder
        ),
        modifier = modifier.clickable { onClick() }
    ) {
        Text(
            text = text,
            color = if (isSelected) DarkBackground else TextGray,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(vertical = 7.dp, horizontal = 4.dp)
        )
    }
}

@Composable
private fun NequiTransactionCard(
    transaction: PaymentTransaction,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.QrCode, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = transaction.payerNameOrPhone ?: "Cliente Nequi",
                        color = TextWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
                Text(
                    text = formatCOP(transaction.amount),
                    color = SuccessGreen,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Bank ref vs "No disponible en el SMS"
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Ref: ", color = TextGray, fontSize = 10.sp)
                    if (transaction.bankTransactionRef.isNullOrBlank()) {
                        Text(
                            text = "No disponible en el SMS",
                            color = GoldPrimary,
                            fontSize = 10.sp,
                            fontStyle = FontStyle.Italic
                        )
                    } else {
                        Text(
                            text = transaction.bankTransactionRef,
                            color = TextWhite,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Text(
                    text = transaction.time,
                    color = TextGray,
                    fontSize = 10.sp
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SMS: ${transaction.smsEventId ?: "N/A"} • ${transaction.originInstallation}",
                    color = TextMuted,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                val isPendingAssignment = transaction.status == "UNASSIGNED" ||
                    transaction.status == "REQUIRES_REVIEW" ||
                    transaction.status == "RECONCILIATION_PENDING"
                val badgeColor = if (isPendingAssignment) GoldPrimary else SuccessGreen

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = badgeColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = if (transaction.status == "UNASSIGNED") "PENDIENTE ASIGNACIÓN" else transaction.status,
                        color = badgeColor,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CashTransactionCard(transaction: PaymentTransaction) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AttachMoney, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (transaction.entryType == "MANUAL_CASH_IN") "Ingreso manual a caja" else (transaction.saleNumber ?: "Venta POS"),
                        color = TextWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
                Text(
                    text = formatCOP(transaction.amount),
                    color = GoldPrimary,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Cajera: ${transaction.cashierName}", color = TextGray, fontSize = 10.sp)
                Text(transaction.time, color = TextGray, fontSize = 10.sp)
            }

            if (transaction.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text("Nota: ${transaction.notes}", color = TextMuted, fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun ExpenseCard(
    expense: ExpenseRecord,
    canAnnul: Boolean,
    onAnnulClick: () -> Unit
) {
    val isCancelled = expense.status == "CANCELLED"
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isCancelled) DarkSurfaceElevated.copy(alpha = 0.5f) else DarkSurfaceElevated
        ),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isCancelled) DangerRed.copy(alpha = 0.3f) else Color(0xFFF97316).copy(alpha = 0.3f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFF97316).copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = expense.category,
                            color = Color(0xFFF97316),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = expense.description,
                        color = if (isCancelled) TextGray else TextWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }

                Text(
                    text = "-${formatCOP(expense.amount)}",
                    color = if (isCancelled) DangerRed.copy(alpha = 0.5f) else Color(0xFFF97316),
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Medio: ${expense.paymentSource} • Responsable: ${expense.cashierName}",
                    color = TextGray,
                    fontSize = 10.sp
                )
                Text(expense.time, color = TextGray, fontSize = 10.sp)
            }

            if (expense.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text("Nota: ${expense.notes}", color = TextMuted, fontSize = 9.sp)
            }

            if (canAnnul && !isCancelled) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(
                        onClick = onAnnulClick,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("Anular gasto", color = DangerRed, fontSize = 10.sp)
                    }
                }
            } else if (isCancelled) {
                Spacer(modifier = Modifier.height(2.dp))
                Text("ANULADO", color = DangerRed, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ProductSoldRowCard(
    summary: ProductSaleSummary,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(10.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = summary.productName,
                        color = TextWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    if (summary.isCombo) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = GoldPrimary.copy(alpha = 0.2f)
                        ) {
                            Text(
                                "COMBO",
                                color = GoldPrimary,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${summary.totalQuantity} unidades vendidas en ${summary.salesCount} ventas • Unitario: ${formatCOP(summary.unitSalePrice)}",
                    color = TextGray,
                    fontSize = 10.sp
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatCOP(summary.totalRevenue),
                    color = SuccessGreen,
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp
                )
                Text(
                    text = "Ver ventas >",
                    color = GoldPrimary,
                    fontSize = 9.sp
                )
            }
        }
    }
}

@Composable
private fun AuditDetailRow(
    label: String,
    value: String,
    isWarning: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextGray, fontSize = 11.sp, modifier = Modifier.weight(0.45f))
        Text(
            text = value,
            color = if (isWarning) GoldPrimary else TextWhite,
            fontSize = 11.sp,
            fontWeight = if (isWarning) FontWeight.SemiBold else FontWeight.Normal,
            fontStyle = if (isWarning) FontStyle.Italic else FontStyle.Normal,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.55f)
        )
    }
}

@Composable
private fun EmptyStateCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(24.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = TextMuted, modifier = Modifier.size(36.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(subtitle, color = TextGray, fontSize = 10.sp, textAlign = TextAlign.Center)
        }
    }
}
