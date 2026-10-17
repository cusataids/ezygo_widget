package com.ezygo.attendance.di

import android.content.Context
import androidx.room.Room
import com.ezygo.attendance.BuildConfig
import com.ezygo.attendance.data.local.AppDatabase
import com.ezygo.attendance.data.local.CredentialStore
import com.ezygo.attendance.data.local.DraftStore
import com.ezygo.attendance.data.local.MIGRATION_3_4
import com.ezygo.attendance.data.local.MIGRATION_4_5
import com.ezygo.attendance.data.local.MIGRATION_5_6
import com.ezygo.attendance.data.local.MIGRATION_6_7
import com.ezygo.attendance.data.local.MIGRATION_7_8
import com.ezygo.attendance.data.local.MIGRATION_8_9
import com.ezygo.attendance.data.local.MIGRATION_9_10
import com.ezygo.attendance.data.local.MIGRATION_10_11
import com.ezygo.attendance.data.local.MIGRATION_11_12
import com.ezygo.attendance.data.local.MIGRATION_12_13
import com.ezygo.attendance.data.local.MIGRATION_13_14
import com.ezygo.attendance.data.local.PreferencesStore
import com.ezygo.attendance.data.local.SamarthCredentialStore
import com.ezygo.attendance.data.local.TokenStore
import com.ezygo.attendance.data.remote.AuthInterceptor
import com.ezygo.attendance.data.remote.EzygoApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        // The contract documents only the fields we need; anything else the server
        // sends must not break decoding.
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        // Deliberately NOT coercing input values: a null attendance status has to
        // stay null so it is never mistaken for an absence.
        coerceInputValues = false
    }

    @Provides
    @Singleton
    fun provideOkHttp(authInterceptor: AuthInterceptor): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)

        if (BuildConfig.DEBUG) {
            // Debug builds only, and the Authorization header is redacted so a
            // bearer token can never reach logcat.
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
                redactHeader("Authorization")
            }
            builder.addInterceptor(logging)
        }
        return builder.build()
    }

    /**
     * A second, deliberately plain client for the calendar feed.
     *
     * It has neither the auth interceptor nor the logging interceptor: the EzyGo
     * bearer token must not travel to a third-party host, and the feed URL is
     * itself a secret that must not be written to logcat. See IcsClient.
     */
    @Provides
    @Singleton
    @CalendarHttpClient
    fun provideCalendarHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .build()

    /**
     * A third plain client, for the Samarth portal.
     *
     * No auth interceptor, for the same reason as the calendar's: an EzyGo bearer
     * token has no business reaching another institution's server. No logging
     * interceptor either, because the portal's traffic carries a session cookie and
     * a login form. Redirects are followed, which is how its login flow works, and
     * each scrape adds its own cookie jar on top of this — see SamarthClient.
     */
    @Provides
    @Singleton
    @SamarthHttpClient
    fun provideSamarthHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        // A grade card is a PDF over a slow institutional host; the shared 60s call
        // timeout is not always enough for one.
        .callTimeout(90, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(EzygoApi.BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideApi(retrofit: Retrofit): EzygoApi = retrofit.create(EzygoApi::class.java)

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            // The fallback stays for the cache-only versions below 3, where dropping
            // everything costs a refetch and nothing else. From 3 onward the database
            // holds the user's own writing, so those steps get real migrations.
            .addMigrations(
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9,
                MIGRATION_9_10,
                MIGRATION_10_11,
                MIGRATION_11_12,
                MIGRATION_12_13,
                MIGRATION_13_14,
            )
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    @Singleton
    fun provideTokenStore(@ApplicationContext context: Context) = TokenStore(context)

    @Provides
    @Singleton
    fun providePreferencesStore(@ApplicationContext context: Context) = PreferencesStore(context)

    @Provides
    @Singleton
    fun provideDraftStore(@ApplicationContext context: Context) = DraftStore(context)

    @Provides
    @Singleton
    fun provideCredentialStore(@ApplicationContext context: Context) = CredentialStore(context)

    @Provides
    @Singleton
    fun provideSamarthCredentialStore(@ApplicationContext context: Context) =
        SamarthCredentialStore(context)
}
