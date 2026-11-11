package ir.hajmousa.uniportal.core

// Parser exceptions can include entire inputs; only carry a fixed, safe reason across layers.
class PortalException(val reason: Reason) : Exception(reason.name) {
    enum class Reason { SESSION_EXPIRED, INVALID_SESSION_KEY, SERVER_ERROR, PARSE_ERROR }
}
