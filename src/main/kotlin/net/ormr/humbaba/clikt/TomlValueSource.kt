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
import com.github.ajalt.clikt.parameters.options.Option
import com.github.ajalt.clikt.sources.ValueSource
import net.peanuuutz.tomlkt.*
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

// based on https://github.com/ajalt/clikt/blob/master/samples/json/src/main/kotlin/com/github/ajalt/clikt/samples/json/JsonValueSource.kt

class TomlValueSource(
    private val root: TomlTable,
) : ValueSource {
    override fun getValues(context: Context, option: Option): List<ValueSource.Invocation> {
        var cursor: TomlElement? = root
        val parts = getKeyParts(option, context)
        for (part in parts) {
            if (cursor !is TomlTable) return emptyList()
            cursor = cursor[part]
        }

        if (cursor == null) return emptyList()

        if (cursor is TomlArray) {
            val stringValues = cursor.map { ValueSource.Invocation.value(it.getStringValue()) }
            return ValueSource.Invocation.just(stringValues)
        }

        return ValueSource.Invocation.just(cursor.getStringValue())
    }

    private fun getKeyParts(option: Option, context: Context): List<String> = (option.valueSourceKey?.split('.')
        ?: (context.commandNameWithParents().drop(1) + ValueSource.name(option)))

    private fun TomlElement.getStringValue(): String? = when (this) {
        TomlNull -> null
        is TomlLiteral -> content
        is TomlArray -> toString()
        is TomlTable -> toString()
    }

    companion object {
        fun from(file: Path): TomlValueSource {
            if (!file.isRegularFile()) return TomlValueSource(TomlTable(emptyMap<String, Any>()))
            return TomlValueSource(Toml.parseToTomlTable(file.readText()))
        }
    }
}