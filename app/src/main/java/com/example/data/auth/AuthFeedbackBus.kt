package com.example.data.auth

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed interface AuthFeedbackEvent {
    data class Success(val message: String) : AuthFeedbackEvent
    data class Error(val message: String) : AuthFeedbackEvent
}

object AuthFeedbackBus {
    private val _events = MutableSharedFlow<AuthFeedbackEvent>(replay = 1)
    val events: SharedFlow<AuthFeedbackEvent> = _events.asSharedFlow()

    fun emitSuccess(message: String) {
        _events.tryEmit(AuthFeedbackEvent.Success(message))
    }

    fun emitError(message: String) {
        _events.tryEmit(AuthFeedbackEvent.Error(message))
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun clear() {
        _events.resetReplayCache()
    }
}
