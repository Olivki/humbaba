/*
 * Copyright 2022 Oliver Berg
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.ormr.humbaba

import dev.kord.common.entity.Snowflake
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.ormr.humbaba.serialization.EnvString

@Serializable
data class HumbabaConfig(
    @SerialName("sad_panda") val sadPanda: SadPanda?,
    @SerialName("file_hosts") val fileHosts: FileHosts,
    @SerialName("dump_guild") val dumpGuild: DumpGuild,
) {
    @Serializable
    data class SadPanda(
        val igneous: EnvString,
        @SerialName("ipb_member_id") val ipbMemberId: EnvString,
        @SerialName("ipb_pass_hash") val ipbPassHash: EnvString,
    ) {
        val cookies = mapOf("igneous" to igneous, "ipb_member_id" to ipbMemberId, "ipb_pass_hash" to ipbPassHash)
    }

    @Serializable
    data class FileHosts(val catbox: Catbox, val zz: Zz) {
        @Serializable
        data class Catbox(@SerialName("user_hash") val userHash: EnvString)

        @Serializable
        data class Zz(val token: EnvString)
    }

    @Serializable
    data class DumpGuild(
        @SerialName("guild_id") val guildId: Snowflake,
        @SerialName("channel_id") val channelId: Snowflake,
    )
}