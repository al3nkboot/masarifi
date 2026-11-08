package com.midroid.masarifi.ui.warranty

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.midroid.masarifi.R
import com.midroid.masarifi.ui.AppViewModel
import com.midroid.masarifi.ui.Route
import com.midroid.masarifi.ui.bills.PickField
import com.midroid.masarifi.ui.bills.dateLabel
import com.midroid.masarifi.ui.components.AppIcons
import com.midroid.masarifi.ui.components.ConfirmDialog
import com.midroid.masarifi.ui.components.EmptyState
import com.midroid.masarifi.ui.components.SectionTitle
import com.midroid.masarifi.ui.theme.AppTheme
import com.midroid.masarifi.ui.theme.Spacing
import com.midroid.masarifi.util.Money
import com.midroid.masarifi.warranty.Warranty
import com.midroid.masarifi.warranty.WarrantyCategory
import com.midroid.masarifi.warranty.WarrantyLogic
import com.midroid.masarifi.warranty.WarrantyStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun categoryLabel(c: WarrantyCategory) = stringResource(
    when (c) { WarrantyCategory.APPLIANCES -> R.string.warranty_cat_appliances; WarrantyCategory.ELECTRONICS -> R.string.warranty_cat_electronics; WarrantyCategory.OTHER -> R.string.warranty_cat_other },
)

private fun categoryEmoji(c: WarrantyCategory) = when (c) { WarrantyCategory.APPLIANCES -> "🧺"; WarrantyCategory.ELECTRONICS -> "📱"; WarrantyCategory.OTHER -> "📦" }

@Composable
private fun statusColors(st: WarrantyStatus): Pair<Color, Color> = when (st) {
    WarrantyStatus.ACTIVE -> AppTheme.colors.positive to AppTheme.colors.positive.copy(alpha = 0.16f)
    WarrantyStatus.EXPIRING -> AppTheme.colors.warning to AppTheme.colors.warning.copy(alpha = 0.18f)
    WarrantyStatus.EXPIRED -> AppTheme.colors.negative to AppTheme.colors.negative.copy(alpha = 0.16f)
}

@Composable
fun StatusChip(st: WarrantyStatus) {
    val (fg, bg) = statusColors(st)
    val label = stringResource(when (st) { WarrantyStatus.ACTIVE -> R.string.warranty_status_active; WarrantyStatus.EXPIRING -> R.string.warranty_status_soon; WarrantyStatus.EXPIRED -> R.string.warranty_status_expired })
    Surface(color = bg, shape = MaterialTheme.shapes.small) {
        Text(label, color = fg, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xxs))
    }
}

@Composable
private fun remainingLabel(w: Warranty, today: LocalDate): String {
    val d = WarrantyLogic.daysLeft(w, today)
    return when {
        d < 0 -> stringResource(R.string.warranty_expired_ago, (-d).toString())
        d == 0L -> stringResource(R.string.warranty_expires_today)
        else -> stringResource(R.string.warranty_days_left, d.toString())
    }
}

/** Where invoice bitmaps are decoded; Robolectric screenshot tests swap it (native decoding only works on the main thread there). */
@androidx.annotation.VisibleForTesting
var imageDecodeDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.IO

/** Small bitmap loader for app-private invoice files (sampled for thumbnails). */
@Composable
fun rememberImage(file: File, maxSide: Int): ImageBitmap? = produceState<ImageBitmap?>(null, file.path, maxSide) {
    value = withContext(imageDecodeDispatcher) {
        runCatching {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, o)
            var s = 1
            while (maxOf(o.outWidth, o.outHeight) / (s * 2) >= maxSide) s *= 2
            BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = s })?.asImageBitmap()
        }.getOrNull()
    }
}.value

