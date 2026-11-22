package com.bizbite.printer.ui.screens

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.bizbite.printer.R
import com.bizbite.printer.domain.model.JobStatus
import com.bizbite.printer.domain.model.PrintJob
import com.bizbite.printer.ui.MainViewModel
import com.bizbite.printer.ui.components.JobCard
import com.bizbite.printer.ui.components.PrimaryButton
import com.bizbite.printer.ui.components.SecondaryButton
import com.bizbite.printer.ui.components.SectionHeader
import com.bizbite.printer.ui.theme.BizBiteTheme

@Composable
fun HistoryScreen(model: MainViewModel) {
    val jobs by model.jobs.collectAsState()
    JobHistoryContent(
        jobs = jobs,
        onRetry = model::retry,
        onCancel = model::cancel,
        onReprint = model::reprint
    )
}

@Composable
private fun JobHistoryContent(
    jobs: List<PrintJob>,
    onRetry: (PrintJob) -> Unit,
    onCancel: (PrintJob) -> Unit,
    onReprint: (PrintJob) -> Unit
) {
    var selectedTab by remember { mutableStateOf("ALL") }
    val shown = jobs.filter { job ->
        when (selectedTab) {
            "PENDING" -> job.status == JobStatus.PENDING || job.status == JobStatus.PRINTING
            "COMPLETED" -> job.status == JobStatus.SUCCESS || job.status == JobStatus.COMPLETED || job.status == JobStatus.FAILED || job.status == JobStatus.CANCELLED
            else -> true
        }
    }.sortedByDescending(PrintJob::createdAt)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionHeader(stringResource(R.string.print_history_title))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("ALL" to R.string.queue_all, "PENDING" to R.string.queue_pending, "COMPLETED" to R.string.queue_completed).forEach { (key, label) ->
                FilterChip(selected = selectedTab == key, onClick = { selectedTab = key }, label = { Text(stringResource(label)) })
            }
        }
        if (shown.isEmpty()) {
            Text(stringResource(R.string.empty_job_history), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            shown.forEach { job ->
                JobCard(job, DateUtils.getRelativeTimeSpanString(job.createdAt).toString()) {
                    if (job.status == JobStatus.FAILED && job.errorMessage != null) {
                        Text(job.errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    if (job.status == JobStatus.SUCCESS && job.errorCode == "DELIVERY_UNCONFIRMED") {
                        Text(job.errorMessage ?: stringResource(R.string.delivery_unconfirmed_detail), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                    if (job.errorCode == "POSSIBLE_PARTIAL") {
                        Text(stringResource(R.string.possible_partial_warning), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    when (job.status) {
                        JobStatus.FAILED -> PrimaryButton(stringResource(R.string.retry_job), { onRetry(job) }, Modifier.fillMaxWidth())
                        JobStatus.SUCCESS, JobStatus.COMPLETED -> SecondaryButton(stringResource(R.string.reprint_job), { onReprint(job) }, Modifier.fillMaxWidth())
                        JobStatus.PENDING -> SecondaryButton(stringResource(R.string.cancel_job), { onCancel(job) }, Modifier.fillMaxWidth())
                        else -> Unit
                    }
                }
            }
        }
    }
}

@Preview(name = "History Light")
@Composable
private fun HistoryLightPreview() = BizBiteTheme(darkTheme = false) {
    JobHistoryContent(
        jobs = listOf(PrintJob("job-100", "", JobStatus.SUCCESS), PrintJob("job-099", "", JobStatus.PENDING), PrintJob("job-098", "", JobStatus.FAILED, errorMessage = "Printer unavailable")),
        onRetry = {}, onCancel = {}, onReprint = {}
    )
}

@Preview(name = "History Dark")
@Composable
private fun HistoryDarkPreview() = BizBiteTheme(darkTheme = true) {
    JobHistoryContent(
        jobs = listOf(PrintJob("job-100", "", JobStatus.SUCCESS), PrintJob("job-099", "", JobStatus.PENDING), PrintJob("job-098", "", JobStatus.FAILED, errorMessage = "Printer unavailable")),
        onRetry = {}, onCancel = {}, onReprint = {}
    )
}
