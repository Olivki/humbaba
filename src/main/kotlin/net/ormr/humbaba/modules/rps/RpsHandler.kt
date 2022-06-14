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

import dev.kord.common.entity.Snowflake
import net.ormr.humbaba.utils.Cache
import net.ormr.kommando.processor.Include
import net.ormr.krautils.collections.enumMapOf
import org.kodein.db.DB

@Include
class RpsHandler(private val db: DB) {
    private val matchCache = Cache<Snowflake, RpsMatch> { expireAfterWrite(RPS_MATCH_DURATION) }
    private val moveCache = Cache<Snowflake, RpsMove> { expireAfterWrite(RPS_MATCH_DURATION) }

    fun registerMatch(
        messageId: Snowflake,
        challenger: Snowflake,
        challengee: Snowflake,
        callback: RpsMatchCallback,
    ) {
        matchCache.put(messageId, RpsMatch(challenger, challengee, callback))
    }

    private fun isParticipant(messageId: Snowflake, userId: Snowflake): Boolean {
        val match = matchCache.get(messageId) ?: return false
        return match.challenger == userId || match.challengee == userId
    }

    private fun getRole(match: RpsMatch, userId: Snowflake): RpsRole? = when (userId) {
        match.challenger -> RpsRole.CHALLENGER
        match.challengee -> RpsRole.CHALLENGEE
        else -> null
    }

    suspend fun registerAttack(messageId: Snowflake, userId: Snowflake, attack: RpsAttack) {
        val match = matchCache.get(messageId)
        if (match != null && isParticipant(messageId, userId)) {
            val role = getRole(match, userId) ?: error("Couldn't find role for message $messageId and user $userId")
            val move = moveCache.get(messageId) { enumMapOf() }
            move.putIfAbsent(role, RpsAttackMove(userId, attack))

            // there's 2 enum constants, so if size is 2, then map is full
            if (move.size == 2) {
                val challengerMove = move.getValue(RpsRole.CHALLENGER)
                val challengeeMove = move.getValue(RpsRole.CHALLENGEE)
                match.callback(challengerMove, challengeeMove)
                endMatch(messageId)
            }
        }
    }

    fun endMatch(messageId: Snowflake) {
        matchCache.invalidate(messageId)
        moveCache.invalidate(messageId)
    }
}