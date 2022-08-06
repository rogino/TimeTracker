package nz.ac.uclive.rog19.seng440.assignment1

import android.util.Log
import com.beust.klaxon.Klaxon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nz.ac.uclive.rog19.seng440.assignment1.model.DateTimeConverter
import nz.ac.uclive.rog19.seng440.assignment1.model.TimeEntry
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request.Builder
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.DateFormat
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.*


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

    var apiKey: String = API_KEY

    var workspaceId: Int = WORKSPACE_ID

    val client: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(BasicAuthInterceptor(apiKey, "api_token"))
        .build()

    val buildUrl: HttpUrl.Builder
        get() = HttpUrl.Builder()
            .scheme("https")
            .host(domain.removePrefix("https://"))
            .addPathSegments(rootPath)

    val jsonConverter = Klaxon().converter(DateTimeConverter)

    suspend fun getCurrentTimeEntry(): TimeEntry? {
        get("${domain}/${rootPath}/me/time_entries/current")?.let {
            if (it != "null") {
                // if no timer currently active, returns `null` which cannot be parsed as JSON
                return jsonConverter.parse<TimeEntry>(it)
            }
        }
        return null
    }

    suspend fun getTimeEntries(startDate: Instant? = null,
    endDate: Instant? = null,
    zoneId: ZoneId = Clock.systemDefaultZone().zone): List<TimeEntry>? {
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

        get(url.build())?.let {
            Log.d(TAG, it.toString())
            return jsonConverter.parseArray<TimeEntry>(it)
        }
        return null
    }

    private suspend fun get(url: String): String? {
        return url.toHttpUrlOrNull()?.let { get(it) }
    }

    /// Make HTTP GET request with Toggl credentials and return response body as string
    private suspend fun get(url: HttpUrl): String? {
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