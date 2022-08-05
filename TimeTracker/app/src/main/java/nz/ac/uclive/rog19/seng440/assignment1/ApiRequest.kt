package nz.ac.uclive.rog19.seng440.assignment1

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.Request.Builder
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.URL
import java.nio.charset.Charset
import javax.net.ssl.HttpsURLConnection


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

    val client: OkHttpClient
    init {
        client = OkHttpClient.Builder()
            .addInterceptor(BasicAuthInterceptor(apiKey, "api_token"))
        .build();
    }

    suspend fun currentTimeEntry(): JSONObject? {
        return getJson("${domain}/${rootPath}/me/time_entries/current")
    }

    suspend fun getJson(url: String): JSONObject? {
        val result = withContext(Dispatchers.IO) {
            val request: Request = Builder()
                .url(url)
                .build()

            Log.d(TAG, request.headers.toString())
            var response: Response? = null
            try {
                response = client.newCall(request).execute()
                when (response.code) {
                    200 -> {
                        Log.d(TAG, response.headers.toString())
                        val json = response.body!!.string()
                        JSONObject(json)
                    }
                    429 -> {
                        Log.d(TAG, "RATE LIMITING")
                        null
                    }
                    else -> {
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