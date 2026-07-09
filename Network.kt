package com.CannedF0xy.hideout

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.DELETE
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL
import java.util.concurrent.TimeUnit

data class E621Response(val posts: List<Post>)
data class Post(
    val id: Int,
    val file: FileData,
    val preview: PreviewData,
    val tags: TagsData,
    val score: ScoreData,
    val description: String?,
    val sources: List<String> = emptyList(),
    val is_favorited: Boolean = false
)
data class FileData(val url: String?, val ext: String)
data class PreviewData(val url: String?)
data class TagsData(val general: List<String> = emptyList(), val artist: List<String> = emptyList(), val character: List<String> = emptyList(), val copyright: List<String> = emptyList())
data class ScoreData(val total: Int = 0)

data class AutocompleteTag(val id: Int, val name: String, val post_count: Int, val category: Int)

interface E621ApiService {
    @GET("posts.json")
    suspend fun getPosts(@Query("tags") tags: String, @Query("limit") limit: Int = 20, @Query("page") page: Int = 1): E621Response

    @GET("tags/autocomplete.json")
    suspend fun getAutocomplete(@Query("search[name_matches]") query: String): List<AutocompleteTag>

    @POST("favorites.json")
    @FormUrlEncoded
    suspend fun addFavorite(@Field("post_id") postId: Int): Response<Unit>

    @DELETE("favorites/{id}.json")
    suspend fun removeFavorite(@Path("id") postId: Int): Response<Unit>
}

object NetworkModule {
    private const val BASE_URL = "https://e621.net/"
    var api: E621ApiService? = null

    const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Linux; Android 13; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.6367.82 Mobile Safari/537.36"

    var username: String = ""
    var apiKey: String = ""
    var cfClearance: String = ""

    var onCloudflareChallenge: (() -> Unit)? = null

    private val headerInterceptor = Interceptor { chain ->
        val original = chain.request()
        val requestBuilder = original.newBuilder()
            .header("User-Agent", DEFAULT_USER_AGENT)

        if (cfClearance.isNotBlank() && cfClearance != "bypass") {
            requestBuilder.header("Cookie", cfClearance)
        }

        if (username.isNotBlank() && apiKey.isNotBlank()) {
            val credential = Credentials.basic(username, apiKey)
            requestBuilder.header("Authorization", credential)
        }

        val response = chain.proceed(requestBuilder.build())

        if (response.code == 403 || response.code == 503) {
            onCloudflareChallenge?.invoke()
        }

        response
    }

    val client: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(headerInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    init {
        api = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(E621ApiService::class.java)
    }
}
