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

package net.ormr.humbaba.utils

import io.github.reactivecircus.cache4k.Cache
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@Suppress("FunctionName")
inline fun <K : Any, V : Any> Cache(builder: Cache.Builder.() -> Unit = {}): Cache<K, V> {
    contract {
        callsInPlace(builder, InvocationKind.EXACTLY_ONCE)
    }

    return Cache.Builder().apply(builder).build()
}

operator fun <K : Any, V : Any> Cache<K, V>.contains(key: K): Boolean = get(key) != null

fun <K : Any, V : Any> Cache<K, V>.getValue(key: K): V =
    get(key) ?: throw NoSuchElementException("Value with key '$key' not found in cache..")