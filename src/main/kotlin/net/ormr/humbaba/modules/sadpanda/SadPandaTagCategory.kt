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

class SadPandaTagCategory private constructor(val name: String) {
    companion object {
        private val cache = hashMapOf<String, SadPandaTagCategory>()

        val FEMALE: SadPandaTagCategory = of("female")
        val MALE: SadPandaTagCategory = of("male")
        val LANGUAGE: SadPandaTagCategory = of("language")
        val OTHER: SadPandaTagCategory = of("other")

        fun of(name: String): SadPandaTagCategory = cache.getOrPut(name) { SadPandaTagCategory(name) }
    }

    override fun equals(other: Any?): Boolean = when {
        this === other -> true
        other !is SadPandaTagCategory -> false
        name != other.name -> false
        else -> true
    }

    override fun hashCode(): Int = name.hashCode()
}