package com.bizbite.printer.domain.model

enum class PrinterStatus {
    CONNECTED, DISCONNECTED, BUSY, PAPER_OUT, COVER_OPEN, CONNECTION_LOST, PRINT_TIMEOUT, PRINT_FAILURE, UNKNOWN
}
enum class ConnectionType { BLUETOOTH, BLE, USB, LAN, MOCK }
enum class JobStatus { PENDING, PRINTING, SUCCESS, COMPLETED, FAILED, CANCELLED }

data class PrinterConfig(
    val id: String,
    val name: String,
    val type: ConnectionType,
    val address: String,
    val port: Int = 9100,
    val paperWidthMm: Int = 58,
    val isDefault: Boolean = false
)

data class PrintJob(
    val id: String,
    val payload: String,
    val status: JobStatus = JobStatus.PENDING,
    val retryCount: Int = 0,
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val serverStatusSynced: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val isSimulated: Boolean = false,
    val completedAt: Long? = null
)

data class PrintResult(val success: Boolean, val errorCode: String? = null, val errorMessage: String? = null)
