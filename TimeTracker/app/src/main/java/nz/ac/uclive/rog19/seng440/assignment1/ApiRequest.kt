package nz.ac.uclive.rog19.seng440.assignment1

import android.util.Log
import com.beust.klaxon.Klaxon
import kotlinx.coroutines.suspendCancellableCoroutine
import nz.ac.uclive.rog19.seng440.assignment1.model.DateTimeConverter
import nz.ac.uclive.rog19.seng440.assignment1.model.Me
import nz.ac.uclive.rog19.seng440.assignment1.model.Project
import nz.ac.uclive.rog19.seng440.assignment1.model.TimeEntry
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request.Builder
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.UnknownHostException
import java.time.Instant
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class TogglApiException(message: String) : Exception(message)

class BasicAuthInterceptor(user: String, password: String) :
    Interceptor {
    private val credentials: String

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request: Request = chain.request()
        val authenticatedRequest = request.newBuilder()
            .header("Authorization", credentials).build()
        return chain.proceed(authenticatedRequest)
    }

    init {
        credentials = Credentials.basic(user, password)
    }
}

class ApiRequest {
    val domain: String = "https://api.track.toggl.com"
    val rootPath: String = "api/v9"

    val authenticated: Boolean get() = apiKey != null

    var apiKey: String? = null
        set(value) {
            field = value
            client = if (value == null) null else {
                buildClientWithAuthenticator(value)
            }
        }

    val workspaceId: Int? get() = currentWorkspaceId ?: defaultWorkspaceId
    var currentWorkspaceId: Int? = null
    var defaultWorkspaceId: Int? = null

    var client: OkHttpClient? = null

    init {
//        apiKey = API_KEY
//        workspaceId = WORKSPACE_ID
    }


