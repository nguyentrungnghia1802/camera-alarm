package com.personal.cameraalarm.trigger

interface DuplicateGuard {
    fun isDuplicate(key: String, nowEpochMs: Long): Boolean
    fun markSeen(key: String, nowEpochMs: Long)
    fun prune(nowEpochMs: Long)
}

class TtlDuplicateGuard(private val ttlMs: Long = 30_000, private val maxEntries: Int = 200) : DuplicateGuard {
    init { require(ttlMs > 0 && maxEntries > 0) }
    private val seen = LinkedHashMap<String, Long>()
    val size: Int get() = seen.size

    @Synchronized override fun isDuplicate(key: String, nowEpochMs: Long): Boolean {
        prune(nowEpochMs)
        return seen[key]?.let { nowEpochMs - it < ttlMs } == true
    }
    @Synchronized override fun markSeen(key: String, nowEpochMs: Long) {
        prune(nowEpochMs)
        seen.remove(key)
        seen[key] = nowEpochMs
        while (seen.size > maxEntries) seen.remove(seen.keys.first())
    }
    @Synchronized override fun prune(nowEpochMs: Long) {
        seen.entries.removeAll { nowEpochMs - it.value >= ttlMs || nowEpochMs < it.value }
    }
}
