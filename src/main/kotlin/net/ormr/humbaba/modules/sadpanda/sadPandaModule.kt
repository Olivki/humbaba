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

package net.ormr.humbaba.modules.sadpanda

import com.github.michaelbull.logging.InlineLogger
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.reply
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kord.rest.builder.message.create.UserMessageCreateBuilder
import dev.kord.rest.builder.message.create.allowedMentions
import dev.kord.rest.builder.message.create.embed
import dev.kord.x.emoji.Emojis
import dev.kord.x.emoji.addReaction
import dev.kord.x.emoji.toReaction
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.toInstant
import net.ormr.humbaba.HumbabaConfig
import net.ormr.humbaba.modules.humbaba.HumbabaGuildConfigRepository
import net.ormr.humbaba.modules.sadpanda.SadPandaController.Companion.COMIC_URL_REGEX
import net.ormr.humbaba.modules.sadpanda.SadPandaController.Companion.PAGE_URL_REGEX
import net.ormr.humbaba.utils.WHITESPACE_REGEX
import net.ormr.humbaba.utils.abbreviate
import net.ormr.kommando.KommandoAware
import net.ormr.kommando.commands.*
import net.ormr.kommando.commands.arguments.slash.long
import net.ormr.kommando.commands.arguments.slash.string
import net.ormr.kommando.commands.permissions.permission
import net.ormr.kommando.processor.Tag
import net.ormr.kommando.structures.eventListener
import net.ormr.kommando.utils.respond

private val logger = InlineLogger()

fun sadPandaCommands(
    @Tag devGuildId: Snowflake,
    repository: HumbabaGuildConfigRepository,
    controller: SadPandaController,
) = commands {
    if (!controller.canScrape()) return@commands

    // TODO: make sure that it's posted only in channels 'repository.canPostNsfwContentIn' allows
    globalSlashCommand("sadpanda", "Commands for sad panda.") {
        permission {
            isAllowedInDms = false
        }
        group("fetch", "Retrieves something from e(x|-)hentai.") {
            subCommand("comic", "Retrieves and posts an embed of a comic.") {
                execute(string("url", "The url to the comic.")) { (url) ->
                    // TODO: Public response
                    val response = interaction.deferEphemeralResponse()
                    kord.launch {
                        response.respond("Not implemented.")
                    }
                }
            }
            subCommand("page", "Retrieves and posts a page.") {
                execute(string("url", "The url to the comic."), long("page", "The page to retrieve.")) { (url, page) ->
                    // TODO: Public response
                    val response = interaction.deferEphemeralResponse()
                    kord.launch {
                        response.respond("Not implemented.")
                    }
                }
            }
        }
    }
}

fun sadPandaMessageListener(
    repository: HumbabaGuildConfigRepository,
    controller: SadPandaController,
    config: HumbabaConfig,
) = eventListener {
    if (!controller.canScrape()) return@eventListener

    on<MessageCreateEvent> {
        val channel = message.getChannel() as? TextChannel ?: return@on
        val guildId = channel.guildId
        if (!repository.canPostNsfwContentIn(guildId, channel)) return@on
        val lines = message.content.splitToSequence(WHITESPACE_REGEX)

        try {
            lines.mapToRegex(COMIC_URL_REGEX)
                .map { SadPandaId(it.groupValues[1], it.groupValues[2]) }
                .forEach { id -> postComic(controller, id, config.dumpGuild) }

            lines.mapToRegex(PAGE_URL_REGEX)
                .map { SadPandaId(it.groupValues[2], it.groupValues[1]) to it.groupValues[3].toInt() }
                .forEach { (id, page) -> postComicPage(controller, id, page) }
        } catch (e: Exception) {
            message.addReaction(Emojis.x.toReaction())
            logger.error(e) { e.message }
            throw e
        }
    }
}

private fun Sequence<String>.mapToRegex(regex: Regex): Sequence<MatchResult> =
    filter { it.matches(regex); }.mapNotNull(regex::matchEntire)

context(KommandoAware, MessageCreateEvent)
        private fun MessageCreateEvent.postComic(
    controller: SadPandaController,
    id: SadPandaId,
    dumpGuild: HumbabaConfig.DumpGuild,
): Job = kord.launch {
    message.addReaction(Emojis.whiteCheckMark)

    // 981238607723520070
    // 981238607723520070
    val comic = controller.getComic(id)

    println(kord.guilds.map { it.id }.toList())

    val dumpChannel = (kord.getChannel(dumpGuild.channelId) as? TextChannel)
        ?: error("Bot is not a member of defined dump guild ${dumpGuild.guildId}")
    val coverUrl = dumpChannel.createMessage {
        addFile(comic.cover.name, comic.cover.content.inputStream())
    }.attachments.first().url

    message.reply {
        createSadPandaEmbed(comic, coverUrl)

        allowedMentions {
            repliedUser = true
        }
    }

    message.deleteOwnReaction(Emojis.whiteCheckMark.toReaction())
}

context(KommandoAware, MessageCreateEvent)
        private fun postComicPage(controller: SadPandaController, id: SadPandaId, page: Int): Job = kord.launch {
    message.addReaction(Emojis.whiteCheckMark)

    val imageContents = controller.getComicPageContents(id, page)

    message.reply {
        addFile(imageContents.name, imageContents.content.inputStream())

        allowedMentions {
            repliedUser = true
        }
    }

    message.deleteOwnReaction(Emojis.whiteCheckMark.toReaction())
}

private fun UserMessageCreateBuilder.createSadPandaEmbed(comic: SadPandaComic, coverUrl: String) {
    embed {
        title = comic.mainTitle.abbreviate(256)
        url = comic.url
        description = comic.subTitle?.abbreviate(256)
        image = coverUrl
        timestamp = comic.publishDate.toInstant(UtcOffset.ZERO)
        footer {
            text = "${comic.pageCount} pages • ${comic.favoriteCount} favorites"
        }

        comic.tags.forEach { (category, tags) ->
            field(category.name, inline = true) {
                val fancyTags = tags.joinToString(separator = ", ") { (_, name, url) ->
                    url?.let { "[${name}]($url)" } ?: name
                }

                if (fancyTags.length > EmbedBuilder.Field.Limits.value) {
                    val normalTags = tags.joinToString { (_, name, _) -> name }
                    if (normalTags.length > EmbedBuilder.Field.Limits.value) {
                        "${normalTags.substring(0, EmbedBuilder.Field.Limits.value - 2)}.."
                    } else normalTags
                } else fancyTags
            }
        }
    }
}