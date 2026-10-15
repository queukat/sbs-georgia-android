package com.queukat.sbsgeorgia.domain.service

import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.isActive

interface DateSource {
    fun observeDate(): Flow<LocalDate>
}

/** No process-lifetime timer: polling exists only while a screen collects it. */
@Singleton
class ClockDateSource @Inject constructor(private val clock: Clock) : DateSource {
    private val refreshes = MutableStateFlow(0L)

    @Synchronized
    fun refresh() {
        refreshes.value += 1
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeDate(): Flow<LocalDate> = merge(
        refreshes.map { LocalDate.now(clock) },
        flow {
            while (currentCoroutineContext().isActive) {
                delay(60_000)
                emit(LocalDate.now(clock))
            }
        }
    ).distinctUntilChanged()
}
