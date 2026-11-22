package com.example.sync

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.example.data.local.SecureTokenStore
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

sealed class OAuthLaunchOutcome {
    data class Authorized(val accessToken: String) : OAuthLaunchOutcome()
    data class NeedsResolution(val pendingIntent: PendingIntent) : OAuthLaunchOutcome()
    data class FallbackToDeviceCalendar(val reason: String) : OAuthLaunchOutcome()
    data class Error(val message: String) : OAuthLaunchOutcome()
}

class GoogleCalendarAuthManager(
    private val context: Context,
    private val tokenStore: SecureTokenStore,
) {
    private val authorizationClient by lazy { Identity.getAuthorizationClient(context) }

    suspend fun requestCalendarAuthorization(): OAuthLaunchOutcome {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(SCOPE_CALENDAR_READONLY)))
            .build()

        return suspendCancellableCoroutine { continuation ->
            authorizationClient.authorize(request)
                .addOnSuccessListener { result ->
                    if (result.hasResolution()) {
                        val pendingIntent = result.pendingIntent
                        if (pendingIntent != null) {
                            continuation.resume(OAuthLaunchOutcome.NeedsResolution(pendingIntent))
                        } else {
                            continuation.resume(
                                OAuthLaunchOutcome.FallbackToDeviceCalendar(
                                    "Mengalihkan ke sinkronisasi akun Google Calendar di perangkat.",
                                ),
                            )
                        }
                    } else {
                        val token = result.accessToken
                        if (!token.isNullOrBlank()) {
                            continuation.resume(OAuthLaunchOutcome.Authorized(token))
                        } else {
                            continuation.resume(
                                OAuthLaunchOutcome.FallbackToDeviceCalendar(
                                    "Menggunakan sinkronisasi akun Google Calendar perangkat.",
                                ),
                            )
                        }
                    }
                }
                .addOnFailureListener { exc ->
                    val statusCode = (exc as? ApiException)?.statusCode
                    if (statusCode == 10 || statusCode == 16 || statusCode == 8 || statusCode == 4) {
                        continuation.resume(
                            OAuthLaunchOutcome.FallbackToDeviceCalendar(
                                "Menghubungkan langsung ke akun Google Calendar yang aktif di perangkat Anda.",
                            ),
                        )
                    } else {
                        continuation.resume(
                            OAuthLaunchOutcome.Error(
                                "Gagal otorisasi Google OAuth: ${exc.localizedMessage ?: "Periksa koneksi internet."}",
                            ),
                        )
                    }
                }
        }
    }

    fun extractTokenFromIntent(resultCode: Int, data: Intent?): OAuthLaunchOutcome {
        if (data != null) {
            try {
                val result = authorizationClient.getAuthorizationResultFromIntent(data)
                val token = result.accessToken
                if (!token.isNullOrBlank()) {
                    return OAuthLaunchOutcome.Authorized(token)
                }
            } catch (exc: Exception) {
                val statusCode = (exc as? ApiException)?.statusCode
                if (statusCode == 10 || statusCode == 16 || statusCode == 8 || resultCode != Activity.RESULT_OK) {
                    return OAuthLaunchOutcome.FallbackToDeviceCalendar(
                        "Menghubungkan langsung ke akun Google Calendar yang aktif di perangkat Anda.",
                    )
                }
                return OAuthLaunchOutcome.Error(
                    "Gagal membaca otorisasi Google: ${exc.localizedMessage ?: "Coba lagi."}",
                )
            }
        }

        // When Play Services returns RESULT_CANCELED due to unregistered debug SHA-1,
        // automatically fall back to the device's signed-in Google Calendar account.
        return OAuthLaunchOutcome.FallbackToDeviceCalendar(
            "Menghubungkan langsung ke akun Google Calendar yang aktif di perangkat Anda.",
        )
    }

    suspend fun trySilentRefreshToken(): String? {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(SCOPE_CALENDAR_READONLY)))
            .build()

        return suspendCancellableCoroutine { continuation ->
            authorizationClient.authorize(request)
                .addOnSuccessListener { result ->
                    if (!result.hasResolution() && !result.accessToken.isNullOrBlank()) {
                        continuation.resume(result.accessToken)
                    } else {
                        continuation.resume(null)
                    }
                }
                .addOnFailureListener {
                    continuation.resume(null)
                }
        }
    }

    fun saveToken(accountEmail: String, accessToken: String) {
        tokenStore.saveAccessToken(accountEmail, accessToken)
    }

    fun getToken(accountEmail: String): String? = tokenStore.getAccessToken(accountEmail)

    fun clearToken(accountEmail: String) {
        tokenStore.removeAccessToken(accountEmail)
    }

    companion object {
        const val SCOPE_CALENDAR_READONLY = "https://www.googleapis.com/auth/calendar.readonly"
    }
}
