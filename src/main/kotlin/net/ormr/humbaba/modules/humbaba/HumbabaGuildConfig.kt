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

package net.ormr.humbaba.modules.humbaba

import dev.kord.common.entity.Snowflake
import kotlinx.serialization.Serializable
import org.kodein.db.model.Id

@Serializable
data class HumbabaGuildConfig(
    @Id val guildId: Snowflake,
    val artChannels: Set<Snowflake> = emptySet(),
    val isNsfwContentAllowed: Boolean = false,
    val isMessageSnoopingAllowed: Boolean = false,
)