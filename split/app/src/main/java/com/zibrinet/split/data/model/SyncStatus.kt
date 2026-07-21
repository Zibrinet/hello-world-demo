package com.zibrinet.split.data.model

/**
 * Sync bookkeeping for a future remote backend. Every record is [LOCAL] today;
 * a sync engine will later mark records [PENDING] while uploading and [SYNCED]
 * once acknowledged. Persisted by name — do not rename constants.
 */
enum class SyncStatus {
    LOCAL,
    PENDING,
    SYNCED,
}
