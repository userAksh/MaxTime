package com.maxtime.app.ui.screens.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxtime.app.data.AppState
import com.maxtime.app.data.BookingStatus
import com.maxtime.app.data.Group
import com.maxtime.app.data.GroupStatus
import com.maxtime.app.ui.components.AvatarStack
import com.maxtime.app.ui.components.BottomCta
import com.maxtime.app.ui.components.PrimaryButton
import com.maxtime.app.ui.components.ScreenScaffold
import com.maxtime.app.ui.components.SecondaryButton
import com.maxtime.app.ui.components.StatusChip
import com.maxtime.app.ui.components.SuccessToast
import com.maxtime.app.ui.components.TopBar
import com.maxtime.app.ui.nav.Router
import com.maxtime.app.ui.nav.Screen
import com.maxtime.app.ui.theme.MaxTimeColors
import com.maxtime.app.ui.theme.MaxTimeSpacing
import com.maxtime.app.ui.theme.MaxTimeType
import kotlinx.coroutines.launch

@Composable
fun GroupDetailScreen(state: AppState, router: Router) {
    val group = state.selectedGroup ?: state.getOrCreateMyGroup().also { state.selectedGroup = it }

    var showCreatedToast by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (state.justCreatedActivity) {
            state.justCreatedActivity = false
            showCreatedToast = true
        }
    }

    Box(Modifier.fillMaxSize()) {
        ScreenScaffold(
            topBar = { TopBar(title = "Group", onBack = { router.navigate(Screen.Groups) }) },
            bottomBar = { GroupFooter(state, group, router) }
        ) {
            Column2(group)

            if (group.connections.isNotEmpty()) {
                Text(
                    "◆ ${group.connections.joinToString(" and ")}, who you know, ${if (group.connections.size > 1) "are" else "is"} going",
                    color = MaxTimeColors.accent2, fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 6.dp)
                )
            }

            when {
                group.effectiveStatus == GroupStatus.DEADLINE_MISSED -> DeadlineMissedBody(group)
                group.effectiveStatus == GroupStatus.CANCELLED -> CancelledBody()
                group.effectiveStatus == GroupStatus.FULL -> FullBody(group, router)
                group.venue == null -> UnassignedBody(group)
                else -> ActiveBody(group)
            }
        }
        SuccessToast(
            "Activity created — waiting for people to join",
            visible = showCreatedToast,
            onDismiss = { showCreatedToast = false }
        )
    }
}

@Composable
private fun Column2(group: Group) {
    Box(Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            androidx.compose.foundation.layout.Column(Modifier.weight(1f).padding(end = 10.dp)) {
                Text("${group.icon} ${group.activity}", color = MaxTimeColors.textStrong, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                Text("${group.whenLabel} · ${group.venueLine}", style = MaxTimeType.caption, modifier = Modifier.padding(top = 4.dp))
            }
            StatusChip(group.statusLabel, group.statusVariant)
        }
    }

    Box(Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 8.dp).fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).background(MaxTimeColors.cardBg).padding(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            val names = listOf("You") + group.connections
            AvatarStack(names, size = 32.dp)
            Text("${group.members} / ${group.capacity} members", style = MaxTimeType.caption)
        }
    }
    val progress = (group.members.toFloat() / group.capacity.toFloat()).coerceIn(0f, 1f)
    Box(Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 8.dp).fillMaxWidth().height(5.dp).clip(RoundedCornerShape(4.dp)).background(MaxTimeColors.bg2)) {
        Box(Modifier.fillMaxWidth(progress).height(5.dp).clip(RoundedCornerShape(4.dp)).background(MaxTimeColors.accent))
    }
}

@Composable
private fun GroupRow(key: String, value: String) {
    Row(
        Modifier
            .padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 2.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaxTimeColors.cardBg)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(key, color = MaxTimeColors.muted, fontSize = 12.sp)
        Text(value, color = MaxTimeColors.textStrong, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
    }
}

@Composable
private fun DeadlineMissedBody(group: Group) {
    GroupRow("Status", "Did not fill by deadline")
    GroupRow("Option A", "Proceed at ₹${group.priceIfDeadlineMissed}/person")
    GroupRow("Option B", "Find another session")
    Text(
        "This group did not reach the minimum group size in time. You can continue at the higher per-person price, extend the search, or book solo instead.",
        style = MaxTimeType.body,
        modifier = Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 8.dp)
    )
}

@Composable
private fun CancelledBody() {
    Text(
        "This activity was cancelled. Any payment made will be refunded as MaxTime credit within 3–5 days.",
        style = MaxTimeType.body,
        modifier = Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 8.dp)
    )
}

