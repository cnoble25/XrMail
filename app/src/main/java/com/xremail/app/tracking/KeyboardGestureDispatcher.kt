package com.xremail.app.tracking

import android.util.Log
import android.view.KeyEvent
import com.xremail.app.viewmodel.EmailViewModel
import com.xremail.app.viewmodel.HandSide
import com.xremail.app.viewmodel.InteractionTier

/**
 * Maps physical keyboard keys to gesture actions for emulator testing.
 * Allows full interaction without XR hand tracking.
 */
class KeyboardGestureDispatcher(
    private val viewModel: EmailViewModel,
    private val handGestures: SecondaryHandGestures,
) {
    companion object {
        private const val TAG = "KeyboardGestures"

        val KEY_MAPPINGS: List<Mapping> = listOf(
            Mapping(KeyEvent.KEYCODE_1, "1", "Expand to Notifications"),
            Mapping(KeyEvent.KEYCODE_2, "2", "Expand to Triage"),
            Mapping(KeyEvent.KEYCODE_3, "3", "Expand to Focus"),
            Mapping(KeyEvent.KEYCODE_0, "0", "Collapse to HUD"),
            Mapping(KeyEvent.KEYCODE_DEL, "Bksp", "Collapse one tier back"),
            Mapping(KeyEvent.KEYCODE_DPAD_LEFT, "Left", "Swipe Left (Archive)"),
            Mapping(KeyEvent.KEYCODE_DPAD_RIGHT, "Right", "Swipe Right (Snooze)"),
            Mapping(KeyEvent.KEYCODE_DPAD_UP, "Up", "Swipe Up (Star)"),
            Mapping(KeyEvent.KEYCODE_DPAD_DOWN, "Down", "Swipe Down (Dismiss)"),
            Mapping(KeyEvent.KEYCODE_SPACE, "Space", "Pinch Select"),
            Mapping(KeyEvent.KEYCODE_ENTER, "Enter", "Pinch Hold Expand"),
            Mapping(KeyEvent.KEYCODE_P, "P", "Open Palm Hold (collapse)"),
            Mapping(KeyEvent.KEYCODE_N, "N", "Select next email"),
            Mapping(KeyEvent.KEYCODE_M, "M", "Toggle finger menu"),
            Mapping(KeyEvent.KEYCODE_I, "I", "Finger menu: Index pinch"),
            Mapping(KeyEvent.KEYCODE_O, "O", "Finger menu: Middle pinch"),
            Mapping(KeyEvent.KEYCODE_K, "K", "Finger menu: Ring pinch"),
            Mapping(KeyEvent.KEYCODE_L, "L", "Finger menu: Pinky pinch"),
            Mapping(KeyEvent.KEYCODE_7, "7", "Dominant hand = LEFT"),
            Mapping(KeyEvent.KEYCODE_8, "8", "Dominant hand = RIGHT"),
            Mapping(KeyEvent.KEYCODE_H, "H", "Toggle help overlay"),
            Mapping(KeyEvent.KEYCODE_BUTTON_X, "Btn X", "Archive selected"),
            Mapping(KeyEvent.KEYCODE_BUTTON_Y, "Btn Y", "Snooze selected"),
            Mapping(KeyEvent.KEYCODE_BUTTON_A, "Btn A", "Reply / Compose"),
            Mapping(KeyEvent.KEYCODE_BUTTON_B, "Btn B", "Forward"),
        )
    }

    data class Mapping(val keyCode: Int, val keyLabel: String, val description: String)

    fun onKeyEvent(event: KeyEvent): Boolean {
        val action = event.action
        // Consume both DOWN and UP to prevent system focus navigation rings.
        // If we don't consume UP, the system enters focus mode and highlights elements.
        if (action != KeyEvent.ACTION_DOWN && action != KeyEvent.ACTION_UP && action != KeyEvent.ACTION_MULTIPLE) return false

        val keyCode = event.keyCode
        val typedChar = event.unicodeChar.takeIf { it > 0 }?.toChar()?.lowercaseChar()
        val typedChars = event.characters?.lowercase().orEmpty()
        val tier = viewModel.uiState.value.tier
        val selectedId = viewModel.uiState.value.selectedEmail?.id

        val isDown = action == KeyEvent.ACTION_DOWN || action == KeyEvent.ACTION_MULTIPLE

        var isMapped = true
        val handled = when {
            keyCode == KeyEvent.KEYCODE_1 || typedChar == '1' || typedChars.contains('1') -> {
                if (isDown) viewModel.expandToNotificationCards(); true
            }
            keyCode == KeyEvent.KEYCODE_2 || typedChar == '2' || typedChars.contains('2') -> {
                if (isDown) viewModel.expandToTriage(); true
            }
            keyCode == KeyEvent.KEYCODE_3 || typedChar == '3' || typedChars.contains('3') -> {
                if (isDown) viewModel.expandToFocus(); true
            }
            keyCode == KeyEvent.KEYCODE_0 || typedChar == '0' || typedChars.contains('0') -> {
                if (isDown) viewModel.collapseToHud(); true
            }
            keyCode == KeyEvent.KEYCODE_DEL -> {
                if (isDown) handGestures.simulateGesture(SecondaryHandGestures.Gesture.OPEN_PALM_HOLD_COLLAPSE); true
            }
            keyCode == KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (isDown) handGestures.simulateGesture(SecondaryHandGestures.Gesture.SWIPE_LEFT_ARCHIVE); true
            }
            keyCode == KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (isDown) handGestures.simulateGesture(SecondaryHandGestures.Gesture.SWIPE_RIGHT_SNOOZE); true
            }
            keyCode == KeyEvent.KEYCODE_DPAD_UP -> {
                if (isDown) handGestures.simulateGesture(SecondaryHandGestures.Gesture.SWIPE_UP_STAR); true
            }
            keyCode == KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (isDown) handGestures.simulateGesture(SecondaryHandGestures.Gesture.SWIPE_DOWN_DISMISS); true
            }
            keyCode == KeyEvent.KEYCODE_SPACE || typedChar == ' ' || typedChars.contains(' ') -> {
                if (isDown) handGestures.simulateGesture(SecondaryHandGestures.Gesture.PINCH_SELECT); true
            }
            keyCode == KeyEvent.KEYCODE_ENTER -> {
                if (isDown) handGestures.simulateGesture(SecondaryHandGestures.Gesture.PINCH_HOLD_EXPAND); true
            }
            keyCode == KeyEvent.KEYCODE_P || typedChar == 'p' || typedChars.contains('p') -> {
                if (isDown) handGestures.simulateGesture(SecondaryHandGestures.Gesture.OPEN_PALM_HOLD_COLLAPSE); true
            }
            keyCode == KeyEvent.KEYCODE_N || typedChar == 'n' || typedChars.contains('n') -> {
                if (isDown) viewModel.selectNextEmail(); true
            }
            keyCode == KeyEvent.KEYCODE_M || typedChar == 'm' || typedChars.contains('m') -> {
                if (isDown) {
                    if (viewModel.uiState.value.isFingerMenuVisible) {
                        handGestures.simulateGesture(SecondaryHandGestures.Gesture.MENU_HIDE)
                    } else {
                        handGestures.simulateGesture(SecondaryHandGestures.Gesture.MENU_SHOW)
                    }
                }
                true
            }
            keyCode == KeyEvent.KEYCODE_I || typedChar == 'i' || typedChars.contains('i') -> {
                if (isDown) handGestures.simulateGesture(SecondaryHandGestures.Gesture.PINCH_INDEX); true
            }
            keyCode == KeyEvent.KEYCODE_O || typedChar == 'o' || typedChars.contains('o') -> {
                if (isDown) handGestures.simulateGesture(SecondaryHandGestures.Gesture.PINCH_MIDDLE); true
            }
            keyCode == KeyEvent.KEYCODE_K || typedChar == 'k' || typedChars.contains('k') -> {
                if (isDown) handGestures.simulateGesture(SecondaryHandGestures.Gesture.PINCH_RING); true
            }
            keyCode == KeyEvent.KEYCODE_L || typedChar == 'l' || typedChars.contains('l') -> {
                if (isDown) handGestures.simulateGesture(SecondaryHandGestures.Gesture.PINCH_PINKY); true
            }
            keyCode == KeyEvent.KEYCODE_7 || typedChar == '7' || typedChars.contains('7') -> {
                if (isDown) viewModel.setDominantHand(HandSide.LEFT); true
            }
            keyCode == KeyEvent.KEYCODE_8 || typedChar == '8' || typedChars.contains('8') -> {
                if (isDown) viewModel.setDominantHand(HandSide.RIGHT); true
            }
            keyCode == KeyEvent.KEYCODE_H || typedChar == 'h' || typedChars.contains('h') -> {
                if (isDown) viewModel.toggleEmulatorHelp(); true
            }
            keyCode == KeyEvent.KEYCODE_BUTTON_X || typedChar == 'x' || typedChars.contains('x') -> {
                if (isDown) handGestures.simulateGesture(SecondaryHandGestures.Gesture.PINCH_INDEX); true
            }
            keyCode == KeyEvent.KEYCODE_BUTTON_Y || typedChar == 'y' || typedChars.contains('y') -> {
                if (isDown) handGestures.simulateGesture(SecondaryHandGestures.Gesture.PINCH_MIDDLE); true
            }
            keyCode == KeyEvent.KEYCODE_BUTTON_A || typedChar == 'a' || typedChars.contains('a') -> {
                if (isDown) handGestures.simulateGesture(SecondaryHandGestures.Gesture.PINCH_PINKY); true
            }
            keyCode == KeyEvent.KEYCODE_BUTTON_B || typedChar == 'b' || typedChars.contains('b') -> {
                if (isDown) viewModel.forwardSelected(); true
            }
            else -> {
                isMapped = false
                // Suppress focus highlighting for ANY key in the emulator by consuming it even if not mapped.
                // We exempt system-critical keys like BACK, VOLUME, and POWER.
                !isSystemKey(keyCode)
            }
        }

        if (handled) {
            if (isDown && isMapped) {
                Log.w(
                    TAG,
                    "Key handled action=$action keyCode=$keyCode unicode=${event.unicodeChar} chars='${event.characters}' tier=$tier selected=$selectedId"
                )
            }
            return true
        }

        return false
    }

    private fun isSystemKey(keyCode: Int): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_BACK,
        KeyEvent.KEYCODE_VOLUME_UP,
        KeyEvent.KEYCODE_VOLUME_DOWN,
        KeyEvent.KEYCODE_VOLUME_MUTE,
        KeyEvent.KEYCODE_POWER,
        KeyEvent.KEYCODE_APP_SWITCH,
        KeyEvent.KEYCODE_HOME,
        KeyEvent.KEYCODE_MENU -> true
        else -> false
    }

    private fun selectedOrFirstEmail() =
        viewModel.uiState.value.selectedEmail ?: viewModel.uiState.value.emails.firstOrNull()
}
