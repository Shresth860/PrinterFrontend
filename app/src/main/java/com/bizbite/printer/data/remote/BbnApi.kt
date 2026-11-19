package com.bizbite.printer.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PATCH
import retrofit2.http.Path
import retrofit2.http.Query
import java.io.IOException

class BbnHttpException(val statusCode: Int, override val message: String) : IOException(message)

interface ApiEnvelope { val success: Boolean; val message: String? }

@Serializable data class LoginInitRequest(val identifier: String)
@Serializable data class OtpVerifyRequest(val reqId: String, val otp: String, val purpose: String)
@Serializable data class LoginRequest(val identifier: String, val pin: String? = null, val verificationToken: String? = null)
@Serializable data class LoginAppRequest(
    val identifier: String,
    val pin: String,
    @SerialName("fcm_token") val fcmToken: String? = null
)
@Serializable data class ApiMessageResponse(override val success: Boolean = false, override val message: String? = null) : ApiEnvelope
@Serializable data class PairingCodeResponse(
    override val success: Boolean = false,
    override val message: String? = null,
    val code: String? = null,
    val expiresAt: String? = null
) : ApiEnvelope
@Serializable data class LoginInitResponse(override val success: Boolean = false, override val message: String? = null, val reqId: String? = null, val requiresOtp: Boolean? = null, val role: String? = null, val token: String? = null, val isAgreed: Boolean? = null, val user: BbnUser? = null, val profile: kotlinx.serialization.json.JsonElement? = null) : ApiEnvelope
@Serializable data class OtpVerifyResponse(override val success: Boolean = false, override val message: String? = null, val verificationToken: String? = null, val mobile: String? = null) : ApiEnvelope
@Serializable data class LoginResponse(override val success: Boolean = false, override val message: String? = null, val token: String? = null, val isAgreed: Boolean? = null, val user: BbnUser? = null, val profile: kotlinx.serialization.json.JsonElement? = null) : ApiEnvelope
@Serializable data class BbnUser(val id: String? = null, val name: String? = null, val role: String? = null, val phone: String? = null)

@Serializable data class CategoryDto(@SerialName("_id") val id: String, val name: String, @SerialName("is_active") val isActive: Boolean = true, @SerialName("display_order") val displayOrder: Int = 0, @SerialName("createdAt") val createdAt: String = "")
@Serializable data class CategoriesResponse(override val success: Boolean = false, override val message: String? = null, val categories: List<CategoryDto> = emptyList()) : ApiEnvelope
@Serializable data class VariantDto(@SerialName("_id") val id: String? = null, val name: String, @SerialName("price_delta") val priceDelta: Double = 0.0, @SerialName("offer_price") val offerPrice: Double? = null)
@Serializable data class AddonDto(@SerialName("_id") val id: String? = null, val name: String, val price: Double = 0.0, @SerialName("applicable_variants") val applicableVariants: List<String> = emptyList())
@Serializable data class ProductDto(@SerialName("_id") val id: String, val name: String, val price: Double = 0.0, @SerialName("offer_price") val offerPrice: Double? = null, @SerialName("category_id") val categoryId: String? = null, @SerialName("is_available") val isAvailable: Boolean = true, @SerialName("is_deleted") val isDeleted: Boolean = false, val image: String? = null, val imageUrl: String? = null, @SerialName("tax_percent") val taxPercent: Double = 0.0, @SerialName("food_type") val foodType: String? = null, val variants: List<VariantDto> = emptyList(), val addons: List<AddonDto> = emptyList(), @SerialName("is_combo") val isCombo: Boolean = false)
@Serializable data class ProductsResponse(override val success: Boolean = false, override val message: String? = null, val products: List<ProductDto> = emptyList()) : ApiEnvelope
@Serializable data class TableDto(@SerialName("_id") val id: String, @SerialName("table_number") val tableNumber: String, val capacity: Int? = null, @SerialName("is_active") val isActive: Boolean = true)
@Serializable data class TablesResponse(override val success: Boolean = false, override val message: String? = null, val tables: List<TableDto> = emptyList()) : ApiEnvelope

