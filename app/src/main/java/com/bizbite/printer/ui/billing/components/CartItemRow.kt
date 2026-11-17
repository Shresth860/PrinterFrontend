package com.bizbite.printer.ui.billing.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bizbite.printer.R
import com.bizbite.printer.ui.billing.CartItem
import com.bizbite.printer.ui.billing.formatRupees

@Composable
fun CartItemRow(item: CartItem, onQuantityChange: (Int) -> Unit, onRemove: () -> Unit) {
    val quantityDesc = stringResource(R.string.item_quantity_accessibility, item.item.name)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.item.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                item.variantName?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (item.addons.isNotEmpty()) Text(item.addons.joinToString { it.name }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val base = item.variantName?.let { name -> item.item.variants.firstOrNull { it.name == name }?.let { it.offerPrice ?: it.priceDelta } } ?: item.item.price
                Text(formatRupees((base + item.addons.sumOf { it.price }) * item.quantity), style = MaterialTheme.typography.labelLarge)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val decreaseDescription = stringResource(R.string.decrease_quantity, item.item.name)
                val increaseDescription = stringResource(R.string.increase_quantity, item.item.name)
                val removeDescription = stringResource(R.string.remove_item, item.item.name)
                IconButton(onClick = { onQuantityChange(-1) }, modifier = Modifier.size(48.dp).semantics { contentDescription = decreaseDescription }) {
                    Icon(painterResource(R.drawable.ic_remove), contentDescription = decreaseDescription, tint = MaterialTheme.colorScheme.primary)
                }
                Text(item.quantity.toString(), modifier = Modifier.semantics { contentDescription = quantityDesc }, style = MaterialTheme.typography.titleSmall)
                IconButton(onClick = { onQuantityChange(1) }, modifier = Modifier.size(48.dp).semantics { contentDescription = increaseDescription }) {
                    Icon(painterResource(R.drawable.ic_add), contentDescription = increaseDescription, tint = MaterialTheme.colorScheme.primary)
                }
                IconButton(onClick = onRemove, modifier = Modifier.size(48.dp).semantics { contentDescription = removeDescription }) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = removeDescription, tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
