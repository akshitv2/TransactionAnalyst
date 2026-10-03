package com.pulsefinance.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulsefinance.app.core.Analytics
import com.pulsefinance.app.core.Transaction
import com.pulsefinance.app.core.ViewMode
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

@Composable
fun DashboardScreen(
    state: DashboardUiState,
    vm: DashboardViewModel,
    onImportTemplates: () -> Unit,
    onImportStoreMap: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    Scaffold(
        containerColor = Pulse.Bg,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Header(state, onRefresh = vm::refresh) }

            if (state.error != null) {
                item { ErrorCard(state.error) }
            }

            item { Controls(state, vm) }

            val noData = state.allTransactions.isEmpty()
            if (noData && state.loading) {
                item { LoadingCard() }
            } else if (noData) {
                item { EmptyCard(state, onImportTemplates) }
            } else {
                item { KpiGrid(state) }
                item { TrendCard(state) }
                item { CategoryCard(state) }
                item { MethodCard(state) }
                item { MerchantsCard(state) }
                item { TransactionsHeader(state, vm) }
                if (state.rows.isEmpty()) {
                    item {
                        Text(
                            "No transactions match these filters.",
                            color = Pulse.TextMuted,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                        )
                    }
                } else {
                    items(state.rows, key = { it.id }) { TransactionRow(it) }
                }
            }
            item { ConfigCard(onImportTemplates, onImportStoreMap, vm::resetConfigs) }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

// ---- header & controls ---------------------------------------------------------------------

@Composable
private fun Header(state: DashboardUiState, onRefresh: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(listOf(Pulse.BlueDeep, Color(0xFF6366F1)))),
            contentAlignment = Alignment.Center,
        ) {
            Text("₹", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row {
                Text("Pulse", color = Pulse.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Finance", color = Pulse.Blue, fontSize = 18.sp)
            }
            Text(headerSubtitle(state), color = Pulse.TextMuted, fontSize = 11.sp)
        }
        if (state.loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = Pulse.Blue,
            )
            Spacer(Modifier.width(12.dp))
        } else {
            IconButton(onClick = onRefresh) {
                Icon(Icons.Filled.Refresh, contentDescription = "Refresh from SMS", tint = Pulse.TextSoft)
            }
        }
    }
}

private fun headerSubtitle(state: DashboardUiState): String {
    val ms = state.lastRefreshedMs ?: return "Reading your messages"
    val time = formatClock(LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault()))
    return "Updated $time, ${"%,d".format(state.scannedMessages)} messages scanned"
}

@Composable
private fun Controls(state: DashboardUiState, vm: DashboardViewModel) {
    PulseCard {
        val monthly = state.mode == ViewMode.MONTHLY

        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (monthly) "${Analytics.monthName(state.month)} ${state.year}" else "All time",
                    color = Pulse.Text,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "${state.periodCount} transaction${if (state.periodCount == 1) "" else "s"}",
                    color = Pulse.TextMuted,
                    fontSize = 12.sp,
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        // Monthly / All time switch
        val track = RoundedCornerShape(12.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .clip(track)
                .background(Pulse.Bg)
                .border(BorderStroke(1.dp, Pulse.Border), track)
                .padding(4.dp)
        ) {
            Segment("Monthly", monthly, Modifier.weight(1f)) { vm.setMode(ViewMode.MONTHLY) }
            Segment("All time", !monthly, Modifier.weight(1f)) { vm.setMode(ViewMode.ALL_TIME) }
        }
        Spacer(Modifier.height(12.dp))

        // Month navigation (dimmed in all-time view; tapping any of it switches back to monthly)
        Row(
            Modifier
                .fillMaxWidth()
                .alpha(if (monthly) 1f else 0.5f),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArrowButton("‹") { vm.navigateMonth(-1) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillDropdown(
                    label = Analytics.monthName(state.month),
                    options = (1..12).map { Analytics.monthName(it) to it },
                    onSelect = vm::setMonth,
                )
                PillDropdown(
                    label = state.year.toString(),
                    options = state.availableYears.map { it.toString() to it },
                    onSelect = vm::setYear,
                )
            }
            ArrowButton("›") { vm.navigateMonth(1) }
        }
    }
}

@Composable
private fun Segment(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Pulse.BlueDeep else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (selected) Color.White else Pulse.TextMuted,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}

@Composable
private fun ArrowButton(glyph: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Pulse.Raised)
            .border(BorderStroke(1.dp, Pulse.Border2), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, color = Pulse.TextSoft, fontSize = 20.sp)
    }
}

