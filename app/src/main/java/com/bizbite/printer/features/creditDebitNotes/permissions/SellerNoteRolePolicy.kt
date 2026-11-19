package com.bizbite.printer.features.creditDebitNotes.permissions

enum class SellerNoteRole { STAFF_CASHIER, MANAGER, OWNER_ADMIN, PLATFORM_SUPPORT, RESTRICTED }

data class SellerNotePermissions(
    val canView: Boolean,
    val canCreateNotes: Boolean,
    val canFullCancelInvoice: Boolean,
    val canCancelNote: Boolean,
    val canApproveRefund: Boolean,
    val canVerifyManualRefund: Boolean,
    val canOverrideNoteTax: Boolean = false
)

object SellerNoteRolePolicy {
    fun mapRole(raw: String?): SellerNoteRole = when (raw.normalizedRole()) {
        "staff", "cashier", "staff cashier", "staff/cashier" -> SellerNoteRole.STAFF_CASHIER
        "manager" -> SellerNoteRole.MANAGER
        "owner", "admin", "owner admin", "owner/admin" -> SellerNoteRole.OWNER_ADMIN
        "platform support", "platform_support", "support" -> SellerNoteRole.PLATFORM_SUPPORT
        else -> SellerNoteRole.RESTRICTED
    }

    fun permissions(raw: String?): SellerNotePermissions = when (mapRole(raw)) {
        SellerNoteRole.STAFF_CASHIER -> SellerNotePermissions(true, true, false, false, false, false)
        SellerNoteRole.MANAGER -> SellerNotePermissions(true, true, false, false, true, true)
        SellerNoteRole.OWNER_ADMIN -> SellerNotePermissions(true, true, true, true, true, true, true)
        SellerNoteRole.PLATFORM_SUPPORT -> SellerNotePermissions(true, false, false, false, false, false)
        // Unknown roles can inspect data and start a standard note only.
        SellerNoteRole.RESTRICTED -> SellerNotePermissions(true, true, false, false, false, false)
    }

    private fun String?.normalizedRole(): String = this.orEmpty()
        .trim()
        .lowercase()
        .replace(Regex("[_-]+"), " ")
        .replace(Regex("\\s+"), " ")
}
