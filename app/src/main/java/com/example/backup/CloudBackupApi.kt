package com.example.backup

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * Authenticated HTTPS REST API contract communicating with the Chroniq PostgreSQL backend.
 * PostgreSQL credentials are NEVER stored or transmitted from the Android client.
 */
interface CloudBackupApi {

    @POST("api/v1/chroniq/backups")
    suspend fun uploadBackup(
        @Header("Authorization") bearerToken: String,
        @Header("X-Device-Id") deviceId: String,
        @Body request: CloudBackupUploadRequest,
    ): Response<CloudBackupSummaryDto>

    @GET("api/v1/chroniq/backups")
    suspend fun listBackups(
        @Header("Authorization") bearerToken: String,
        @Header("X-Device-Id") deviceId: String,
        @Query("user_email") userEmail: String,
    ): Response<CloudBackupListResponse>

    @GET("api/v1/chroniq/backups/{backupId}")
    suspend fun downloadBackup(
        @Header("Authorization") bearerToken: String,
        @Header("X-Device-Id") deviceId: String,
        @Path("backupId") backupId: String,
    ): Response<CloudBackupDetailResponse>

    @POST("api/v1/chroniq/sync")
    suspend fun syncAlarms(
        @Header("Authorization") bearerToken: String,
        @Header("X-Device-Id") deviceId: String,
        @Body request: CloudSyncRequest,
    ): Response<CloudSyncResponse>

    companion object {
        private val BLOCKED_HOSTS = setOf("localhost", "127.0.0.1", "0.0.0.0", "::1", "169.254.169.254")

        /**
         * Validates that the configured backend endpoint uses HTTPS and does not target
         * local loopback or link-local metadata endpoints (SSRF mitigation).
         */
        fun validateHttpsEndpoint(rawUrl: String): Result<String> {
            val trimmed = rawUrl.trim()
            if (trimmed.isBlank()) {
                return Result.failure(IllegalArgumentException("URL endpoint backend HTTPS belum dikonfigurasi."))
            }
            val parsed = runCatching { URI(trimmed) }.getOrNull()
                ?: return Result.failure(IllegalArgumentException("Format URL tidak valid."))

            if (!parsed.scheme.equals("https", ignoreCase = true)) {
                return Result.failure(IllegalArgumentException("Backend API wajib menggunakan HTTPS demi keamanan."))
            }
            val host = parsed.host?.lowercase()
                ?: return Result.failure(IllegalArgumentException("Host URL tidak valid."))

            if (host in BLOCKED_HOSTS ||
                host.startsWith("10.") ||
                host.startsWith("192.168.") ||
                host.startsWith("169.254.") ||
                host.endsWith(".local")
            ) {
                return Result.failure(IllegalArgumentException("Host jaringan internal/loopback tidak diizinkan."))
            }

            val normalized = if (trimmed.endsWith("/")) trimmed else "$trimmed/"
            return Result.success(normalized)
        }

        fun create(validatedBaseUrl: String): CloudBackupApi {
            val moshi = Moshi.Builder()
                .addLast(KotlinJsonAdapterFactory())
                .build()

            val okHttp = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS)
                .writeTimeout(25, TimeUnit.SECONDS)
                .build()

            return Retrofit.Builder()
                .baseUrl(validatedBaseUrl)
                .client(okHttp)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(CloudBackupApi::class.java)
        }
    }
}
