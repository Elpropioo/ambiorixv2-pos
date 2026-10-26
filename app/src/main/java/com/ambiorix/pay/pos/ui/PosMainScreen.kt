package com.ambiorix.pay.pos.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.ambiorix.pay.R
import com.ambiorix.pay.ui.screens.HistoryScreen
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

    // Repository Flows
    val products by posRepository.products.collectAsState()
    val categories by posRepository.categories.collectAsState()
    val activeCashier by posRepository.activeCashier.collectAsState()
    val cashiers by posRepository.cashiers.collectAsState()
    val inventoryMovements by posRepository.inventoryMovements.collectAsState()
    val ingredients by posRepository.ingredients.collectAsState()
    val cashShift by posRepository.cashShift.collectAsState()
    val toppingsList = remember { PosRepository.defaultToppings() }

    // Catalog & Cart State
    var selectedCategoryId by remember { mutableStateOf("todos") }
    var searchQuery by remember { mutableStateOf("") }
    val cart = remember { mutableStateListOf<CartItem>() }
    var discountPercent by remember { mutableIntStateOf(0) }

    // Modals & Dialogs State
    var showCashierDialog by remember { mutableStateOf(false) }
    var showProductAdminDialog by remember { mutableStateOf(false) }
    var showCategoryAdminDialog by remember { mutableStateOf(false) }
    var showStockAdminDialog by remember { mutableStateOf(false) }
    var showFinancialHistoryDialog by remember { mutableStateOf(false) }

    // Product Variant & Topping Selector
    var selectedProductForModal by remember { mutableStateOf<Product?>(null) }
    var modalVariantId by remember { mutableStateOf("") }
    val modalSelectedToppings = remember { mutableStateListOf<String>() }

    // Checkout Modal State
    var showCheckoutModal by remember { mutableStateOf(false) }
    var checkoutMethod by remember { mutableStateOf("NEQUI") }
    var cashReceivedInput by remember { mutableStateOf("") }

    // Cash shift states
    var showExpenseDialog by remember { mutableStateOf(false) }
    var expenseConcept by remember { mutableStateOf("") }
    var expenseAmount by remember { mutableStateOf("") }

    var showBlindCloseDialog by remember { mutableStateOf(false) }
    var countedCashInput by remember { mutableStateOf("") }
    var blindCloseReport by remember { mutableStateOf<Pair<Long, Long>?>(null) }

    // Calculations
    val cartSubtotal = cart.sumOf { it.totalPrice }
    val cartDiscount = (cartSubtotal * (discountPercent.toDouble() / 100.0)).toLong()
    val cartTotal = maxOf(0L, cartSubtotal - cartDiscount)

    val activeProducts = remember(products, selectedCategoryId, searchQuery) {
        val q = searchQuery.trim().lowercase(Locale.getDefault())
        products.filter { p ->
            p.isActive &&
            (selectedCategoryId == "todos" || p.categoryId == selectedCategoryId) &&
            (q.isEmpty() || p.name.lowercase(Locale.getDefault()).contains(q) || p.description.lowercase(Locale.getDefault()).contains(q))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.ambiorix_logo),
                            contentDescription = "Logo",
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "AMBIORIX PAY",
                            color = TextWhite,
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = GoldPrimary.copy(alpha = 0.2f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.4f))
                        ) {
                            Text(
                                text = "Salon de Onces",
                                color = GoldPrimary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                },
                actions = {
                    // Active Cashier Badge (Clickable to edit name / switch profile)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = DarkSurfaceElevated,
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                        modifier = Modifier
                            .clickable { showCashierDialog = true }
                            .padding(end = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = GoldPrimary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = activeCashier.name,
                                color = TextWhite,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

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

                    Spacer(modifier = Modifier.width(4.dp))

                    // Open Financial History Calendar Button (Sound test removed)
                    IconButton(
                        onClick = { showFinancialHistoryDialog = true }
                    ) {
                        Icon(Icons.Default.DateRange, contentDescription = "Historial Financiero", tint = GoldPrimary)
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
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        // Owner Quick Actions Toolbar (Products, Categories, Inventory)
                        if (currentRole == PosRole.OWNER) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Button(
                                    onClick = { showProductAdminDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Category, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Productos", color = TextWhite, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = { showCategoryAdminDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.List, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Categorías", color = TextWhite, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = { showStockAdminDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Tune, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Stock", color = TextWhite, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = { showFinancialHistoryDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.DateRange, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Historial", color = TextWhite, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Search & Filter
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Buscar producto...", fontSize = 12.sp, color = TextGray) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextGray, modifier = Modifier.size(16.dp)) },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Close, contentDescription = null, tint = TextGray, modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = GoldPrimary,
                                unfocusedBorderColor = DarkBorder,
                                focusedTextColor = TextWhite,
                                unfocusedTextColor = TextWhite
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Category Bar (Dynamic from PosRepository)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val allCategories = listOf(Category("todos", "Todos", 0, true, "🍦")) + categories
                            allCategories.forEach { cat ->
                                val isSelected = selectedCategoryId == cat.id
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) GoldPrimary else DarkSurfaceElevated,
                                    modifier = Modifier.clickable { selectedCategoryId = cat.id }
                                ) {
                                    Text(
                                        text = "${cat.emoji} ${cat.name}",
                                        color = if (isSelected) DarkBackground else TextWhite,
                                        fontWeight = if (isSelected) FontWeight.Black else FontWeight.SemiBold,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }

                        // Product Grid (2 columns)
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            items(activeProducts, key = { it.id }) { product ->
                                val isOutOfStock = product.stockQuantity <= 0.0
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = DarkSurfaceElevated,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isOutOfStock) DangerRed.copy(alpha = 0.5f) else DarkBorder),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedProductForModal = product
                                            modalVariantId = product.variants.firstOrNull()?.id ?: ""
                                            modalSelectedToppings.clear()
                                        }
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        // Product Image or Emoji Fallback
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(80.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(Color.Black.copy(alpha = 0.3f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (product.imagePath != null && File(product.imagePath).exists()) {
                                                AsyncImage(
                                                    model = File(product.imagePath),
                                                    contentDescription = product.name,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            } else {
                                                Text(product.emoji, fontSize = 38.sp)
                                            }

                                            // Stock Badge
                                            if (isOutOfStock) {
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = DangerRed,
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .padding(4.dp)
                                                ) {
                                                    Text("Agotado", color = TextWhite, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                                }
                                            } else if (product.stockQuantity <= product.minStockAlert) {
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = GoldPrimary,
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .padding(4.dp)
                                                ) {
                                                    Text("Quedan ${product.stockQuantity.toInt()}", color = DarkBackground, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = product.name,
                                            color = TextWhite,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = formatCOP(product.basePrice),
                                                color = GoldPrimary,
                                                fontWeight = FontWeight.Black,
                                                fontSize = 12.sp
                                            )
                                            Icon(
                                                imageVector = Icons.Default.AddCircle,
                                                contentDescription = "Agregar",
                                                tint = GoldPrimary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Compact Floating Cart Bar
                        if (cart.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = GoldPrimary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable { showCheckoutModal = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = CircleShape,
                                            color = DarkBackground,
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(cart.sumOf { it.quantity }.toString(), color = GoldPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Cobrar Orden", color = DarkBackground, fontWeight = FontWeight.Black, fontSize = 13.sp)
                                    }
                                    Text(formatCOP(cartTotal), color = DarkBackground, fontWeight = FontWeight.Black, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }

                // ==========================================
                // 2. CASH DRAWER & SHIFT
                // ==========================================
                "cash" -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = DarkSurfaceElevated,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("Turno de Caja Activo", color = TextGray, fontSize = 11.sp)
                                Text("Cajera: ${cashShift.cashierName}", color = TextWhite, fontWeight = FontWeight.Black, fontSize = 16.sp)
                                Text("Apertura: ${cashShift.openedAt}", color = GoldPrimary, fontSize = 11.sp)

                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("Base Inicial", color = TextGray, fontSize = 10.sp)
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
                            Text("Inventario de Productos", color = TextWhite, fontWeight = FontWeight.Black, fontSize = 14.sp)
                            Button(
                                onClick = { showStockAdminDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = null, tint = DarkBackground, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Ajustar Stock", color = DarkBackground, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        products.forEach { prod ->
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
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(prod.emoji, fontSize = 24.sp)
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(prod.name, color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            Text(
                                                text = "Precio: ${formatCOP(prod.basePrice)}" + if (currentRole == PosRole.OWNER) " • Costo: ${formatCOP(prod.baseCost)}" else "",
                                                color = TextGray,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "${prod.stockQuantity.toInt()} ${prod.unit}",
                                            color = if (prod.stockQuantity <= prod.minStockAlert) DangerRed else GoldPrimary,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            text = if (prod.stockQuantity <= 0.0) "Agotado" else if (prod.stockQuantity <= prod.minStockAlert) "Stock Bajo" else "Óptimo",
                                            color = if (prod.stockQuantity <= prod.minStockAlert) DangerRed else SuccessGreen,
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
                // 4. QR KIOSCO
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

    // =========================================================================
    // DIALOG: EDIT CASHIER NAME & SELECT PROFILE
    // =========================================================================
    if (showCashierDialog) {
        var cashierNameInput by remember { mutableStateOf(activeCashier.name) }
        var cashierError by remember { mutableStateOf<String?>(null) }
        var isCreatingNew by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showCashierDialog = false },
            confirmButton = {
                Button(
                    onClick = {
                        if (isCreatingNew) {
                            val res = posRepository.createCashier(cashierNameInput)
                            if (res.isSuccess) {
                                posRepository.selectCashier(res.getOrThrow().id)
                                showCashierDialog = false
                            } else {
                                cashierError = res.exceptionOrNull()?.message ?: "Error al crear cajera"
                            }
                        } else {
                            val res = posRepository.updateCashierName(activeCashier.id, cashierNameInput)
                            if (res.isSuccess) {
                                showCashierDialog = false
                            } else {
                                cashierError = res.exceptionOrNull()?.message ?: "Error al actualizar nombre"
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
                ) {
                    Text("Guardar", color = DarkBackground, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCashierDialog = false }) {
                    Text("Cancelar", color = TextGray)
                }
            },
            title = {
                Text(
                    text = if (isCreatingNew) "Agregar Nueva Cajera" else "Editar Nombre de Cajera",
                    color = TextWhite,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "El nombre de la cajera se mostrará en los recibos, ventas y arqueos de turno.",
                        color = TextGray,
                        fontSize = 11.sp
                    )

                    OutlinedTextField(
                        value = cashierNameInput,
                        onValueChange = {
                            cashierNameInput = it
                            cashierError = null
                        },
                        label = { Text("Nombre de la Cajera") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextWhite,
                            unfocusedTextColor = TextWhite,
                            focusedBorderColor = GoldPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (cashierError != null) {
                        Text(cashierError!!, color = DangerRed, fontSize = 11.sp)
                    }

                    if (!isCreatingNew) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Perfiles de Cajeras Guardados:", color = GoldPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        cashiers.forEach { profile ->
                            val isSel = profile.id == activeCashier.id
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSel) GoldPrimary.copy(alpha = 0.2f) else DarkSurfaceElevated,
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (isSel) GoldPrimary else DarkBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        posRepository.selectCashier(profile.id)
                                        cashierNameInput = profile.name
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(profile.name, color = TextWhite, fontSize = 12.sp)
                                    if (isSel) {
                                        Text("Activa", color = GoldPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        TextButton(
                            onClick = {
                                isCreatingNew = true
                                cashierNameInput = ""
                            }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("+ Agregar Otro Perfil", color = GoldPrimary, fontSize = 11.sp)
                        }
                    }
                }
            },
            containerColor = DarkSurfaceElevated
        )
    }

    // =========================================================================
    // DIALOG: PRODUCT MANAGEMENT (CRUD)
    // =========================================================================
    if (showProductAdminDialog) {
        var editingProduct by remember { mutableStateOf<Product?>(null) }
        var isNewProduct by remember { mutableStateOf(false) }

        var prodName by remember { mutableStateOf("") }
        var prodPrice by remember { mutableStateOf("") }
        var prodCost by remember { mutableStateOf("") }
        var prodCatId by remember { mutableStateOf(categories.firstOrNull()?.id ?: "helados") }
        var prodDesc by remember { mutableStateOf("") }
        var prodUnit by remember { mutableStateOf("unidades") }
        var prodStock by remember { mutableStateOf("10") }
        var prodMinAlert by remember { mutableStateOf("5") }
        var prodImagePath by remember { mutableStateOf<String?>(null) }
        var prodEmoji by remember { mutableStateOf("🍨") }
        var prodActive by remember { mutableStateOf(true) }
        var prodIsCombo by remember { mutableStateOf(false) }
        var prodAllowNegative by remember { mutableStateOf(false) }
        val prodRecipe = remember { mutableStateListOf<RecipeItem>() }
        val prodComboItems = remember { mutableStateListOf<ComboItem>() }
        var formError by remember { mutableStateOf<String?>(null) }

        // Ingredient picker helper state
        var selectedIngIdForRecipe by remember { mutableStateOf("") }
        var recipeQtyInput by remember { mutableStateOf("100") }
        var recipeUnitInput by remember { mutableStateOf("g") }

        // Combo item helper state
        var selectedChildProdId by remember { mutableStateOf("") }
        var comboQtyInput by remember { mutableStateOf("1") }

        val photoPickerLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            uri?.let {
                val saved = ImageStore.saveGalleryImage(context, it, "prod")
                if (saved != null) {
                    prodImagePath = saved
                }
            }
        }

        AlertDialog(
            onDismissRequest = {
                showProductAdminDialog = false
                editingProduct = null
                isNewProduct = false
            },
            confirmButton = {
                if (editingProduct != null || isNewProduct) {
                    Button(
                        onClick = {
                            val priceLong = prodPrice.toLongOrNull() ?: -1L
                            val costLong = prodCost.toLongOrNull() ?: 0L
                            val stockDbl = prodStock.toDoubleOrNull() ?: 0.0
                            val minAlertDbl = prodMinAlert.toDoubleOrNull() ?: 5.0

                            if (prodName.trim().isEmpty()) {
                                formError = "El nombre es obligatorio"
                                return@Button
                            }
                            if (priceLong < 0) {
                                formError = "El precio debe ser un número válido"
                                return@Button
                            }

                            if (isNewProduct) {
                                val res = posRepository.createProduct(
                                    name = prodName,
                                    basePrice = priceLong,
                                    categoryId = prodCatId,
                                    description = prodDesc,
                                    imagePath = prodImagePath,
                                    emoji = prodEmoji,
                                    stockQuantity = stockDbl,
                                    unit = prodUnit,
                                    baseCost = costLong,
                                    minStockAlert = minAlertDbl,
                                    isCombo = prodIsCombo,
                                    recipe = prodRecipe.toList(),
                                    comboItems = prodComboItems.toList(),
                                    allowNegativeStock = prodAllowNegative
                                )
                                if (res.isSuccess) {
                                    isNewProduct = false
                                    editingProduct = null
                                } else {
                                    formError = res.exceptionOrNull()?.message
                                }
                            } else {
                                val res = posRepository.updateProduct(
                                    id = editingProduct!!.id,
                                    name = prodName,
                                    basePrice = priceLong,
                                    categoryId = prodCatId,
                                    description = prodDesc,
                                    imagePath = prodImagePath,
                                    emoji = prodEmoji,
                                    unit = prodUnit,
                                    baseCost = costLong,
                                    minStockAlert = minAlertDbl,
                                    isActive = prodActive,
                                    isCombo = prodIsCombo,
                                    recipe = prodRecipe.toList(),
                                    comboItems = prodComboItems.toList(),
                                    allowNegativeStock = prodAllowNegative
                                )
                                if (res.isSuccess) {
                                    editingProduct = null
                                } else {
                                    formError = res.exceptionOrNull()?.message
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
                    ) {
                        Text("Guardar Producto", color = DarkBackground, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = { showProductAdminDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
                    ) {
                        Text("Listo", color = DarkBackground, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                if (editingProduct != null || isNewProduct) {
                    TextButton(onClick = {
                        editingProduct = null
                        isNewProduct = false
                    }) {
                        Text("Volver", color = TextGray)
                    }
                }
            },
            title = {
                Text(
                    text = if (isNewProduct) "Crear Nuevo Producto" else if (editingProduct != null) "Editar Producto" else "Administrar Productos",
                    color = TextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            },
            text = {
                if (editingProduct != null || isNewProduct) {
                    // Form View
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = prodName,
                            onValueChange = { prodName = it; formError = null },
                            label = { Text("Nombre del Producto") },
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextWhite, unfocusedTextColor = TextWhite, focusedBorderColor = GoldPrimary),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = prodPrice,
                                onValueChange = { prodPrice = it; formError = null },
                                label = { Text("Precio COP") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = GoldPrimary, unfocusedTextColor = GoldPrimary, focusedBorderColor = GoldPrimary),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = prodCost,
                                onValueChange = { prodCost = it },
                                label = { Text("Costo Base COP") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextWhite, unfocusedTextColor = TextWhite, focusedBorderColor = GoldPrimary),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Category Dropdown selector
                        Text("Categoría:", color = TextGray, fontSize = 11.sp)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            categories.forEach { cat ->
                                val isSel = prodCatId == cat.id
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSel) GoldPrimary else DarkSurfaceElevated,
                                    modifier = Modifier.clickable { prodCatId = cat.id }
                                ) {
                                    Text(
                                        text = "${cat.emoji} ${cat.name}",
                                        color = if (isSel) DarkBackground else TextWhite,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        // Photo Picker Section
                        Text("Fotografía del Producto:", color = TextGray, fontSize = 11.sp)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.Black,
                                modifier = Modifier.size(50.dp)
                            ) {
                                if (prodImagePath != null && File(prodImagePath!!).exists()) {
                                    AsyncImage(
                                        model = File(prodImagePath!!),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(prodEmoji, fontSize = 24.sp)
                                    }
                                }
                            }

                            Button(
                                onClick = { photoPickerLauncher.launch("image/*") },
                                colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = GoldPrimary, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Elegir de Galería", color = TextWhite, fontSize = 11.sp)
                            }

                            if (prodImagePath != null) {
                                TextButton(onClick = { prodImagePath = null }) {
                                    Text("Quitar", color = DangerRed, fontSize = 11.sp)
                                }
                            }
                        }

                        OutlinedTextField(
                            value = prodDesc,
                            onValueChange = { prodDesc = it },
                            label = { Text("Descripción (opcional)") },
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextWhite, unfocusedTextColor = TextWhite, focusedBorderColor = GoldPrimary),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = prodStock,
                                onValueChange = { prodStock = it },
                                label = { Text("Stock Actual") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextWhite, unfocusedTextColor = TextWhite, focusedBorderColor = GoldPrimary),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = prodUnit,
                                onValueChange = { prodUnit = it },
                                label = { Text("Unidad (ej. unidades)") },
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextWhite, unfocusedTextColor = TextWhite, focusedBorderColor = GoldPrimary),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Tipo de Producto: Estándar con receta vs Combo
                        Text("Tipo de Producto:", color = TextGray, fontSize = 11.sp)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (!prodIsCombo) GoldPrimary else DarkSurfaceElevated,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { prodIsCombo = false }
                            ) {
                                Text(
                                    "🍦 Individual / Receta",
                                    color = if (!prodIsCombo) DarkBackground else TextWhite,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (prodIsCombo) GoldPrimary else DarkSurfaceElevated,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { prodIsCombo = true }
                            ) {
                                Text(
                                    "🎁 Combo Compuesto",
                                    color = if (prodIsCombo) DarkBackground else TextWhite,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp)
                                )
                            }
                        }

                        if (prodIsCombo) {
                            // SECCIÓN: COMBO ITEMS
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = DarkBackground,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("Componentes del Combo (${prodComboItems.size})", color = GoldPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)

                                    if (prodComboItems.isEmpty()) {
                                        Text("No has añadido productos a este combo.", color = TextGray, fontSize = 10.sp)
                                    } else {
                                        prodComboItems.forEachIndexed { idx, ci ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("• ${ci.quantity}x ${ci.productName}", color = TextWhite, fontSize = 11.sp)
                                                IconButton(
                                                    onClick = { prodComboItems.removeAt(idx) },
                                                    modifier = Modifier.size(20.dp)
                                                ) {
                                                    Icon(Icons.Default.Close, contentDescription = "Quitar", tint = DangerRed, modifier = Modifier.size(14.dp))
                                                }
                                            }
                                        }
                                    }

                                    // Selector de producto para añadir
                                    val candidateProducts = products.filter { p ->
                                        p.id != editingProduct?.id && !p.isCombo
                                    }
                                    if (candidateProducts.isNotEmpty()) {
                                        Text("Añadir producto:", color = TextGray, fontSize = 10.sp)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            var selProd by remember { mutableStateOf(candidateProducts.first().id) }
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = DarkSurfaceElevated,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .horizontalScroll(rememberScrollState())
                                                        .padding(4.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    candidateProducts.forEach { cp ->
                                                        val isSel = selProd == cp.id
                                                        Surface(
                                                            shape = RoundedCornerShape(6.dp),
                                                            color = if (isSel) GoldPrimary else DarkBackground,
                                                            modifier = Modifier.clickable { selProd = cp.id }
                                                        ) {
                                                            Text(
                                                                cp.name,
                                                                color = if (isSel) DarkBackground else TextWhite,
                                                                fontSize = 9.sp,
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            Button(
                                                onClick = {
                                                    val cp = candidateProducts.find { it.id == selProd }
                                                    if (cp != null) {
                                                        prodComboItems.add(ComboItem(cp.id, cp.name, 1))
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                                                shape = RoundedCornerShape(6.dp),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text("+ Añadir", color = DarkBackground, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // SECCIÓN: RECETA E INGREDIENTES
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = DarkBackground,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    val recipeCost = posRepository.calculateRecipeCost(prodRecipe)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Receta e Insumos (${prodRecipe.size})", color = GoldPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        Text("Costo: ${formatCOP(recipeCost)}", color = TextGray, fontSize = 10.sp)
                                    }

                                    if (prodRecipe.isEmpty()) {
                                        Text("Sin receta definida. Se descontará directamente del stock del producto.", color = TextGray, fontSize = 10.sp)
                                    } else {
                                        prodRecipe.forEachIndexed { idx, item ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("• ${item.ingredientName}: ${item.quantity} ${item.unit}", color = TextWhite, fontSize = 11.sp)
                                                IconButton(
                                                    onClick = { prodRecipe.removeAt(idx) },
                                                    modifier = Modifier.size(20.dp)
                                                ) {
                                                    Icon(Icons.Default.Close, contentDescription = "Quitar", tint = DangerRed, modifier = Modifier.size(14.dp))
                                                }
                                            }
                                        }
                                    }

                                    // Selector de ingrediente a incorporar
                                    if (ingredients.isNotEmpty()) {
                                        val firstIng = ingredients.first()
                                        if (selectedIngIdForRecipe.isEmpty()) {
                                            selectedIngIdForRecipe = firstIng.id
                                            recipeUnitInput = firstIng.unit
                                        }

                                        Text("Añadir Insumo a la Receta:", color = TextGray, fontSize = 10.sp)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            ingredients.forEach { ing ->
                                                val isSel = selectedIngIdForRecipe == ing.id
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = if (isSel) GoldPrimary else DarkSurfaceElevated,
                                                    modifier = Modifier.clickable {
                                                        selectedIngIdForRecipe = ing.id
                                                        recipeUnitInput = ing.unit
                                                    }
                                                ) {
                                                    Text(
                                                        "${ing.name} (${ing.unit})",
                                                        color = if (isSel) DarkBackground else TextWhite,
                                                        fontSize = 9.sp,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            OutlinedTextField(
                                                value = recipeQtyInput,
                                                onValueChange = { recipeQtyInput = it },
                                                label = { Text("Cantidad") },
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextWhite, unfocusedTextColor = TextWhite),
                                                modifier = Modifier.weight(1f)
                                            )
                                            OutlinedTextField(
                                                value = recipeUnitInput,
                                                onValueChange = { recipeUnitInput = it },
                                                label = { Text("Unidad") },
                                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextWhite, unfocusedTextColor = TextWhite),
                                                modifier = Modifier.weight(1f)
                                            )
                                            Button(
                                                onClick = {
                                                    val ing = ingredients.find { it.id == selectedIngIdForRecipe }
                                                    val q = recipeQtyInput.toDoubleOrNull() ?: 0.0
                                                    if (ing != null && q > 0) {
                                                        prodRecipe.add(RecipeItem(ing.id, ing.name, q, recipeUnitInput.trim()))
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text("+ Agregar", color = DarkBackground, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Permitir inventario negativo
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Permitir existencias negativas", color = TextWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text("Permite vender aun sin stock registrado previo", color = TextGray, fontSize = 9.sp)
                            }
                            Switch(
                                checked = prodAllowNegative,
                                onCheckedChange = { prodAllowNegative = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = GoldPrimary)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Producto Activo en Ventas", color = TextWhite, fontSize = 12.sp)
                            Switch(
                                checked = prodActive,
                                onCheckedChange = { prodActive = it },
                                colors = SwitchDefaults.colors(checkedThumbColor = GoldPrimary)
                            )
                        }

                        if (formError != null) {
                            Text(formError!!, color = DangerRed, fontSize = 11.sp)
                        }
                    }
                } else {
                    // Products List View
                    Column(
                        modifier = Modifier
                            .heightIn(max = 420.dp)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                isNewProduct = true
                                prodName = ""
                                prodPrice = ""
                                prodCost = ""
                                prodCatId = categories.firstOrNull()?.id ?: "helados"
                                prodDesc = ""
                                prodStock = "10"
                                prodUnit = "unidades"
                                prodMinAlert = "5"
                                prodImagePath = null
                                prodEmoji = "🍨"
                                prodActive = true
                                prodIsCombo = false
                                prodAllowNegative = false
                                prodRecipe.clear()
                                prodComboItems.clear()
                                formError = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = DarkBackground, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("+ Nuevo Producto", color = DarkBackground, fontWeight = FontWeight.Bold)
                        }

                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(products, key = { it.id }) { p ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = DarkBackground,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (p.imagePath != null && File(p.imagePath).exists()) {
                                                AsyncImage(
                                                    model = File(p.imagePath),
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .size(34.dp)
                                                        .clip(RoundedCornerShape(6.dp))
                                                )
                                            } else {
                                                Text(p.emoji, fontSize = 22.sp)
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(p.name, color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                    if (p.isCombo) {
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Surface(
                                                            shape = RoundedCornerShape(4.dp),
                                                            color = GoldPrimary.copy(alpha = 0.2f)
                                                        ) {
                                                            Text("COMBO", color = GoldPrimary, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                                        }
                                                    } else if (p.recipe.isNotEmpty()) {
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Surface(
                                                            shape = RoundedCornerShape(4.dp),
                                                            color = SuccessGreen.copy(alpha = 0.2f)
                                                        ) {
                                                            Text("RECETA", color = SuccessGreen, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                                        }
                                                    }
                                                }
                                                Text("${formatCOP(p.basePrice)} • ${p.stockQuantity.toInt()} ${p.unit}", color = GoldPrimary, fontSize = 10.sp)
                                            }
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = {
                                                    editingProduct = p
                                                    isNewProduct = false
                                                    prodName = p.name
                                                    prodPrice = p.basePrice.toString()
                                                    prodCost = p.baseCost.toString()
                                                    prodCatId = p.categoryId
                                                    prodDesc = p.description
                                                    prodStock = p.stockQuantity.toString()
                                                    prodUnit = p.unit
                                                    prodMinAlert = p.minStockAlert.toString()
                                                    prodImagePath = p.imagePath
                                                    prodEmoji = p.emoji
                                                    prodActive = p.isActive
                                                    prodIsCombo = p.isCombo
                                                    prodAllowNegative = p.allowNegativeStock
                                                    prodRecipe.clear()
                                                    prodRecipe.addAll(p.recipe)
                                                    prodComboItems.clear()
                                                    prodComboItems.addAll(p.comboItems)
                                                    formError = null
                                                }
                                            ) {
                                                Icon(Icons.Default.Edit, contentDescription = "Editar", tint = GoldPrimary, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            containerColor = DarkSurfaceElevated
        )
    }

    // =========================================================================
    // DIALOG: CATEGORY MANAGEMENT (SAFE DELETE & REASSIGN)
    // =========================================================================
    if (showCategoryAdminDialog) {
        var newCatName by remember { mutableStateOf("") }
        var newCatEmoji by remember { mutableStateOf("🍦") }
        var catToDelete by remember { mutableStateOf<Category?>(null) }
        var reassignTargetId by remember { mutableStateOf("") }
        var catError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = {
                showCategoryAdminDialog = false
                catToDelete = null
            },
            confirmButton = {
                if (catToDelete != null) {
                    Button(
                        onClick = {
                            val res = posRepository.deleteCategory(catToDelete!!.id, reassignTargetId)
                            if (res.isSuccess) {
                                catToDelete = null
                            } else {
                                catError = res.exceptionOrNull()?.message
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
                    ) {
                        Text("Confirmar Reasignación y Eliminar", color = TextWhite, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = { showCategoryAdminDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
                    ) {
                        Text("Cerrar", color = DarkBackground, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                if (catToDelete != null) {
                    TextButton(onClick = { catToDelete = null }) {
                        Text("Cancelar", color = TextGray)
                    }
                }
            },
            title = {
                Text(
                    text = if (catToDelete != null) "Reasignar Productos de Categoría" else "Administrar Categorías",
                    color = TextWhite,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                if (catToDelete != null) {
                    val count = posRepository.getProductCountForCategory(catToDelete!!.id)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "La categoría '${catToDelete!!.name}' tiene $count producto(s) asociado(s). Para no perderlos, selecciona la categoría de destino:",
                            color = TextWhite,
                            fontSize = 12.sp
                        )
                        categories.filter { it.id != catToDelete!!.id }.forEach { target ->
                            val isSel = reassignTargetId == target.id
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSel) GoldPrimary.copy(alpha = 0.2f) else DarkBackground,
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (isSel) GoldPrimary else DarkBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { reassignTargetId = target.id }
                            ) {
                                Text(
                                    text = "${target.emoji} ${target.name}",
                                    color = if (isSel) GoldPrimary else TextWhite,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                        if (catError != null) {
                            Text(catError!!, color = DangerRed, fontSize = 11.sp)
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .heightIn(max = 400.dp)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Create Category
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedTextField(
                                value = newCatName,
                                onValueChange = { newCatName = it; catError = null },
                                placeholder = { Text("Nueva Categoría") },
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextWhite, unfocusedTextColor = TextWhite),
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    val res = posRepository.createCategory(newCatName, newCatEmoji)
                                    if (res.isSuccess) {
                                        newCatName = ""
                                    } else {
                                        catError = res.exceptionOrNull()?.message
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Crear", color = DarkBackground, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (catError != null) {
                            Text(catError!!, color = DangerRed, fontSize = 11.sp)
                        }

                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(categories, key = { it.id }) { cat ->
                                val count = posRepository.getProductCountForCategory(cat.id)
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = DarkBackground,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text("${cat.emoji} ${cat.name}", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text("$count productos asociados", color = TextGray, fontSize = 10.sp)
                                        }

                                        if (categories.size > 1) {
                                            IconButton(
                                                onClick = {
                                                    catToDelete = cat
                                                    val other = categories.firstOrNull { it.id != cat.id }
                                                    reassignTargetId = other?.id ?: ""
                                                }
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = DangerRed, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            containerColor = DarkSurfaceElevated
        )
    }

    // =========================================================================
    // DIALOG: STOCK ADJUSTMENT & AUDIT LOGS
    // =========================================================================
    if (showStockAdminDialog) {
        var selectedProductForStock by remember { mutableStateOf<Product?>(products.firstOrNull()) }
        var adjustmentAmount by remember { mutableStateOf("") }
        var adjustmentReason by remember { mutableStateOf("") }
        var adjustmentType by remember { mutableStateOf("MANUAL_ADJUSTMENT") } // MANUAL_ADJUSTMENT, PURCHASE, WASTE_MERMA
        var stockError by remember { mutableStateOf<String?>(null) }
        var currentSubTab by remember { mutableStateOf("adjust") } // adjust, logs

        AlertDialog(
            onDismissRequest = { showStockAdminDialog = false },
            confirmButton = {
                if (currentSubTab == "adjust") {
                    Button(
                        onClick = {
                            val prod = selectedProductForStock
                            if (prod == null) return@Button
                            val qty = adjustmentAmount.toDoubleOrNull()
                            if (qty == null) {
                                stockError = "Cantidad inválida"
                                return@Button
                            }

                            when (adjustmentType) {
                                "PURCHASE" -> {
                                    val res = posRepository.recordPurchaseEntry(prod.id, qty, adjustmentReason, activeCashier.name)
                                    if (res.isSuccess) {
                                        adjustmentAmount = ""
                                        adjustmentReason = ""
                                        currentSubTab = "logs"
                                    } else stockError = res.exceptionOrNull()?.message
                                }
                                "WASTE_MERMA" -> {
                                    val res = posRepository.recordWaste(prod.id, qty, adjustmentReason, activeCashier.name)
                                    if (res.isSuccess) {
                                        adjustmentAmount = ""
                                        adjustmentReason = ""
                                        currentSubTab = "logs"
                                    } else stockError = res.exceptionOrNull()?.message
                                }
                                else -> {
                                    val res = posRepository.adjustStock(
                                        productId = prod.id,
                                        newStock = qty,
                                        reason = adjustmentReason,
                                        userName = activeCashier.name,
                                        type = "MANUAL_ADJUSTMENT"
                                    )
                                    if (res.isSuccess) {
                                        adjustmentAmount = ""
                                        adjustmentReason = ""
                                        currentSubTab = "logs"
                                    } else stockError = res.exceptionOrNull()?.message
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
                    ) {
                        Text("Registrar Ajuste", color = DarkBackground, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = { showStockAdminDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
                    ) {
                        Text("Cerrar", color = DarkBackground, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showStockAdminDialog = false }) {
                    Text("Cancelar", color = TextGray)
                }
            },
            title = {
                Text("Inventario y Auditoría", color = TextWhite, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 440.dp)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Sub Tabs: Adjust vs Logs
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { currentSubTab = "adjust" },
                            colors = ButtonDefaults.buttonColors(containerColor = if (currentSubTab == "adjust") GoldPrimary else DarkBackground),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Ajustar Stock", color = if (currentSubTab == "adjust") DarkBackground else TextWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { currentSubTab = "logs" },
                            colors = ButtonDefaults.buttonColors(containerColor = if (currentSubTab == "logs") GoldPrimary else DarkBackground),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Historial (${inventoryMovements.size})", color = if (currentSubTab == "logs") DarkBackground else TextWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (currentSubTab == "adjust") {
                        Column(
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Selecciona el producto:", color = TextGray, fontSize = 11.sp)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                products.forEach { p ->
                                    val isSel = selectedProductForStock?.id == p.id
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSel) GoldPrimary else DarkBackground,
                                        modifier = Modifier.clickable {
                                            selectedProductForStock = p
                                            adjustmentAmount = p.stockQuantity.toString()
                                        }
                                    ) {
                                        Text(
                                            text = "${p.emoji} ${p.name} (${p.stockQuantity.toInt()})",
                                            color = if (isSel) DarkBackground else TextWhite,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    onClick = { adjustmentType = "MANUAL_ADJUSTMENT" },
                                    colors = ButtonDefaults.buttonColors(containerColor = if (adjustmentType == "MANUAL_ADJUSTMENT") GoldPrimary else DarkBackground),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Fijar Total", fontSize = 9.sp, color = if (adjustmentType == "MANUAL_ADJUSTMENT") DarkBackground else TextWhite)
                                }
                                Button(
                                    onClick = { adjustmentType = "PURCHASE" },
                                    colors = ButtonDefaults.buttonColors(containerColor = if (adjustmentType == "PURCHASE") SuccessGreen else DarkBackground),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("+ Entrada", fontSize = 9.sp, color = TextWhite)
                                }
                                Button(
                                    onClick = { adjustmentType = "WASTE_MERMA" },
                                    colors = ButtonDefaults.buttonColors(containerColor = if (adjustmentType == "WASTE_MERMA") DangerRed else DarkBackground),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("- Merma", fontSize = 9.sp, color = TextWhite)
                                }
                            }

                            OutlinedTextField(
                                value = adjustmentAmount,
                                onValueChange = { adjustmentAmount = it; stockError = null },
                                label = { Text(if (adjustmentType == "MANUAL_ADJUSTMENT") "Nuevo Stock Resultante" else "Cantidad a Ingresar/Descontar") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = GoldPrimary, unfocusedTextColor = GoldPrimary),
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = adjustmentReason,
                                onValueChange = { adjustmentReason = it; stockError = null },
                                label = { Text("Motivo Obligatorio (ej. Factura 123, Vencimiento)") },
                                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextWhite, unfocusedTextColor = TextWhite),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Text("Usuario auditor: ${activeCashier.name}", color = TextGray, fontSize = 11.sp)

                            if (stockError != null) {
                                Text(stockError!!, color = DangerRed, fontSize = 11.sp)
                            }
                        }
                    } else {
                        // Movements Log View
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (inventoryMovements.isEmpty()) {
                                item {
                                    Text("Sin movimientos registrados", color = TextGray, fontSize = 11.sp)
                                }
                            } else {
                                items(inventoryMovements, key = { it.id }) { mov ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = DarkBackground,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(mov.productName, color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                Text(
                                                    text = (if (mov.change > 0) "+" else "") + "${mov.change} (${mov.resultingStock})",
                                                    color = if (mov.change > 0) SuccessGreen else DangerRed,
                                                    fontWeight = FontWeight.Black,
                                                    fontSize = 12.sp
                                                )
                                            }
                                            Text("${mov.type} • ${mov.reason}", color = TextGray, fontSize = 10.sp)
                                            Text("${mov.dateFormatted} por ${mov.userName}", color = GoldPrimary, fontSize = 9.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            containerColor = DarkSurfaceElevated
        )
    }

    // Modal: Product Variant & Toppings Selector
    selectedProductForModal?.let { product ->
        AlertDialog(
            onDismissRequest = { selectedProductForModal = null },
            confirmButton = {
                Button(
                    onClick = {
                        val variant = product.variants.find { it.id == modalVariantId } ?: product.variants.firstOrNull()
                        val selectedToppings = toppingsList.filter { modalSelectedToppings.contains(it.id) }
                        val unitPrice = product.basePrice + (variant?.priceDelta ?: 0L) + selectedToppings.sumOf { it.price }
                        val estimatedCost = product.baseCost + (selectedToppings.size * 500L)

                        val cartItem = CartItem(
                            cartItemId = "ci-${System.currentTimeMillis()}",
                            productId = product.id,
                            productName = product.name,
                            variantId = variant?.id ?: "",
                            variantName = variant?.name ?: "Estándar",
                            basePrice = product.basePrice,
                            variantPriceDelta = variant?.priceDelta ?: 0L,
                            unitPrice = unitPrice,
                            quantity = 1,
                            toppings = selectedToppings,
                            totalPrice = unitPrice,
                            estimatedCost = estimatedCost,
                            imagePath = product.imagePath
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
                    if (product.variants.isNotEmpty()) {
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
        var checkoutError by remember { mutableStateOf<String?>(null) }
        var activeReservationId by remember { mutableStateOf<String?>(null) }
        val allReservations by posRepository.reservations.collectAsState()
        val activeReservation = remember(allReservations, activeReservationId) {
            allReservations.find { it.id == activeReservationId }
        }
        val recoverableReservations = remember(allReservations) {
            posRepository.getRecoverableReservations().filter { it.isPendingHold }
        }

        // Automatically complete UI flow if bank notification matched and completed the active reservation
        LaunchedEffect(activeReservation?.state) {
            if (activeReservation != null && activeReservation.state == ReservationState.COMPLETED) {
                onPaymentSuccess(activeReservation.amount, "Pago Nequi Verificado", "Nequi")
                cart.clear()
                activeReservationId = null
                showCheckoutModal = false
            }
        }

        AlertDialog(
            onDismissRequest = {
                if (activeReservation == null || activeReservation.state != ReservationState.ACTIVE) {
                    showCheckoutModal = false
                    checkoutError = null
                }
            },
            confirmButton = {
                if (activeReservation != null && (activeReservation.state == ReservationState.ACTIVE || activeReservation.state == ReservationState.PAYMENT_CANDIDATE)) {
                    // Explicit auditable manual confirmation by cashier (Section 17)
                    Button(
                        onClick = {
                            val res = posRepository.completeSale(
                                items = cart.toList(),
                                subtotal = cartSubtotal,
                                discount = cartDiscount,
                                total = cartTotal,
                                paymentMethod = "NEQUI",
                                cashierName = activeCashier.name,
                                senderName = "Verificación Manual (${activeCashier.name})",
                                channel = "Manual Auditado",
                                saleId = "sale-${activeReservation.id}"
                            )
                            if (res.isSuccess) {
                                onPaymentSuccess(cartTotal, "Verificación Manual", "Nequi")
                                cart.clear()
                                activeReservationId = null
                                showCheckoutModal = false
                            } else {
                                checkoutError = res.exceptionOrNull()?.message
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
                    ) {
                        Text("Confirmación Manual Verificada", color = DarkBackground, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                } else {
                    Button(
                        onClick = {
                            checkoutError = null
                            if (checkoutMethod == "CASH") {
                                val res = if (activeReservationId != null) {
                                    posRepository.completeCashSaleFromReservation(activeReservationId!!, activeCashier.name)
                                } else {
                                    posRepository.completeSale(
                                        items = cart.toList(),
                                        subtotal = cartSubtotal,
                                        discount = cartDiscount,
                                        total = cartTotal,
                                        paymentMethod = "CASH",
                                        cashierName = activeCashier.name
                                    )
                                }
                                if (res.isSuccess) {
                                    onPaymentSuccess(cartTotal, "Venta en Efectivo", "Caja Salon de Onces")
                                    cart.clear()
                                    activeReservationId = null
                                    showCheckoutModal = false
                                } else {
                                    checkoutError = res.exceptionOrNull()?.message ?: "Error al completar venta"
                                }
                            } else {
                                // Create Nequi reservation and enter waiting state (Sections 4 & 8: never mark sale paid on reservation creation!)
                                val resResult = posRepository.createNequiReservation(
                                    amount = cartTotal,
                                    items = cart.toList(),
                                    subtotal = cartSubtotal,
                                    discount = cartDiscount,
                                    cashierName = activeCashier.name
                                )
                                if (resResult.isSuccess) {
                                    activeReservationId = resResult.getOrThrow().id
                                } else {
                                    checkoutError = resResult.exceptionOrNull()?.message ?: "Error al crear reserva Nequi"
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GoldPrimary)
                    ) {
                        Text(if (checkoutMethod == "NEQUI") "Iniciar Espera Nequi" else "Confirmar Cobro", color = DarkBackground, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                if (activeReservation == null) {
                    TextButton(onClick = {
                        showCheckoutModal = false
                        checkoutError = null
                    }) {
                        Text("Cerrar", color = TextGray)
                    }
                }
            },
            title = {
                Text(
                    text = if (activeReservation != null) "Esperando Pago Nequi: ${formatCOP(activeReservation.amount)}" else "Finalizar Cobro: ${formatCOP(cartTotal)}",
                    color = TextWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Cajera a cargo: ${activeCashier.name}", color = GoldPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)

                    if (activeReservation != null && (activeReservation.state == ReservationState.ACTIVE || activeReservation.state == ReservationState.PAYMENT_CANDIDATE)) {
                        // Section 8: Waiting Screen inside existing modal design
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = DarkBackground,
                            border = androidx.compose.foundation.BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("⏳ Estado: ESPERANDO NOTIFICACIÓN BANCARIA", color = GoldPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text("Valor exacto esperado: ${formatCOP(activeReservation.amount)}", color = TextWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text("ID Reserva: ${activeReservation.id}", color = TextGray, fontSize = 10.sp)
                                Text(
                                    text = "Sincronización otro local: ${
                                        when (activeReservation.syncState) {
                                            SyncState.ACKNOWLEDGED -> "Sincronizada (ACK recibido)"
                                            SyncState.SENT -> "SMS enviado (esperando ACK)"
                                            SyncState.PENDING_SEND -> "Enviando SMS (confirmando con operador)..."
                                            SyncState.RETRY_PENDING -> "Sincronización pendiente (reintentando SMS)"
                                            else -> activeReservation.syncState.name
                                        }
                                    }",
                                    color = if (activeReservation.syncState == SyncState.ACKNOWLEDGED) SuccessGreen else GoldPrimary,
                                    fontSize = 10.sp
                                )
                                Text("Recepción notificación bancaria: En espera de SMS Nequi...", color = TextGray, fontSize = 10.sp)
                            }
                        }

                        // Section 9, 10, 11 Buttons: Dejar pendiente, Cancelar, Cambiar a efectivo
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(
                                onClick = {
                                    posRepository.leaveReservationPending(activeReservation.id)
                                    activeReservationId = null
                                    showCheckoutModal = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = DarkBackground),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Dejar pendiente", color = GoldPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    posRepository.cancelNequiReservation(activeReservation.id, "USER_CANCEL")
                                    activeReservationId = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Cancelar", color = TextWhite, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Button(
                            onClick = {
                                posRepository.switchNequiReservationToCash(activeReservation.id)
                                checkoutMethod = "CASH"
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Cambiar a efectivo (conservando orden)", color = DarkBackground, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
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
                                text = "Al confirmar, se creará una reserva Nequi coordinada por SMS y se abrirá la espera de notificación bancaria.",
                                color = TextGray,
                                fontSize = 11.sp
                            )
                            if (recoverableReservations.isNotEmpty()) {
                                Text("Operaciones Nequi pendientes recuperables:", color = GoldPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                recoverableReservations.take(2).forEach { pendingRes ->
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = DarkBackground,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                val restored = posRepository.recoverReservationCart(pendingRes.id)
                                                if (restored.isNotEmpty()) {
                                                    cart.clear()
                                                    cart.addAll(restored)
                                                }
                                                activeReservationId = pendingRes.id
                                            }
                                    ) {
                                        Text(
                                            text = "Recuperar ${pendingRes.id} (${formatCOP(pendingRes.amount)})",
                                            color = GoldPrimary,
                                            fontSize = 10.sp,
                                            modifier = Modifier.padding(6.dp)
                                        )
                                    }
                                }
                            }
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

                    if (checkoutError != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = DangerRed.copy(alpha = 0.2f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, DangerRed),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "⚠️ $checkoutError",
                                color = DangerRed,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
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
                    Text("Ingresa el PIN de seguridad para acceder a la administración de productos, costos e informes:", color = TextGray, fontSize = 12.sp)
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

    if (showFinancialHistoryDialog) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showFinancialHistoryDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                HistoryScreen(
                    posRepository = posRepository,
                    onBackClick = { showFinancialHistoryDialog = false }
                )
            }
        }
    }
}

