package com.xremail.app.tracking

import android.content.ContentResolver
import android.util.Log
import androidx.xr.runtime.FaceTrackingMode
import androidx.xr.runtime.HandTrackingMode
import androidx.xr.runtime.Session
import androidx.xr.runtime.SessionConfigureSuccess
import kotlinx.coroutines.CoroutineScope

private const val TAG = "XrSessionManager"

/**
 * Orchestrates XR session configuration and starts all tracking subsystems.
 * Call [startAll] once you have a valid XR Session (from LocalSession.current
 * or Session.create()). On emulator or non-XR devices where Session is null,
 * all trackers remain in their default/simulated state.
 *
 * **Important:** [Session.configure] must be called once with every mode the
 * app needs. [SecondaryHandGestures] and [FaceAttentionTracker] used to each
 * call `configure` separately; the second call overwrote the first and disabled
 * hand tracking on device.
 */
class XrSessionManager(
    val faceTracker: FaceAttentionTracker,
    val handGestures: SecondaryHandGestures,
    val tiltScroll: TiltScrollController,
) {

    private var started = false

    fun startAll(
        session: Session?,
        contentResolver: ContentResolver,
        scope: CoroutineScope,
    ) {
        if (session == null) {
            Log.i(TAG, "No XR session available — running in 2D/emulator mode with simulated input")
            return
        }
        if (started) return
        started = true

        Log.i(TAG, "Starting XR tracking subsystems")

        val merged = session.config.copy(
            handTracking = HandTrackingMode.BOTH,
            faceTracking = FaceTrackingMode.BLEND_SHAPES,
        )
        val configResult = session.configure(merged)
        if (configResult is SessionConfigureSuccess) {
            Log.i(TAG, "Session configured: hand + face tracking enabled together")
        } else {
            Log.w(TAG, "Session configure result: $configResult")
        }

        handGestures.startTracking(session, contentResolver, scope)
        faceTracker.startTracking(session, scope)
        tiltScroll.startTracking(session, scope)
    }

    fun stopAll() {
        if (!started) return
        started = false

        handGestures.stopTracking()
        faceTracker.stopTracking()
        tiltScroll.stopTracking()
    }
}
