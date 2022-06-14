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

import com.github.ajalt.clikt.core.BadParameterValue
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.long
import com.github.ajalt.clikt.parameters.types.path
import com.github.michaelbull.logging.InlineLogger
import dev.kord.common.entity.Permissions
import dev.kord.common.entity.Snowflake
import dev.kord.gateway.Intent
import dev.kord.gateway.Intents
import dev.kord.gateway.PrivilegedIntent
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import net.ormr.humbaba.clikt.TomlValueSource
import net.ormr.humbaba.modules.sadpanda.SadPandaController
import net.ormr.katbox.Catbox
import net.ormr.kommando.bot
import net.ormr.kommando.commands.permissions.defaultCommandPermissions
import net.peanuuutz.tomlkt.Toml
import org.kodein.db.DB
import org.kodein.db.Value
import org.kodein.db.ValueConverter
import org.kodein.db.impl.open
import org.kodein.di.bindEagerSingleton
import org.kodein.di.bindSingleton
import org.kodein.di.instance
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText

class HumbabaCli : CliktCommand(printHelpOnEmptyArgs = true) {
    companion object {
        private val logger = InlineLogger()
    }

    val botToken by option(help = "The Discord bot token", envvar = "HUMBABA_BOT_TOKEN").required()
    val dataDirectory by option(help = "The directory wherein the bot will dump a lot of files")
        .path(canBeFile = false, canBeSymlink = false)
        .defaultLazy { Path("./data/").createDirectories() }
    val dbDirectory by option("-db", "--database-directory", help = "The directory to store the DB in")
        .path(canBeFile = false, canBeSymlink = false)
        .defaultLazy { dataDirectory.resolve("db").createDirectories() }
    val configFile by option(help = "The config file for Humbaba.")
        .path(canBeDir = false, canBeSymlink = false, mustExist = true)
        .defaultLazy { Path("./config.toml").takeIf { it.exists() } ?: dataDirectory.resolve("config.toml") }
        .check("No file supplied and no file exists at default locations") { it.exists() }
    val devMode by option(help = "Should the bot run in dev mode").flag()
    val devGuild by option(help = "The ID of the dev guild").long().convert { Snowflake(it) }.required()

    init {
        versionOption("0.1.0")
        context {
            valueSources(
                TomlValueSource.from(Path("./cli.toml")),
                TomlValueSource.from(Path("./data/", "cli.toml")),
            )
        }
    }

    @OptIn(PrivilegedIntent::class)
    override fun run() = runBlocking {
        logger.info { "Dev mode is ${if (devMode) "on" else "off"}, dev guild id is $devGuild" }
        val config = createHumbabaConfig()
        if (config.sadPanda == null) logger.warn { "Sad panda functionality is turned off, as no cookies were provided." }
        bot(
            token = botToken,
            intents = Intents.nonPrivileged + Intent.MessageContent,
            presence = { playing("Dungeon Defenders") },
            di = {
                bindEagerSingleton { this@HumbabaCli }
                bindEagerSingleton(tag = "isDevMode") { devMode }
                bindEagerSingleton(tag = "devGuildId") { devGuild }
                bindEagerSingleton { config }
                bindSingleton {
                    DB.open(
                        dbDirectory.toAbsolutePath().toString(),
                        ValueConverter.forClass<Snowflake> { Value.of(it.value.toLong()) }
                    )
                }
                bindSingleton { Catbox(config.fileHosts.catbox.userHash) }
            },
        ) {
            Runtime.getRuntime().addShutdownHook(Thread {
                instance<SadPandaController>().close()
                instance<DB>().close()
            })
            defaultCommandPermissions {
                global {
                    defaultPermissions = Permissions()
                    isAllowedInDms = false
                }
                guild {
                    defaultPermissions = Permissions()
                }
            }
        }
    }

    private fun createHumbabaConfig(): HumbabaConfig = try {
        Toml.decodeFromString(HumbabaConfig.serializer(), configFile.readText())
    } catch (e: SerializationException) {
        logger.error(e) { "Could not read TOML from file." }
        throw BadParameterValue("Config file is not a valid TOML file", "config-file")
    }
}