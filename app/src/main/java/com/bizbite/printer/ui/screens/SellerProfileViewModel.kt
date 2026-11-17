package com.bizbite.printer.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bizbite.printer.data.remote.BbnApi
import com.bizbite.printer.data.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@HiltViewModel
class SellerProfileViewModel @Inject constructor(private val api: BbnApi, private val session: SessionManager) : ViewModel() {
    private val mutableName = MutableStateFlow<String?>(null)
    val storeName = mutableName.asStateFlow()
    private val mutableRecentOrders = MutableStateFlow<List<com.bizbite.printer.data.remote.SellerOrderDto>>(emptyList())
    val recentOrders = mutableRecentOrders.asStateFlow()
    init {
        viewModelScope.launch {
            runCatching {
                val seller = api.sellerMe()["seller"]?.jsonObject ?: error("Seller profile was not returned.")
                val agreed = seller["isAgreed"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()
                if (agreed == false) {
                    session.logout()
                    error("Seller agreement is not accepted; seller operations are blocked until agreement is completed in the seller portal.")
                }
                val store = seller["store_profile"]?.jsonObject
                val name = store?.string("store_name") ?: seller["business_info"]?.jsonObject?.string("business_name")
                mutableName.value = name?.takeIf(String::isNotBlank)
            }
            runCatching {
                val response = api.orders(1, 3)
                if (response.success) mutableRecentOrders.value = response.orders
            }
        }
    }
    private fun JsonObject.string(key: String) = this[key]?.jsonPrimitive?.contentOrNull
}
