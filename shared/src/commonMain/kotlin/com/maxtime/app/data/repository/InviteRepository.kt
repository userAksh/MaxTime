package com.maxtime.app.data.repository

import androidx.compose.runtime.mutableStateMapOf
import com.maxtime.app.data.Invite
import com.maxtime.app.data.InviteStatus

interface InviteRepository {
    val invites: MutableMap<String, Invite>

    fun sendInvite(personName: String, activity: String, whenLabel: String): Invite
    fun accept(personName: String): Invite?
    fun decline(personName: String)
}

class MockInviteRepository : InviteRepository {
    override val invites: MutableMap<String, Invite> = mutableStateMapOf()

    override fun sendInvite(personName: String, activity: String, whenLabel: String): Invite {
        val invite = Invite(personName, activity, whenLabel)
        invites[personName] = invite
        return invite
    }

    override fun accept(personName: String): Invite? {
        val invite = invites[personName] ?: return null
        if (invite.status != InviteStatus.ACCEPTED) invite.status = InviteStatus.ACCEPTED
        return invite
    }

    override fun decline(personName: String) {
        invites[personName]?.status = InviteStatus.DECLINED
    }
}
