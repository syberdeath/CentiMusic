package com.hkm.carlauncher.map

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FollowStateTest {
    @Test
    fun followingAtStart() {
        assertTrue(FollowState().isFollowing(0))
    }

    @Test
    fun resumesFifteenSecondsAfterGesture() {
        val f = FollowState()
        f.onUserGesture(nowMs = 1_000)
        assertFalse(f.isFollowing(15_999))
        assertTrue(f.isFollowing(16_000))
    }

    @Test
    fun recenterResumesImmediately() {
        val f = FollowState()
        f.onUserGesture(1_000)
        f.onRecenter()
        assertTrue(f.isFollowing(1_001))
    }
}
