package com.ambiorix.pay.pos.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ambiorix.pay.data.AppSettings
import com.ambiorix.pay.data.ImageStore
import com.ambiorix.pay.pos.*
import com.ambiorix.pay.sound.SoundAnnouncer
import com.ambiorix.pay.ui.theme.*
import java.io.File
import java.text.NumberFormat
import java.util.Locale

fun formatCOP(amount: Long): String {
    val format = NumberFormat.getCurrencyInstance(Locale("es", "CO"))
    format.maximumFractionDigits = 0
    return format.format(amount)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PosMainScreen(
    settings: AppSettings,
    posRepository: PosRepository,
    soundAnnouncer: SoundAnnouncer,
    onOpenSettingsClick: () -> Unit,
    onPaymentSuccess: (amount: Long, sender: String?, channel: String?) -> Unit
) {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf("pos") } // pos, cash, stock, qr, settings
    var currentRole by remember { mutableStateOf(PosRole.CASHIER) }
    var showPinDialog by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }

    // Catalog & Cart State
    val products = remember { PosRepository.defaultProducts() }
    val categories = remember { PosRepository.defaultCategories() }
    val toppingsList = remember { PosRepository.defaultToppings() }

    var selectedCategoryId by remember { mutableStateOf("todos") }
    var searchQuery by remember { mutableStateOf("") }

    val cart = remember { mutableStateListOf<CartItem>() }
    var discountPercent by remember { mutableIntStateOf(0) }

    // Selection Modal State
    var selectedProductForModal by remember { mutableStateOf<Product?>(null) }
    var modalVariantId by remember { mutableStateOf("") }
    val modalSelectedToppings = remember { mutableStateListOf<String>() }

    // Checkout Modal State
    var showCheckoutModal by remember { mutableStateOf(false) }
    var checkoutMethod by remember { mutableStateOf("NEQUI") }
    var cashReceivedInput by remember { mutableStateOf("") }

    // Cash shift states
    val cashShift by posRepository.cashShift.collectAsState()
    val ingredients by posRepository.ingredients.collectAsState()
    val protocolState by posRepository.protocolState.collectAsState()
    val smsLogs by posRepository.smsLogs.collectAsState()

    var showExpenseDialog by remember { mutableStateOf(false) }
    var expenseConcept by remember { mutableStateOf("") }
    var expenseAmount by remember { mutableStateOf("") }

    var showBlindCloseDialog by remember { mutableStateOf(false) }
    var countedCashInput by remember { mutableStateOf("") }
    var blindCloseReport by remember { mutableStateOf<Pair<Long, Long>?>(null) } // Expected to Counted

    // Calculations
    val cartSubtotal = cart.sumOf { it.totalPrice }
    val cartDiscount = (cartSubtotal * (discountPercent.toDouble() / 100.0)).toLong()
    val cartTotal = maxOf(0L, cartSubtotal - cartDiscount)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "AMBIORIX POS",
                            color = TextWhite,
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = GoldPrimary.copy(alpha = 0.2f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "HELADERÍA",
                                color = GoldPrimary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                actions = {
                    // Role Switcher Button
                    Button(
                        onClick = {
                            if (currentRole == PosRole.CASHIER) {
                                pinInput = ""
                                pinError = false
                                showPinDialog = true
                            } else {
                                currentRole = PosRole.CASHIER
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (currentRole == PosRole.OWNER) GoldPrimary.copy(alpha = 0.2f) else DarkSurfaceElevated
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = if (currentRole == PosRole.OWNER) Icons.Default.Shield else Icons.Default.Lock,
                            contentDescription = null,
                            tint = if (currentRole == PosRole.OWNER) GoldPrimary else TextGray,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (currentRole == PosRole.OWNER) "Patrón" else "Cajera",
                            color = if (currentRole == PosRole.OWNER) GoldPrimary else TextWhite,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Voice test button
                    IconButton(
                        onClick = {
                            soundAnnouncer.speakTest(15000L, settings.includeThankYou)
                        }
                    ) {
                        Icon(Icons.Default.VolumeUp, contentDescription = null, tint = GoldPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF070B16),
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = currentTab == "pos",
                    onClick = { currentTab = "pos" },
                    icon = { Icon(Icons.Default.ShoppingBag, contentDescription = "Venta") },
                    label = { Text("Venta", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = DarkBackground,
                        selectedTextColor = GoldPrimary,
                        indicatorColor = GoldPrimary,
                        unselectedIconColor = TextGray,
                        unselectedTextColor = TextGray
                    )
                )

                NavigationBarItem(
                    selected = currentTab == "cash",
                    onClick = { currentTab = "cash" },
                    icon = { Icon(Icons.Default.AttachMoney, contentDescription = "Caja") },
                    label = { Text("Caja", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = DarkBackground,
                        selectedTextColor = GoldPrimary,
                        indicatorColor = GoldPrimary,
                        unselectedIconColor = TextGray,
                        unselectedTextColor = TextGray
                    )
                )

                NavigationBarItem(
                    selected = currentTab == "stock",
                    onClick = { currentTab = "stock" },
                    icon = { Icon(Icons.Default.Inventory2, contentDescription = "Stock") },
                    label = { Text("Stock", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = DarkBackground,
                        selectedTextColor = GoldPrimary,
                        indicatorColor = GoldPrimary,
                        unselectedIconColor = TextGray,
                        unselectedTextColor = TextGray
                    )
                )

                NavigationBarItem(
                    selected = currentTab == "qr",
                    onClick = { currentTab = "qr" },
                    icon = { Icon(Icons.Default.QrCode, contentDescription = "QR") },
                    label = { Text("QR", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = DarkBackground,
                        selectedTextColor = GoldPrimary,
                        indicatorColor = GoldPrimary,
                        unselectedIconColor = TextGray,
                        unselectedTextColor = TextGray
                    )
                )

                NavigationBarItem(
                    selected = currentTab == "settings",
                    onClick = onOpenSettingsClick,
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Ajustes") },
                    label = { Text("Ajustes", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = DarkBackground,
                        selectedTextColor = GoldPrimary,
                        indicatorColor = GoldPrimary,
                        unselectedIconColor = TextGray,
                        unselectedTextColor = TextGray
                    )
                )
            }
        },
        containerColor = DarkBackground
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (currentTab) {
                // ==========================================
                // 1. POS / VENTA SCREEN
                // ==========================================
                "pos" -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        // Category Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            categories.forEach { (catId, catName) ->
                                val isSelected = selectedCategoryId == catId
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) GoldPrimary else DarkSurfaceElevated,
                                    modifier = Modifier
                                        .clickable { selectedCategoryId = catId }
                                ) {
                                    Text(
                                        text = catName,
                                        color = if (isSelected) DarkBackground else TextWhite,
                                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.SemiBold,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        // Product Grid (2 columns)
                        val filteredProducts = remember(selectedCategoryId, searchQuery) {
                            products.filter { p ->
                                (selectedCategoryId == "todos" || p.categoryId == selectedCategoryId) &&
                                (searchQuery.isEmpty() || p.name.contains(searchQuery, ignoreCase = true))
                            }
                        }

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredProducts) { prod ->
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = DarkSurfaceElevated,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedProductForModal = prod
                                            modalVariantId = prod.variants.firstOrNull()?.id ?: ""
                                            modalSelectedToppings.clear()
                                        }
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(prod.emoji, fontSize = 24.sp)
                                            if (prod.badge != null) {
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = GoldPrimary.copy(alpha = 0.2f)
                                                ) {
                                                    Text(
                                                        prod.badge,
                                                        color = GoldPrimary,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = prod.name,
                                            color = TextWhite,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = prod.description,
                                            color = TextGray,
                                            fontSize = 10.sp,
                                            maxLines = 1
                                        )

                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = formatCOP(prod.basePrice),
                                                color = GoldPrimary,
                                                fontWeight = FontWeight.Black,
                                                fontSize = 12.sp
                                            )
                                            Surface(
                                                shape = CircleShape,
                                                color = GoldPrimary.copy(alpha = 0.2f),
                                                modifier = Modifier.size(20.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        Icons.Default.Add,
                                                        contentDescription = null,
                                                        tint = GoldPrimary,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Floating Cart Bar (if cart has items)
                        if (cart.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xFF0F172A),
                                border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "${cart.sumOf { it.quantity }} productos en orden",
                                            color = TextGray,
                                            fontSize = 10.sp
                                        )
                                        Text(
                                            text = formatCOP(cartTotal),
                                            color = GoldPrimary,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 16.sp
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        IconButton(
                                            onClick = { cart.clear() }
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Vaciar", tint = TextGray)
                                        }

                                        Button(
                                            onClick = {
                                                showCheckoutModal = true
                                                checkoutMethod = "NEQUI"
                                                cashReceivedInput = cartTotal.toString()
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text(
                                                text = "Cobrar",
                                                color = DarkBackground,
                                                fontWeight = FontWeight.Black,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ==========================================
                // 2. CAJA & ARQUEO CIEGO
                // ==========================================
                "cash" -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = DarkSurfaceElevated,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("TURNO ACTIVO", color = GoldPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        Text(cashShift.cashierName, color = TextWhite, fontSize = 15.sp, fontWeight = FontWeight.Black)
                                        Text("Abierto hoy a las ${cashShift.openedAt}", color = TextGray, fontSize = 11.sp)
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = SuccessGreen.copy(alpha = 0.2f)
                                    ) {
                                        Text("Abierta", color = SuccessGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("Fondo Base", color = TextGray, fontSize = 10.sp)
                                        Text(formatCOP(cashShift.initialCash), color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                    Column {
                                        Text("Ventas Efectivo", color = TextGray, fontSize = 10.sp)
                                        Text("+${formatCOP(cashShift.cashIn)}", color = SuccessGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                    Column {
                                        Text("Gastos Turno", color = TextGray, fontSize = 10.sp)
                                        Text("-${formatCOP(cashShift.expenses.sumOf { it.amount })}", color = DangerRed, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }
                            }
                        }

                        // Buttons for cash actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    expenseConcept = ""
                                    expenseAmount = ""
                                    showExpenseDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Gasto Menor", color = TextWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    countedCashInput = ""
                                    blindCloseReport = null
                                    showBlindCloseDialog = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = TextWhite, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Arqueo Ciego", color = TextWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Expense list
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = DarkSurfaceElevated,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("Gastos Registrados en Turno", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                if (cashShift.expenses.isEmpty()) {
                                    Text("Sin gastos registrados", color = TextGray, fontSize = 11.sp)
                                } else {
                                    cashShift.expenses.forEach { exp ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(exp.concept, color = TextWhite, fontSize = 12.sp)
                                            Text("-${formatCOP(exp.amount)}", color = DangerRed, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // ==========================================
                // 3. STOCK & INVENTARIO
                // ==========================================
                "stock" -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Inventario de Ingredientes", color = TextWhite, fontWeight = FontWeight.Black, fontSize = 14.sp)
                            IconButton(onClick = { posRepository.resetIngredients() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Restaurar", tint = GoldPrimary)
                            }
                        }

                        ingredients.forEach { ing ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = DarkSurfaceElevated,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(ing.name, color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Text("Lote: ${ing.batchCode} • Vence: ${ing.expiryDate}", color = TextGray, fontSize = 10.sp)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "${ing.currentStock.toInt()} ${ing.unit}",
                                            color = if (ing.currentStock <= ing.minStockAlert) DangerRed else GoldPrimary,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            text = if (ing.currentStock <= ing.minStockAlert) "Stock Bajo" else "Óptimo",
                                            color = if (ing.currentStock <= ing.minStockAlert) DangerRed else SuccessGreen,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // ==========================================
                // 4. QR KIOSCO ORIGINAL
                // ==========================================
                "qr" -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(settings.businessName, color = TextWhite, fontWeight = FontWeight.Black, fontSize = 18.sp)
                        Text("ESCANEA PARA PAGAR CON NEQUI", color = GoldPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(settings.phoneText, color = TextGray, fontSize = 12.sp)

                        Spacer(modifier = Modifier.height(16.dp))

                        // Clean QR container
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = Color.Black,
                            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                            modifier = Modifier.size(240.dp)
                        ) {
                            if (settings.qr1Path != null && File(settings.qr1Path).exists()) {
                                AsyncImage(
                                    model = File(settings.qr1Path),
                                    contentDescription = "QR Comercio",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(8.dp)
                                )
                            } else {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.QrCode, contentDescription = null, tint = GoldPrimary.copy(alpha = 0.5f), modifier = Modifier.size(90.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal: Product Variant & Toppings Selector
    selectedProductForModal?.let { product ->
        AlertDialog(
            onDismissRequest = { selectedProductForModal = null },
            confirmButton = {
                Button(
                    onClick = {
                        val variant = product.variants.find { it.id == modalVariantId } ?: product.variants.first()
                        val selectedToppings = toppingsList.filter { modalSelectedToppings.contains(it.id) }
                        val unitPrice = product.basePrice + variant.priceDelta + selectedToppings.sumOf { it.price }
                        val estimatedCost = product.baseCost + (selectedToppings.size * 500L)

                        val cartItem = CartItem(
                            cartItemId = "ci-${System.currentTimeMillis()}",
                            productId = product.id,
                            productName = product.name,
                            variantId = variant.id,
                            variantName = variant.name,
                            basePrice = product.basePrice,
                            variantPriceDelta = variant.priceDelta,
                            unitPrice = unitPrice,
                            quantity = 1,
                            toppings = selectedToppings,
                            totalPrice = unitPrice,
                            estimatedCost = estimatedCost
                        )
                        cart.add(cartItem)
                        selectedProductForModal = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
                ) {
                    Text("Agregar a la Orden", color = DarkBackground, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedProductForModal = null }) {
                    Text("Cancelar", color = TextGray)
                }
            },
            title = {
                Text("${product.emoji} ${product.name}", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Tamaño / Presentación:", color = GoldPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    product.variants.forEach { v ->
                        val isSel = modalVariantId == v.id
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSel) GoldPrimary.copy(alpha = 0.2f) else DarkSurfaceElevated,
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isSel) GoldPrimary else DarkBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { modalVariantId = v.id }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(v.name, color = TextWhite, fontSize = 12.sp)
                                Text(formatCOP(product.basePrice + v.priceDelta), color = GoldPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (product.allowedToppings) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Toppings Opcionales:", color = GoldPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        toppingsList.forEach { t ->
                            val isChecked = modalSelectedToppings.contains(t.id)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isChecked) modalSelectedToppings.remove(t.id)
                                        else modalSelectedToppings.add(t.id)
                                    },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { chk ->
                                        if (chk) modalSelectedToppings.add(t.id)
                                        else modalSelectedToppings.remove(t.id)
                                    },
                                    colors = CheckboxDefaults.colors(checkedColor = GoldPrimary)
                                )
                                Text(t.name, color = TextWhite, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                Text("+${formatCOP(t.price)}", color = GoldPrimary, fontSize = 11.sp)
                            }
                        }
                    }
                }
            },
            containerColor = DarkSurfaceElevated
        )
    }

    // Modal: Checkout Payment
    if (showCheckoutModal) {
        AlertDialog(
            onDismissRequest = { showCheckoutModal = false },
            confirmButton = {
                Button(
                    onClick = {
                        if (checkoutMethod == "CASH") {
                            posRepository.completeSale(
                                items = cart.toList(),
                                subtotal = cartSubtotal,
                                discount = cartDiscount,
                                total = cartTotal,
                                paymentMethod = "CASH",
                                cashierName = cashShift.cashierName
                            )
                            onPaymentSuccess(cartTotal, "Venta en Efectivo", "Caja Heladería")
                            cart.clear()
                            showCheckoutModal = false
                        } else {
                            // Start Nequi SMS coordination
                            posRepository.sendProtocolCommand("RESERVE", cartTotal)
                            // Simulate quick confirmation for user testing
                            posRepository.completeSale(
                                items = cart.toList(),
                                subtotal = cartSubtotal,
                                discount = cartDiscount,
                                total = cartTotal,
                                paymentMethod = "NEQUI",
                                cashierName = cashShift.cashierName,
                                senderName = "SANTIAGO ALEJANDRO MORGAN AVILA",
                                channel = "Bre-B"
                            )
                            onPaymentSuccess(cartTotal, "SANTIAGO ALEJANDRO MORGAN AVILA", "Nequi · Bre-B")
                            cart.clear()
                            showCheckoutModal = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
                ) {
                    Text("Confirmar Cobro", color = DarkBackground, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCheckoutModal = false }) {
                    Text("Cancelar", color = TextGray)
                }
            },
            title = {
                Text("Finalizar Cobro: ${formatCOP(cartTotal)}", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { checkoutMethod = "NEQUI" },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (checkoutMethod == "NEQUI") GoldPrimary else DarkBackground
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Nequi", color = if (checkoutMethod == "NEQUI") DarkBackground else TextWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { checkoutMethod = "CASH" },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (checkoutMethod == "CASH") GoldPrimary else DarkBackground
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Efectivo", color = if (checkoutMethod == "CASH") DarkBackground else TextWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (checkoutMethod == "NEQUI") {
                        Text(
                            text = if (settings.peerStoreEnabled && settings.peerStorePhone.isNotEmpty()) {
                                "Se enviará SMS de reserva a ${settings.peerStoreName} (${settings.peerStorePhone}) con 120s de exclusividad."
                            } else {
                                "Cobro directo Nequi en pantalla."
                            },
                            color = TextGray,
                            fontSize = 11.sp
                        )
                    } else {
                        OutlinedTextField(
                            value = cashReceivedInput,
                            onValueChange = { cashReceivedInput = it },
                            label = { Text("Efectivo Recibido (COP)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = GoldPrimary,
                                unfocusedTextColor = GoldPrimary,
                                focusedBorderColor = GoldPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            containerColor = DarkSurfaceElevated
        )
    }

    // PIN Verification Dialog for Owner Mode
    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            confirmButton = {
                Button(
                    onClick = {
                        if (pinInput == settings.pinCode) {
                            currentRole = PosRole.OWNER
                            showPinDialog = false
                        } else {
                            pinError = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
                ) {
                    Text("Ingresar", color = DarkBackground, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) {
                    Text("Cancelar", color = TextGray)
                }
            },
            title = { Text("PIN del Patrón", color = TextWhite, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Ingresa el PIN de seguridad para acceder a los costos e informes:", color = TextGray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { pinInput = it; pinError = false },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = GoldPrimary,
                            unfocusedTextColor = GoldPrimary,
                            focusedBorderColor = GoldPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (pinError) {
                        Text("PIN incorrecto", color = DangerRed, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            },
            containerColor = DarkSurfaceElevated
        )
    }

    // Dialog for Expense Recording
    if (showExpenseDialog) {
        AlertDialog(
            onDismissRequest = { showExpenseDialog = false },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = expenseAmount.toLongOrNull() ?: 0L
                        if (expenseConcept.isNotEmpty() && amt > 0) {
                            posRepository.addCashExpense(expenseConcept, amt)
                            showExpenseDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
                ) {
                    Text("Guardar", color = DarkBackground, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExpenseDialog = false }) {
                    Text("Cancelar", color = TextGray)
                }
            },
            title = { Text("Registrar Gasto de Caja", color = TextWhite, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = expenseConcept,
                        onValueChange = { expenseConcept = it },
                        label = { Text("Concepto (ej. Hielo)") },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextWhite, unfocusedTextColor = TextWhite)
                    )
                    OutlinedTextField(
                        value = expenseAmount,
                        onValueChange = { expenseAmount = it },
                        label = { Text("Monto en COP") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = GoldPrimary, unfocusedTextColor = GoldPrimary)
                    )
                }
            },
            containerColor = DarkSurfaceElevated
        )
    }

    // Dialog for Blind Close (Arqueo Ciego)
    if (showBlindCloseDialog) {
        AlertDialog(
            onDismissRequest = { showBlindCloseDialog = false },
            confirmButton = {
                if (blindCloseReport == null) {
                    Button(
                        onClick = {
                            val counted = countedCashInput.toLongOrNull() ?: 0L
                            val closed = posRepository.performBlindClose(counted)
                            blindCloseReport = Pair(closed.expectedCash ?: 0L, counted)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                    ) {
                        Text("Auditar Arqueo", color = TextWhite, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = { showBlindCloseDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
                    ) {
                        Text("Cerrar", color = DarkBackground, fontWeight = FontWeight.Bold)
                    }
                }
            },
            title = { Text("Cierre Ciego de Turno", color = TextWhite, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (blindCloseReport == null) {
                        Text("Ingresa el dinero físico contado en la gaveta:", color = TextGray, fontSize = 12.sp)
                        OutlinedTextField(
                            value = countedCashInput,
                            onValueChange = { countedCashInput = it },
                            label = { Text("Efectivo Contado (COP)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = GoldPrimary, unfocusedTextColor = GoldPrimary)
                        )
                    } else {
                        val (expected, counted) = blindCloseReport!!
                        val diff = counted - expected
                        Text("Esperado por sistema: ${formatCOP(expected)}", color = TextWhite, fontSize = 12.sp)
                        Text("Contado físicamente: ${formatCOP(counted)}", color = TextWhite, fontSize = 12.sp)
                        Text(
                            text = if (diff == 0L) "Caja Cuadrada Exacta" else if (diff > 0) "Sobrante de +${formatCOP(diff)}" else "Faltante de -${formatCOP(Math.abs(diff))}",
                            color = if (diff == 0L) SuccessGreen else if (diff > 0) GoldPrimary else DangerRed,
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp
                        )
                    }
                }
            },
            containerColor = DarkSurfaceElevated
        )
    }
}
