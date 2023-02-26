/*
 * Copyright 2012-2023 The Feign Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package example.github

import feign.Logger
import feign.Logger.ErrorLogger
import feign.Param
import feign.Request
import feign.RequestLine
import feign.Response
import feign.codec.Decoder
import feign.codec.Encoder
import feign.codec.ErrorDecoder
import feign.gson.GsonEncoder
import feign.kotlin.CoroutineFeign
import java.io.IOException
import java.util.concurrent.TimeUnit

suspend fun main() {
    val github = GitHub.connect()
    println("Let's fetch and print a list of the contributors to this org.")
    val contributors = github.contributors("openfeign")
    for (contributor in contributors) {
        println(contributor)
    }
    println("Now, let's cause an error.")
    try {
        github.contributors("openfeign", "some-unknown-project")
    } catch (e: GitHubClientError) {
        println(e.message)
    }
    println("Now, try to create an issue - which will also cause an error.")
    try {
        val issue = GitHub.Issue(
            title = "The title",
            body = "Some Text",
        )
        github.createIssue(issue, "OpenFeign", "SomeRepo")
    } catch (e: GitHubClientError) {
        println(e.message)
    }
}

/**
 * Inspired by `com.example.retrofit.GitHubClient`
 */

interface GitHub {
    data class Repository(
        val name: String
    )

    data class Contributor(
        val login: String
    )

    data class Issue(
        val title: String,
        val body: String,
        val assignees: List<String> = emptyList(),
        val milestone: Int = 0,
        val labels: List<String> = emptyList(),
    )

    @RequestLine("GET /users/{username}/repos?sort=full_name")
    suspend fun repos(@Param("username") owner: String): List<Repository>

    @RequestLine("GET /repos/{owner}/{repo}/contributors")
    suspend fun contributors(@Param("owner") owner: String, @Param("repo") repo: String): List<Contributor>

    @RequestLine("POST /repos/{owner}/{repo}/issues")
    suspend fun createIssue(issue: Issue, @Param("owner") owner: String, @Param("repo") repo: String)

    companion object {
        fun connect(): GitHub {
            val decoder: Decoder = feign.gson.GsonDecoder()
            val encoder: Encoder = GsonEncoder()
            return CoroutineFeign.builder<Unit>()
                .encoder(encoder)
                .decoder(decoder)
                .errorDecoder(GitHubErrorDecoder(decoder))
                .logger(ErrorLogger())
                .logLevel(Logger.Level.BASIC)
                .requestInterceptor { template ->
                    template.header(
                        // not available when building PRs...
                        // https://docs.travis-ci.com/user/environment-variables/#defining-encrypted-variables-in-travisyml
                        "Authorization",
                        "token 383f1c1b474d8f05a21e7964976ab0d403fee071");
                }
                .options(Request.Options(10, TimeUnit.SECONDS, 60, TimeUnit.SECONDS, true))
                .target(GitHub::class.java, "https://api.github.com")
        }
    }
}

/** Lists all contributors for all repos owned by a user.  */
suspend fun GitHub.contributors(owner: String): List<String> {
    return repos(owner)
        .flatMap { contributors(owner, it.name) }
        .map { it.login }
        .distinct()
}

internal class GitHubClientError() : RuntimeException() {
    override val message: String? = null
}

internal class GitHubErrorDecoder(
    private val decoder: Decoder
) : ErrorDecoder {
    private val defaultDecoder: ErrorDecoder = ErrorDecoder.Default()
    override fun decode(methodKey: String, response: Response): Exception {
        return try {
            // must replace status by 200 other GSONDecoder returns null
            val response = response.toBuilder().status(200).build()
            decoder.decode(response, GitHubClientError::class.java) as Exception
        } catch (fallbackToDefault: IOException) {
            defaultDecoder.decode(methodKey, response)
        }
    }
}
