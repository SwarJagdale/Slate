package app.webcodex.codex.data

import app.webcodex.codex.network.ApiService
import app.webcodex.codex.network.WorkspacesResponse
import app.webcodex.codex.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.logging.HttpLoggingInterceptor.Level
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class CodexRepository(
    private val api: ApiService,
    private val okHttp: OkHttpClient
) {
    suspend fun getWorkspaces(baseUrl: String, token: String): Result<WorkspacesResponse> {
        return try {
            val url = baseUrl.trimEnd('/') + "/workspaces"
            val auth = "Bearer $token"
            val fullUrl = if (url.startsWith("http")) url else "http://$url"
            val response = api.getWorkspaces(fullUrl, auth)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

fun createOkHttp(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .writeTimeout(30, TimeUnit.SECONDS)
    .apply {
        if (BuildConfig.DEBUG) {
            addInterceptor(HttpLoggingInterceptor().apply { level = Level.BASIC })
        }
    }
    .build()

fun createWebSocketOkHttp(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(0, TimeUnit.SECONDS)
    .writeTimeout(30, TimeUnit.SECONDS)
    .pingInterval(30, TimeUnit.SECONDS)
    .apply {
        if (BuildConfig.DEBUG) {
            addInterceptor(HttpLoggingInterceptor().apply { level = Level.BASIC })
        }
    }
    .build()

fun createRetrofit(okHttp: OkHttpClient): Retrofit = Retrofit.Builder()
    .baseUrl("http://localhost/")
    .client(okHttp)
    .addConverterFactory(GsonConverterFactory.create())
    .build()
