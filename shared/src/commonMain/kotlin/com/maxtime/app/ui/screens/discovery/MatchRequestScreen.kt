package com.maxtime.app.ui.screens.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.maxtime.app.data.AppState
import com.maxtime.app.data.InviteStatus
import com.maxtime.app.ui.components.BottomCta
import com.maxtime.app.ui.components.MaxTimeCard
import com.maxtime.app.ui.components.PrimaryButton
import com.maxtime.app.ui.components.ScreenScaffold
import com.maxtime.app.ui.components.SecondaryButton
import com.maxtime.app.ui.components.TopBar
import com.maxtime.app.ui.nav.Router
import com.maxtime.app.ui.nav.Screen
import com.maxtime.app.ui.theme.MaxTimeColors
import com.maxtime.app.ui.theme.MaxTimeSpacing
import com.maxtime.app.ui.theme.MaxTimeType

/**
 * Confirm -> pending -> accepted/declined all happen as content swaps within this one screen,
 * not as navigation to a new screen (Section 5's inline view-morphing spec).
 */
@Composable
fun MatchRequestScreen(state: AppState, router: Router) {
    val personName = state.selectedPersonName ?: "Harsh"
    val invite = state.invites[personName]
    val isNewInvite = invite == null
    val outOfInvites = isNewInvite && state.freeInvitesLeft <= 0

    ScreenScaffold(topBar = { TopBar(title = "Send Invite", onBack = { router.goBack() }) }) {
        when {
            outOfInvites -> OutOfInvitesView(router)
            invite != null && invite.status == InviteStatus.DECLINED -> DeclinedView(personName, router)
            invite != null && (invite.status == InviteStatus.PENDING || invite.status == InviteStatus.ACCEPTED) ->
                PendingOrAcceptedView(state, personName, invite.status == InviteStatus.ACCEPTED, router)
            else -> ConfirmView(state, personName, router)
        }
    }
}

@Composable
private fun ConfirmView(state: AppState, personName: String, router: Router) {
    MaxTimeCard(Modifier.padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 16.dp)) {
        Text("Confirm invite", color = MaxTimeColors.textStrong, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, modifier = Modifier.padding(bottom = 14.dp))
        ConfirmRow("Activity", state.intent.activity)
        ConfirmRow("When", state.intent.whenLabel)
        ConfirmRow("Sending to", personName)
    }
    Text(
        "This will use 1 of your ${state.freeInvitesLeft} remaining free invites if accepted.",
        style = MaxTimeType.body,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = MaxTimeSpacing.screenPadding)
    )
    Row(Modifier.fillMaxWidth().padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 16.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
        SecondaryButton("Cancel", Modifier.weight(1f), onClick = { router.goBack() })
        PrimaryButton("Send Invite", Modifier.weight(1f), onClick = { state.sendInvite(personName) })
    }
}

@Composable
private fun ConfirmRow(key: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 9.dp).border(0.dp, MaxTimeColors.cardBorder),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
    ) {
        Text(key, color = MaxTimeColors.muted2, fontSize = 12.sp)
        Text(value, color = MaxTimeColors.textStrong, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
    }
}

@Composable
private fun PendingOrAcceptedView(state: AppState, personName: String, accepted: Boolean, router: Router) {
    if (accepted) {
        Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(64.dp).clip(CircleShape).background(MaxTimeColors.accentWash).border(2.dp, MaxTimeColors.accent, CircleShape),
                contentAlignment = Alignment.Center
            ) { Text("✓", color = MaxTimeColors.accent, fontSize = 28.sp) }
        }
        Text(
            "$personName accepted", color = MaxTimeColors.textStrong, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        )
        Text(
            "You are both in the group now.", style = MaxTimeType.caption,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        )
        BottomCta { PrimaryButton("Open group", onClick = { router.navigate(Screen.GroupDetail) }) }
    } else {
        Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(64.dp).clip(CircleShape).border(3.dp, MaxTimeColors.accentDim, CircleShape)
            )
        }
        Text(
            "Waiting for response", color = MaxTimeColors.textStrong, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        )
        Text(
            "$personName will get a notification. This does not use a free invite unless accepted.",
            style = MaxTimeType.caption,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 6.dp)
        )
        BottomCta {
            PrimaryButton("Simulate: $personName accepted", onClick = {
                state.simulateAccept(personName)
                router.navigate(Screen.GroupDetail)
            })
            Text(
                "Simulate: $personName declined", color = MaxTimeColors.muted, fontSize = 11.5.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).clickable { state.simulateDecline(personName) }
            )
        }
    }
}

@Composable
private fun DeclinedView(personName: String, router: Router) {
    Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(MaxTimeColors.redWash).border(2.dp, MaxTimeColors.red, CircleShape),
            contentAlignment = Alignment.Center
        ) { Text("✕", color = MaxTimeColors.red, fontSize = 28.sp) }
    }
    Text(
        "$personName declined", color = MaxTimeColors.textStrong, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
    )
    Text(
        "No invite was used. You can try inviting someone else.", style = MaxTimeType.caption,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
    )
    BottomCta { PrimaryButton("Find someone else", onClick = { router.navigate(Screen.FindResults) }) }
}

@Composable
private fun OutOfInvitesView(router: Router) {
    Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(MaxTimeColors.amberWash).border(2.dp, MaxTimeColors.amber, CircleShape),
            contentAlignment = Alignment.Center
        ) { Text("⚠", color = MaxTimeColors.amber, fontSize = 28.sp) }
    }
    Text(
        "You are out of free invites", color = MaxTimeColors.textStrong, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
    )
    Text(
        "You have used all 3 free invites. You can still book solo and join any open group at no cost — upgrade to keep sending direct invites.",
        style = MaxTimeType.caption,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = MaxTimeSpacing.screenPadding, vertical = 6.dp)
    )
    BottomCta {
        PrimaryButton("Upgrade to invite more people", onClick = { router.navigate(Screen.FindResults) })
        Text(
            "Maybe later, join a group instead", color = MaxTimeColors.muted, fontSize = 11.5.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp).clickable { router.navigate(Screen.FindResults) }
        )
    }
}
