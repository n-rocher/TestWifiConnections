package fr.nrocher.testwificonnections


import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Base64
import android.util.Log
import android.view.PixelCopy
import android.view.TextureView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import fr.nrocher.testwificonnections.ui.theme.TestWifiConnectionsTheme
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.Locale
import java.util.concurrent.CountDownLatch


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val wifiHelper = WifiHelper(applicationContext, this)

        val activity = this
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())

        val locationPermissionRequest = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            when {
                permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                    Log.i("location", "ACCESS_FINE_LOCATION granted")
                }
                permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                    Log.i("location", "ACCESS_COARSE_LOCATION granted")
                } else -> {
                    Log.i("location", "not granted")
                }
            }
        }

        locationPermissionRequest.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))

        var mCodec : MediaCodec

        var drone: KTello? = null

        var tts: TextToSpeech? = null

        tts = TextToSpeech(this, TextToSpeech.OnInitListener { status ->
            Log.d("TTS", "status:$status")
            if (status == TextToSpeech.SUCCESS) {
                // Set language for TTS
                val result = tts?.setLanguage(Locale.FRANCE)
                tts?.setSpeechRate(1.5f)
                if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    Log.e("TTS","Language data is missing or the language is not supported." )
                } else {

                    wifiHelper.ttsCallback = { text ->

                        Log.i("TTS", "Should read : ${text}")
                        tts?.speak(text.toString(), TextToSpeech.QUEUE_ADD, null, "tts")

                    }
                }
            } else {
                Log.e("TTS","TTS initialization failed." )
            }
        })

        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)

        var speechRecognizer: SpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

        val speechRecognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        speechRecognizerIntent.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )

        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d("STT", "onReadyForSpeech")
                speechRecognizer.startListening(speechRecognizerIntent)
            }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onBeginningOfSpeech() {}

            override fun onEndOfSpeech() {
                // Start speech recognition again after it ends
                speechRecognizer.startListening(speechRecognizerIntent)
            }

            override fun onError(error: Int) {
                val errorMessage: String = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_CLIENT -> "Other client side errors"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                    SpeechRecognizer.ERROR_NETWORK -> "Network related errors"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network operation timeout"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No recognition result matched"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "RecognitionService busy"
                    SpeechRecognizer.ERROR_SERVER -> "Server sends error status"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input"
                    else -> "Unknown error"
                }

                if(error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    tts?.speak("Désolé, je n'ai pas compris.", TextToSpeech.QUEUE_ADD, null, "tts")
                }

                Log.e("STT", "Error: $errorMessage")
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                matches?.let {
                    val text = it[0]
                    Toast.makeText(applicationContext, "STT:$text", Toast.LENGTH_SHORT).show()
                    Log.i("STT", "STT:$text")

                    wifiHelper.sttCallback?.let { it1 -> it1(text) }

                }
            }
        })

        setContent {

            var isConnectedToDrone by remember { mutableStateOf(false) }
            var droneIsFlying by remember { mutableStateOf(false) }
            var startedUdpLink by remember { mutableStateOf(false) }
            var udpLinkEstablished by remember { mutableStateOf(false) }

            var showBigTTSbutton by remember { mutableStateOf(false) }

            var droneName by remember { mutableStateOf("") }


            var wifiHelper_websocketConnection by remember { mutableStateOf<WebSocketClient?>(null) }
            val webSocketConnection by remember(wifiHelper_websocketConnection) { mutableStateOf(wifiHelper.websocketConnection?.isConnected ?: null) }

            wifiHelper.droneNameCallback = { name ->
                droneName = name
            }

            wifiHelper.udpLinkEstablishedCallback = { bool ->
                udpLinkEstablished = bool
            }

            TestWifiConnectionsTheme {

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {

                    Box(modifier = Modifier.fillMaxSize()) {

                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center
                        ) {

                            Surface(color = Color(25, 25, 25)) {

                                AndroidView(
                                    modifier = Modifier.aspectRatio(960f / 720f),
                                    factory = { context ->
                                        TextureView(context).apply {

                                            val textureView = this

                                            surfaceTextureListener =
                                                object : TextureView.SurfaceTextureListener {
                                                    override fun onSurfaceTextureAvailable(
                                                        surface: SurfaceTexture,
                                                        width: Int,
                                                        height: Int
                                                    ) {

                                                        val format = MediaFormat.createVideoFormat(
                                                            MediaFormat.MIMETYPE_VIDEO_AVC,
                                                            960,
                                                            720
                                                        )
                                                        format.setInteger(
                                                            MediaFormat.KEY_MAX_INPUT_SIZE,
                                                            5000
                                                        )

                                                        var lastDecodedBitmap: Bitmap? = null

                                                        var and_surface =
                                                            android.view.Surface(textureView.surfaceTexture)

                                                        Thread {
                                                            try {
                                                                mCodec =
                                                                    MediaCodec.createDecoderByType(
                                                                        MediaFormat.MIMETYPE_VIDEO_AVC
                                                                    )
                                                                mCodec.configure(
                                                                    format,
                                                                    and_surface,
                                                                    null,
                                                                    0
                                                                )
                                                                mCodec.start()

                                                                var previousPacket = byteArrayOf()

                                                                wifiHelper.videoBufferCallback =
                                                                    { frame ->

                                                                        previousPacket += frame

                                                                        if (frame.size < 1460) { // Max paquet of 1460, so last packet of the current image < 1460 https://github.com/dji-sdk/Tello-Python/blob/master/doc/readme.pdf

                                                                            val inputIndex: Int =
                                                                                mCodec.dequeueInputBuffer(
                                                                                    -1
                                                                                )

                                                                            if (inputIndex >= 0) {

                                                                                val inputBuffer: ByteBuffer? =
                                                                                    mCodec.getInputBuffer(
                                                                                        inputIndex
                                                                                    )
                                                                                if (inputBuffer != null) {
                                                                                    inputBuffer.put(
                                                                                        previousPacket
                                                                                    )
                                                                                    mCodec.queueInputBuffer(
                                                                                        inputIndex,
                                                                                        0,
                                                                                        previousPacket.size,
                                                                                        0,
                                                                                        0
                                                                                    )
                                                                                }
                                                                            }

                                                                            val bufferInfo1 =
                                                                                MediaCodec.BufferInfo()
                                                                            val outputIndex: Int =
                                                                                mCodec.dequeueOutputBuffer(
                                                                                    bufferInfo1,
                                                                                    0
                                                                                )
                                                                            if (outputIndex >= 0) {
                                                                                mCodec.releaseOutputBuffer(
                                                                                    outputIndex,
                                                                                    true
                                                                                )
                                                                            }

                                                                            previousPacket =
                                                                                byteArrayOf()

                                                                        }
                                                                    }

                                                                wifiHelper.returnDroneCamera = {

                                                                    var stringBuffer = ""
                                                                    val latch =
                                                                        CountDownLatch(1) // CountDownLatch to wait for PixelCopy to finish

                                                                    Thread {
                                                                        val bitmap =
                                                                            Bitmap.createBitmap(
                                                                                textureView.width,
                                                                                textureView.height,
                                                                                Bitmap.Config.ARGB_8888
                                                                            )
                                                                        PixelCopy.request(
                                                                            and_surface,
                                                                            bitmap,
                                                                            { copyResult ->
                                                                                if (copyResult == PixelCopy.SUCCESS) {

                                                                                    // Bitmap is successfully captured
                                                                                    val resizedBitmap =
                                                                                        Bitmap.createScaledBitmap(
                                                                                            bitmap,
                                                                                            960,
                                                                                            720,
                                                                                            true
                                                                                        ).also {
                                                                                            // Free memory of original bitmap if applicable
                                                                                            bitmap.recycle()
                                                                                        }
                                                                                    val outputStream =
                                                                                        ByteArrayOutputStream()
                                                                                    resizedBitmap.compress(
                                                                                        Bitmap.CompressFormat.PNG,
                                                                                        100,
                                                                                        outputStream
                                                                                    )
                                                                                    val byteArray =
                                                                                        outputStream.toByteArray()
                                                                                    stringBuffer =
                                                                                        Base64.encodeToString(
                                                                                            byteArray,
                                                                                            Base64.DEFAULT
                                                                                        )

                                                                                    latch.countDown() // Release the latch to signal completion
                                                                                } else {
                                                                                    Log.e(
                                                                                        "PixelCopy",
                                                                                        "FAILED : $copyResult"
                                                                                    )
                                                                                    latch.countDown() // Release the latch even if failed
                                                                                }
                                                                            },
                                                                            handler
                                                                        )
                                                                    }.start()

                                                                    try {
                                                                        latch.await()
                                                                    } catch (e: InterruptedException) {
                                                                        e.printStackTrace()
                                                                    }

                                                                    stringBuffer
                                                                }


                                                            } catch (e: Exception) {
                                                                e.printStackTrace()
                                                            }
                                                        }.start()


                                                    }

                                                    override fun onSurfaceTextureSizeChanged(
                                                        surface: SurfaceTexture,
                                                        width: Int,
                                                        height: Int
                                                    ) {
                                                    }

                                                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                                                        return true
                                                    }

                                                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                                                }
                                        }
                                    }
                                )
                            }

                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {


                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {

                                CustomIconButton(
                                    onClick = {
                                        tts?.stop()
                                        speechRecognizer.startListening(speechRecognizerIntent)
                                    },
                                    contentDescription = "TTS",
                                    iconResourceId = R.drawable.baseline_record_voice_over_24
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                CustomIconButton(
                                    onClick = {
                                        showBigTTSbutton = !showBigTTSbutton
                                    },
                                    contentDescription = "Switch screen layout",
                                    iconResourceId = if(showBigTTSbutton) R.drawable.baseline_videogame_asset_24 else R.drawable.baseline_mic_24
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                if (!isConnectedToDrone) {
                                    CustomIconButton(
                                        onClick = {
                                            wifiHelper.connectToTello { d ->
                                                isConnectedToDrone = true
                                                drone = d
                                                wifiHelper_websocketConnection =
                                                    wifiHelper.connectToVLM()
                                            }
                                        },
                                        contentDescription = "Connect to the drone",
                                        iconResourceId = R.drawable.baseline_offline_bolt_24
                                    )
                                } else {
                                    CustomIconButton(
                                        onClick = {
                                            Log.i("DRONE", "Take OFF")
                                            Thread {
                                                drone!!.takeOff()
                                            }.start()
                                            droneIsFlying = true
                                        },
                                        contentDescription = "Take Off",
                                        iconResourceId = R.drawable.outline_swipe_up_alt_24
                                    )

                                    Spacer(modifier = Modifier.width(8.dp))

                                    CustomIconButton(
                                        onClick = {
                                            Log.i("DRONE", "Land")
                                            Thread {
                                                drone!!.land()
                                            }.start()
                                            droneIsFlying = false
                                        },
                                        contentDescription = "Land",
                                        iconResourceId = R.drawable.outline_swipe_down_alt_24
                                    )

                                    Spacer(modifier = Modifier.width(8.dp))

                                    CustomIconButton(
                                        onClick = {
                                            Thread {
                                                drone!!.switchCamera()
                                            }.start()
                                        },
                                        contentDescription = "Switch camera",
                                        iconResourceId = R.drawable.baseline_cameraswitch_24
                                    )

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Text("$droneName")

                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {

                                CustomIconButton(
                                    onClick = {},
                                    contentDescription = "AGENT LINK",
                                    iconResourceId = R.drawable.baseline_support_agent_24,
                                    backgroundColor = if (webSocketConnection != null) {
                                        if (webSocketConnection == true) {
                                            Color.Green
                                        } else {
                                            Color.Red
                                        }
                                    } else {
                                        Color.Gray
                                    }
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                CustomIconButton(
                                    onClick = {
                                        wifiHelper.createUDPBridge()
                                        startedUdpLink = true
                                        wifiHelper.udpLinkEstablished = true
                                    },
                                    contentDescription = "UDP LINK",
                                    iconResourceId = R.drawable.baseline_link_24,
                                    backgroundColor = if (startedUdpLink) {
                                        if (udpLinkEstablished) {
                                            Color.Green
                                        } else {
                                            Color.Red
                                        }
                                    } else {
                                        Color.Gray
                                    }
                                )

                            }

                        }

                        if(showBigTTSbutton) {

                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(32.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                CustomIconButton(
                                    onClick = {
                                        tts?.stop()
                                        speechRecognizer.startListening(speechRecognizerIntent)
                                    },
                                    contentDescription = "TTS",
                                    iconResourceId = R.drawable.baseline_record_voice_over_24,
                                    modifier = Modifier.width(200.dp).height(200.dp),
                                    iconModifier = Modifier.width(75.dp).height(75.dp)
                                )
                            }

                        }else {

                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Bottom
                            ) {

                                JoyStick(
                                    size = 175.dp,
                                    dotSize = 60.dp,
                                    backgroundImage = R.drawable.joystick_background,
                                    dotImage = R.drawable.joystick_dot,
                                ) { x: Double, y: Double ->
                                    drone?.UP_DOWN_VELOCITY = clamp(y)
                                    drone?.YAW_VELOCITY = clamp(x)
                                }

                                JoyStick(
                                    size = 175.dp,
                                    dotSize = 60.dp,
                                    backgroundImage = R.drawable.joystick_background,
                                    dotImage = R.drawable.joystick_dot,
                                ) { x: Double, y: Double ->
                                    drone?.FORWARD_BACKWARD_VELOCITY = clamp(y)
                                    drone?.LEFT_RIGHT_VELOCITY = clamp(x)
                                }

                            }

                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CustomIconButton(
    onClick: () -> Unit,
    iconResourceId: Int,
    contentDescription: String,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceTint,
) {
    Box(
        modifier = Modifier.background(backgroundColor, shape = RoundedCornerShape(percent = 50))
    ) {
        IconButton(
            modifier = modifier,
            onClick = onClick,
        ) {
            Icon(
                painterResource(iconResourceId),
                contentDescription = contentDescription,
                tint = Color.Black,
                modifier = iconModifier,
            )
        }
    }
}

fun clamp(double: Double) : Int {
    return (double*100).toInt()
}