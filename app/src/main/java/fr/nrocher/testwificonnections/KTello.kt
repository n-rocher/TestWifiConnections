package fr.nrocher.testwificonnections

import android.net.Network
import android.util.Log
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.charset.StandardCharsets

class KTello(private var network: Network, private var computerSendResponseCallback: ((String) -> Unit)?) {

    var LEFT_RIGHT_VELOCITY: Int = 0
    var FORWARD_BACKWARD_VELOCITY: Int = 0
    var UP_DOWN_VELOCITY: Int = 0
    var YAW_VELOCITY: Int = 0

    internal var ip: InetAddress? = null
        private set
    internal var port: Int = 0
        private set
    internal var socket: DatagramSocket? = null
        private set
    var isImperial: Boolean = false

    var curentCamera: Int = 0

    var state: DroneState = DroneState()

    val isConnected: Boolean
        get() = if (null == socket) false else socket!!.isConnected

    /**
     *  Instrumentation Commands
     */


    val battery: String
        @Throws(IOException::class)
        get() = sendCommand("battery?")

    val speed: String
        @Throws(IOException::class)
        get() = sendCommand("speed?")

    val time: String
        @Throws(IOException::class)
        get() = sendCommand("time?")

    /**
     *  Connection Commands
     */

    @Throws(Exception::class)
    fun connect() {
        this.connect("192.168.10.1", 8889)
    }

    @Throws(Exception::class)
    fun connect(strIP: String, port: Int) {
        this.port = port

        ip = network.getByName(strIP) // <--- SUPER IMPORTANT

        socket = DatagramSocket(port)

        network.bindSocket(socket) // <--- SUPER IMPORTANT

        socket!!.connect(ip!!, port)
        sendCommand("command")
        sendCommand("downvision $curentCamera")

        Log.i("KTELLO", "localPort: " + socket!!.localPort)
        Log.i("KTELLO", "localSocketAddress: " + socket!!.localSocketAddress)
        Log.i("KTELLO", "localAddress: " + socket!!.localAddress)
        Log.i("KTELLO", "port: " + socket!!.port)
        Log.i("KTELLO", "remoteSocketAddress: " + socket!!.remoteSocketAddress)
        Log.i("KTELLO", "reuseAddress: " + socket!!.reuseAddress)
        Log.i("KTELLO", "receiveBufferSize: " + socket!!.receiveBufferSize)
        Log.i("KTELLO", "sendBufferSize: " + socket!!.sendBufferSize)
        Log.i("KTELLO", "trafficClass: " + socket!!.trafficClass)


        var stateSocket = DatagramSocket(8890)
        network.bindSocket(stateSocket) // <--- SUPER IMPORTANT
//        stateSocket!!.connect(ip!!, port)

        Thread {

            while (isConnected) {
                val buffer = ByteArray(1024)
                val packet = DatagramPacket(buffer, buffer.size)
                stateSocket.receive(packet)
                val data = packet.data
                val length = packet.length
                val stateSTR = String(data, 0, length)
                state = parseDroneState(stateSTR)
            }

        }.start()

    }

    /**
     *  Control Commands
     */

    @Throws(IOException::class)
    fun takeOff(): Boolean {
        return isOK(sendCommand("takeoff"))
    }

    @Throws(IOException::class)
    fun land(): Boolean {
        return isOK(sendCommand("land"))
    }

    /**
     * Fly up xx | xx = (20-500 cm)
     */
    @Throws(IOException::class)
    fun up(z: Int): Boolean {
        return isOK(sendCommand("up " + getDistance(z)))
    }

    /**
     * Fly down xx | xx = (20-500 cm)
     */
    @Throws(IOException::class)
    fun down(z: Int): Boolean {
        return isOK(sendCommand("down " + getDistance(z)))
    }

    /**
     * Fly left xx | xx = (20-500 cm)
     */
    @Throws(IOException::class)
    fun left(x: Int): Boolean {
        return isOK(sendCommand("left " + getDistance(x)))
    }

    /**
     * Fly right xx | xx = (20-500 cm)
     */
    @Throws(IOException::class)
    fun right(x: Int): Boolean {
        return isOK(sendCommand("right " + getDistance(x)))
    }

    /**
     * Fly forward xx | xx = (20-500 cm)
     */
    @Throws(IOException::class)
    fun forward(y: Int): Boolean {
        return isOK(sendCommand("forward " + getDistance(y)))
    }

    /**
     * Fly backward xx | xx = (20-500 cm)
     */
    @Throws(IOException::class)
    fun back(y: Int): Boolean {
        return isOK(sendCommand("back " + getDistance(y)))
    }

    /**
     * Rotate clockwise x° | x = (1-3600°)
     */
    @Throws(IOException::class)
    fun cw(x: Int): Boolean {
        return isOK(sendCommand("cw $x"))
    }

    /**
     * Rotate counter-clockwise xx° | xx = (1-3600°)
     */
    @Throws(IOException::class)
    fun ccw(x: Int): Boolean {
        return isOK(sendCommand("ccw $x"))
    }

    /**
     * Flip x l = (left) r = (right) f = (forward) b = (back) bl = (back/left) rb = (back/right) fl = (front/left) fr = (front/right)
     */
    @Throws(IOException::class)
    fun flip(direction: String): Boolean {
        return isOK(sendCommand("flip $direction"))
    }

