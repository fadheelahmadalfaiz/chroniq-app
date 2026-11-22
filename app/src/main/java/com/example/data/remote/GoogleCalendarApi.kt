package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface GoogleCalendarApi {

    @GET("users/me/calendarList")
    suspend fun getCalendarList(
        @Header("Authorization") bearerToken: String,
        @Query("minAccessRole") minAccessRole: String = "reader",
    ): Response<CalendarListResponse>

    @GET("calendars/{calendarId}/events")
    suspend fun getCalendarEvents(
        @Header("Authorization") bearerToken: String,
        @Path("calendarId") calendarId: String,
        @Query("singleEvents") singleEvents: Boolean? = null,
        @Query("timeMin") timeMin: String? = null,
        @Query("timeMax") timeMax: String? = null,
        @Query("syncToken") syncToken: String? = null,
        @Query("showDeleted") showDeleted: Boolean = true,
        @Query("maxResults") maxResults: Int = 250,
    ): Response<EventsListResponse>

    companion object {
        private const val BASE_URL = "https://www.googleapis.com/calendar/v3/"

        fun create(): GoogleCalendarApi {
            // Avoid logging Authorization headers to prevent token leakage.
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build()

            val moshi = Moshi.Builder()
                .addLast(KotlinJsonAdapterFactory())
                .build()

            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(GoogleCalendarApi::class.java)
        }
    }
}

@JsonClass(generateAdapter = true)
data class CalendarListResponse(
    val items: List<GoogleCalendarItem> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class GoogleCalendarItem(
    val id: String,
    val summary: String? = null,
    @Json(name = "summaryOverride") val summaryOverride: String? = null,
    val primary: Boolean? = false,
    @Json(name = "backgroundColor") val backgroundColor: String? = null,
    val selected: Boolean? = true,
)

@JsonClass(generateAdapter = true)
data class EventsListResponse(
    val items: List<GoogleCalendarEvent> = emptyList(),
    @Json(name = "nextSyncToken") val nextSyncToken: String? = null,
    @Json(name = "nextPageToken") val nextPageToken: String? = null,
)

@JsonClass(generateAdapter = true)
data class GoogleCalendarEvent(
    val id: String,
    val status: String? = null,
    val summary: String? = null,
    val description: String? = null,
    val location: String? = null,
    val start: GoogleEventDateTime? = null,
    val end: GoogleEventDateTime? = null,
)

@JsonClass(generateAdapter = true)
data class GoogleEventDateTime(
    val date: String? = null, // YYYY-MM-DD for All-day events
    @Json(name = "dateTime") val dateTime: String? = null, // RFC3339 timestamp
    val timeZone: String? = null,
)
