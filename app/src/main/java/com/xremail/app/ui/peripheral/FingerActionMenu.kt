package com.xremail.app.ui.peripheral

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xremail.app.ui.theme.XREmailColors
import com.xremail.app.viewmodel.FingerSlot
import com.xremail.app.viewmodel.HandSide
import com.xremail.app.viewmodel.QuickActionId

@Composable
fun FingerActionMenu(
    activeHand: HandSide?,
    assignments: Map<FingerSlot, QuickActionId>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = XREmailColors.surfaceElevated.copy(alpha = 0.92f),
                shape = RoundedCornerShape(12.dp),
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "${activeHand?.name ?: "NON_DOMINANT"} HAND MENU",
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = XREmailColors.primary,
        )
        FingerSlot.entries.forEach { slot ->
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = slotLabel(slot),
                    color = XREmailColors.onSurface,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = actionLabel(assignments[slot]),
                    color = XREmailColors.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

private fun slotLabel(slot: FingerSlot): String = when (slot) {
    FingerSlot.INDEX -> "Index pinch"
    FingerSlot.MIDDLE -> "Middle pinch"
    FingerSlot.RING -> "Ring pinch"
    FingerSlot.PINKY -> "Pinky pinch"
}

private fun actionLabel(action: QuickActionId?): String = when (action) {
    QuickActionId.ARCHIVE_SELECTED -> "Archive"
    QuickActionId.SNOOZE_SELECTED -> "Snooze"
    QuickActionId.TOGGLE_STAR_SELECTED -> "Star"
    QuickActionId.REPLY_SELECTED -> "Reply"
    null -> "None"
}
