package com.bizbite.printer.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.bizbite.printer.BuildConfig
import com.bizbite.printer.data.PrinterRepositoryImpl
import com.bizbite.printer.data.auth.AuthRepository
import com.bizbite.printer.data.auth.BbnAuthRepository
import com.bizbite.printer.data.local.PrinterDao
import com.bizbite.printer.data.local.PrinterDatabase
import com.bizbite.printer.data.remote.PrintServiceApi
import com.bizbite.printer.data.remote.TokenAuthenticator
import com.bizbite.printer.data.security.TokenStore
import com.bizbite.printer.data.session.PersistentSessionManager
import com.bizbite.printer.data.session.SessionManager
import com.bizbite.printer.data.menu.MenuRepository
import com.bizbite.printer.data.menu.BbnMenuRepository
import com.bizbite.printer.features.creditDebitNotes.services.CreditDebitNotesRepository
import com.bizbite.printer.features.creditDebitNotes.services.BbnCreditDebitNotesRepository
import com.bizbite.printer.domain.repository.PrinterRepository
import com.bizbite.printer.printer.PrinterConnectionFactory
import com.bizbite.printer.printer.PrinterConnectionFactoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.HttpUrl.Companion.toHttpUrl
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType

internal fun shouldAttachBbnAuthorization(encodedPath: String): Boolean =
    !encodedPath.endsWith("/users/login/init") &&
        !encodedPath.endsWith("/users/verify-otp") &&
        !encodedPath.endsWith("/users/login") &&
        !encodedPath.endsWith("/users/login/app")

internal fun shouldLogoutBbnSession(responseCode: Int, authorizationHeader: String?): Boolean =
    responseCode == 401 && authorizationHeader != null

@Module
@InstallIn(SingletonComponent::class)
abstract class AppBindings {
    @Binds @Singleton abstract fun bindPrinterRepository(implementation: PrinterRepositoryImpl): PrinterRepository
    @Binds @Singleton abstract fun bindPrinterConnectionFactory(implementation: PrinterConnectionFactoryImpl): PrinterConnectionFactory
    @Binds @Singleton abstract fun bindSessionManager(implementation: PersistentSessionManager): SessionManager
    @Binds @Singleton abstract fun bindAuthRepository(implementation: BbnAuthRepository): AuthRepository
    @Binds @Singleton abstract fun bindMenuRepository(implementation: BbnMenuRepository): MenuRepository
    @Binds @Singleton abstract fun bindCreditDebitNotesRepository(implementation: BbnCreditDebitNotesRepository): CreditDebitNotesRepository
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun database(@ApplicationContext context: Context): PrinterDatabase =
        Room.databaseBuilder(context, PrinterDatabase::class.java, "bizbite-printer.db")
            .addMigrations(
                PrinterDatabase.MIGRATION_1_2,
                PrinterDatabase.MIGRATION_2_3,
                PrinterDatabase.MIGRATION_3_4
            )
            .addCallback(object : RoomDatabase.Callback() {
                override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    PrinterDatabase.removeVirtualPrinterData(db)
                }
            })
            .build()

    @Provides fun printerDao(database: PrinterDatabase): PrinterDao = database.printerDao()

    @Provides @Singleton
    fun json(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = false
        explicitNulls = false
    }

    @Provides @Singleton @Named("refresh")
    fun refreshClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(java.time.Duration.ofSeconds(20))
        .readTimeout(java.time.Duration.ofSeconds(90))
        .callTimeout(java.time.Duration.ofSeconds(100))
        .build()

    @Provides @Singleton
    fun authenticator(tokenStore: TokenStore, json: Json, @Named("refresh") client: OkHttpClient) =
        TokenAuthenticator(tokenStore, json, BuildConfig.BASE_URL, client)

    @Provides @Singleton
    fun okHttpClient(tokenStore: TokenStore, authenticator: TokenAuthenticator): OkHttpClient {
        val authHeader = Interceptor { chain ->
            val request: Request = chain.request().newBuilder().apply {
                tokenStore.accessToken()
                    ?.takeUnless { tokenStore.isAccessTokenExpired() }
                    ?.let { header("Authorization", "Bearer $it") }
            }.build()
            chain.proceed(request)
        }
        val logging = HttpLoggingInterceptor().apply {
            redactHeader("Authorization")
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        }
        return OkHttpClient.Builder()
            .addInterceptor(authHeader)
            .addInterceptor(logging)
            .authenticator(authenticator)
            .connectTimeout(java.time.Duration.ofSeconds(20))
            .readTimeout(java.time.Duration.ofSeconds(90))
            .callTimeout(java.time.Duration.ofSeconds(100))
            .build()
    }

    @Provides @Singleton
    fun retrofit(client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides @Singleton
    fun api(retrofit: Retrofit): PrintServiceApi = retrofit.create(PrintServiceApi::class.java)

    @Provides @Singleton @Named("bbnClient")
    fun bbnClient(session: com.bizbite.printer.data.security.SellerSessionStore, sessionManager: com.bizbite.printer.data.session.SessionManager): OkHttpClient {
        if (BuildConfig.DEBUG) {
            val bbnUrl = BuildConfig.BBN_BASE_URL.toHttpUrl()
            android.util.Log.d("BbnApi", "BBN base URL: ${bbnUrl.host}${bbnUrl.encodedPath}")
        }
        val logging = HttpLoggingInterceptor().apply {
            redactHeader("Authorization")
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        }
        return OkHttpClient.Builder()
        .addInterceptor(Interceptor { chain ->
            val request = chain.request().newBuilder().apply {
                val path = chain.request().url.encodedPath
                if (shouldAttachBbnAuthorization(path)) {
                    session.token()?.takeIf(String::isNotBlank)?.let { header("Authorization", "Bearer $it") }
                }
            }.build()
            val response = chain.proceed(request)
            if (response.isSuccessful) return@Interceptor response
            if (shouldLogoutBbnSession(response.code, request.header("Authorization"))) sessionManager.logout()
            val body = response.peekBody(1024L * 1024L).string()
            val message = runCatching { org.json.JSONObject(body).optString("message") }.getOrNull()
                ?.takeIf { it.isNotBlank() }
            response.close()
            throw com.bizbite.printer.data.remote.BbnHttpException(response.code, message ?: "BBN request failed (HTTP ${response.code}).")
        })
        .addInterceptor(logging)
        .connectTimeout(java.time.Duration.ofSeconds(20))
        .readTimeout(java.time.Duration.ofSeconds(60))
        .callTimeout(java.time.Duration.ofSeconds(70))
        .build()
    }

    @Provides @Singleton @Named("bbnRetrofit")
    fun bbnRetrofit(@Named("bbnClient") client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder().baseUrl(BuildConfig.BBN_BASE_URL).client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build()

    /** Dedicated create-note client: never replay a POST after an ambiguous transport failure. */
    @Provides @Singleton @Named("bbnCreateApi")
    fun bbnCreateApi(@Named("bbnClient") source: OkHttpClient, json: Json): com.bizbite.printer.data.remote.BbnApi =
        Retrofit.Builder().baseUrl(BuildConfig.BBN_BASE_URL)
            .client(source.newBuilder().retryOnConnectionFailure(false).build())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build().create(com.bizbite.printer.data.remote.BbnApi::class.java)

    @Provides @Singleton
    fun bbnApi(@Named("bbnRetrofit") retrofit: Retrofit): com.bizbite.printer.data.remote.BbnApi =
        retrofit.create(com.bizbite.printer.data.remote.BbnApi::class.java)
}