@Serializable data class OrderItemRequest(@SerialName("product_id") val productId: String? = null, @SerialName("combo_id") val comboId: String? = null, val quantity: Int, val variant: VariantSelection? = null, val addons: List<AddonSelection> = emptyList(), val instruction: String? = null)
@Serializable data class VariantSelection(val name: String)
@Serializable data class AddonSelection(@SerialName("addon_id") val addonId: String? = null, val name: String? = null)
@Serializable data class CounterBillingRequest(@SerialName("customer_name") val customerName: String, @SerialName("customer_phone") val customerPhone: String, val items: List<OrderItemRequest>, @SerialName("payment_method") val paymentMethod: String, @SerialName("order_type") val orderType: String, @SerialName("table_id") val tableId: String? = null, @SerialName("manual_discount_type") val manualDiscountType: String? = null, @SerialName("manual_discount_value") val manualDiscountValue: Double = 0.0, @SerialName("coupon_code") val couponCode: String? = null, @SerialName("discount_code") val discountCode: String? = null, @SerialName("apply_offers") val applyOffers: Boolean = true)
@Serializable data class SellerOrderDto(@SerialName("_id") val id: String? = null, @SerialName("invoice_number") val invoiceNumber: String? = null, @SerialName("kot_number") val kotNumber: String? = null, @SerialName("total_amount") val totalAmount: Double? = null, @SerialName("subtotal") val subtotal: Double? = null, @SerialName("discount_amount") val discountAmount: Double? = null, @SerialName("tax_amount") val taxAmount: Double? = null, @SerialName("order_type") val orderType: String? = null, @SerialName("createdAt") val createdAt: String? = null, @SerialName("invoice_printed_at") val invoicePrintedAt: String? = null, @SerialName("kot_printed_at") val kotPrintedAt: String? = null, @SerialName("customer_name") val customerName: String? = null, @SerialName("customer_phone") val customerPhone: String? = null, @SerialName("payment_method") val paymentMethod: String? = null, @SerialName("payment_status") val paymentStatus: String? = null, @SerialName("delivery_status") val deliveryStatus: String? = null, @SerialName("table_number") val tableNumber: String? = null, @SerialName("items") val items: List<kotlinx.serialization.json.JsonElement> = emptyList())
@Serializable data class CounterBillingResponse(override val success: Boolean = false, override val message: String? = null, val order: SellerOrderDto? = null) : ApiEnvelope
@Serializable data class OrdersResponse(override val success: Boolean = false, override val message: String? = null, val count: Int = 0, val orders: List<SellerOrderDto> = emptyList(), val pagination: kotlinx.serialization.json.JsonElement? = null) : ApiEnvelope
@Serializable data class OrderDetailResponse(override val success: Boolean = false, override val message: String? = null, val data: SellerOrderDto? = null, val order: SellerOrderDto? = null) : ApiEnvelope
@Serializable data class PrintModeRequest(val mode: String)
@Serializable data class PrintNumbersDto(@SerialName("kot_number") val kotNumber: Int? = null, @SerialName("kot_printed_at") val kotPrintedAt: String? = null, @SerialName("invoice_seq") val invoiceSeq: Int? = null, @SerialName("invoice_number") val invoiceNumber: String? = null, @SerialName("invoice_printed_at") val invoicePrintedAt: String? = null)
@Serializable data class PrintNumbersResponse(override val success: Boolean = false, override val message: String? = null, val numbers: PrintNumbersDto? = null) : ApiEnvelope

interface BbnApi {
    @POST("seller/printer/pairing-code") suspend fun createPairingCode(): PairingCodeResponse
    @POST("users/login/app") suspend fun loginApp(@Body body: LoginAppRequest): LoginResponse
    @POST("users/login/init") suspend fun loginInit(@Body body: LoginInitRequest): LoginInitResponse
    @POST("users/verify-otp") suspend fun verifyOtp(@Body body: OtpVerifyRequest): OtpVerifyResponse
    @POST("users/login") suspend fun login(@Body body: LoginRequest): LoginResponse
    @GET("seller/me") suspend fun sellerMe(): kotlinx.serialization.json.JsonObject
    @GET("menu-categories/get") suspend fun categories(): CategoriesResponse
    @GET("products/dashboard/all") suspend fun products(): ProductsResponse
    @GET("tables/seller/list") suspend fun tables(): TablesResponse
    @POST("orders/counter-billing") suspend fun counterBilling(@Body body: CounterBillingRequest): CounterBillingResponse
    @GET("orders/list") suspend fun orders(@Query("page") page: Int, @Query("limit") limit: Int): OrdersResponse
    @GET("orders/{id}") suspend fun order(@Path("id") id: String): OrderDetailResponse
    @POST("orders/{id}/print-numbers") suspend fun reservePrintNumbers(@Path("id") id: String, @Body body: PrintModeRequest): PrintNumbersResponse
    @POST("orders/{id}/print-confirm") suspend fun confirmPrint(@Path("id") id: String, @Body body: PrintModeRequest): PrintNumbersResponse

    @GET("credit-debit-notes") suspend fun creditDebitNotes(): kotlinx.serialization.json.JsonObject
    @GET("credit-debit-notes/{id}") suspend fun creditDebitNote(@Path("id") id: String): kotlinx.serialization.json.JsonObject
    @POST("credit-debit-notes/{type}") suspend fun createCreditDebitNote(
        @Path("type") type: String,
        @Body body: kotlinx.serialization.json.JsonObject
    ): kotlinx.serialization.json.JsonObject
    @PATCH("credit-debit-notes/{id}/cancel") suspend fun cancelCreditDebitNote(
        @Path("id") id: String
    ): kotlinx.serialization.json.JsonObject
}
