package com.xremail.app.ui.peripheral

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xremail.app.tracking.SecondaryHandGestures
import com.xremail.app.ui.theme.XREmailColors
import com.xremail.app.viewmodel.EmailViewModel
import com.xremail.app.viewmodel.InteractionTier

/**
 * Tap targets for the same actions as [com.xremail.app.tracking.KeyboardGestureDispatcher],
 * for emulator builds or devices without reliable hand tracking.
 */
@Composable
fun GestureDebugBar(
    currentTier: InteractionTier,
    viewModel: EmailViewModel,
    handGestures: SecondaryHandGestures,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    val scroll2 = rememberScrollState()
    val btn = ButtonDefaults.buttonColors(
        containerColor = XREmailColors.surfaceVariant.copy(alpha = 0.95f),
        contentColor = XREmailColors.onSurface,
    )
    val small = ButtonDefaults.ContentPadding

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(XREmailColors.surface.copy(alpha = 0.88f))
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "No hand gestures? Tier: ${currentTier.name} | Dom: ${viewModel.uiState.value.dominantHand}",
            fontSize = 9.sp,
            color = XREmailColors.onSurfaceDim,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 2.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scroll),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Button(onClick = { viewModel.expandToNotificationCards() }, colors = btn, contentPadding = small) {
                Text("Notif", fontSize = 10.sp)
            }
            Button(onClick = { viewModel.expandToTriage() }, colors = btn, contentPadding = small) {
                Text("Triage", fontSize = 10.sp)
            }
            Button(onClick = { viewModel.expandToFocus() }, colors = btn, contentPadding = small) {
                Text("Focus", fontSize = 10.sp)
            }
            Button(onClick = { viewModel.collapseToHud() }, colors = btn, contentPadding = small) {
                Text("HUD", fontSize = 10.sp)
            }
            Button(onClick = { viewModel.collapseOneTier() }, colors = btn, contentPadding = small) {
                Text("Back", fontSize = 10.sp)
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scroll2),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Button(
                onClick = { handGestures.simulateGesture(SecondaryHandGestures.Gesture.PINCH_SELECT) },
                colors = btn,
                contentPadding = small,
            ) { Text("Pinch", fontSize = 10.sp) }
            Button(
                onClick = { handGestures.simulateGesture(SecondaryHandGestures.Gesture.PINCH_HOLD_EXPAND) },
                colors = btn,
                contentPadding = small,
            ) { Text("Hold", fontSize = 10.sp) }
            Button(
                onClick = { handGestures.simulateGesture(SecondaryHandGestures.Gesture.OPEN_PALM_HOLD_COLLAPSE) },
                colors = btn,
                contentPadding = small,
            ) { Text("Palm", fontSize = 10.sp) }
            Button(
                onClick = { handGestures.simulateGesture(SecondaryHandGestures.Gesture.SWIPE_LEFT_ARCHIVE) },
                colors = btn,
                contentPadding = small,
            ) { Text("←", fontSize = 10.sp) }
            Button(
                onClick = { handGestures.simulateGesture(SecondaryHandGestures.Gesture.SWIPE_RIGHT_SNOOZE) },
                colors = btn,
                contentPadding = small,
            ) { Text("→", fontSize = 10.sp) }
            Button(
                onClick = { handGestures.simulateGesture(SecondaryHandGestures.Gesture.SWIPE_UP_STAR) },
                colors = btn,
                contentPadding = small,
            ) { Text("↑", fontSize = 10.sp) }
            Button(
                onClick = { handGestures.simulateGesture(SecondaryHandGestures.Gesture.SWIPE_DOWN_DISMISS) },
                colors = btn,
                contentPadding = small,
            ) { Text("↓", fontSize = 10.sp) }
            Button(onClick = { viewModel.selectNextEmail() }, colors = btn, contentPadding = small) {
                Text("Next", fontSize = 10.sp)
            }
            Button(onClick = { viewModel.toggleEmulatorHelp() }, colors = btn, contentPadding = small) {
                Text("Help", fontSize = 10.sp)
            }
            Button(onClick = { viewModel.setDominantHand(com.xremail.app.viewmodel.HandSide.LEFT) }, colors = btn, contentPadding = small) {
                Text("Dom L", fontSize = 10.sp)
            }
            Button(onClick = { viewModel.setDominantHand(com.xremail.app.viewmodel.HandSide.RIGHT) }, colors = btn, contentPadding = small) {
                Text("Dom R", fontSize = 10.sp)
            }
            Button(onClick = { handGestures.simulateGesture(SecondaryHandGestures.Gesture.MENU_SHOW) }, colors = btn, contentPadding = small) {
                Text("Menu +", fontSize = 10.sp)
            }
            Button(onClick = { handGestures.simulateGesture(SecondaryHandGestures.Gesture.MENU_HIDE) }, colors = btn, contentPadding = small) {
                Text("Menu -", fontSize = 10.sp)
            }
            Button(onClick = {
                viewModel.uiState.value.selectedEmail?.let(viewModel::archiveEmail)
                    ?: viewModel.uiState.value.emails.firstOrNull()?.let(viewModel::archiveEmail)
                handGestures.simulateGesture(SecondaryHandGestures.Gesture.PINCH_INDEX)
            }, colors = btn, contentPadding = small) {
                Text("Idx", fontSize = 10.sp)
            }
            Button(onClick = {
                viewModel.uiState.value.selectedEmail?.let(viewModel::snoozeEmail)
                    ?: viewModel.uiState.value.emails.firstOrNull()?.let(viewModel::snoozeEmail)
                handGestures.simulateGesture(SecondaryHandGestures.Gesture.PINCH_MIDDLE)
            }, colors = btn, contentPadding = small) {
                Text("Mid", fontSize = 10.sp)
            }
            Button(onClick = {
                viewModel.uiState.value.selectedEmail?.let(viewModel::toggleStar)
                    ?: viewModel.uiState.value.emails.firstOrNull()?.let(viewModel::toggleStar)
                handGestures.simulateGesture(SecondaryHandGestures.Gesture.PINCH_RING)
            }, colors = btn, contentPadding = small) {
                Text("Ring", fontSize = 10.sp)
            }
            Button(onClick = {
                viewModel.startCompose()
                handGestures.simulateGesture(SecondaryHandGestures.Gesture.PINCH_PINKY)
            }, colors = btn, contentPadding = small) {
                Text("Pinky", fontSize = 10.sp)
            }
        }
    }
}
