package com.xremail.app.tracking

import android.util.Log
import com.xremail.app.viewmodel.EmailViewModel
import com.xremail.app.viewmodel.HandSide
import com.xremail.app.viewmodel.InteractionTier

private const val TAG = "GestureMapper"

/**
 * Context-aware gesture-to-action mapper. Routes the same physical gesture
 * to different ViewModel actions depending on the current InteractionTier.
 *
 * Tier escalation model (designed for walking/on-the-go use):
 *   AMBIENT_HUD: pinch = expand to notification cards
 *   NOTIFICATION_CARDS: pinch = open highlighted email in triage,
 *                       swipe-left = archive from card, swipe-right = snooze,
 *                       swipe-down = collapse back to HUD
 *   TRIAGE: swipe-left = archive, swipe-right = snooze, pinch = select + TTS,
 *           pinch-hold = expand to focus, swipe-down = collapse to HUD
 *   FOCUS: swipe-down = collapse to triage, pinch = standard select
 */
class GestureToActionMapper(
    private val viewModel: EmailViewModel,
) {

    fun onGesture(gesture: SecondaryHandGestures.Gesture, tier: InteractionTier) {
        Log.d(TAG, "gesture=$gesture tier=$tier")
        if (handleFingerMenuGesture(gesture)) return
        when (tier) {
            InteractionTier.AMBIENT_HUD -> handleAmbientGesture(gesture)
            InteractionTier.NOTIFICATION_CARDS -> handleNotificationCardsGesture(gesture)
            InteractionTier.TRIAGE -> handleTriageGesture(gesture)
            InteractionTier.FOCUS -> handleFocusGesture(gesture)
        }
    }

    private fun handleFingerMenuGesture(gesture: SecondaryHandGestures.Gesture): Boolean {
        when (gesture) {
            SecondaryHandGestures.Gesture.MENU_SHOW -> {
                val hand = viewModel.uiState.value.dominantHand.opposite()
                Log.d(TAG, "  -> showFingerMenu($hand)")
                viewModel.showFingerMenu(hand)
                return true
            }
            SecondaryHandGestures.Gesture.MENU_HIDE -> {
                Log.d(TAG, "  -> hideFingerMenu()")
                viewModel.hideFingerMenu()
                return true
            }
            else -> {}
        }

        when (gesture) {
            SecondaryHandGestures.Gesture.PINCH_INDEX -> {
                Log.i(TAG, "PINCH_INDEX received -> archiveSelected()")
                viewModel.archiveSelected()
            }
            SecondaryHandGestures.Gesture.PINCH_MIDDLE -> {
                Log.i(TAG, "PINCH_MIDDLE received -> snoozeSelected()")
                viewModel.snoozeSelected()
            }
            SecondaryHandGestures.Gesture.PINCH_RING -> {
                Log.i(TAG, "PINCH_RING received -> toggleStarSelected()")
                viewModel.uiState.value.selectedEmail?.let(viewModel::toggleStar)
            }
            SecondaryHandGestures.Gesture.PINCH_PINKY -> {
                Log.i(TAG, "PINCH_PINKY received -> startCompose()")
                viewModel.startCompose()
            }
            else -> return false
        }
        return true
    }

    private fun handleAmbientGesture(gesture: SecondaryHandGestures.Gesture) {
        when (gesture) {
            SecondaryHandGestures.Gesture.PINCH_SELECT -> {
                Log.d(TAG, "  -> expandToNotificationCards()")
                viewModel.expandToNotificationCards()
            }
            SecondaryHandGestures.Gesture.OPEN_PALM_HOLD_COLLAPSE -> {
                Log.d(TAG, "  -> collapseOneTier()")
                viewModel.collapseOneTier()
            }
            else -> { /* no-op in ambient — gaze handles expansion */ }
        }
    }

    private fun handleNotificationCardsGesture(gesture: SecondaryHandGestures.Gesture) {
        when (gesture) {
            SecondaryHandGestures.Gesture.PINCH_SELECT -> {
                val highlighted = viewModel.uiState.value.highlightedNotificationId
                val email = viewModel.uiState.value.emails.find { it.id == highlighted }
                if (email != null) {
                    Log.d(TAG, "  -> openFromNotification(${email.id})")
                    viewModel.openFromNotification(email)
                } else {
                    Log.d(TAG, "  -> expandToTriage()")
                    viewModel.expandToTriage()
                }
            }
            SecondaryHandGestures.Gesture.SWIPE_LEFT_ARCHIVE -> {
                val highlighted = viewModel.uiState.value.highlightedNotificationId
                val email = viewModel.uiState.value.emails.find { it.id == highlighted }
                if (email != null) {
                    Log.d(TAG, "  -> archiveEmail(${email.id})")
                    viewModel.archiveEmail(email)
                }
            }
            SecondaryHandGestures.Gesture.SWIPE_RIGHT_SNOOZE -> {
                val highlighted = viewModel.uiState.value.highlightedNotificationId
                val email = viewModel.uiState.value.emails.find { it.id == highlighted }
                if (email != null) {
                    Log.d(TAG, "  -> snoozeEmail(${email.id})")
                    viewModel.snoozeEmail(email)
                }
            }
            SecondaryHandGestures.Gesture.SWIPE_DOWN_DISMISS -> {
                Log.d(TAG, "  -> collapseFromNotificationCards()")
                viewModel.collapseFromNotificationCards()
            }
            SecondaryHandGestures.Gesture.PINCH_HOLD_EXPAND -> {
                Log.d(TAG, "  -> expandToTriage()")
                viewModel.expandToTriage()
            }
            SecondaryHandGestures.Gesture.SWIPE_UP_STAR -> {
                val highlighted = viewModel.uiState.value.highlightedNotificationId
                val email = viewModel.uiState.value.emails.find { it.id == highlighted }
                if (email != null) {
                    Log.d(TAG, "  -> toggleStar(${email.id})")
                    viewModel.toggleStar(email)
                }
            }
            SecondaryHandGestures.Gesture.OPEN_PALM_HOLD_COLLAPSE -> {
                Log.d(TAG, "  -> collapseOneTier()")
                viewModel.collapseOneTier()
            }
            else -> { }
        }
    }

    private fun handleTriageGesture(gesture: SecondaryHandGestures.Gesture) {
        when (gesture) {
            SecondaryHandGestures.Gesture.SWIPE_LEFT_ARCHIVE -> {
                Log.d(TAG, "  -> archiveSelected()")
                viewModel.archiveSelected()
            }
            SecondaryHandGestures.Gesture.SWIPE_RIGHT_SNOOZE -> {
                Log.d(TAG, "  -> snoozeSelected()")
                viewModel.snoozeSelected()
            }
            SecondaryHandGestures.Gesture.PINCH_SELECT -> {
                viewModel.uiState.value.selectedEmail?.let {
                    Log.d(TAG, "  -> selectEmail(${it.id})")
                    viewModel.selectEmail(it)
                }
            }
            SecondaryHandGestures.Gesture.PINCH_HOLD_EXPAND -> {
                Log.d(TAG, "  -> expandToFocus()")
                viewModel.expandToFocus()
            }
            SecondaryHandGestures.Gesture.SWIPE_DOWN_DISMISS -> {
                Log.d(TAG, "  -> collapseToHud()")
                viewModel.collapseToHud()
            }
            SecondaryHandGestures.Gesture.SWIPE_UP_STAR -> {
                viewModel.uiState.value.selectedEmail?.let {
                    Log.d(TAG, "  -> toggleStar(${it.id})")
                    viewModel.toggleStar(it)
                }
            }
            SecondaryHandGestures.Gesture.OPEN_PALM_HOLD_COLLAPSE -> {
                Log.d(TAG, "  -> collapseOneTier()")
                viewModel.collapseOneTier()
            }
            else -> { }
        }
    }

    private fun handleFocusGesture(gesture: SecondaryHandGestures.Gesture) {
        when (gesture) {
            SecondaryHandGestures.Gesture.SWIPE_DOWN_DISMISS -> {
                Log.d(TAG, "  -> collapseToTriage()")
                viewModel.collapseToTriage()
            }
            SecondaryHandGestures.Gesture.PINCH_HOLD_EXPAND -> {
                Log.d(TAG, "  -> collapseToTriage() (pinch-hold escape)")
                viewModel.collapseToTriage()
            }
            SecondaryHandGestures.Gesture.PINCH_SELECT -> {
                Log.d(TAG, "  -> collapseToTriage() (pinch back from full layout)")
                viewModel.collapseToTriage()
            }
            SecondaryHandGestures.Gesture.OPEN_PALM_HOLD_COLLAPSE -> {
                Log.d(TAG, "  -> collapseOneTier()")
                viewModel.collapseOneTier()
            }
            else -> { /* no-op in focus */ }
        }
    }

    private fun selectedOrFirstEmail() =
        viewModel.uiState.value.selectedEmail ?: viewModel.uiState.value.emails.firstOrNull()
}

private fun HandSide.opposite(): HandSide = when (this) {
    HandSide.LEFT -> HandSide.RIGHT
    HandSide.RIGHT -> HandSide.LEFT
}