@Composable
private fun Thumb(vm: AppViewModel, name: String, size: Int = 88, onClick: () -> Unit, onRemove: (() -> Unit)? = null) {
    val bmp = rememberImage(vm.warrantyImages.file(name), 320)
    Box(Modifier.size(size.dp).clip(MaterialTheme.shapes.medium).background(AppTheme.colors.chip).clickable(onClick = onClick)) {
        if (bmp != null) Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(AppIcons.Receipt, null, Modifier.align(Alignment.Center), tint = AppTheme.colors.muted)
        if (onRemove != null) {
            Surface(shape = MaterialTheme.shapes.extraLarge, color = Color.Black.copy(alpha = 0.55f), modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.xs).size(24.dp).clickable(onClick = onRemove)) {
                Icon(Icons.Filled.Close, stringResource(R.string.delete), tint = Color.White, modifier = Modifier.padding(Spacing.xs))
            }
        }
    }
}

// ------------------------------------------------------------------ list

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WarrantyListScreen(vm: AppViewModel, navigate: (Route) -> Unit, onBack: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val all by vm.warranties.collectAsStateWithLifecycle()
    val today = vm.today()
    var filter by remember { mutableStateOf<WarrantyStatus?>(null) }
    val sorted = remember(all, today) { WarrantyLogic.sorted(all, today) }
    val shown = sorted.filter { filter == null || WarrantyLogic.status(it, today) == filter }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.warranties)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { navigate(Route.WarrantyEdit()) },
                icon = { Icon(Icons.Filled.Add, null) }, text = { Text(stringResource(R.string.warranty_add)) },
                containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                val counts = all.groupingBy { WarrantyLogic.status(it, today) }.eachCount()
                FilterChip(filter == null, { filter = null }, label = { Text(stringResource(R.string.warranty_filter_all) + " ${all.size}") })
                listOf(WarrantyStatus.ACTIVE to R.string.warranty_status_active, WarrantyStatus.EXPIRING to R.string.warranty_status_soon, WarrantyStatus.EXPIRED to R.string.warranty_status_expired).forEach { (st, res) ->
                    FilterChip(filter == st, { filter = if (filter == st) null else st }, label = { Text(stringResource(res) + " ${counts[st] ?: 0}") })
                }
            }
            if (shown.isEmpty()) { EmptyState(stringResource(R.string.warranty_empty)); return@Column }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, top = Spacing.sm, bottom = Spacing.fabClearance), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                items(shown, key = { it.id }) { w ->
                    val st = WarrantyLogic.status(w, today)
                    Card(onClick = { navigate(Route.WarrantyDetail(w.id)) }, colors = CardDefaults.cardColors(containerColor = AppTheme.colors.card), modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                            val first = w.imageList.firstOrNull()
                            if (first != null) Thumb(vm, first, 52, onClick = { navigate(Route.WarrantyDetail(w.id)) })
                            else Box(Modifier.size(52.dp).clip(MaterialTheme.shapes.medium).background(AppTheme.colors.chip), contentAlignment = Alignment.Center) {
                                Text(categoryEmoji(w.cat), style = MaterialTheme.typography.titleLarge)
                            }
                            Spacer(Modifier.width(Spacing.md))
                            Column(Modifier.weight(1f)) {
                                Text(w.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                                Text(listOf(categoryLabel(w.cat), w.brand, w.store).filter { it.isNotBlank() }.joinToString(" • "), style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.muted, maxLines = 1)
                                Spacer(Modifier.height(Spacing.xs))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    StatusChip(st)
                                    Spacer(Modifier.width(Spacing.sm))
                                    Text(remainingLabel(w, today), style = MaterialTheme.typography.bodySmall, color = statusColors(st).first)
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(stringResource(R.string.warranty_until), style = MaterialTheme.typography.labelSmall, color = AppTheme.colors.muted)
                                Text(dateLabel(LocalDate.ofEpochDay(w.expiryDate), s.arabic), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ add / edit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WarrantyEditScreen(vm: AppViewModel, id: Long?, onBack: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val all by vm.warranties.collectAsStateWithLifecycle()
    val existing = id?.let { i -> all.firstOrNull { it.id == i } }
    val today = vm.today()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf(id == null) }
    var name by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf(WarrantyCategory.ELECTRONICS) }
    var brand by remember { mutableStateOf("") }
    var store by remember { mutableStateOf("") }
    var purchase by remember { mutableStateOf(today) }
    var dur by remember { mutableStateOf("1") }
    var unit by remember { mutableStateOf(Warranty.UNIT_YEARS) }
    var price by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    val images = remember { mutableStateListOf<String>() }
    val added = remember { mutableStateListOf<String>() }
    val removed = remember { mutableStateListOf<String>() }
    var picking by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(existing?.id) {
        if (existing != null && !loaded) {
            name = existing.name; cat = existing.cat; brand = existing.brand; store = existing.store; purchase = LocalDate.ofEpochDay(existing.purchaseDate)
            dur = existing.durationValue.toString(); unit = existing.durationUnit; price = existing.price?.let { Money.toInput(it, s.currencyInfo) }.orEmpty()
            notes = existing.notes; images.clear(); images.addAll(existing.imageList); loaded = true
        }
    }
    fun addUri(uri: Uri?) { if (uri != null) scope.launch { vm.importWarrantyImage(uri)?.let { images.add(it); added.add(it) } } }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(5)) { uris -> uris.forEach { addUri(it) } }
    var camUri by remember { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = camUri
        if (ok && u != null) scope.launch { vm.importWarrantyImage(u)?.let { images.add(it); added.add(it) }; runCatching { ctx.contentResolver.delete(u, null, null) } }
    }
    fun openCamera() = runCatching {
        val f = File(ctx.cacheDir, "camera").apply { mkdirs() }.let { File(it, "capture_${System.currentTimeMillis()}.jpg") }
        val u = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
        camUri = u; camera.launch(u)
    }
    val months = (dur.toIntOrNull() ?: 0).coerceIn(0, 99)
    val expiry = WarrantyLogic.expiry(purchase, months, unit)
    val parsedPrice = price.takeIf { it.isNotBlank() }?.let { Money.parse(it) }
    val canSave = name.isNotBlank() && months > 0 && (price.isBlank() || parsedPrice != null)
    val leave = { vm.discardWarrantyImages(added.toList()); onBack() }
    androidx.activity.compose.BackHandler { leave() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (id == null) R.string.warranty_add else R.string.warranty_edit)) },
                navigationIcon = { IconButton(onClick = leave) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg)) {
            OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.warranty_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs))
            Text(stringResource(R.string.warranty_category), style = MaterialTheme.typography.bodySmall, color = AppTheme.colors.muted, modifier = Modifier.padding(top = Spacing.sm, bottom = Spacing.xs))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                WarrantyCategory.entries.forEachIndexed { i, c ->
                    SegmentedButton(cat == c, { cat = c }, SegmentedButtonDefaults.itemShape(i, WarrantyCategory.entries.size)) { Text(categoryLabel(c), maxLines = 1) }
                }
            }
            Row(Modifier.fillMaxWidth()) {
                OutlinedTextField(brand, { brand = it }, label = { Text(stringResource(R.string.warranty_brand)) }, singleLine = true, modifier = Modifier.weight(1f).padding(vertical = Spacing.xs))
                Spacer(Modifier.width(Spacing.sm))
                OutlinedTextField(store, { store = it }, label = { Text(stringResource(R.string.warranty_store)) }, singleLine = true, modifier = Modifier.weight(1f).padding(vertical = Spacing.xs))
            }
            Box(Modifier.fillMaxWidth().padding(vertical = Spacing.xs)) {
                OutlinedTextField(dateLabel(purchase, s.arabic), {}, readOnly = true, enabled = false, label = { Text(stringResource(R.string.warranty_purchase_date)) }, modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(disabledTextColor = MaterialTheme.colorScheme.onSurface, disabledLabelColor = AppTheme.colors.muted, disabledBorderColor = MaterialTheme.colorScheme.outline))
                Box(Modifier.matchParentSize().clickable { picking = true })
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(dur, { dur = it.filter(Char::isDigit).take(2) }, label = { Text(stringResource(R.string.warranty_duration)) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f).padding(vertical = Spacing.xs))
                Spacer(Modifier.width(Spacing.sm))
                SingleChoiceSegmentedButtonRow(Modifier.weight(1.3f)) {
                    listOf(Warranty.UNIT_MONTHS to R.string.warranty_months, Warranty.UNIT_YEARS to R.string.warranty_years).forEachIndexed { i, (u, res) ->
                        SegmentedButton(unit == u, { unit = u }, SegmentedButtonDefaults.itemShape(i, 2)) { Text(stringResource(res)) }
                    }
                }
            }
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs)) {
                Row(Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                    Icon(AppIcons.Shield, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(Modifier.width(Spacing.sm))
                    Text(stringResource(R.string.warranty_expiry_is, dateLabel(expiry, s.arabic)), color = MaterialTheme.colorScheme.onSecondaryContainer, style = MaterialTheme.typography.bodyMedium)
                }
            }
            OutlinedTextField(price, { price = it }, label = { Text(stringResource(R.string.warranty_price)) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), suffix = { Text(s.currencyInfo.symbol(s.arabic)) },
                modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs))
            OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.bill_notes)) }, modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.xs))

            SectionTitle(stringResource(R.string.warranty_invoice_photos) + if (images.isEmpty()) "" else " (${images.size})", Modifier.padding(top = Spacing.sm))
            if (images.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.padding(bottom = Spacing.sm)) {
                    items(images.toList(), key = { it }) { n -> Thumb(vm, n, onClick = { viewing = n }, onRemove = { images.remove(n); if (!added.remove(n)) removed.add(n) else vm.discardWarrantyImages(listOf(n)) }) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedButton(onClick = { openCamera() }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.warranty_take_photo)) }
                OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.warranty_from_gallery)) }
            }
            Spacer(Modifier.height(Spacing.lg))
            Button(
                enabled = canSave, modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val base = existing ?: Warranty(name = "", purchaseDate = 0, expiryDate = 0)
                    vm.saveWarranty(
                        base.copy(name = name.trim(), category = cat.ordinal, brand = brand.trim(), store = store.trim(), purchaseDate = purchase.toEpochDay(),
                            durationValue = months, durationUnit = unit, expiryDate = expiry.toEpochDay(), price = parsedPrice, notes = notes.trim()).withImages(images.toList()),
                        removedImages = removed.toList(),
                    )
                    onBack()
                },
            ) { Text(stringResource(R.string.save)) }
            Spacer(Modifier.height(Spacing.xxl))
        }
    }
    if (picking) {
        val st = rememberDatePickerState(initialSelectedDateMillis = purchase.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(onDismissRequest = { picking = false }, confirmButton = {
            TextButton(onClick = { st.selectedDateMillis?.let { purchase = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }; picking = false }) { Text(stringResource(R.string.ok)) }
        }) { DatePicker(st) }
    }
    viewing?.let { n -> ZoomImageDialog(vm.warrantyImages.file(n)) { viewing = null } }
}

