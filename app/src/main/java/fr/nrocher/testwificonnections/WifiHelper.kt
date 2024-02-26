package fr.nrocher.testwificonnections

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.MacAddress
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class WifiHelper(private val context: Context, private val activity: Activity) {

    var websocketConnection: WebSocketClient? = null

    private val wifiManager: WifiManager by lazy {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }
    private val connectivityManager: ConnectivityManager by lazy {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    var videoBufferCallback : ((ByteArray) -> Unit)? = null
    var droneNameCallback : ((String) -> Unit)? = null
    var droneSendCommandCallback : ((String) -> Unit)? = null
    var computerSendResponseCallback : ((String) -> Unit)? = null
    var udpLinkEstablishedCallback: ((Boolean) -> Unit)? = null

    var udpLinkEstablished: Boolean = false

    var sttCallback : ((String) -> Unit)? = null
    var ttsCallback : ((String) -> Unit)? = null

    var returnDroneCamera : (() -> String?)? = null

    var shouldSendRCControl : Boolean = true

    private lateinit var droneClass : KTello

    fun createUDPBridge() {
        Thread {

            val socket = DatagramSocket()

            val computerAddress = InetAddress.getByName("88.175.125.62")

            val receiveData = "connect".toByteArray()
            val packet = DatagramPacket(receiveData, receiveData.size, computerAddress, 43211)
            socket.send(packet)

            computerSendResponseCallback = { response ->
                val receiveData = response.toByteArray()
                 socket.send(DatagramPacket(receiveData, receiveData.size, computerAddress, 43211))
            }

            while (true) {

                val buffer = ByteArray(1024)
                val packet = DatagramPacket(buffer, buffer.size)

                // Wait for a packet
                socket.receive(packet)

                // Extract necessary information from the packet:
                val data = packet.data
                val length = packet.length
                val address = packet.address
                val port = packet.port

                // Process the received data (replace with your logic):
                val message = String(data, 0, length) // Convert byte array to string
                println("Received packet from $address:$port: $message")

                droneSendCommandCallback!!(message)
            }
        }.start()
    }

    fun connectToVLM(): WebSocketClient {

        val temp_conn = WebSocketClient()
        websocketConnection = temp_conn

        Thread {

            activity.runOnUiThread {
                Toast.makeText(context, "[VLM] Connecting...", Toast.LENGTH_SHORT).show()
            }

            websocketConnection!!.start()

            websocketConnection!!.receivedMessage = { json ->

                activity.runOnUiThread {
                    Toast.makeText(context, "[VLM] RECEIVED $json", Toast.LENGTH_SHORT).show()
                }

                try {
                    val parsedData = Json.decodeFromString<Map<String, String>>(json)

                    Log.i("WEBSOCKET RECEIVED", parsedData.toString())

                    if(parsedData["type"] == "tts") {
                        ttsCallback?.let { it(parsedData["data"]!!) }
                    }

                    if(parsedData["type"] == "takeoff") {
                        ttsCallback?.let { it("C'est parti !") }
                        droneClass.takeOff()
                    }

                    if(parsedData["type"] == "stop") {
                        ttsCallback?.let { it("Très bien ! J'atterris tout de suite !") }
                        droneClass.land()
                    }

                    if(parsedData["type"] == "drone_battery") {
                        val userObject = buildJsonObject {
                            put("type", "drone_battery")
                            put("battery", droneClass.state.bat)
                            put("time", droneClass.state.time)
                        }
                        Log.i("BATTERY COMMAND", userObject.toString())
                        ttsCallback?.let { it("Le drone a ${droneClass.state.bat}% de batterie !") }
                        // websocketConnection!!.sendMessage(userObject.toString())
                    }

                    if(parsedData["type"] == "drone_camera") {
                        var droneCameraBase64 = returnDroneCamera?.let { it() }
                        if(droneCameraBase64 != null){
                            val userObject = buildJsonObject {
                                put("type", "drone_camera")
                                put("data", droneCameraBase64)
                            }
                            websocketConnection!!.sendMessage(userObject.toString())
                        }
                    }

                    if(parsedData["type"] == "drone_scene_description") {

                        shouldSendRCControl = false
                        Thread.sleep(1000)

                        // On suppose que le drone vole deja

                        var img1 = returnDroneCamera?.let { it() }
                        if(img1 != null){
                            val userObject = buildJsonObject {
                                put("type", "drone_scene_description")
                                put("img", "1")
                                put("data", img1)
                            }
                            websocketConnection!!.sendMessage(userObject.toString())
                        }

                        Thread.sleep(400)
                        droneClass.cw(90)
                        Thread.sleep(200)

                        var img2 = returnDroneCamera?.let { it() }
                        if(img2 != null){
                            val userObject = buildJsonObject {
                                put("type", "drone_scene_description")
                                put("img", "2")
                                put("data", img2)
                            }
                            websocketConnection!!.sendMessage(userObject.toString())
                        }

                        Thread.sleep(400)
                        droneClass.cw(90)
                        Thread.sleep(200)

                        var img3 = returnDroneCamera?.let { it() }
                        if(img3 != null){
                            val userObject = buildJsonObject {
                                put("type", "drone_scene_description")
                                put("img", "3")
                                put("data", img3)
                            }
                            websocketConnection!!.sendMessage(userObject.toString())
                        }

                        Thread.sleep(400)
                        droneClass.cw(90)
                        Thread.sleep(200)

                        var img4 = returnDroneCamera?.let { it() }
                        if(img4 != null){
                            val userObject = buildJsonObject {
                                put("type", "drone_scene_description")
                                put("img", "4")
                                put("data", img4)
                            }
                            websocketConnection!!.sendMessage(userObject.toString())
                        }

                        shouldSendRCControl = true

                    }

                } catch (e: Exception) {
                    Log.e("JSON PARSER", "Failed parsing json in the receiveMessage function.")
                }

            }

            sttCallback = { stt_text ->
                val userObject = buildJsonObject {
                    put("type", "stt")
                    put("data", stt_text)
                }
                activity.runOnUiThread {
                    Toast.makeText(context, "[VLM] SENT $userObject", Toast.LENGTH_SHORT).show()
                }
                websocketConnection!!.sendMessage(userObject.toString())
            }
        }.start()

        return temp_conn
    }

    // Method to list WiFi connections
    fun connectToTello(callbackFunction: (KTello) -> Unit) {
        Log.i("WIFI", "listWifiConnections")
        if (!wifiManager.isWifiEnabled) {
            Log.i("WIFI", "Wifi disabled")
            Toast.makeText(context, "[FAILED] Wifi disabled", Toast.LENGTH_LONG).show()
        } else {

            // WiFi is enabled, or API level is lower than M
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {

                Log.i("WIFI", "listWifiConnections")

                var receiverRegistered = true

                val wifiScanReceiver = object : BroadcastReceiver() {
                    @SuppressLint("MissingPermission")
                    override fun onReceive(context: Context, intent: Intent) {
                        if (intent.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
                            val scanResults = wifiManager.scanResults
                            // Process the list of scanResults
                            val listOfTelloWifi = scanResults.filter { it.wifiSsid.toString().contains("TELLO-")   }

                            if(listOfTelloWifi.isEmpty()) {

                                Toast.makeText(context, "[FAILED] Not TELLO wifi found", Toast.LENGTH_LONG).show()

                            } else {

                                // Simple flag to make sure to not do the loop multiple times
                                // Here is a memory leak because we are in a infite loop if the drone is never detected
                                if(!receiverRegistered) return

                                receiverRegistered = false
                                context.unregisterReceiver(this) // Not sure this one works here

                                val droneWifiConfig = listOfTelloWifi[0] // If multiple tello drone, then you have no choice

                                Log.i("WIFI ANDROID", "Connecting to -> $droneWifiConfig")

                                Toast.makeText(context, "[DRONE] Connecting to " + droneWifiConfig.wifiSsid.toString(), Toast.LENGTH_SHORT).show()

                                Log.i("WIFI ANDROID", "isStaApConcurrencySupported = " + wifiManager.isStaApConcurrencySupported)
                                Log.i("WIFI ANDROID", "isStaConcurrencyForMultiInternetSupported = " + wifiManager.isStaConcurrencyForMultiInternetSupported)
                                Log.i("WIFI ANDROID", "isStaConcurrencyForLocalOnlyConnectionsSupported = " + wifiManager.isStaConcurrencyForLocalOnlyConnectionsSupported)


                                GlobalScope.launch(Dispatchers.Main) {
                                    try {
                                        var droneName = droneWifiConfig.wifiSsid.toString()
                                        droneNameCallback?.let { it(droneName.substring(1, droneName.length-1)) }
                                        var tello = connectToLocalWifi(droneWifiConfig.BSSID, "12345678") // I'VE SET UP MY DRONE'S WIFI TO HAVE THIS PASSWORD

                                        droneClass = tello

                                        callbackFunction(tello)
                                    } catch (e: Exception) {
                                        // Handle connection failure
                                    }
                                }
                            }
                        }
                    }
                }

                val intentFilter = IntentFilter()
                intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)

                // Register the receiver and start scanning
                context.registerReceiver(wifiScanReceiver, intentFilter)

                val resultScan = wifiManager.startScan()
                Log.i("WIFI START SCAN", resultScan.toString())
                if(resultScan) {
                    Toast.makeText(context, "[SUCCESS] Scanning wifi", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "[FAILED] Scanning wifi", Toast.LENGTH_SHORT).show()
                }

            } else {
                Log.i("WIFI", "NOT GIVEN PERMISSION")
                Toast.makeText(context, "[FAILED] Permission issue", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Connect to a local-only WiFi network
    suspend fun connectToLocalWifi(bssid: String, password: String): KTello {
        val specifier = WifiNetworkSpecifier.Builder()
            .setBssid(MacAddress.fromString(bssid))
            .setWpa2Passphrase(password)
            .build()

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .setNetworkSpecifier(specifier)
            .build()

        val serverSocket = DatagramSocket()
        val internetNetwork = connectivityManager.activeNetwork
        internetNetwork?.bindSocket(serverSocket)
        val computerAddress = internetNetwork?.getByName("88.175.125.62")

        val deferred = CompletableDeferred<KTello>()

        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                // Local WiFi connected
                Log.i("WIFIHELPER", "CONNECTED TO DRONE")

                val tello = KTello(network, computerSendResponseCallback) // Here we specify the network the UDP socket should go to
                tello.connect()  // Send Drone commands

                Thread.sleep(500)

                droneSendCommandCallback = { command ->
                    if(command.startsWith("rc")) {
                        val numbers = command.drop(3).split(" ").map { it.toInt() }
                        tello.LEFT_RIGHT_VELOCITY = numbers[0]
                        tello.FORWARD_BACKWARD_VELOCITY = numbers[1]
                        tello.UP_DOWN_VELOCITY = numbers[2]
                        tello.YAW_VELOCITY = numbers[3]
                    } else {
                        tello.sendCommandWithoutReturn(command)
                    }
                }

                // Thread to receive the UDP packet from video from the drone
                Thread {

                    val droneSocket = DatagramSocket(11111)
                    network.bindSocket(droneSocket)

                    while(tello.isConnected) {
                        val receiveData = ByteArray(2048)
                        val receivePacket = DatagramPacket(receiveData, receiveData.size)
                        droneSocket.receive(receivePacket)

                        videoBufferCallback?.let { it(receivePacket.data.copyOfRange(0, receivePacket.length)) }

                        if(udpLinkEstablished) {
                            val packet = DatagramPacket(receivePacket.data, receivePacket.length, computerAddress, 43210)
                            serverSocket.send(packet)
                        }

                    }

                }.start()

                Thread {
                    while(tello.isConnected) {
                        if(shouldSendRCControl) {
                            tello.sendRCControl()
                        }
                        Thread.sleep(30)
                    }
                }.start()

                tello.streamon()
                tello.setSpeed(10)
                deferred.complete(tello)
            }

            override fun onUnavailable() {
                super.onUnavailable()
                // Local WiFi connection failed
                Log.e("WIFIHELPER", "Failed to connect to drone")
                deferred.completeExceptionally(Exception("Failed to connect to drone"))
            }

        }

        connectivityManager.requestNetwork(request, networkCallback, 25000)

        return deferred.await()

    }

}