@Composable
private fun FullBody(group: Group, router: Router) {
    GroupRow("Created by", group.creator)
    GroupRow("Booking status", if (group.bookingStatus == BookingStatus.CONFIRMED) "Confirmed" else "Not yet booked")
    Text(
        "This group is full — no more spots to join. You can still open the chat with the group, or look for a similar session.",
        style = MaxTimeType.body,
        modifier = Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 8.dp)
    )
    ChatUnlockButton(router)
}

@Composable
private fun UnassignedBody(group: Group) {
    GroupRow("Created by", group.creator)
    GroupRow("Open slots", "${group.openSlots} remaining")
    Text(
        "This activity intent has people, but no venue is matched yet. Nothing is booked or guaranteed until a real session is found.",
        style = MaxTimeType.body,
        modifier = Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 8.dp)
    )
}

@Composable
private fun ActiveBody(group: Group) {
    GroupRow("Created by", group.creator)
    GroupRow("Open slots", "${group.openSlots} remaining")
    GroupRow("Fill deadline", group.deadline)
    GroupRow("If not filled", "Proceeds at ₹${group.priceIfDeadlineMissed}/person")
    GroupRow("Booking status", if (group.bookingStatus == BookingStatus.CONFIRMED) "Confirmed" else "Not yet booked")
}

@Composable
private fun ChatUnlockButton(router: Router) {
    Box(
        Modifier
            .padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(MaxTimeColors.accent)
            .clickable { router.navigate(Screen.GroupChat) }
            .padding(14.dp),
        contentAlignment = Alignment.Center
    ) { Text("Open Group Chat", color = MaxTimeColors.onAccent, fontWeight = FontWeight.Bold, fontSize = 12.5.sp) }
}

@Composable
private fun GroupFooter(state: AppState, group: Group, router: Router) {
    val scope = rememberCoroutineScope()
    BottomCta {
        when {
            group.effectiveStatus == GroupStatus.DEADLINE_MISSED -> {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton("Find another session", Modifier.weight(1f), onClick = { router.navigate(Screen.FindResults) })
                    PrimaryButton("Confirm ₹${group.priceIfDeadlineMissed}", Modifier.weight(1f), onClick = {
                        state.confirmHigherPrice()
                        router.navigate(Screen.BookingReview)
                    })
                }
                Text(
                    "Or just book solo instead", color = MaxTimeColors.muted, fontSize = 11.5.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp).clickable {
                        scope.launch {
                            state.selectSession()
                            router.navigate(Screen.SessionDetail)
                        }
                    }
                )
            }
            group.effectiveStatus == GroupStatus.CANCELLED -> {
                PrimaryButton("Find another session", onClick = { router.navigate(Screen.FindResults) })
            }
            group.effectiveStatus == GroupStatus.FULL -> {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton("Find a similar session", Modifier.weight(1f), onClick = { router.navigate(Screen.FindResults) })
                    PrimaryButton("Proceed to Booking", Modifier.weight(1f), onClick = { router.navigate(Screen.GroupChat) })
                }
            }
            group.venue == null -> {
                PrimaryButton("Check for a matching session", onClick = {
                    scope.launch { state.resolveSessionForGroup(group) }
                })
                Text(
                    "Leave", color = MaxTimeColors.muted, fontSize = 11.5.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp).clickable { router.navigate(Screen.Home) }
                )
            }
            group.bookingStatus == BookingStatus.CONFIRMED -> {
                PrimaryButton("View Group", onClick = { router.navigate(Screen.GroupChat) })
            }
            else -> {
                // The creator (and anyone who's already tapped Join Group before) is already a
                // member the moment the group exists — offering them "Join Group"/"Leave" again
                // makes no sense and, until now, "Join Group" didn't even add anyone: it just
                // navigated to chat. Only someone who found this group and isn't in it yet
                // (e.g. via "Join a group" in Find Results) should see that choice.
                val alreadyMember = group.memberList.any { it.personName == state.currentUser.name }
                if (alreadyMember) {
                    PrimaryButton("Open Group Chat", onClick = { router.navigate(Screen.GroupChat) })
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SecondaryButton("Not now", Modifier.weight(1f), onClick = { router.navigate(Screen.Home) })
                        PrimaryButton("Join Group", Modifier.weight(1f), onClick = {
                            state.joinGroup(group)
                            router.navigate(Screen.GroupChat)
                        })
                    }
                }
                Text(
                    "Simulate: deadline reached", color = MaxTimeColors.muted, fontSize = 11.5.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp).clickable { group.status = GroupStatus.DEADLINE_MISSED }
                )
            }
        }
    }
}
