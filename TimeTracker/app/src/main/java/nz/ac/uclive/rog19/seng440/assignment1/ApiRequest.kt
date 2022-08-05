package nz.ac.uclive.rog19.seng440.assignment1

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.DataOutputStream
import java.net.URL
import java.nio.charset.Charset
import javax.net.ssl.HttpsURLConnection

class ApiRequest {
    val domain: String = "https://api.track.toggl.com"
    val rootPath: String = "api/v9"

    var apiKey: String = API_KEY

    var workspaceId: Int = WORKSPACE_ID

    suspend fun currentTimeEntry(): JSONObject? {
        return getJson(URL("${domain}/${rootPath}/me/time_entries/current"))
    }

    val authHeader: String get() {
        return String(Base64.encode("${apiKey}:api_token".toByteArray(), Base64.NO_WRAP))
    }

    suspend fun getJson(url: URL): JSONObject? {
        Log.d(TAG, url.toString())
        Log.d(TAG, url.authority)
        val result = withContext(Dispatchers.IO) {
            val connection = url.openConnection() as HttpsURLConnection
            connection.setRequestProperty("Authorization", authHeader)
            connection.doOutput = true

            connection.connect()

            val status: Int = connection.responseCode

            Log.d(TAG, "RESPONE CODE ${status}")
            if (status == HttpsURLConnection.HTTP_OK) {
                val header: String = connection.getHeaderField("x-request-id")
                Log.d(TAG, header)
            }


            try {
                val json = BufferedInputStream(connection.inputStream).readBytes()
                    .toString(Charset.defaultCharset())
                JSONObject(json)
            } catch(error: Error) {
                error.printStackTrace()
                null
            } finally {
                connection.disconnect()
            }
        }
        return result
    }

    suspend fun postJson(url: URL, body: String): JSONObject? {
        Log.d(TAG, url.toString())
        Log.d(TAG, url.authority)
        val result = withContext(Dispatchers.IO) {
            val connection = url.openConnection() as HttpsURLConnection

            connection.setRequestProperty("Content-Type", "application/json");
            connection.doOutput = true
            connection.doInput = true

            val localDataOutputStream = DataOutputStream(connection.outputStream)
            localDataOutputStream.writeBytes(body.toString())
            localDataOutputStream.flush()
            localDataOutputStream.close()

            val status: Int = connection.responseCode

            Log.d(TAG, "RESPONE CODE ${status}")
            if (status == HttpsURLConnection.HTTP_OK) {
                val header: String = connection.getHeaderField("x-request-id")
                Log.d(TAG, header)
            }
            try {
                val json = BufferedInputStream(connection.inputStream).readBytes()
                    .toString(Charset.defaultCharset())
                JSONObject(json)
            } catch(error: Error) {
                error.printStackTrace()
                null
            } finally {
                connection.disconnect()
            }
        }
        return result
    }
}