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

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.Closeable

class ZzHt internal constructor(val token: String, dummy: Unit) : Closeable {
    private val client: HttpClient = HttpClient {
        expectSuccess = true
        HttpResponseValidator {
            handleResponseExceptionWithRequest { cause, request ->
                val exception = cause as? ClientRequestException ?: return@handleResponseExceptionWithRequest
                val response = exception.response
                val body = response.body<BasicResponse>()
                throw IllegalArgumentException(body.description)
            }
        }
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
            })
        }
        defaultRequest {
            url {
                protocol = URLProtocol.HTTPS
                host = "zz.ht"
                path("api/")
            }
        }
    }

    private suspend inline fun <reified R, reified B> post(
        route: String,
        body: B? = null,
    ): R = client.post(route) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }.body()

    suspend fun verifyToken() {
        val dab: String = post("tokens/verify", TokenBody(token))
        println(dab)
    }

    override fun close() {
        client.close()
    }

    @Serializable
    private data class BasicResponse(val success: Boolean, val description: String?)

    @Serializable
    private data class TokenBody(val token: String)
}

suspend fun ZzHt(token: String): ZzHt {
    val instance = ZzHt(token, Unit)
    instance.verifyToken()
    return instance
}

suspend fun main() {
    ZzHt("baby")
}