    /**
     * Set current speed as xx | xx = (1-100 cm/s)
     */
    @Throws(IOException::class)
    fun setSpeed(speed: Int): Boolean {
        return isOK(sendCommand("speed $speed"))
    }

    /**
     * Set streamon
     */
    @Throws(IOException::class)
    fun streamon(): Boolean {
        return isOK(sendCommand("streamon"))
    }

    /**
     * Internal functions
     */

    private fun getDistance(distance: Int): Int {
        return if (!isImperial) distance else Math.round((distance.toFloat() * 2.54).toFloat())
    }

    private fun isOK(strResult: String?): Boolean {
        return null != strResult && strResult == "OK"
    }

    @Throws(IOException::class)
    private fun sendCommand(strCommand: String?): String {
        if (null == strCommand || 0 == strCommand.length)
            return "empty command"
        if (!socket!!.isConnected)
            return "disconnected"
        val sendData = strCommand.toByteArray()
        val sendPacket = DatagramPacket(sendData, sendData.size, ip, port)
        socket!!.send(sendPacket)

        val receiveData = ByteArray(1024)
        val receivePacket = DatagramPacket(receiveData, receiveData.size)
        socket!!.receive(receivePacket)
        val ret = String(receivePacket.data, 0, receivePacket.length, StandardCharsets.UTF_8)
        Log.i("KTELLO", "Tello $strCommand: $ret")

        computerSendResponseCallback?.let { it("$strCommand-$ret") }

        return ret
    }

    @Throws(IOException::class)
    fun sendCommandWithoutReturn(strCommand: String?) {
        if (null == strCommand || 0 == strCommand.length)
            return
        val sendData = strCommand.toByteArray()
        val sendPacket = DatagramPacket(sendData, sendData.size, ip, port)
        socket!!.send(sendPacket)
    }

    @Throws(IOException::class)
    fun sendControl(left_right_velocity: Int, forward_backward_velocity: Int, up_down_velocity: Int, yaw_velocity: Int) {
        sendCommandWithoutReturn("rc $left_right_velocity $forward_backward_velocity $up_down_velocity $yaw_velocity")
    }

    fun sendRCControl() {
        sendControl(LEFT_RIGHT_VELOCITY, FORWARD_BACKWARD_VELOCITY, UP_DOWN_VELOCITY, YAW_VELOCITY)
    }

    fun close() {
        if (null != socket)
            socket!!.close()
    }

    fun switchCamera() {
        if(curentCamera == 0) curentCamera = 1 else curentCamera = 0
        sendCommand("downvision $curentCamera")
    }

}

data class DroneState(
    val mid: Int = -1,
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f,
    val mpry: List<Float> = listOf(0f, 0f, 0f),
    val pitch: Float = 0f,
    val roll: Float = 0f,
    val yaw: Float = 0f,
    val vgx: Float = 0f,
    val vgy: Float = 0f,
    val vgz: Float = 0f,
    val templ: Int = 0,
    val temph: Int = 0,
    val tof: Int = 0,
    val h: Int = 0,
    val bat: Int = 0,
    val baro: Float = 0f,
    val time: Long = 0L,
    val agx: Float = -0f,
    val agy: Float = -0f,
    val agz: Float = -0f
) {
    override fun toString(): String {
        return "DroneState(mid=$mid, x=$x, y=$y, z=$z, mpry=$mpry, pitch=$pitch, roll=$roll, yaw=$yaw, vgx=$vgx, vgy=$vgy, vgz=$vgz, templ=$templ, temph=$temph, tof=$tof, h=$h, bat=$bat, baro=$baro, time=$time, agx=$agx, agy=$agy, agz=$agz)"
    }
}

fun parseDroneState(dataString: String): DroneState {
    var values = dataString.split(";").map { it.trim().split(":") }.dropLast(1)
    val map = values.associate { it[0] to it[1] }

    return try {
        DroneState(
            map["mid"]?.toInt() ?: -1,
            map["x"]?.toFloat() ?: 0f,
            map["y"]?.toFloat() ?: 0f,
            map["z"]?.toFloat() ?: 0f,
            map["mpry"]?.split(",")?.map { it.toFloat() } ?: listOf(0f, 0f, 0f),
            map["pitch"]?.toFloat() ?: 0f,
            map["roll"]?.toFloat() ?: 0f,
            map["yaw"]?.toFloat() ?: 0f,
            map["vgx"]?.toFloat() ?: 0f,
            map["vgy"]?.toFloat() ?: 0f,
            map["vgz"]?.toFloat() ?: 0f,
            map["templ"]?.toInt() ?: 0,
            map["temph"]?.toInt() ?: 0,
            map["tof"]?.toInt() ?: 0,
            map["h"]?.toInt() ?: 0,
            map["bat"]?.toInt() ?: 0,
            map["baro"]?.toFloat() ?: 0f,
            map["time"]?.toLong() ?: 0L,
            map["agx"]?.toFloat() ?: -0f,
            map["agy"]?.toFloat() ?: -0f,
            map["agz"]?.toFloat() ?: -0f,
        )
    } catch (e: NumberFormatException) {
        e.printStackTrace()
        DroneState()
    }
}