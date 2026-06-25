package com.example.service

import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

// --- Exa Search Data Classes ---

data class ExaSearchRequest(
    @Json(name = "query") val query: String,
    @Json(name = "type") val type: String = "auto",
    @Json(name = "numResults") val numResults: Int = 10,
    @Json(name = "contents") val contents: ExaContents? = null,
    @Json(name = "filters") val filters: ExaFilters? = null
)

data class ExaContents(
    @Json(name = "text") val text: Boolean = false,
    @Json(name = "highlights") val highlights: Boolean = false,
    @Json(name = "summary") val summary: Boolean = false
)

data class ExaFilters(
    @Json(name = "category") val category: String? = null,
    @Json(name = "dateAfter") val dateAfter: String? = null,
    @Json(name = "dateBefore") val dateBefore: String? = null,
    @Json(name = "site") val site: String? = null,
    @Json(name = "includeDomains") val includeDomains: List<String>? = null,
    @Json(name = "excludeDomains") val excludeDomains: List<String>? = null
)

data class ExaSearchResponse(
    @Json(name = "requestId") val requestId: String? = null,
    @Json(name = "results") val results: List<ExaResult>? = null
)

data class ExaResult(
    @Json(name = "title") val title: String? = null,
    @Json(name = "url") val url: String? = null,
    @Json(name = "publishedDate") val publishedDate: String? = null,
    @Json(name = "author") val author: String? = null,
    @Json(name = "text") val text: String? = null,
    @Json(name = "highlights") val highlights: List<String>? = null,
    @Json(name = "summary") val summary: String? = null,
    @Json(name = "id") val id: String? = null
)

// --- Exa Answer (RAG) Data Classes ---

data class ExaAnswerRequest(
    @Json(name = "query") val query: String,
    @Json(name = "text") val text: Boolean = true
)

data class ExaAnswerResponse(
    @Json(name = "requestId") val requestId: String? = null,
    @Json(name = "answer") val answer: String? = null,
    @Json(name = "citations") val citations: List<ExaCitation>? = null
)

data class ExaCitation(
    @Json(name = "id") val id: String? = null,
    @Json(name = "title") val title: String? = null,
    @Json(name = "url") val url: String? = null,
    @Json(name = "publishedDate") val publishedDate: String? = null
)

// --- Exa FindSimilar Data Classes ---

data class ExaFindSimilarRequest(
    @Json(name = "url") val url: String,
    @Json(name = "numResults") val numResults: Int = 5,
    @Json(name = "contents") val contents: ExaContents? = null
)

// --- Retrofit Interface ---

interface ExaSearchApi {

    @POST("search")
    suspend fun search(
        @Header("x-api-key") apiKey: String,
        @Body request: ExaSearchRequest
    ): ExaSearchResponse

    @POST("answer")
    suspend fun answer(
        @Header("x-api-key") apiKey: String,
        @Body request: ExaAnswerRequest
    ): ExaAnswerResponse

    @POST("findSimilar")
    suspend fun findSimilar(
        @Header("x-api-key") apiKey: String,
        @Body request: ExaFindSimilarRequest
    ): ExaSearchResponse
}

// --- Singleton Client ---

object ExaSearchClient {

    val API_KEY = com.example.BuildConfig.EXA_API_KEY
    const val BASE_URL = "https://api.exa.ai/"

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val api: ExaSearchApi = retrofit.create(ExaSearchApi::class.java)
}
