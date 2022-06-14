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

import dev.kord.common.entity.ChannelType
import dev.kord.common.entity.Permission
import dev.kord.common.entity.Snowflake
import net.ormr.humbaba.utils.getGuildId
import net.ormr.kommando.commands.*
import net.ormr.kommando.commands.arguments.slash.boolean
import net.ormr.kommando.commands.arguments.slash.channel
import net.ormr.kommando.commands.permissions.permission
import net.ormr.kommando.processor.Tag
import net.ormr.kommando.structures.messageFilter
import net.ormr.kommando.utils.respondEphemeral
import net.ormr.kommando.utils.toChannelMention

// TODO: these need actual permissions
fun humbabaCommands(@Tag devGuildId: Snowflake, repository: HumbabaGuildConfigRepository) = commands {
    globalSlashCommand("humbaba_config", "Commands for setting up Humbaba bot.") {
        permission {
            +Permission.ManageGuild
            isAllowedInDms = false
        }
        group("set", "Sets an option to a value.") {
            subCommand("nsfw_content", "Handles the 'nsfw_content' config value.") {
                execute(boolean("allowed", "Whether NSFW content is allowed or not.")) { (isAllowed) ->
                    repository.setNsfwContentEnabled(getGuildId(), isAllowed)
                    interaction.respondEphemeral("NSFW content is now ${formatAllowed(isAllowed)}.")
                }
            }
            subCommand("message_snooping", "Handles the 'message_snooping' config value.") {
                execute(boolean("allowed", "Whether message snooping is allowed or not.")) { (isAllowed) ->
                    repository.setMessageSnooping(getGuildId(), isAllowed)
                    interaction.respondEphemeral("Message snooping is now ${formatAllowed(isAllowed)}.")
                }
            }
        }
        group("add", "Adds a value to a list of options.") {
            subCommand("art_channel", "Adds an art channel.") {
                execute(channel("channel", "The channel to add.")) { (channel) ->
                    if (channel.type != ChannelType.GuildText) {
                        interaction.respondEphemeral("Only normal text channels can be marked as art channels.")
                        return@execute
                    }

                    val channelMention = channel.id.toChannelMention()
                    if (repository.addArtChannel(getGuildId(), channel.id)) {
                        interaction.respondEphemeral("$channelMention is already a art channel.")
                    } else {
                        interaction.respondEphemeral("$channelMention has been added as a art channel.")
                    }
                }
            }
        }
        group("remove", "Removes a value from options.") {
            subCommand("art_channel", "Removes an art channel.") {
                execute(channel("channel", "The channel to remove.")) { (channel) ->
                    val channelMention = channel.id.toChannelMention()
                    if (repository.removeArtChannel(getGuildId(), channel.id)) {
                        interaction.respondEphemeral("Removed channel $channelMention from art channels.")
                    } else {
                        interaction.respondEphemeral("$channelMention is not an art channel.")
                    }
                }
            }
        }
        group("get", "Prints the value of a specific config value.") {
            subCommand("art_channels", "Prints all the channels for the 'art_channels' config value.") {
                execute {
                    val artChannels = repository.getArtChannels(getGuildId())
                    if (artChannels.isEmpty()) {
                        interaction.respondEphemeral("There are currently no registered art channels.")
                    } else {
                        val formattedChannels = artChannels.joinToString { it.toChannelMention() }
                        interaction.respondEphemeral("Currently registered art channels: $formattedChannels")
                    }
                }
            }
            subCommand("nsfw_content", "Prints the value of the 'nsfw_content' config value.") {
                execute {
                    val isAllowed = formatAllowed(repository.isNsfwContentEnabled(getGuildId()))
                    interaction.respondEphemeral("NSFW content is currently $isAllowed.")
                }
            }
        }
        group("info", "Prints information about the specific config value.") {
            subCommand("nsfw_content", "Prints info about the 'nsfw_content' config value.") {
                execute {
                    interaction.respondEphemeral("""
                        Whether or not Humbaba is allowed to post embeds for NSFW content.
                        
                        Note that NSFW content will only be posted to channels which are registered in `art_channels`, and are set as an age restricted channel.
                    """.trimIndent())
                }
            }
            subCommand("message_snooping", "Prints info about the 'message_snooping' config value.") {
                execute {
                    interaction.respondEphemeral("""
                        Whether or not Humbaba is allowed to snoop on all the messages sent in the guild.
                        
                        Having this set to `true` enables Humbaba to respond with embeds to user messages containing direct links to sources with embedding.
                    """.trimIndent())
                }
            }
            subCommand("art_channels", "art baby") {
                execute {
                    interaction.respondEphemeral("""
                        The channels in this guild that are registered as an art channel.
                        
                        Being registered as an art channel means that Humbaba is allowed to create embeds for art related sources in that channel.
                        
                        Art that is considered NSFW *(Not always foolproof, as it sometimes depends on what the art creator has tagged it as, and they don't always tag R18 content as R18)* will not have embeds created unless the `nsfw_content` config value is set to `true` and the art channel is set as an age restricted channel.
                    """.trimIndent())
                }
            }
        }
        subCommand("reset", "Resets the config for this guild to the default values.") {
            execute {
                repository.setConfigToDefault(getGuildId())
                interaction.respondEphemeral("The config for this guild has been reset to the default values.")
            }
        }
    }
}

fun humbabaMessageSnoopingFilter(repository: HumbabaGuildConfigRepository) =
    messageFilter { guildId?.let(repository::isMessageSnoopingAllowed) ?: true }

private fun formatAllowed(isAllowed: Boolean): String = if (isAllowed) "allowed" else "not allowed"