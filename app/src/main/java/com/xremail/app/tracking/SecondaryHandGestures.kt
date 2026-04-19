package com.xremail.app.tracking

import android.content.ContentResolver
import android.util.Log
import androidx.xr.arcore.Hand
import androidx.xr.arcore.HandJointType
import androidx.xr.runtime.Session
import androidx.xr.runtime.math.Vector3
import com.xremail.app.viewmodel.HandSide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

private const val TAG = "SecondaryHandGestures"

private const val PINCH_DISTANCE_THRESHOLD = 0.055f
private const val PINCH_HOLD_DURATION_MS = 600L
private const val SWIPE_DISTANCE_THRESHOLD = 0.055f
private const val SWIPE_VELOCITY_THRESHOLD = 0.09f
private const val DEDUP_WINDOW_MS = 250L
private const val PALM_SPREAD_ENTER_THRESHOLD = 0.06f
private const val PALM_SPREAD_EXIT_THRESHOLD = 0.05f
private const val PALM_SHOW_DWELL_MS = 250L
private const val PALM_HIDE_DWELL_MS = 350L
private const val SLOT_PINCH_ENTER_M = 0.045f
private const val SLOT_PINCH_EXIT_M = 0.060f
private const val SLOT_PINCH_COOLDOWN_MS = 300L
private const val SLOT_WIN_MARGIN_M = 0.010f

/**
 * Tracks pinch + swipe gestures on BOTH hands. Early versions only watched the
 * non-dominant hand because the OS-level gaze-pinch on the dominant hand was
 * expected to drive panel clicks directly. In practice users pinch with either
 * hand interchangeably, so we observe both and de-dupe identical gestures
 * fired within 250 ms.
 */
class SecondaryHandGestures {

    enum class Gesture {
        PINCH_SELECT,
        PINCH_HOLD_EXPAND,
        OPEN_PALM_HOLD_COLLAPSE,
        SWIPE_LEFT_ARCHIVE,
        SWIPE_RIGHT_SNOOZE,
        SWIPE_DOWN_DISMISS,
        SWIPE_UP_STAR,
        MENU_SHOW,
        MENU_HIDE,
        PINCH_INDEX,
        PINCH_MIDDLE,
        PINCH_RING,
        PINCH_PINKY,
    }

    private val _gestures = MutableSharedFlow<Gesture>(extraBufferCapacity = 16)
    val gestures: SharedFlow<Gesture> = _gestures.asSharedFlow()

    private var leftJob: Job? = null
    private var rightJob: Job? = null

    private val leftHand = HandState("L", HandSide.LEFT)
    private val rightHand = HandState("R", HandSide.RIGHT)
    @Volatile private var dominantHand: HandSide = HandSide.RIGHT

    @Volatile private var lastEmitType: Gesture? = null
    @Volatile private var lastEmitMs: Long = 0L

    fun startTracking(
        session: Session,
        contentResolver: ContentResolver,
        scope: CoroutineScope,
    ) {
        stopTracking()

        val left = Hand.left(session)
        val right = Hand.right(session)

        if (left == null && right == null) {
            Log.w(TAG, "Neither hand available")
            return
        }

        if (left != null) {
            leftJob = scope.launch {
                left.state.collect { handState -> processHandState(handState, leftHand) }
            }
        } else {
            Log.w(TAG, "Left hand unavailable")
        }

        if (right != null) {
            rightJob = scope.launch {
                right.state.collect { handState -> processHandState(handState, rightHand) }
            }
        } else {
            Log.w(TAG, "Right hand unavailable")
        }
    }

    fun stopTracking() {
        leftJob?.cancel()
        rightJob?.cancel()
        leftJob = null
        rightJob = null
        leftHand.reset()
        rightHand.reset()
    }

    fun setDominantHand(hand: HandSide) {
        dominantHand = hand
    }

