package com.bizbite.printer.ui.billing.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bizbite.printer.BuildConfig
import com.bizbite.printer.R
import com.bizbite.printer.data.menu.MenuItem
import com.bizbite.printer.ui.billing.formatRupees
import com.bizbite.printer.ui.components.PrimaryButton
import com.bizbite.printer.ui.theme.BizBiteTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI

@Composable
fun ProductCard(item: MenuItem, onAdd: (String?, List<com.bizbite.printer.data.remote.AddonDto>) -> Unit, modifier: Modifier = Modifier) {
    var selected by remember(item.id) { mutableStateOf(emptySet<String>()) }
    val selectedAddons = item.addons.filter { (it.id ?: it.name) in selected }
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ProductImage(item.imageUrl)
            val foodLabel = when (item.foodType?.lowercase()) {
                "veg" -> R.string.food_vegetarian
                "non-veg" -> R.string.food_non_vegetarian
                "egg" -> R.string.food_egg
                else -> null
            }
            foodLabel?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall) }
            Text(item.name, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
            Text(formatRupees(item.price), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            item.addons.forEach { addon ->
                Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = (addon.id ?: addon.name) in selected, onCheckedChange = { checked -> selected = if (checked) selected + (addon.id ?: addon.name) else selected - (addon.id ?: addon.name) })
                    Text(stringResource(R.string.addon_price_format, addon.name, formatRupees(addon.price)), style = MaterialTheme.typography.bodySmall)
                }
            }
            if (item.variants.isEmpty()) {
                PrimaryButton(stringResource(R.string.add), { onAdd(null, selectedAddons) }, Modifier.fillMaxWidth())
            } else item.variants.forEach { variant ->
                val price = variant.offerPrice ?: variant.priceDelta
                val validAddons = selectedAddons.filter { it.applicableVariants.isEmpty() || (variant.id != null && variant.id in it.applicableVariants) }
                PrimaryButton(stringResource(R.string.variant_price_format, variant.name, formatRupees(price)), { onAdd(variant.name, validAddons) }, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ProductImage(path: String?) {
    val bitmap by produceState<Bitmap?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val raw = path?.takeIf(String::isNotBlank) ?: return@runCatching null
                val uri = URI(raw)
                val url = if (uri.isAbsolute) uri.toURL() else {
                    val base = URI(BuildConfig.BBN_BASE_URL)
                    URI(base.scheme, base.authority, "/", null, null).resolve(raw).toURL()
                }
                url.openConnection().apply { connectTimeout = 8_000; readTimeout = 10_000 }.getInputStream().use { BitmapFactory.decodeStream(it) }
            }.getOrNull()
        }
    }
    if (bitmap != null) {
        Image(bitmap!!.asImageBitmap(), contentDescription = stringResource(R.string.product_image_description), modifier = Modifier.fillMaxWidth().height(104.dp), contentScale = ContentScale.Crop)
    } else {
        androidx.compose.foundation.layout.Box(
            Modifier.fillMaxWidth().height(104.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(stringResource(R.string.product_image_placeholder), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Preview(name = "Product Card Light")
@Composable
private fun ProductCardLightPreview() = BizBiteTheme(darkTheme = false) {
    ProductCard(MenuItem("p1", "Paneer Tikka", 220.0, "c1"), { _, _ -> })
}

@Preview(name = "Product Card Dark")
@Composable
private fun ProductCardDarkPreview() = BizBiteTheme(darkTheme = true) {
    ProductCard(MenuItem("p1", "Paneer Tikka", 220.0, "c1"), { _, _ -> })
}
