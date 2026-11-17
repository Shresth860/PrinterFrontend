package com.bizbite.printer.data.local

import androidx.room.Entity
import androidx.room.ColumnInfo
import androidx.room.PrimaryKey
import com.bizbite.printer.domain.model.ConnectionType
import com.bizbite.printer.domain.model.JobStatus
import com.bizbite.printer.domain.model.PrintJob
import com.bizbite.printer.domain.model.PrinterConfig

@Entity(tableName = "local_job")
data class LocalJobEntity(
    @PrimaryKey val id: String,
    val payload: String,
    val status: String,
    val retryCount: Int,
    val errorCode: String?,
    val errorMessage: String?,
    val serverStatusSynced: Boolean,
    val createdAt: Long,
    @ColumnInfo(defaultValue = "0") val isSimulated: Boolean = false,
    @ColumnInfo(defaultValue = "NULL") val completedAt: Long? = null
) {
    fun toModel() = PrintJob(
        id, payload, JobStatus.valueOf(status), retryCount, errorCode, errorMessage,
        serverStatusSynced, createdAt, isSimulated, completedAt
    )

    companion object {
        fun from(job: PrintJob) = LocalJobEntity(
            job.id, job.payload, job.status.name, job.retryCount, job.errorCode, job.errorMessage,
            job.serverStatusSynced, job.createdAt, job.isSimulated, job.completedAt
        )
    }
}

@Entity(tableName = "status_outbox")
data class StatusOutboxEntity(
    @PrimaryKey val id: String,
    val jobId: String,
    val status: String,
    val errorCode: String?,
    val errorMessage: String?,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "printer_status_outbox")
data class PrinterStatusOutboxEntity(
    @PrimaryKey val id: String,
    val printerId: String,
    val status: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "printer_config")
data class PrinterEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val address: String,
    val port: Int,
    val paperWidthMm: Int,
    val isDefault: Boolean,
    val backendSynced: Boolean = false
) {
    fun toModel() = PrinterConfig(id, name, ConnectionType.valueOf(type), address, port, paperWidthMm, isDefault)

    companion object {
        fun from(printer: PrinterConfig) = PrinterEntity(
            printer.id, printer.name, printer.type.name, printer.address, printer.port,
            printer.paperWidthMm, printer.isDefault, false
        )
    }
}
