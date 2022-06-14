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

package net.ormr.humbaba.clikt

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.InvalidFileFormat
import com.github.ajalt.clikt.parameters.options.Option
import com.github.ajalt.clikt.sources.ValueSource
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.*
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readText

// based on https://github.com/ajalt/clikt/blob/master/samples/json/src/main/kotlin/com/github/ajalt/clikt/samples/json/JsonValueSource.kt

class JsonValueSource(
    private val root: JsonObject,
) : ValueSource {
    override fun getValues(context: Context, option: Option): List<ValueSource.Invocation> {
        var cursor: JsonElement? = root
        val parts = option.valueSourceKey?.split(".")
            ?: (context.commandNameWithParents().drop(1) + ValueSource.name(option))
        println(parts)
        for (part in parts) {
            if (cursor !is JsonObject) return emptyList()
            cursor = cursor[part]
        }

        if (cursor == null) return emptyList()

        if (cursor is JsonArray) {
            return ValueSource.Invocation.just(cursor.map { ValueSource.Invocation.value(it.getStringValue()) })
        }
        return ValueSource.Invocation.just(cursor.getStringValue())
    }

    private fun JsonElement.getStringValue(): String? = when (this) {
        is JsonObject -> toString()
        is JsonArray -> toString()
        is JsonPrimitive -> contentOrNull
        else -> error("Either new json type, or json literal, which can't be accessed. $this")
    }

    companion object {
        fun from(file: Path, requireValid: Boolean = false): JsonValueSource {
            if (!file.isRegularFile()) return JsonValueSource(JsonObject(emptyMap()))

            val json = try {
                Json.parseToJsonElement(file.readText()) as? JsonObject
                    ?: throw InvalidFileFormat(file.toAbsolutePath().toString(), "object expected", 1)
            } catch (e: SerializationException) {
                if (requireValid) throw InvalidFileFormat(file.name, e.message ?: "could not read file")
                JsonObject(emptyMap())
            }
            return JsonValueSource(json)
        }
    }
}