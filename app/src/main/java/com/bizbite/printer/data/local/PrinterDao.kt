package com.bizbite.printer.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface PrinterDao {
    @Query("SELECT * FROM local_job WHERE id = :id LIMIT 1")
    suspend fun findJob(id: String): LocalJobEntity?

    @Query("SELECT * FROM local_job WHERE status IN ('PENDING','PRINTING') ORDER BY createdAt ASC")
    suspend fun pendingJobs(): List<LocalJobEntity>

    @Query("SELECT * FROM local_job WHERE status = 'PRINTING' ORDER BY createdAt ASC")
    suspend fun interruptedJobs(): List<LocalJobEntity>

    @Query("SELECT * FROM local_job WHERE status NOT IN ('PENDING','PRINTING') ORDER BY createdAt DESC")
    fun observeHistory(): Flow<List<LocalJobEntity>>

    @Query("SELECT * FROM local_job ORDER BY CASE status WHEN 'PRINTING' THEN 0 WHEN 'PENDING' THEN 1 ELSE 2 END, createdAt ASC")
    fun observeJobs(): Flow<List<LocalJobEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertJob(job: LocalJobEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertJobIfAbsent(job: LocalJobEntity): Long

    @Query("SELECT COUNT(*) FROM local_job WHERE status IN ('PENDING','PRINTING')")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT MAX(completedAt) FROM local_job WHERE status IN ('SUCCESS','COMPLETED')")
    fun observeLastPrintAt(): Flow<Long?>

    @Query("SELECT * FROM status_outbox ORDER BY createdAt ASC")
    suspend fun pendingOutbox(): List<StatusOutboxEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addOutbox(item: StatusOutboxEntity)

    @Query("DELETE FROM status_outbox WHERE id = :id")
    suspend fun deleteOutbox(id: String)

    @Query("UPDATE local_job SET serverStatusSynced = 1 WHERE id = :jobId")
    suspend fun markStatusSynced(jobId: String)

    @Query("SELECT * FROM printer_status_outbox ORDER BY createdAt ASC")
    suspend fun pendingPrinterStatuses(): List<PrinterStatusOutboxEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addPrinterStatus(item: PrinterStatusOutboxEntity)

    @Query("DELETE FROM printer_status_outbox WHERE id = :id")
    suspend fun deletePrinterStatus(id: String)

    @Query("SELECT * FROM printer_config ORDER BY isDefault DESC")
    fun observePrinters(): Flow<List<PrinterEntity>>

    @Query("SELECT * FROM printer_config ORDER BY isDefault DESC")
    suspend fun allPrinters(): List<PrinterEntity>

    @Query("SELECT * FROM printer_config WHERE isDefault = 1 LIMIT 1")
    suspend fun defaultPrinter(): PrinterEntity?

    @Query("SELECT * FROM printer_config WHERE id = :id LIMIT 1")
    suspend fun findPrinter(id: String): PrinterEntity?

    @Query("UPDATE printer_config SET isDefault = 0, backendSynced = 0")
    suspend fun clearDefaultPrinter()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPrinter(printer: PrinterEntity)

    @Query("SELECT * FROM printer_config WHERE backendSynced = 0 AND type != 'MOCK'")
    suspend fun unsyncedPrinters(): List<PrinterEntity>

    @Query("UPDATE printer_config SET backendSynced = 1 WHERE id = :id")
    suspend fun markPrinterSynced(id: String)

    @Transaction
    suspend fun saveDefaultPrinter(printer: PrinterEntity) {
        clearDefaultPrinter()
        upsertPrinter(printer.copy(isDefault = true))
    }
}
