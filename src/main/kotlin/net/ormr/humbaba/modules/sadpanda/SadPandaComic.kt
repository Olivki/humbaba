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

import kotlinx.datetime.LocalDateTime

data class SadPandaComic(
    val id: SadPandaId,
    val mainTitle: String,
    val subTitle: String?,
    val tags: Map<SadPandaTagCategory, List<SadPandaTag>>,
    val cover: SadPandaImageContent,
    val publishDate: LocalDateTime,
    val pageCount: Int,
    val favoriteCount: Int,
) {
    val url: String
        get() = "https://exhentai.org/g/${id.id}/${id.hash}/"
}