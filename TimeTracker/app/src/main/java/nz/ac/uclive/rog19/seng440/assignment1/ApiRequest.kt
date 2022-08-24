package nz.ac.uclive.rog19.seng440.assignment1

import android.content.Context
import android.util.Log
import com.beust.klaxon.Klaxon
import kotlinx.coroutines.suspendCancellableCoroutine
import nz.ac.uclive.rog19.seng440.assignment1.model.*
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

    /// Used for localized error messages
    var context: Context? = null

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

    private val jsonConverter: Klaxon
      get() = Klaxon().converter(DateTimeConverter)

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    suspend fun getTags(): List<TogglTag>? {
        Log.d(TAG, "Get tags for user")
        get("${domain}/${rootPath}/me/tags", client!!)?.let {
            val tags = jsonConverter.parseArray<TogglTag>(it)
            Log.d(TAG, "Get ${tags?.count()} tags projects for user")
            return tags
        }
        return null
    }

    suspend fun getStringTags(): List<String>? {
        return getTags()?.filter { it.workspaceId == workspaceId!! }?.map { it.name }
    }

    suspend fun getProjects(): List<Project>? {
        Log.d(TAG, "Get projects for workspace $workspaceId")
        get("${domain}/${rootPath}/workspaces/${workspaceId}/projects", client!!)?.let {
            val projects = jsonConverter.parseArray<Project>(it)
            Log.d(TAG, "Get ${projects?.count()} projects from workspace $workspaceId")
            return projects
        }
        return null
    }

    suspend fun getCurrentTimeEntry(): TimeEntry? {
        Log.d(TAG, "Get current time entry")
        get("${domain}/${rootPath}/me/time_entries/current", client!!)?.let {
            if (it != "null") {
                // if no timer currently active, returns `null` which cannot be parsed as JSON
                val entry = jsonConverter.parse<TimeEntry>(it)
                Log.d(TAG, "Get current time entry(id = ${entry?.id})")
            }
        }
        return null
    }

    // api/v9/me/time_entries/current is set, but the end time is the previously set value, regardless
    // of if end time is not sent or is sent as null
    suspend fun updateTimeEntryByDeletingAndCreatingBecauseTogglV9ApiSucks(entry: TimeEntry): TimeEntry? {
        val response = newTimeEntry(entry = entry.copy(id = null))
        if (entry.id != null) {
            deleteEntry(entry)
        }
        return response
    }

    suspend fun updateTimeEntry(entry: TimeEntry): TimeEntry? {
        var url = buildUrl
            .addPathSegments("workspaces/${workspaceId}/time_entries/${entry.id}")
            .build()
        Log.d(TAG, "Update time entry(id=${entry.id})")
        Log.d(TAG, jsonConverter.toJsonString(entry))
        post(url, jsonConverter.toJsonString(entry).toRequestBody(jsonType), put = true, client!!)?.let {
            Log.d(TAG, it)
            val entry = jsonConverter.parse<TimeEntry>(it)
            Log.d(TAG, "Time entry(id=${entry?.id}) updated")
            return entry
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
        Log.d(TAG, "Create time entry")
        post(url, jsonConverter.toJsonString(entry).toRequestBody(jsonType), put = false, client!!)?.let {
            val entry = jsonConverter.parse<TimeEntry>(it)
            Log.d(TAG, "Time entry(id = ${entry?.id}) created")
            return entry
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

        Log.d(TAG, "Authenticate user with email=$email")
        get(url, client)?.let { body ->
            return jsonConverter.parse<Me>(body)?.also {
                apiKey = it.apiToken
                defaultWorkspaceId = it.defaultWorkspaceId
                Log.d(TAG, "User authenticated, set workspace to ${it.defaultWorkspaceId}")
            }
        }

        return null
    }

    suspend fun deleteEntry(id: Long, workspaceId: Int?) {
        var url = buildUrl.addPathSegments(
            "workspaces/${workspaceId ?: this.workspaceId}/time_entries/${id}"
        ).build()
        Log.d(TAG, "Delete time entry(id = $id) from workspace ${workspaceId ?: this.workspaceId}")
        delete(url, client!!)
    }

    suspend fun deleteEntry(entry: TimeEntry): Boolean {
        if (entry.id == null) return false

        deleteEntry(entry.id!!, entry.workspaceId)
        return true
    }

    suspend fun getTimeEntries(
        startDate: Instant? = null,
        endDate: Instant? = null
    ): List<TimeEntry>? {
        var urlBuilder = buildUrl.addPathSegments("me/time_entries")
        startDate?.let {
            urlBuilder = if (endDate == null) {
                urlBuilder.addQueryParameter("since", (it.toEpochMilli() / 1000).toString())
            } else {
                // v9 API: YYYY-MM-DD
                urlBuilder.addQueryParameter("start_date", it.toString().substring(0..9))
            }
        }
        endDate?.let {
            urlBuilder = if (startDate == null) {
                urlBuilder.addQueryParameter("before", (it.toEpochMilli() / 1000).toString())
            } else {
                urlBuilder.addQueryParameter("end_date", it.toString().substring(0..9))
            }
        }

        val url = urlBuilder.build()
        Log.d(TAG, "Get time entries, url = $url")
        get(url, client!!).let {
            Log.d(TAG, "Receive time entries response, length = ${it.length}")
            val entries = jsonConverter.parseArray<TimeEntry>(it)
            Log.d(TAG, "Retrieve ${entries?.count()} entries, date range ${entries?.last()?.startTime} - ${entries?.first()?.startTime}")
            return entries
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
        return handleCall(client.newCall(request))
    }

    private suspend fun post(url: String, body: RequestBody, put: Boolean = false, client: OkHttpClient): String? {
        return url.toHttpUrlOrNull()?.let { post(it, body, put, client) }
    }

    /// Make HTTP POST request with Toggl credentials, the body being some JSON,
    // and return response body as string
    private suspend fun post(url: HttpUrl, body: RequestBody, put: Boolean = false, client: OkHttpClient): String {
        var builder = Builder().url(url)
        builder = if (put) builder.put(body) else builder.post(body)
        val request: Request = builder.build()

        Log.d(TAG, "${if (put) "PUT" else "POST"} request to ${request.url}")
        return handleCall(client.newCall(request))
    }

    private suspend fun delete(url: HttpUrl, client: OkHttpClient): String {
        val request = Builder().url(url).delete().build()
        Log.d(TAG, "DELETE request to ${request.url}")
        return handleCall(client.newCall(request))
    }

    private suspend fun handleCall(call: Call): String {
        return suspendCancellableCoroutine { continuation ->
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (e is UnknownHostException) {
                        return continuation.resumeWithException(
                            TogglApiException(context?.getString(R.string.api_error_server_connection_failed) ?:
                                "Could not connect to server"
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
                            return continuation.resumeWithException( TogglApiException(
                                context?.getString(R.string.api_error_no_body) ?: "No body received"
                            ))
                        }
                        val body = response.body!!.string()
                        response.close()
                        return continuation.resume(body)
                    }
                    Log.e(TAG, "ERROR ${response.code} FROM API SERVER")
                    val body = response.body?.string()
                    Log.e(TAG, response.headers!!.toString())
                    Log.e(TAG, body ?: "no body received")

                    val exception = when (response.code) {
                        429 -> {
                            TogglApiException(
                                context?.getString(R.string.api_error_rate_limit) ?: "Toggl rate limit"
                            )
                        }
                        403 -> {
                            val attemptsRemaining = response.headers["x-remaining-login-attempts"]?.toIntOrNull()
                            val message = if (attemptsRemaining == null) {
                                context?.getString(R.string.api_error_invalid_credentials) ?: "Invalid credentials"
                            } else {
                                context?.resources?.getQuantityString(
                                    R.plurals.api_error_invalid_credentials_x_attempts_left,
                                    attemptsRemaining,
                                    attemptsRemaining
                                    ) ?:
                                "Invalid credentials. $attemptsRemaining attempts left"
                            }
                            TogglApiException(message)
                        }
                        401 -> {
                            TogglApiException(
                                context?.getString(R.string.api_error_not_authorized) ?: "Not authorized"
                            )
                        }
                        else -> {
                            TogglApiException(
                                context?.getString(R.string.api_error_http_code, response.code, body ?: "") ?: "Error ${response.code}")
                        }
                    }
                    response.close()
                    return continuation.resumeWithException(exception)
                }
            })
        }
    }
}