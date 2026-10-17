package com.ezygo.attendance.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/** Application-lifetime scope for state that outlives any single screen. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/**
 * The HTTP client used for the calendar feed, which must stay separate from the
 * one that talks to EzyGo — see IcsClient for why sharing it would leak a token.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class CalendarHttpClient

/**
 * The HTTP client used for the Samarth portal, separate for the same reason as the
 * calendar's: it must never carry an EzyGo token, and it needs a cookie jar of its
 * own, which the shared client deliberately does not have.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SamarthHttpClient

@Module
@InstallIn(SingletonComponent::class)
object CoroutineModule {

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