    private fun processHandState(handState: Hand.State, s: HandState) {
        val thumbTip = handState.handJoints[HandJointType.HAND_JOINT_TYPE_THUMB_TIP] ?: return
        val indexTip = handState.handJoints[HandJointType.HAND_JOINT_TYPE_INDEX_TIP] ?: return
        val middleTip = handState.handJoints[HandJointType.HAND_JOINT_TYPE_MIDDLE_TIP] ?: return
        val ringTip = handState.handJoints[HandJointType.HAND_JOINT_TYPE_RING_TIP] ?: return
        val pinkyTip = handState.handJoints[HandJointType.HAND_JOINT_TYPE_LITTLE_TIP] ?: return
        val palm = handState.handJoints[HandJointType.HAND_JOINT_TYPE_PALM] ?: return

        val pinchDistance = Vector3.distance(thumbTip.translation, indexTip.translation)
        val now = System.currentTimeMillis()
        val nonDominant = dominantHand.opposite()
        val isMenuHand = s.side == nonDominant
        val palmFacingUser = detectPalmFacingUser(
            palm = palm.translation,
            index = indexTip.translation,
            middle = middleTip.translation,
            ring = ringTip.translation,
            pinky = pinkyTip.translation,
            s = s,
        )

        updateFingerMenuGate(
            isMenuHand = isMenuHand,
            palmFacingUser = palmFacingUser,
            now = now,
            s = s,
        )

        if (isMenuHand && s.menuVisible) {
            detectFingerPinchSlot(
                thumb = thumbTip.translation,
                index = indexTip.translation,
                middle = middleTip.translation,
                ring = ringTip.translation,
                pinky = pinkyTip.translation,
                now = now,
                s = s,
            )
        } else {
            s.resetFingerSlots()
            detectPinch(pinchDistance, now, s)
        }

        trackPalmForSwipe(palm.translation, now, s)
    }

    private fun detectPalmFacingUser(
        palm: Vector3,
        index: Vector3,
        middle: Vector3,
        ring: Vector3,
        pinky: Vector3,
        s: HandState,
    ): Boolean {
        val avgFingerSpread = (
            Vector3.distance(palm, index) +
                Vector3.distance(palm, middle) +
                Vector3.distance(palm, ring) +
                Vector3.distance(palm, pinky)
            ) / 4f
        val threshold = if (s.palmFacingUser) PALM_SPREAD_EXIT_THRESHOLD else PALM_SPREAD_ENTER_THRESHOLD
        return avgFingerSpread >= threshold
    }

    private fun updateFingerMenuGate(
        isMenuHand: Boolean,
        palmFacingUser: Boolean,
        now: Long,
        s: HandState,
    ) {
        if (!isMenuHand) {
            if (s.menuVisible) emit(Gesture.MENU_HIDE, s)
            s.menuVisible = false
            s.palmFacingUser = false
            s.palmFacingStartMs = 0L
            s.palmAwayStartMs = 0L
            return
        }

        s.palmFacingUser = palmFacingUser
        if (palmFacingUser) {
            s.palmAwayStartMs = 0L
            if (s.palmFacingStartMs == 0L) s.palmFacingStartMs = now
            if (!s.menuVisible && now - s.palmFacingStartMs >= PALM_SHOW_DWELL_MS) {
                s.menuVisible = true
                emit(Gesture.MENU_SHOW, s)
            }
        } else {
            s.palmFacingStartMs = 0L
            if (s.menuVisible) {
                if (s.palmAwayStartMs == 0L) s.palmAwayStartMs = now
                if (now - s.palmAwayStartMs >= PALM_HIDE_DWELL_MS) {
                    s.menuVisible = false
                    s.resetFingerSlots()
                    emit(Gesture.MENU_HIDE, s)
                }
            }
        }
    }

    private fun detectFingerPinchSlot(
        thumb: Vector3,
        index: Vector3,
        middle: Vector3,
        ring: Vector3,
        pinky: Vector3,
        now: Long,
        s: HandState,
    ) {
        val distances = listOf(
            FingerDistance(Gesture.PINCH_INDEX, Vector3.distance(thumb, index)),
            FingerDistance(Gesture.PINCH_MIDDLE, Vector3.distance(thumb, middle)),
            FingerDistance(Gesture.PINCH_RING, Vector3.distance(thumb, ring)),
            FingerDistance(Gesture.PINCH_PINKY, Vector3.distance(thumb, pinky)),
        ).sortedBy { it.distance }

        val winner = distances.first()
        val runnerUp = distances.getOrNull(1)
        val marginOk = runnerUp == null || (runnerUp.distance - winner.distance) >= SLOT_WIN_MARGIN_M
        val shouldPress = winner.distance < SLOT_PINCH_ENTER_M && marginOk

        if (shouldPress && !s.slotPressed) {
            if (now - s.lastSlotEmitMs >= SLOT_PINCH_COOLDOWN_MS) {
                emit(winner.gesture, s)
                s.lastSlotEmitMs = now
            }
            s.slotPressed = true
            s.lastSlotGesture = winner.gesture
        }

        val shouldRelease = winner.distance > SLOT_PINCH_EXIT_M
        if (s.slotPressed && shouldRelease) {
            s.slotPressed = false
            s.lastSlotGesture = null
        }
    }

