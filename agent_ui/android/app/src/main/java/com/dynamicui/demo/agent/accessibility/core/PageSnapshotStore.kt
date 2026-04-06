package com.dynamicui.demo.agent.accessibility.core

import java.util.concurrent.atomic.AtomicReference

object PageSnapshotStore {
    private val latestRef = AtomicReference<PageSnapshot?>(null)
    private val digestRef = AtomicReference("")
    private val updatedAtRef = AtomicReference(0L)
    private const val THROTTLE_MS = 250L

    fun update(snapshot: PageSnapshot) {
        val now = System.currentTimeMillis()
        val digest = snapshot.digest()
        val prevDigest = digestRef.get()
        val prevUpdatedAt = updatedAtRef.get()
        if (digest == prevDigest && now - prevUpdatedAt < THROTTLE_MS) {
            return
        }
        latestRef.set(snapshot)
        digestRef.set(digest)
        updatedAtRef.set(now)
    }

    fun latest(): PageSnapshot? = latestRef.get()

    fun latestDigest(): String = digestRef.get()
}
