package nz.ac.uclive.rog19.seng440.assignment1

import android.util.Log
import com.beust.klaxon.Klaxon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nz.ac.uclive.rog19.seng440.assignment1.model.DateTimeConverter
import nz.ac.uclive.rog19.seng440.assignment1.model.Me
import nz.ac.uclive.rog19.seng440.assignment1.model.Project
import nz.ac.uclive.rog19.seng440.assignment1.model.TimeEntry
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request.Builder
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.time.Instant


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

    var apiKey: String? = null
    set(value) {
        if (value != null) {
            client = buildClientWithAuthenticator(value)
        } else {
            client = null
        }
    }

    var workspaceId: Int? = null

    var client: OkHttpClient? = null
    init {
//        apiKey = API_KEY
//        workspaceId = WORKSPACE_ID
    }


    fun buildClientWithAuthenticator(apiKey: String): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(BasicAuthInterceptor(apiKey, "api_token"))
            .build()

    }

    val buildUrl: HttpUrl.Builder
        get() = HttpUrl.Builder()
            .scheme("https")
            .host(domain.removePrefix("https://"))
            .addPathSegments(rootPath)

    val jsonConverter = Klaxon().converter(DateTimeConverter)

    val jsonType = "application/json; charset=utf-8".toMediaType()

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
    private suspend fun get(url: HttpUrl, client: OkHttpClient): String? {
        val result = withContext(Dispatchers.IO) {
            val request: Request = Builder()
                .url(url)
                .build()

            var response: Response? = null
            try {
                response = client.newCall(request).execute()
                when (response.code) {
                    200 -> {
                        response.body!!.string()
                    }
                    429 -> {
                        Log.d(TAG, "RATE LIMITING")
                        null
                    }
                    else -> {
                        Log.e(TAG, "ERROR ${response.code} MAKING REQUEST TO ${url.toString()}")
                        Log.e(TAG, response.headers.toString())
                        Log.e(TAG, response.body!!.string())
                        null
                    }
                }
            } catch (error: Error) {
                error.printStackTrace()
                null
            } finally {
                response?.close()
            }
        }
        return result
    }

    private suspend fun post(url: String, body: RequestBody, put: Boolean = false): String? {
        return url.toHttpUrlOrNull()?.let { post(it, body, put) }
    }

    /// Make HTTP POST request with Toggl credentials, the body being some JSON,
    // and return response body as string
    private suspend fun post(url: HttpUrl, body: RequestBody, put: Boolean = false): String? {
        val result = withContext(Dispatchers.IO) {
            var builder = Builder().url(url)
            builder = if (put) builder.put(body) else builder.post(body)
            val request: Request = builder.build()

            Log.d(TAG, request.method)
            var response: Response? = null
            try {
                response = client!!.newCall(request).execute()
                when (response.code) {
                    200 -> {
                        response.body!!.string()
                    }
                    429 -> {
                        Log.d(TAG, "RATE LIMITING")
                        null
                    }
                    else -> {
                        Log.e(TAG, "ERROR ${response.code} MAKING REQUEST TO ${url.toString()}")
                        Log.e(TAG, response.headers!!.toString())
                        Log.e(TAG, response.body!!.string())
                        null
                    }
                }
            } catch (error: Error) {
                error.printStackTrace()
                null
            } finally {
                response?.close()
            }
        }
        return result
    }
}