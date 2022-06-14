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

import com.github.michaelbull.logging.InlineLogger
import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.channel.TextChannel
import net.ormr.kommando.processor.Include
import org.kodein.db.DB
import org.kodein.db.getById

@Include
class HumbabaGuildConfigRepository(private val db: DB) {
    companion object {
        private val logger = InlineLogger()
    }

    fun createDefaultConfig(guildId: Snowflake): HumbabaGuildConfig = HumbabaGuildConfig(guildId)

    fun getConfig(guildId: Snowflake): HumbabaGuildConfig =
        db.getById(guildId) ?: setConfigToDefault(guildId).also {
            logger.info { "No guild config found for $guildId, creating a default one." }
        }

    fun setConfig(config: HumbabaGuildConfig) {
        logger.debug { "Inserting guild config $config" }
        db.put(config)
    }

    inline fun editConfig(guildId: Snowflake, factory: (HumbabaGuildConfig) -> HumbabaGuildConfig) {
        val config = getConfig(guildId)
        setConfig(factory(config))
    }

    fun setConfigToDefault(guildId: Snowflake): HumbabaGuildConfig = createDefaultConfig(guildId).also(::setConfig)

    fun getArtChannels(guildId: Snowflake): Set<Snowflake> = getConfig(guildId).artChannels

    fun isArtChannel(guildId: Snowflake, channelId: Snowflake): Boolean = channelId in getArtChannels(guildId)

    fun addArtChannel(guildId: Snowflake, channelId: Snowflake): Boolean {
        val config = getConfig(guildId)
        val isArtChannel = channelId in config.artChannels
        if (!isArtChannel) setConfig(config.copy(artChannels = config.artChannels + channelId))
        return isArtChannel
    }

    fun removeArtChannel(guildId: Snowflake, channelId: Snowflake): Boolean {
        val config = getConfig(guildId)
        val isArtChannel = channelId in config.artChannels
        if (isArtChannel) setConfig(config.copy(artChannels = config.artChannels - channelId))
        return isArtChannel
    }

    fun isNsfwContentEnabled(guildId: Snowflake): Boolean = getConfig(guildId).isNsfwContentAllowed

    fun setNsfwContentEnabled(guildId: Snowflake, isEnabled: Boolean) {
        editConfig(guildId) { it.copy(isNsfwContentAllowed = isEnabled) }
    }

    fun isMessageSnoopingAllowed(guildId: Snowflake): Boolean = getConfig(guildId).isMessageSnoopingAllowed

    fun setMessageSnooping(guildId: Snowflake, isAllowed: Boolean) {
        editConfig(guildId) { it.copy(isMessageSnoopingAllowed = isAllowed) }
    }

    fun canPostArtIn(guildId: Snowflake, channel: TextChannel): Boolean = isArtChannel(guildId, channel.id)

    fun canPostNsfwContentIn(guildId: Snowflake, channel: TextChannel): Boolean {
        val config = getConfig(guildId)
        return channel.id in config.artChannels && (config.isNsfwContentAllowed && channel.isNsfw)
    }
}