// ------------------------------------------------------------------ detail

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WarrantyDetailScreen(vm: AppViewModel, id: Long, navigate: (Route) -> Unit, onBack: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val all by vm.warranties.collectAsStateWithLifecycle()
    val w = all.firstOrNull { it.id == id }
    val today = vm.today()
    var confirmDelete by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<String?>(null) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(w?.name ?: stringResource(R.string.warranties), maxLines = 1) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
                actions = {
                    if (w != null) {
                        IconButton(onClick = { navigate(Route.WarrantyEdit(w.id)) }) { Icon(Icons.Filled.Edit, stringResource(R.string.warranty_edit)) }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, stringResource(R.string.delete)) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        if (w == null) { Box(Modifier.padding(padding)) { EmptyState(stringResource(R.string.warranty_empty)) }; return@Scaffold }
        val st = WarrantyLogic.status(w, today)
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg)) {
            Card(colors = CardDefaults.cardColors(containerColor = AppTheme.colors.card), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(Spacing.lg)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusChip(st); Spacer(Modifier.width(Spacing.sm))
                        Text(remainingLabel(w, today), style = MaterialTheme.typography.titleMedium, color = statusColors(st).first)
                    }
                    Spacer(Modifier.height(Spacing.sm))
                    val total = (w.expiryDate - w.purchaseDate).coerceAtLeast(1)
                    val used = (today.toEpochDay() - w.purchaseDate).coerceIn(0, total)
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { used.toFloat() / total }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(MaterialTheme.shapes.small),
                        color = statusColors(st).first, trackColor = AppTheme.colors.chip, drawStopIndicator = {},
                    )
                }
            }
            Spacer(Modifier.height(Spacing.sm))
            @Composable fun Line(label: String, value: String) {
                if (value.isBlank()) return
                Row(Modifier.fillMaxWidth().padding(vertical = Spacing.sm)) {
                    Text(label.substringBefore(" ("), color = AppTheme.colors.muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(value, style = MaterialTheme.typography.bodyLarge)
                }
                androidx.compose.material3.HorizontalDivider(color = AppTheme.colors.divider)
            }
            Line(stringResource(R.string.warranty_category), categoryLabel(w.cat))
            Line(stringResource(R.string.warranty_brand), w.brand)
            Line(stringResource(R.string.warranty_store), w.store)
            Line(stringResource(R.string.warranty_purchase_date), dateLabel(LocalDate.ofEpochDay(w.purchaseDate), s.arabic))
            Line(stringResource(R.string.warranty_duration), "${w.durationValue} " + stringResource(if (w.durationUnit == Warranty.UNIT_MONTHS) R.string.warranty_months else R.string.warranty_years))
            Line(stringResource(R.string.warranty_until), dateLabel(LocalDate.ofEpochDay(w.expiryDate), s.arabic))
            w.price?.let { Line(stringResource(R.string.warranty_price), Money.format(it, s.currencyInfo, s.arabic)) }
            Line(stringResource(R.string.bill_notes), w.notes)
            SectionTitle(stringResource(R.string.warranty_invoice_photos) + " (${w.imageList.size})", Modifier.padding(top = Spacing.sm))
            if (w.imageList.isEmpty()) Text(stringResource(R.string.warranty_no_photos), color = AppTheme.colors.muted, style = MaterialTheme.typography.bodyMedium)
            w.imageList.forEach { n ->
                val bmp = rememberImage(vm.warrantyImages.file(n), 1080)
                Box(Modifier.fillMaxWidth().padding(vertical = Spacing.xs).clip(MaterialTheme.shapes.large).background(AppTheme.colors.chip).clickable { viewing = n }) {
                    if (bmp != null) Image(bmp, stringResource(R.string.warranty_invoice_photos), Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
                    else Box(Modifier.fillMaxWidth().height(160.dp))
                }
            }
            Spacer(Modifier.height(Spacing.xxl))
        }
    }
    if (confirmDelete && w != null) {
        ConfirmDialog(stringResource(R.string.delete), stringResource(R.string.warranty_confirm_delete), stringResource(R.string.delete), { vm.deleteWarranty(w); onBack() }, { confirmDelete = false })
    }
    viewing?.let { n -> ZoomImageDialog(vm.warrantyImages.file(n)) { viewing = null } }
}

/** Fullscreen invoice viewer: pinch to zoom, drag to pan, double-tap resets. */
@Composable
fun ZoomImageDialog(file: File, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val bmp = rememberImage(file, 2048)
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (bmp != null) Image(
                bmp, null, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 6f)
                            offset = if (scale == 1f) Offset.Zero else offset + pan
                        }
                    }
                    .pointerInput(Unit) { detectTapGestures(onDoubleTap = { scale = if (scale > 1f) 1f else 2.5f; offset = Offset.Zero }) }
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
            )
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.md)) { Icon(Icons.Filled.Close, stringResource(R.string.close), tint = Color.White) }
        }
    }
}
