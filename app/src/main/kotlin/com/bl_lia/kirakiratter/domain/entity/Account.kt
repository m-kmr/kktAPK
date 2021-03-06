package com.bl_lia.kirakiratter.domain.entity

import com.bl_lia.kirakiratter.domain.entity.realm.RealmAccount
import java.io.Serializable

data class Account(
        val id: Int,
        val userName: String? = null,
        val displayName: String? = null,
        val avatar: String? = null,
        val header: String? = null,
        val note: String? = null,
        val followersCount: Int? = null,
        val followingCount: Int? = null,
        val statusesCount: Int? = null
): Serializable {

    companion object {
        fun invalidAccount(): Account = Account(-1)
    }

    val preparedDisplayName: String? =
            if (displayName.isNullOrEmpty()) {
                userName
            } else {
                displayName
            }

    val isInvalid: Boolean = id == -1

    fun toRealm(): RealmAccount =
            RealmAccount(
                    id = id,
                    userName = userName,
                    displayName = displayName,
                    avatar = avatar,
                    header = header,
                    note = note,
                    followersCount = followersCount,
                    followingCount = followingCount,
                    statusesCount = statusesCount
            )
}