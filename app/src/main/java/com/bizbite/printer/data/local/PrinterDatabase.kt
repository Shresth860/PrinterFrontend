package com.bizbite.printer.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        LocalJobEntity::class,
        StatusOutboxEntity::class,
        PrinterStatusOutboxEntity::class,
        PrinterEntity::class
    ],
    version = 4
)
abstract class PrinterDatabase : RoomDatabase() {
    abstract fun printerDao(): PrinterDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE local_job ADD COLUMN isSimulated INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE local_job ADD COLUMN completedAt INTEGER DEFAULT NULL")
                database.execSQL(
                    "UPDATE local_job SET completedAt = createdAt WHERE status IN ('SUCCESS','COMPLETED')"
                )
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS printer_status_outbox (
                        id TEXT NOT NULL PRIMARY KEY,
                        printerId TEXT NOT NULL,
                        status TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        fun removeVirtualPrinterData(database: SupportSQLiteDatabase) {
            database.execSQL(
                "DELETE FROM status_outbox WHERE jobId IN (SELECT id FROM local_job WHERE isSimulated = 1)"
            )
            database.execSQL("DELETE FROM local_job WHERE isSimulated = 1")
            database.execSQL("DELETE FROM printer_config WHERE type = 'MOCK'")
            database.execSQL(
                """
                UPDATE printer_config
                SET isDefault = 1
                WHERE id = (
                    SELECT id FROM printer_config
                    WHERE type != 'MOCK'
                    ORDER BY isDefault DESC, rowid ASC
                    LIMIT 1
                )
                AND NOT EXISTS (SELECT 1 FROM printer_config WHERE isDefault = 1)
                """.trimIndent()
            )
        }
    }
}
