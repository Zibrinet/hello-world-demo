package com.zibrinet.split.data.model

/** Persisted by name — do not rename constants. */
enum class SplitType {
    /** Shares defined as percentages that sum to 100. */
    PERCENT,

    /** Shares entered as exact minor-unit amounts that sum to the total. */
    EXACT,
}