    private fun detectPinch(distance: Float, now: Long, s: HandState) {
        val wasPinching = s.isPinching
        s.isPinching = distance < PINCH_DISTANCE_THRESHOLD

        if (s.isPinching && !wasPinching) {
            s.pinchStartTimeMs = now
            s.pinchEmitted = false
        }

        if (s.isPinching && !s.pinchEmitted) {
            val holdDuration = now - s.pinchStartTimeMs
            if (holdDuration >= PINCH_HOLD_DURATION_MS) {
                emit(Gesture.PINCH_HOLD_EXPAND, s)
                s.pinchEmitted = true
            }
        }

        if (!s.isPinching && wasPinching && !s.pinchEmitted) {
            emit(Gesture.PINCH_SELECT, s)
        }
    }

    private fun trackPalmForSwipe(palmPos: Vector3, now: Long, s: HandState) {
        s.palmPositionHistory.add(now to palmPos)
        if (s.palmPositionHistory.size > POSITION_HISTORY_MAX) {
            s.palmPositionHistory.removeAt(0)
        }

        if (s.palmPositionHistory.size < 4) return

        val oldest = s.palmPositionHistory.first()
        val newest = s.palmPositionHistory.last()
        val dt = (newest.first - oldest.first) / 1000f
        if (dt < 0.05f) return

        val dx = newest.second.x - oldest.second.x
        val dy = newest.second.y - oldest.second.y

        val vx = dx / dt
        val vy = dy / dt

        val absDx = kotlin.math.abs(dx)
        val absDy = kotlin.math.abs(dy)

        if (absDx > SWIPE_DISTANCE_THRESHOLD && absDx > absDy * 1.5f) {
            if (kotlin.math.abs(vx) > SWIPE_VELOCITY_THRESHOLD) {
                if (vx > 0) emit(Gesture.SWIPE_RIGHT_SNOOZE, s)
                else emit(Gesture.SWIPE_LEFT_ARCHIVE, s)
                s.palmPositionHistory.clear()
            }
        } else if (absDy > SWIPE_DISTANCE_THRESHOLD && absDy > absDx * 1.5f) {
            if (kotlin.math.abs(vy) > SWIPE_VELOCITY_THRESHOLD) {
                if (vy > 0) emit(Gesture.SWIPE_UP_STAR, s)
                else emit(Gesture.SWIPE_DOWN_DISMISS, s)
                s.palmPositionHistory.clear()
            }
        }
    }

    private fun emit(gesture: Gesture, s: HandState) {
        val now = System.currentTimeMillis()
        if (lastEmitType == gesture && now - lastEmitMs < DEDUP_WINDOW_MS) {
            Log.d(TAG, "dedup ${s.label} $gesture (within $DEDUP_WINDOW_MS ms)")
            return
        }
        lastEmitType = gesture
        lastEmitMs = now
        Log.d(TAG, "emit ${s.label} $gesture")
        _gestures.tryEmit(gesture)
    }

    fun onGestureDetected(gesture: Gesture) {
        _gestures.tryEmit(gesture)
    }

    fun simulateGesture(gesture: Gesture) {
        _gestures.tryEmit(gesture)
    }

    private data class FingerDistance(val gesture: Gesture, val distance: Float)

    private class HandState(val label: String, val side: HandSide) {
        var isPinching = false
        var pinchStartTimeMs = 0L
        var pinchEmitted = false
        val palmPositionHistory = mutableListOf<Pair<Long, Vector3>>()
        var palmFacingUser = false
        var menuVisible = false
        var palmFacingStartMs = 0L
        var palmAwayStartMs = 0L
        var slotPressed = false
        var lastSlotGesture: Gesture? = null
        var lastSlotEmitMs = 0L

        fun reset() {
            isPinching = false
            pinchStartTimeMs = 0L
            pinchEmitted = false
            palmPositionHistory.clear()
            palmFacingUser = false
            menuVisible = false
            palmFacingStartMs = 0L
            palmAwayStartMs = 0L
            slotPressed = false
            lastSlotGesture = null
            lastSlotEmitMs = 0L
        }

        fun resetFingerSlots() {
            slotPressed = false
            lastSlotGesture = null
        }
    }

    companion object {
        private const val POSITION_HISTORY_MAX = 10
    }
}

private fun HandSide.opposite(): HandSide = when (this) {
    HandSide.LEFT -> HandSide.RIGHT
    HandSide.RIGHT -> HandSide.LEFT
}
