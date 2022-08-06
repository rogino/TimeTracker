package nz.ac.uclive.rog19.seng440.assignment1

import android.util.Log
import com.beust.klaxon.Klaxon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nz.ac.uclive.rog19.seng440.assignment1.model.DateTimeConverter
import nz.ac.uclive.rog19.seng440.assignment1.model.TimeEntry
import okhttp3.*
import okhttp3.Request.Builder
import java.io.IOException


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

    val jsonConverter = Klaxon().converter(DateTimeConverter)

    suspend fun currentTimeEntry(): TimeEntry? {
        get("${domain}/${rootPath}/me/time_entries/current")?.let {
            if (it != "null") {
                // if no timer currently active, returns `null` which cannot be parsed as JSON
                return jsonConverter.parse<TimeEntry>(it)
            }
        }
        return null
    }

    private suspend fun get(url: String): String? {
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
                        Log.d(TAG, response.headers!!.toString())
                        Log.d(TAG, response.body!!.string())
                        null
                    }
                }
            } catch(error: Error) {
                error.printStackTrace()
                null
            } finally {
                response?.close()
            }
        }
        return result
    }
}