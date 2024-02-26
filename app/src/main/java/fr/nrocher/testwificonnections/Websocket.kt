package fr.nrocher.testwificonnections

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import okhttp3.*
import okhttp3.internal.wait
import java.util.concurrent.TimeUnit


class WebSocketClient() : Thread()  {

    var webSocket: WebSocket? = null
    var isConnected by mutableStateOf(false)
    var receivedMessage : ((String) -> Unit)? = null

    override fun run() {
        startWebSocket()
    }

    fun startWebSocket() {

        val client: OkHttpClient = OkHttpClient.Builder().readTimeout(0,  TimeUnit.MILLISECONDS).build()
        val request: Request = Request.Builder().url("ws://88.175.125.62:43212").build()

        webSocket = null

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                super.onOpen(webSocket, response)
                println("WebSocket connection opened: ${response}")
                webSocket.send("Hello...");
                isConnected = true
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                println("Received message: $text")
                receivedMessage?.let { it(text) }
                isConnected = true
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                println("WebSocket connection closing: $code - $reason")
                isConnected = false
                Thread.sleep(1000)
                startWebSocket()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                println("WebSocket connection failed: ${t.message}")
                t.printStackTrace();
                isConnected = false
                Thread.sleep(1000)
                startWebSocket()
            }
        })
    }

   fun sendMessage(message: String) {
        Log.d("WEBSOCKET", "isConnected : $isConnected")
        if(isConnected) {
            webSocket!!.send(message)
        }
    }

    fun close() {
        webSocket!!.close(NORMAL_CLOSURE_STATUS, "Goodbye!")
    }

    companion object {
        const val NORMAL_CLOSURE_STATUS = 1000
    }
}