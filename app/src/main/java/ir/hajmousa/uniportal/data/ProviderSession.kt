package ir.hajmousa.uniportal.data

import ir.hajmousa.uniportal.core.PortalException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class ProviderSession {
    enum class State { Unknown, Active, NeedsLogin }

    private val mutableState = MutableStateFlow(State.Unknown)
    val state = mutableState.asStateFlow()

    internal fun onFetch(result: Result<*>) {
        when {
            result.isSuccess -> mutableState.value = State.Active
            (result.exceptionOrNull() as? PortalException)?.reason == PortalException.Reason.SESSION_EXPIRED -> requireLogin()
        }
    }

    internal fun requireLogin() { mutableState.value = State.NeedsLogin }
    internal fun reset() { mutableState.value = State.Unknown }
}
