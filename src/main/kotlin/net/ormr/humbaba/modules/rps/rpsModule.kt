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

package net.ormr.humbaba.modules.rps

import dev.kord.common.entity.ButtonStyle
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.interaction.response.createPublicFollowup
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.rest.builder.message.create.allowedMentions
import dev.kord.rest.builder.message.modify.allowedMentions
import dev.kord.x.emoji.DiscordEmoji
import dev.kord.x.emoji.Emojis
import kotlinx.coroutines.cancel
import net.ormr.humbaba.utils.getGuildId
import net.ormr.kommando.commands.arguments.slash.user
import net.ormr.kommando.commands.commands
import net.ormr.kommando.commands.execute
import net.ormr.kommando.commands.globalSlashCommand
import net.ormr.kommando.commands.permissions.permission
import net.ormr.kommando.commands.subCommand
import net.ormr.kommando.components.*
import net.ormr.kommando.processor.Tag
import net.ormr.kommando.utils.respondEphemeral
import kotlin.time.Duration.Companion.minutes

val RPS_MATCH_DURATION = 5.minutes

private fun rpsComponents(handler: RpsHandler) = components {
    fun rpsButton(emoji: DiscordEmoji, attack: RpsAttack) {
        button(ButtonStyle.Secondary) {
            emoji(emoji)
            execute {
                println(attack)
                handler.registerAttack(message.id, interaction.user.id, attack)
                interaction.respondEphemeral("You've picked $attack!")
            }
        }
    }

    rpsButton(Emojis.fist, RpsAttack.ROCK)
    rpsButton(Emojis.v, RpsAttack.SCISSOR)
    rpsButton(Emojis.raisedHand, RpsAttack.PAPER)
}

fun rpsCommands(@Tag devGuildId: Snowflake, handler: RpsHandler) = commands {
    globalSlashCommand("rps", "Good ol' rock, paper, scissors.") {
        permission {
            isAllowedInDms = false
        }
        subCommand("challenge", "Challenge someone to a deadly game of rock, paper, scissors.") {
            execute(user("challengee", "The user to challenge.")) { (challengee) ->
                val challenger = interaction.user
                if (challengee.id == challenger.id) {
                    interaction.respondEphemeral("You can't challenge yourself.")
                    return@execute
                }
                val rpsComponents = rpsComponents(handler)
                val response = interaction.deferPublicResponse()
                val matchMessage = response.respond {
                    content =
                        "${interaction.user.asMember(getGuildId()).displayName} just challenged <@${challengee.id}> to a deadly game of rock, paper, scissors!"
                    rpsComponents.applyToMessageAndRegister(kommando, this)
                    allowedMentions {
                        users += challengee.id
                    }
                }
                val disableJob = matchMessage.disableComponentsIn(
                    kommando,
                    RPS_MATCH_DURATION,
                    rpsComponents,
                    shouldUnregister = true,
                )
                handler.registerMatch(
                    matchMessage.message.id,
                    challenger.id,
                    challengee.id,
                ) { (challenger, a), (challengee, b) ->
                    val matchResponse = when {
                        a.canBeat(b) -> "$a beats $b! <@${challenger}> wins."
                        a.isTie(b) -> "It's a tie! No one wins."
                        else -> "$b beats $a! <@${challengee}> wins."
                    }
                    disableJob.cancel("Manually disabling components.")
                    rpsComponents.disableAllAndApplyToMessage(kommando, matchMessage.message, shouldUnregister = true)
                    matchMessage.createPublicFollowup {
                        content = matchResponse
                        allowedMentions {
                            users += challenger
                            users += challengee
                        }
                    }
                }
            }
        }
        subCommand("score", "Check your score against someone.") {
            execute(user("user", "The user to check your score against.")) { (user) ->
                interaction.respondEphemeral("Not implemented lol")
            }
        }
    }
}