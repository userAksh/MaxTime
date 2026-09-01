package com.maxtime.app.ui.screens.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxtime.app.data.AppState
import com.maxtime.app.ui.components.ScreenScaffold
import com.maxtime.app.ui.components.TopBar
import com.maxtime.app.ui.nav.Router
import com.maxtime.app.ui.nav.Screen
import com.maxtime.app.ui.theme.MaxTimeColors
import com.maxtime.app.ui.theme.MaxTimeShapes
import com.maxtime.app.ui.theme.MaxTimeSpacing
import com.maxtime.app.ui.theme.MaxTimeType

@Composable
fun GroupChatScreen(state: AppState, router: Router) {
    val group = state.selectedGroup ?: state.getOrCreateMyGroup().also { state.selectedGroup = it }

    ScreenScaffold(
        topBar = { TopBar(title = "${group.activity} Group", onBack = { router.goBack() }) },
        bottomBar = { ChatInputRow() }
    ) {
        Row(
            Modifier
                .padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 8.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaxTimeColors.accentWash)
                .border(1.dp, MaxTimeColors.accentDim, RoundedCornerShape(12.dp))
                .clickable { router.navigate(Screen.BookingReview) }
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f).padding(end = 10.dp)) {
                Text("${group.activity} · ${group.members}/${group.capacity} confirmed", color = MaxTimeColors.textStrong, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                val priceLabel = if (group.priceNormal > 0) " · ₹${group.priceNormal}" else ""
                Text("${group.whenLabel} · ${group.venue ?: "Session pending"}$priceLabel", style = MaxTimeType.caption)
            }
            Text("Book", color = MaxTimeColors.accent2, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }

        Column(Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SystemEvent("${group.creator} created this activity")
            ChatBubble(group.creator, "I can reach a bit early, that work?", fromMe = false)
            ChatBubble(null, "Works for me too", fromMe = true)
            val remaining = group.openSlots
            SystemEvent("$remaining spot${if (remaining == 1) "" else "s"} remain${if (remaining == 1) "s" else ""}")
        }
    }
}

@Composable
private fun SystemEvent(text: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(Modifier.clip(RoundedCornerShape(10.dp)).background(MaxTimeColors.bg2).padding(horizontal = 12.dp, vertical = 5.dp)) {
            Text(text, color = MaxTimeColors.muted, fontSize = 10.5.sp)
        }
    }
}

@Composable
private fun ChatBubble(sender: String?, text: String, fromMe: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (fromMe) Arrangement.End else Arrangement.Start) {
        Box(
            Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(if (fromMe) MaxTimeColors.accent else MaxTimeColors.cardBg)
                .then(if (!fromMe) Modifier.border(1.dp, MaxTimeColors.cardBorder, RoundedCornerShape(14.dp)) else Modifier)
                .padding(horizontal = 13.dp, vertical = 10.dp)
        ) {
            Column {
                if (sender != null) {
                    Text(sender, color = MaxTimeColors.accent2, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 3.dp))
                }
                Text(text, color = if (fromMe) MaxTimeColors.onAccent else MaxTimeColors.text, fontSize = 12.5.sp, fontWeight = if (fromMe) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun ChatInputRow() {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaxTimeColors.bg1)
            .border(1.dp, MaxTimeColors.cardBorder)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier.weight(1f).clip(MaxTimeShapes.pill).background(MaxTimeColors.cardBg).border(1.dp, MaxTimeColors.cardBorder, MaxTimeShapes.pill).padding(horizontal = 14.dp, vertical = 11.dp)
        ) { Text("Message the group…", color = MaxTimeColors.muted, fontSize = 12.5.sp) }
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(MaxTimeColors.accent),
            contentAlignment = Alignment.Center
        ) { Text("↑", color = MaxTimeColors.onAccent, fontWeight = FontWeight.ExtraBold) }
    }
}