// ---- KPI cards ----------------------------------------------------------------------------

@Composable
private fun KpiGrid(state: DashboardUiState) {
    val k = state.summary.kpis
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            KpiCard("Total debits", formatInr(k.totalDebits), Pulse.Rose, "All expenses and outflows", Pulse.Rose, Modifier.weight(1f))
            KpiCard("Total credits", formatInr(k.totalCredits), Pulse.Emerald, "Inflows and refunds", Pulse.Emerald, Modifier.weight(1f))
        }
        Row(
            Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            KpiCard(
                "Net cash flow",
                (if (k.netCashFlow >= 0) "+" else "") + formatInr(k.netCashFlow),
                if (k.netCashFlow >= 0) Pulse.Emerald else Pulse.Rose,
                "Credits minus debits",
                Pulse.Blue,
                Modifier.weight(1f),
            )
            KpiCard(
                "Card spend (net)",
                formatInr(k.netCardSpend),
                Pulse.Text,
                "Gross ${formatInr(k.cardSpendGross)}, refunds ${formatInr(k.cardRefunds)}",
                Pulse.Indigo,
                Modifier.weight(1f),
            )
        }
        KpiCard(
            "Top category",
            k.topCategory ?: "None yet",
            Pulse.Amber,
            "${formatInr(k.topCategoryAmount)} in debits",
            Pulse.AmberDeep,
            Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun KpiCard(
    title: String,
    value: String,
    valueColor: Color,
    sub: String,
    accent: Color,
    modifier: Modifier,
) {
    PulseCard(modifier.fillMaxHeight()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Spacer(Modifier.width(8.dp))
            Text(title, color = Pulse.TextMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            value,
            color = valueColor,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(sub, color = Pulse.TextMuted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

// ---- chart cards --------------------------------------------------------------------------

@Composable
private fun TrendCard(state: DashboardUiState) {
    PulseCard {
        if (state.mode == ViewMode.MONTHLY) {
            SectionTitle("Daily spending", "Debits for ${Analytics.monthName(state.month)} ${state.year}")
        } else {
            SectionTitle("Monthly spending", "Debits per month, all time")
        }
        if (state.summary.trend.isEmpty()) {
            Text("No debit transactions yet.", color = Pulse.TextMuted, fontSize = 13.sp)
        } else {
            TrendChart(state.summary.trend)
        }
    }
}

@Composable
private fun CategoryCard(state: DashboardUiState) {
    val cats = state.summary.categories
    val total = cats.sumOf { it.value }
    PulseCard {
        SectionTitle("Category breakdown", "Debits grouped by category")
        if (cats.isEmpty()) {
            Text("No debit transactions in this period.", color = Pulse.TextMuted, fontSize = 13.sp)
            return@PulseCard
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            DonutChart(cats, Pulse.CategoryPalette) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(formatCompactInr(total), color = Pulse.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("spent", color = Pulse.TextMuted, fontSize = 11.sp)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            cats.forEachIndexed { i, c ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Pulse.CategoryPalette[i % Pulse.CategoryPalette.size])
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        c.label,
                        color = Pulse.TextSoft,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    val pct = if (total > 0) c.value / total * 100 else 0.0
                    Text(
                        "${formatInr(c.value)}  ${"%.0f".format(pct)}%",
                        color = Pulse.Text,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun MethodCard(state: DashboardUiState) {
    val methods = state.summary.methods
    val max = methods.maxOfOrNull { it.value } ?: 0.0
    PulseCard {
        SectionTitle("Payment channels", "Volume across cards, UPI and auto-debits")
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            methods.forEach { m ->
                BarRow(
                    label = Analytics.methodLabel(m.label),
                    valueText = formatInr(m.value),
                    fraction = fractionOf(m.value, max),
                    color = Pulse.methodColor(m.label),
                    dimmed = m.value <= 0.0,
                )
            }
        }
    }
}

@Composable
private fun MerchantsCard(state: DashboardUiState) {
    val merchants = state.summary.merchants
    val max = merchants.firstOrNull()?.value ?: 0.0
    PulseCard {
        SectionTitle("Top 5 merchants", "Where most of your money went")
        if (merchants.isEmpty()) {
            Text("No debit transactions recorded.", color = Pulse.TextMuted, fontSize = 13.sp)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                merchants.forEachIndexed { i, m ->
                    BarRow(
                        label = "#${i + 1}  ${m.label}",
                        valueText = formatInr(m.value),
                        fraction = fractionOf(m.value, max),
                        color = Pulse.AmberDeep,
                    )
                }
            }
        }
    }
}

// ---- transactions -------------------------------------------------------------------------

@Composable
private fun TransactionsHeader(state: DashboardUiState, vm: DashboardViewModel) {
    PulseCard {
        SectionTitle(
            "Transactions",
            "${state.rows.size} of ${state.periodCount} shown",
        )
        OutlinedTextField(
            value = state.search,
            onValueChange = vm::setSearch,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Search store or category", fontSize = 13.sp) },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = Pulse.Text,
                unfocusedTextColor = Pulse.Text,
                cursorColor = Pulse.Blue,
                focusedBorderColor = Pulse.Blue,
                unfocusedBorderColor = Pulse.Border2,
                focusedContainerColor = Pulse.Bg,
                unfocusedContainerColor = Pulse.Bg,
                focusedPlaceholderColor = Pulse.TextFaint,
                unfocusedPlaceholderColor = Pulse.TextFaint,
            ),
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillDropdown<String?>(
                label = state.categoryFilter ?: "All categories",
                options = listOf<Pair<String, String?>>("All categories" to null) +
                    state.availableCategories.map { it to it },
                onSelect = vm::setCategoryFilter,
                modifier = Modifier.weight(1f, fill = false),
            )
            PillDropdown<String?>(
                label = state.methodFilter?.let { Analytics.methodShortLabel(it) } ?: "All channels",
                options = listOf<Pair<String, String?>>("All channels" to null) +
                    state.availableMethods.map { Analytics.methodLabel(it) to it },
                onSelect = vm::setMethodFilter,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}

@Composable
private fun TransactionRow(t: Transaction) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Pulse.Card)
            .border(BorderStroke(1.dp, Pulse.Border), shape)
            .padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                t.storeName.ifBlank { "Unknown" },
                color = Pulse.Text,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                MethodBadge(t.template)
                CategoryChip(t.category)
            }
            Text(formatDateTime(t.dateTime), color = Pulse.TextFaint, fontSize = 11.sp)
        }
        Spacer(Modifier.width(10.dp))
        Text(
            (if (t.isDebit) "- " else "+ ") + formatInr(t.amount),
            color = if (t.isDebit) Pulse.Rose else Pulse.Emerald,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

// ---- states -------------------------------------------------------------------------------

@Composable
private fun LoadingCard() {
    PulseCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Pulse.Blue)
            Spacer(Modifier.width(12.dp))
            Text("Reading your inbox and matching bank alerts", color = Pulse.TextSoft, fontSize = 13.sp)
        }
    }
}

@Composable
private fun EmptyCard(state: DashboardUiState, onImport: () -> Unit) {
    PulseCard {
        SectionTitle("No transactions found")
        Text(
            "Scanned ${"%,d".format(state.scannedMessages)} messages against ${state.templateCount} templates " +
                "and nothing matched. If your bank's alert format isn't covered yet, upload a custom " +
                "templates.json file.",
            color = Pulse.TextMuted,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(16.dp))
        androidx.compose.material3.Button(
            onClick = onImport,
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Pulse.BlueDeep)
        ) {
            Text("Upload templates.json", color = Color.White, fontSize = 13.sp)
        }
        if (state.hiddenNaCount > 0) {
            Spacer(Modifier.height(12.dp))
            Text(
                "${state.hiddenNaCount} matched alerts were hidden because their category is NA.",
                color = Pulse.TextMuted,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun ConfigCard(onTemplates: () -> Unit, onStoreMap: () -> Unit, onReset: () -> Unit) {
    PulseCard {
        SectionTitle("Custom Configuration", "Override default parsing rules")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.TextButton(
                onClick = onTemplates,
                modifier = Modifier.weight(1f),
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = Pulse.Blue)
            ) {
                Text("Import Templates", fontSize = 12.sp)
            }
            androidx.compose.material3.TextButton(
                onClick = onStoreMap,
                modifier = Modifier.weight(1f),
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = Pulse.Blue)
            ) {
                Text("Import Store Map", fontSize = 12.sp)
            }
        }
        androidx.compose.material3.TextButton(
            onClick = onReset,
            modifier = Modifier.fillMaxWidth(),
            colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = Pulse.Rose)
        ) {
            Text("Reset to Defaults", fontSize = 12.sp)
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    PulseCard {
        SectionTitle("Couldn't read messages")
        Text(message, color = Pulse.Rose, fontSize = 13.sp)
    }
}