    private fun buildClientWithAuthenticator(apiKey: String): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(BasicAuthInterceptor(apiKey, "api_token"))
            .build()

    }

    val buildUrl: HttpUrl.Builder
        get() = HttpUrl.Builder()
            .scheme("https")
            .host(domain.removePrefix("https://"))
            .addPathSegments(rootPath)

    private val jsonConverter = Klaxon().converter(DateTimeConverter)

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    suspend fun getTags(): List<Project>? {
        get("${domain}/${rootPath}/workspaces/${workspaceId}/projects", client!!)?.let {
            return jsonConverter.parseArray<Project>(it)
        }
        return null
    }

    suspend fun getProjects(): List<Project>? {
        get("${domain}/${rootPath}/workspaces/${workspaceId}/projects", client!!)?.let {
            return jsonConverter.parseArray<Project>(it)
        }
        return null
    }

    suspend fun getCurrentTimeEntry(): TimeEntry? {
        get("${domain}/${rootPath}/me/time_entries/current", client!!)?.let {
            if (it != "null") {
                // if no timer currently active, returns `null` which cannot be parsed as JSON
                return jsonConverter.parse<TimeEntry>(it)
            }
        }
        return null
    }

    suspend fun updateTimeEntry(entry: TimeEntry): TimeEntry? {
        var url = buildUrl
            .addPathSegments("workspaces/${workspaceId}/time_entries/${entry.id}")
            .build()
        Log.d(TAG, "UPDATE TIME ENTRY: ${url}")
        Log.d(TAG, jsonConverter.toJsonString(entry))
        post(url, jsonConverter.toJsonString(entry).toRequestBody(jsonType), put = true)?.let {
            return jsonConverter.parse<TimeEntry>(it)
        }
        return null
    }

    suspend fun newTimeEntry(entry: TimeEntry): TimeEntry? {
        if (entry.workspaceId == null) {
            entry.workspaceId = WORKSPACE_ID
        }
        var url = buildUrl
            .addPathSegments("workspaces/${workspaceId}/time_entries")
            .build()
        Log.d(TAG, jsonConverter.toJsonString(entry))
        post(url, jsonConverter.toJsonString(entry).toRequestBody(jsonType))?.let {
            return jsonConverter.parse<TimeEntry>(it)
        }
        return null
    }

    suspend fun authenticate(email: String, password: String): Me? {
        val client = OkHttpClient.Builder()
            .addInterceptor(BasicAuthInterceptor(email, password))
            .build()

        var url = buildUrl
            .addPathSegments("me")
            .build()

        get(url, client)?.let { body ->
            return jsonConverter.parse<Me>(body)?.also {
                apiKey = it.apiToken
                defaultWorkspaceId = it.defaultWorkspaceId
            }
        }

        return null
    }


    suspend fun getTimeEntries(
        startDate: Instant? = null,
        endDate: Instant? = null
    ): List<TimeEntry>? {
        var url = buildUrl.addPathSegments("me/time_entries")
        startDate?.let {
            url = if (endDate == null) {
                url.addQueryParameter("since", (it.toEpochMilli() / 1000).toString())
            } else {
                // v9 API: YYYY-MM-DD
                url.addQueryParameter("start_date", it.toString().substring(0..9))
            }
        }
        endDate?.let {
            url = if (startDate == null) {
                url.addQueryParameter("before", (it.toEpochMilli() / 1000).toString())
            } else {
                url.addQueryParameter("end_date", it.toString().substring(0..9))
            }
        }

        get(url.build(), client!!)?.let {
            Log.d(TAG, it.toString())
            return jsonConverter.parseArray<TimeEntry>(it)
        }
        return null
    }

    private suspend fun get(url: String, client: OkHttpClient): String? {
        return url.toHttpUrlOrNull()?.let { get(it, client) }
    }

    /// Make HTTP GET request with Toggl credentials and return response body as string
    private suspend fun get(url: HttpUrl, client: OkHttpClient): String {
        val request: Request = Builder()
            .url(url)
            .build()

        Log.d(TAG, "GET request to ${request.url}")
        return handleCall(client!!.newCall(request))
    }

    private suspend fun post(url: String, body: RequestBody, put: Boolean = false): String? {
        return url.toHttpUrlOrNull()?.let { post(it, body, put) }
    }

    /// Make HTTP POST request with Toggl credentials, the body being some JSON,
    // and return response body as string
    private suspend fun post(url: HttpUrl, body: RequestBody, put: Boolean = false): String {
        var builder = Builder().url(url)
        builder = if (put) builder.put(body) else builder.post(body)
        val request: Request = builder.build()

        Log.d(TAG, "POST request to ${request.url}")
        return handleCall(client!!.newCall(request))
    }

    private suspend fun handleCall(call: Call): String {
        return suspendCancellableCoroutine { continuation ->
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (e is UnknownHostException) {
                        return continuation.resumeWithException(
                            TogglApiException(
                                "Could not connect to server. Check your internet connection and try again"
                            )
                        )
                    }
                    Log.e(TAG, "ERROR MAKING API REQUEST")
                    Log.e(TAG, e.stackTraceToString())
                    return continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    if (response.isSuccessful) {
                        if (response.body == null) {
                            response.close()
                            return continuation.resumeWithException(TogglApiException("No body received"))
                        }
                        val body = response.body!!.string()
                        response.close()
                        return continuation.resume(body)
                    }
                    Log.e(TAG, "ERROR ${response.code} FROM API SERVER")
                    Log.e(TAG, response.headers!!.toString())
                    Log.e(TAG, response.body!!.string())

                    val exception = when (response.code) {
                        429 -> {
                            TogglApiException("Toggl API rate limiting in place - please try again later")
                        }
                        403 -> {
                            var message = "Invalid credentials"
                            val attemptsRemaining = response.headers["x-remaining-login-attempts"]
                            attemptsRemaining?.let {
                                message += ". $attemptsRemaining login attempts remaining"
                            }
                            TogglApiException(message)
                        }
                        401 -> {
                            TogglApiException("Not authorized")
                        }
                        else -> {
                            TogglApiException("Error code ${response.code} received")
                        }
                    }
                    response.close()
                    return continuation.resumeWithException(exception)
                }
            })
        }
    }
}