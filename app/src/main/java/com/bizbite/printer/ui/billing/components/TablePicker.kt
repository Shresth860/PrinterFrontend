package com.bizbite.printer.ui.billing.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bizbite.printer.R
import com.bizbite.printer.data.menu.SellerTable

@Composable
fun TablePicker(tables: List<SellerTable>, selected: String?, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.select_table))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tables.forEach { table ->
                val label = table.capacity?.let { stringResource(R.string.table_with_capacity, table.number, it) }
                    ?: stringResource(R.string.table_number, table.number)
                FilterChip(selected == table.id, onClick = { onSelect(table.id) }, label = { Text(label) })
            }
        }
    }
}
