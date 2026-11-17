package com.bizbite.printer.ui.billing.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.bizbite.printer.R
import com.bizbite.printer.ui.billing.BillTotals
import com.bizbite.printer.ui.billing.formatRupees
import com.bizbite.printer.ui.theme.BizBiteTheme

@Composable
fun BillSummary(totals: BillTotals) {
    Card(shape = MaterialTheme.shapes.medium, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryRow(stringResource(R.string.subtotal), totals.subtotal)
            SummaryRow(stringResource(R.string.discount_label), -totals.discount)
            SummaryRow(stringResource(R.string.gst_label), totals.gst)
            androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            SummaryRow(stringResource(R.string.grand_total), totals.grandTotal, bold = true)
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: Double, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, style = if (bold) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium)
        Text(formatRupees(value), fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, style = if (bold) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium)
    }
}

@Preview(name = "Bill Summary Light")
@Composable
private fun BillSummaryLightPreview() = BizBiteTheme(darkTheme = false) { BillSummary(BillTotals(450.0, 20.0, 21.5, 452.0)) }

@Preview(name = "Bill Summary Dark")
@Composable
private fun BillSummaryDarkPreview() = BizBiteTheme(darkTheme = true) { BillSummary(BillTotals(450.0, 20.0, 21.5, 452.0)) }
