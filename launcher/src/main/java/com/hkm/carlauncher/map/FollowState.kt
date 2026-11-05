package com.hkm.carlauncher.map

/** The camera follows the car until the user pans or zooms, and resumes after [resumeAfterMs] or on re-centre. */
class FollowState(private val resumeAfterMs: Long = 15_000) {
    private var lastGestureMs: Long? = null

    fun onUserGesture(nowMs: Long) {
        lastGestureMs = nowMs
    }

    fun onRecenter() {
        lastGestureMs = null
    }

    fun isFollowing(nowMs: Long): Boolean {
        val last = lastGestureMs ?: return true
        return nowMs - last >= resumeAfterMs
    }
}
