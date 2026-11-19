package com.bizbite.printer.features.creditDebitNotes.config

import javax.inject.Inject
import javax.inject.Singleton

/** Only capabilities absent from the audited backend default to mock mode. */
data class CreditDebitEndpointConfig(val useMock: Boolean)

data class CreditDebitConfig(
    val notesList: CreditDebitEndpointConfig = CreditDebitEndpointConfig(false),
    val noteDetail: CreditDebitEndpointConfig = CreditDebitEndpointConfig(false),
    val createNote: CreditDebitEndpointConfig = CreditDebitEndpointConfig(false),
    val cancelNote: CreditDebitEndpointConfig = CreditDebitEndpointConfig(false),
    val orderDetail: CreditDebitEndpointConfig = CreditDebitEndpointConfig(false),
    val invoicePayments: CreditDebitEndpointConfig = CreditDebitEndpointConfig(true),
    val eligibility: CreditDebitEndpointConfig = CreditDebitEndpointConfig(false),
    val fullCancellation: CreditDebitEndpointConfig = CreditDebitEndpointConfig(true),
    val razorpayRefund: CreditDebitEndpointConfig = CreditDebitEndpointConfig(true),
    val manualRefund: CreditDebitEndpointConfig = CreditDebitEndpointConfig(true),
    val creditApplication: CreditDebitEndpointConfig = CreditDebitEndpointConfig(true),
    val refundStatus: CreditDebitEndpointConfig = CreditDebitEndpointConfig(true),
    val notePdf: CreditDebitEndpointConfig = CreditDebitEndpointConfig(true),
    val customerCreditBalance: CreditDebitEndpointConfig = CreditDebitEndpointConfig(true),
    val reauthApproval: CreditDebitEndpointConfig = CreditDebitEndpointConfig(true),
    val discountAdjustment: CreditDebitEndpointConfig = CreditDebitEndpointConfig(true)
)

val DefaultCreditDebitConfig = CreditDebitConfig()

@Singleton
class CreditDebitEndpointSettings @Inject constructor() {
    @Volatile var current: CreditDebitConfig = DefaultCreditDebitConfig
}
