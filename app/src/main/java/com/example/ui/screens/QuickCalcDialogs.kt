package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.foundation.clickable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.viewmodel.PosViewModel
import com.example.util.HapticHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.launch

@Composable
fun QuickCalcHistoryDialog(
    viewModel: PosViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val orderHistory by viewModel.orderHistory.collectAsStateWithLifecycle()
    val todaySales by viewModel.todaySales.collectAsStateWithLifecycle()
    val todayOrderCount by viewModel.todayOrderCount.collectAsStateWithLifecycle()

    var selectedOrderForDetail by remember { mutableStateOf<com.example.data.model.OrderWithItems?>(null) }

    // Intercept system back button when viewing order details inside the container
    BackHandler(enabled = selectedOrderForDetail != null) {
        selectedOrderForDetail = null
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    RoundedCornerShape(28.dp)
                )
                .padding(16.dp)
        ) {
            val currentSelected = selectedOrderForDetail
            if (currentSelected == null) {
                // HISTORY LIST VIEW inside container
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Header Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = "History",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Calculation History",
                                fontWeight = FontWeight.Bold,
                                fontFamily = com.example.calculator.tokens.CalculatorTokens.CalcTypeface,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 18.sp
                            )
                        }
                        IconButton(
                            onClick = {
                                HapticHelper.performHeavyClick(context)
                                onDismiss()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Today sales summary banner: Solid matte M3 container
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Today's Total",
                                fontSize = 11.sp,
                                fontFamily = com.example.calculator.tokens.CalculatorTokens.CalcTypeface,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "GH₵ ${String.format(Locale.US, "%.2f", todaySales ?: 0.0)}",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = com.example.calculator.tokens.CalculatorTokens.CalcTypeface,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Entries",
                                fontSize = 11.sp,
                                fontFamily = com.example.calculator.tokens.CalculatorTokens.CalcTypeface,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "$todayOrderCount",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = com.example.calculator.tokens.CalculatorTokens.CalcTypeface,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }

                    if (orderHistory.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No calculations or orders yet",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontFamily = com.example.calculator.tokens.CalculatorTokens.CalcTypeface,
                                fontSize = 14.sp
                            )
                        }
                    } else {
                        val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(orderHistory) { orderWithItems ->
                                val order = orderWithItems.order
                                val time = timeFormat.format(Date(order.timestamp))
                                val isMultiBatch = orderWithItems.items.size > 1
                                val batchCount = orderWithItems.items.size
                                val batchLabel = if (isMultiBatch) "$batchCount-in-1" else "1-in-1"

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable {
                                            HapticHelper.performHeavyClick(context)
                                            selectedOrderForDetail = orderWithItems
                                        }
                                        .border(
                                            1.dp,
                                            MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                                            RoundedCornerShape(14.dp)
                                        ),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                                    ),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .weight(1f, fill = false)
                                                .padding(end = 10.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(
                                                            if (isMultiBatch) MaterialTheme.colorScheme.tertiaryContainer
                                                            else MaterialTheme.colorScheme.surfaceVariant
                                                        )
                                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = batchLabel,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        fontFamily = com.example.calculator.tokens.CalculatorTokens.CalcTypeface,
                                                        color = if (isMultiBatch) MaterialTheme.colorScheme.onTertiaryContainer
                                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }

                                                Text(
                                                    text = time,
                                                    fontSize = 11.sp,
                                                    fontFamily = com.example.calculator.tokens.CalculatorTokens.CalcTypeface,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(4.dp))

                                            val summary = orderWithItems.items.joinToString(" • ") {
                                                it.name.removePrefix("Batch ").substringAfter(": ")
                                            }

                                            Text(
                                                text = summary.ifBlank { "Calculation #${order.orderNumber}" },
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium,
                                                fontFamily = com.example.calculator.tokens.CalculatorTokens.CalcTypeface,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Column(horizontalAlignment = Alignment.End) {
                                            Text(
                                                text = "GH₵ ${String.format(Locale.US, "%.2f", order.grandTotal)}",
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = com.example.calculator.tokens.CalculatorTokens.CalcTypeface,
                                                color = MaterialTheme.colorScheme.primary,
                                                maxLines = 1
                                            )
                                            Text(
                                                text = "Tap to view ›",
                                                fontSize = 10.sp,
                                                fontFamily = com.example.calculator.tokens.CalculatorTokens.CalcTypeface,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // DETAILS PAGE VIEW inside the EXACT SAME CONTAINER
                val items = currentSelected.items
                val pageCount = items.size
                val pagerState = rememberPagerState(pageCount = { pageCount })
                val coroutineScope = rememberCoroutineScope()

                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Top Row (48dp high): Back arrow (left), Custom Indicator (middle), Close X (right)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                HapticHelper.performHeavyClick(context)
                                selectedOrderForDetail = null
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Middle: Custom Gliding Page Indicator (hidden if 1-in-1 session)
                        if (pageCount > 1) {
                            val pageProgress = pagerState.currentPage + pagerState.currentPageOffsetFraction
                            CustomGlidingIndicator(
                                pageCount = pageCount,
                                pageProgress = pageProgress,
                                onDotClick = { index ->
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(index)
                                    }
                                }
                            )
                        } else {
                            Spacer(modifier = Modifier.width(40.dp))
                        }

                        IconButton(
                            onClick = {
                                HapticHelper.performHeavyClick(context)
                                onDismiss()
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Middle: Horizontal Pager for batches
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) { pageIndex ->
                        val batchItem = items[pageIndex]
                        val foodItems = remember(batchItem) { parseFoodItemsAndPrices(batchItem) }

                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 4.dp, vertical = 8.dp)
                        ) {
                            val availableHeightPx = constraints.maxHeight.toFloat()
                            val density = LocalDensity.current
                            val availableHeightDp = with(density) { availableHeightPx.toDp() }

                            val numberOfItems = foodItems.size.coerceAtLeast(1)
                            val rowHeightDp = availableHeightDp / numberOfItems

                            val nameSizeValue = (rowHeightDp.value * 0.55f).coerceIn(12f, 20f)
                            val nameSizeSp = nameSizeValue.sp
                            val gapDp = (rowHeightDp.value - 1.25f * nameSizeValue).dp.coerceIn(2.dp, 18.dp)
                            val priceSizeSp = (0.52f * nameSizeValue).coerceAtLeast(9f).sp

                            val isScrollable = foodItems.size > 20

                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(if (isScrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier),
                                verticalArrangement = Arrangement.spacedBy(gapDp)
                            ) {
                                foodItems.forEach { (foodName, price) ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = foodName,
                                            fontSize = nameSizeSp,
                                            fontWeight = FontWeight.Medium,
                                            fontFamily = com.example.calculator.tokens.CalculatorTokens.CalcTypeface,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        Spacer(modifier = Modifier.width(10.dp))

                                        val priceFormatted = if (price % 1.0 == 0.0) {
                                            "GH₵ ${price.toInt()}"
                                        } else {
                                            String.format(Locale.US, "GH₵ %.2f", price)
                                        }

                                        Text(
                                            text = priceFormatted,
                                            fontSize = priceSizeSp,
                                            fontFamily = com.example.calculator.tokens.CalculatorTokens.CalcTypeface,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Bottom Total: Pinned bottom right
                    val currentBatchSubtotal = items[pagerState.currentPage].subtotal
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 4.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = "GH₵ ",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = com.example.calculator.tokens.CalculatorTokens.CalcTypeface,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Text(
                            text = String.format(Locale.US, "%.2f", currentBatchSubtotal),
                            fontSize = 46.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = com.example.calculator.tokens.CalculatorTokens.CalcTypeface,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

/**
 * Custom Gliding Page Indicator: 8dp dots spaced 22dp center-to-center in outlineVariant color.
 * 22dp x 8dp primary gliding pill smoothly moving with swipe offset.
 */
@Composable
private fun CustomGlidingIndicator(
    pageCount: Int,
    pageProgress: Float,
    onDotClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (pageCount <= 1) return

    val totalTrackWidthDp = ((pageCount - 1) * 22 + 22).dp

    Box(
        modifier = modifier
            .width(totalTrackWidthDp)
            .height(8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(pageCount) { index ->
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                        .clickable { onDotClick(index) }
                )
            }
        }

        val offsetDp = (pageProgress * 22f).dp
        Box(
            modifier = Modifier
                .offset(x = offsetDp)
                .width(22.dp)
                .height(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}

/**
 * Parses food item names and prices from an OrderItemEntity.
 * Sorts food names by character length ascending (shortest to longest).
 */
private fun parseFoodItemsAndPrices(orderItem: com.example.data.model.OrderItemEntity): List<Pair<String, Double>> {
    val rawName = orderItem.name.removePrefix("Batch ").substringAfter(": ").trim()
    val batchSubtotal = orderItem.subtotal

    if (rawName.isBlank() || rawName.startsWith("Calculation:")) {
        val label = if (rawName.startsWith("Calculation:")) "Calculation" else "Items"
        return listOf(Pair(label, batchSubtotal))
    }

    val parts = rawName.split(",", "•", ";").map { it.trim() }.filter { it.isNotBlank() }
    if (parts.isEmpty()) {
        return listOf(Pair(rawName, batchSubtotal))
    }

    val parsedItems = parts.map { part ->
        var clean = part
        clean = clean.replace(Regex("(?i)^\\d+\\s*(cups|pieces|plates|bowls|packs|x)?\\s*(of)?\\s*"), "")
        clean = clean.replace(Regex("\\s*\\(.*?\\)"), "")
        clean = clean.trim()
        val capitalized = clean.split(" ").joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
        }
        capitalized.ifBlank { "Item" }
    }

    val count = parsedItems.size
    val itemsWithPrices = mutableListOf<Pair<String, Double>>()

    if (count == 1) {
        itemsWithPrices.add(Pair(parsedItems[0], batchSubtotal))
    } else {
        val basePrice = batchSubtotal / count
        val roundedBase = Math.round(basePrice * 100.0) / 100.0
        val remainder = batchSubtotal - (roundedBase * count)

        parsedItems.forEachIndexed { idx, name ->
            val itemPrice = if (idx == 0) roundedBase + remainder else roundedBase
            itemsWithPrices.add(Pair(name, itemPrice))
        }
    }

    return itemsWithPrices.sortedWith(compareBy<Pair<String, Double>> { it.first.length })
}

/**
 * Full Swipeable Batch Receipt Modal View.
 * Displays batches as clean, playful, swipeable cards with flat matte Material 3 dry paint colors.
 * Fully adaptive for all screens including Android 17 floating app bubbles.
 */
@Composable
fun FullBatchReceiptDetailDialog(
    orderWithItems: com.example.data.model.OrderWithItems,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val items = orderWithItems.items
    val totalBatches = items.size
    val pagerState = rememberPagerState(pageCount = { totalBatches })

    Dialog(onDismissRequest = onClose) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(colorScheme.surfaceContainerHigh)
                .border(1.dp, colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(28.dp))
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (totalBatches > 1) "$totalBatches-in-1 Session Breakdown" else "Session Receipt",
                            fontWeight = FontWeight.Bold,
                            fontFamily = com.example.calculator.tokens.CalculatorTokens.CalcTypeface,
                            color = colorScheme.onSurface,
                            fontSize = 17.sp,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Order #${orderWithItems.order.orderNumber}",
                            fontSize = 11.sp,
                            fontFamily = com.example.calculator.tokens.CalculatorTokens.CalcTypeface,
                            color = colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(
                        onClick = {
                            HapticHelper.performHeavyClick(context)
                            onClose()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Swipeable Horizontal Pager across batches
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                ) { pageIndex ->
                    val batchItem = items[pageIndex]
                    val palette = com.example.ui.theme.MatteBatchPalette.getBatchColorToken(pageIndex, colorScheme)
                    val cleanBatchName = batchItem.name.removePrefix("Batch ").substringAfter(": ")

                    // Flat "Dry Paint" Matte Card
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 4.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(palette.containerColor)
                            .border(BorderStroke(1.5.dp, palette.borderColor), RoundedCornerShape(22.dp))
                            .padding(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Top Tag row: "Batch X of Y"
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(palette.onContainerColor.copy(alpha = 0.15f))
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "BATCH ${pageIndex + 1} OF $totalBatches",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        fontFamily = com.example.calculator.tokens.CalculatorTokens.CalcTypeface,
                                        color = palette.onContainerColor
                                    )
                                }

                                if (totalBatches > 1) {
                                    Text(
                                        text = if (pageIndex < totalBatches - 1) "Swipe ‹ ›" else "Last Batch",
                                        fontSize = 11.sp,
                                        fontFamily = com.example.calculator.tokens.CalculatorTokens.CalcTypeface,
                                        color = palette.onContainerColor.copy(alpha = 0.75f)
                                    )
                                }
                            }

                            // Middle: Big, bold rounded numerals
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "GH₵ ${String.format(Locale.US, "%.2f", batchItem.subtotal)}",
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = com.example.calculator.tokens.CalculatorTokens.CalcTypeface,
                                    color = palette.onContainerColor,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                // Spoken food items summary in rounded clean font
                                Text(
                                    text = cleanBatchName.ifBlank { "Batch Items" },
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = com.example.calculator.tokens.CalculatorTokens.CalcTypeface,
                                    color = palette.onContainerColor.copy(alpha = 0.90f),
                                    maxLines = 2,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }

                            // Bottom Subtext
                            Text(
                                text = "Recorded in session #${orderWithItems.order.orderNumber}",
                                fontSize = 10.sp,
                                fontFamily = com.example.calculator.tokens.CalculatorTokens.CalcTypeface,
                                color = palette.onContainerColor.copy(alpha = 0.65f)
                            )
                        }
                    }
                }

                // Page Indicator Dots & Grand Total Summary Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Indicator dots
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(totalBatches) { dotIndex ->
                            val isSelected = pagerState.currentPage == dotIndex
                            Box(
                                modifier = Modifier
                                    .size(if (isSelected) 10.dp else 6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) colorScheme.primary
                                        else colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                    )
                            )
                        }
                    }

                    // Combined Session Grand Total
                    Text(
                        text = "Total: GH₵ ${String.format(Locale.US, "%.2f", orderWithItems.order.grandTotal)}",
                        fontWeight = FontWeight.Bold,
                        fontFamily = com.example.calculator.tokens.CalculatorTokens.CalcTypeface,
                        fontSize = 15.sp,
                        color = colorScheme.primary
                    )
                }

                // Close Button
                OutlinedButton(
                    onClick = {
                        HapticHelper.performHeavyClick(context)
                        onClose()
                    },
                    border = BorderStroke(1.dp, colorScheme.primary),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                ) {
                    Text(
                        text = "Back to List",
                        color = colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontFamily = com.example.calculator.tokens.CalculatorTokens.CalcTypeface,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
fun QuickCalcCatalogDialog(
    viewModel: PosViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val allItems by viewModel.allMenuItems.collectAsStateWithLifecycle()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ShoppingBag,
                        contentDescription = "Catalog",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Menu & Pricing (GH₵)",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 17.sp
                    )
                }
                IconButton(
                    onClick = {
                        HapticHelper.performHeavyClick(context)
                        onDismiss()
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${allItems.size} dishes configured",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = {
                        HapticHelper.performHeavyClick(context)
                        viewModel.resetMenuDefaults()
                    }) {
                        Icon(
                            Icons.Default.RestartAlt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Reset Defaults",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(allItems, key = { it.id }) { item ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                    RoundedCornerShape(10.dp)
                                ),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.name,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${item.unitLabel} • ${item.category}",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = "${String.format("%.2f", item.price)} GH₵",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                HapticHelper.performHeavyClick(context)
                onDismiss()
            }) {
                Text("Close", color = MaterialTheme.colorScheme.primary)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
fun QuickCalcSettingsDialog(
    viewModel: PosViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val savedApiKey by viewModel.geminiApiKey.collectAsStateWithLifecycle()
    val isSmartMode by viewModel.isSmartTranscriptionEnabled.collectAsStateWithLifecycle()

    var localApiKey by remember { mutableStateOf(savedApiKey) }
    var localSmartMode by remember { mutableStateOf(isSmartMode) }
    var isKeyVisible by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(28.dp))
                .padding(18.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header Title Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Voice & App Settings",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 20.sp
                    )
                }

                // Card 1: Gemini API Key
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Gemini API Key",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Text(
                            text = "Required for Gemini Live transcription (gemini-3.5-transcribe-live). Get an API key from Google AI Studio.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )

                        OutlinedTextField(
                            value = localApiKey,
                            onValueChange = { localApiKey = it },
                            placeholder = { Text("Enter your API key", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
                            singleLine = true,
                            visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Key,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            trailingIcon = {
                                IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                                    Icon(
                                        imageVector = if (isKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle key visibility",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Card 2: Smart Mode Toggle
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Smart Mode",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Enable intelligent features for a better experience.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 16.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Switch(
                            checked = localSmartMode,
                            onCheckedChange = { localSmartMode = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        )
                    }
                }

                // Card 3: Diagnostics & Telemetry
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Analytics,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Diagnostics & Telemetry",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Text(
                            text = "Inspect logs, checklist, and run automated Gemini WebSocket tests.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 16.sp
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                                .clickable {
                                    HapticHelper.performHeavyClick(context)
                                    onDismiss()
                                    viewModel.openDiagnostics()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.OpenInNew,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "View Diagnostics & Telemetry",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }

                // Card 4: Material 3 Expressive Theming
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Palette,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Material 3 Expressive Theming",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Dynamic colors, modern Material 3 design, and expressive UI.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 16.sp
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Bottom Action Row: Cancel & Save Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            HapticHelper.performHeavyClick(context)
                            onDismiss()
                        },
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text(
                            text = "Cancel",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    Button(
                        onClick = {
                            HapticHelper.performHeavyClick(context)
                            viewModel.saveGeminiApiKey(localApiKey)
                            viewModel.toggleSmartTranscription(localSmartMode)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text(
                            text = "Save",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }
    }
}

