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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxtime.app.analytics.AnalyticsEvent
import com.maxtime.app.analytics.AnalyticsTracker
import com.maxtime.app.data.AppState
import com.maxtime.app.data.ChatMessage
import com.maxtime.app.data.firebase.BlockedUsersRepository
import com.maxtime.app.data.firebase.ChatRepository
import com.maxtime.app.data.firebase.ModerationFunctions
import com.maxtime.app.data.firebase.ReportReason
import com.maxtime.app.ui.components.AvatarCircle
import com.maxtime.app.ui.components.ScreenScaffold
import com.maxtime.app.ui.components.TopBar
import com.maxtime.app.ui.nav.Router
import com.maxtime.app.ui.nav.Screen
import com.maxtime.app.ui.theme.MaxTimeColors
import com.maxtime.app.ui.theme.MaxTimeShapes
import com.maxtime.app.ui.theme.MaxTimeSpacing
import com.maxtime.app.ui.theme.MaxTimeType
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import kotlinx.coroutines.launch

@Composable
fun GroupChatScreen(state: AppState, router: Router) {
    val group = state.selectedGroup ?: state.getOrCreateMyGroup().also { state.selectedGroup = it }
    val myId = Firebase.auth.currentUser?.uid ?: state.currentUser.id
    val chatRepository = remember { ChatRepository() }
    val moderationFunctions = remember { ModerationFunctions() }
    val blockedUsersRepository = remember { BlockedUsersRepository() }
    val messagesFlow = remember(group.id) { chatRepository.observeMessages(group.id) }
    val allMessages by messagesFlow.collectAsState(initial = emptyList<ChatMessage>())
    val blockedIdsFlow = remember(myId) { blockedUsersRepository.observeBlockedUserIds(myId) }
    val blockedIds by blockedIdsFlow.collectAsState(initial = emptySet<String>())
    val messages = allMessages.filter { it.senderId !in blockedIds }
    var draft by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    // Apple Guideline 1.2 (UGC safety) — report goes to the reportUser callable for an admin to
    // review/act on; block is purely a local filter (see blockedIds above), never visible to the
    // blocked user. Both are required alongside any user-to-user chat feature.
    var reportTarget by remember { mutableStateOf<ChatMessage?>(null) }
    var blockTarget by remember { mutableStateOf<ChatMessage?>(null) }

    LaunchedEffect(group.id) {
        AnalyticsTracker.track(AnalyticsEvent.ChatOpened(groupId = group.id, activityId = group.activity))
    }

    fun send() {
        val text = draft
        if (text.isBlank()) return
        draft = ""
        AnalyticsTracker.track(AnalyticsEvent.ChatMessageSent(groupId = group.id, activityId = group.activity, messageLength = text.length))
        scope.launch { chatRepository.sendMessage(group.id, myId, state.currentUser.name, text, state.currentUser.photoUrl) }
    }

    ScreenScaffold(
        topBar = { TopBar(title = "${group.activity} Group", onBack = { router.navigate(Screen.Groups) }) },
        bottomBar = { ChatInputRow(draft, { draft = it }, onSend = ::send) }
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
                Text("${group.activity} · ${group.members}/${group.capacity} people confirmed", color = MaxTimeColors.textStrong, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                val priceLabel = if (group.priceNormal > 0) " · ₹${group.priceNormal}" else ""
                Text("${group.whenLabel} · ${group.venue ?: "Session pending"}$priceLabel", style = MaxTimeType.caption)
            }
            Text("Book", color = MaxTimeColors.accent2, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }

        Column(Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SystemEvent("${group.creator} created this activity")
            if (messages.isEmpty()) {
                SystemEvent("No messages yet — say hello")
            } else {
                messages.forEach { msg ->
                    ChatBubble(
                        msg.senderName, msg.text, msg.photoUrl,
                        fromMe = msg.senderId == myId,
                        onReport = { reportTarget = msg },
                        onBlock = { blockTarget = msg }
                    )
                }
            }
            val remaining = group.openSlots
            SystemEvent("$remaining spot${if (remaining == 1) "" else "s"} remain${if (remaining == 1) "s" else ""}")
        }
    }

    reportTarget?.let { msg ->
        ReportMessageDialog(
            senderName = msg.senderName,
            onDismiss = { reportTarget = null },
            onConfirm = { reason ->
                reportTarget = null
                scope.launch {
                    try {
                        moderationFunctions.reportUser(group.id, msg.senderId, reason, msg.text)
                    } catch (_: Exception) {
                        // Best-effort — a failed report shouldn't crash the chat screen.
                    }
                }
            }
        )
    }

    blockTarget?.let { msg ->
        AlertDialog(
            onDismissRequest = { blockTarget = null },
            title = { Text("Block ${msg.senderName}?") },
            text = { Text("You won't see their messages in this or any other group anymore. They won't be notified.") },
            confirmButton = {
                TextButton(onClick = {
                    blockTarget = null
                    AnalyticsTracker.track(AnalyticsEvent.UserBlocked(groupId = group.id))
                    scope.launch { blockedUsersRepository.blockUser(myId, msg.senderId) }
                }) { Text("Block", color = MaxTimeColors.red, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { blockTarget = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ReportMessageDialog(senderName: String, onDismiss: () -> Unit, onConfirm: (ReportReason) -> Unit) {
    var selected by remember { mutableStateOf(ReportReason.SPAM) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report $senderName") },
        text = {
            Column {
                ReportReason.entries.forEach { reason ->
                    Row(
                        Modifier.fillMaxWidth().clickable { selected = reason }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selected == reason, onClick = { selected = reason })
                        Text(reason.label, fontSize = 13.sp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(selected) }) { Text("Report", color = MaxTimeColors.red, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
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
private fun ChatBubble(
    sender: String?,
    text: String,
    photoUrl: String?,
    fromMe: Boolean,
    onReport: () -> Unit = {},
    onBlock: () -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (fromMe) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!fromMe) {
            AvatarCircle(name = sender ?: "?", photoUrl = photoUrl, size = 22.dp, modifier = Modifier.padding(end = 6.dp))
        }
        Box {
            Box(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (fromMe) MaxTimeColors.accent else MaxTimeColors.cardBg)
                    .then(if (!fromMe) Modifier.border(1.dp, MaxTimeColors.cardBorder, RoundedCornerShape(14.dp)) else Modifier)
                    .then(if (!fromMe) Modifier.clickable { showMenu = true } else Modifier)
                    .padding(horizontal = 13.dp, vertical = 10.dp)
            ) {
                Column {
                    if (sender != null) {
                        Text(sender, color = MaxTimeColors.accent2, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 3.dp))
                    }
                    Text(text, color = if (fromMe) MaxTimeColors.onAccent else MaxTimeColors.text, fontSize = 12.5.sp, fontWeight = if (fromMe) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
            if (!fromMe) {
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(text = { Text("Report") }, onClick = { showMenu = false; onReport() })
                    DropdownMenuItem(text = { Text("Block ${sender ?: "user"}") }, onClick = { showMenu = false; onBlock() })
                }
            }
        }
        if (fromMe) {
            AvatarCircle(name = sender ?: "?", photoUrl = photoUrl, size = 22.dp, modifier = Modifier.padding(start = 6.dp))
        }
    }
}

@Composable
private fun ChatInputRow(value: String, onValueChange: (String) -> Unit, onSend: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .shadow(elevation = 10.dp)
            .background(MaxTimeColors.bg1)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            Modifier.weight(1f).clip(MaxTimeShapes.pill).background(MaxTimeColors.cardBg).border(1.dp, MaxTimeColors.cardBorder, MaxTimeShapes.pill).padding(horizontal = 14.dp, vertical = 11.dp)
        ) {
            Box {
                if (value.isEmpty()) {
                    Text("Message the group…", color = MaxTimeColors.muted, fontSize = 12.5.sp)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = TextStyle(color = MaxTimeColors.textStrong, fontSize = 12.5.sp),
                    cursorBrush = SolidColor(MaxTimeColors.accent),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(MaxTimeColors.accent).clickable { onSend() },
            contentAlignment = Alignment.Center
        ) { Text("↑", color = MaxTimeColors.onAccent, fontWeight = FontWeight.ExtraBold) }
    }
}
