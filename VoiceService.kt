
package com.personalvideoai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.util.Log

import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.KeywordSpotter
import com.k2fsa.sherpa.onnx.KeywordSpotterConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig

import kotlin.concurrent.thread

class VoiceService : Service() {

    companion object {
        const val CHANNEL_ID = "lotus_voice_channel"
        const val NOTIFICATION_ID = 1001
        const val TAG = "LotusVoice"
    }

    private val sampleRate = 16000

    @Volatile
    private var isRecording = false

    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private var spotter: KeywordSpotter? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        val notification = buildNotification(
            "Lotus Voice Assistant is running"
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        if (!isRecording) {
            startWakeWordDetection()
        }

        return START_STICKY
    }

    private fun startWakeWordDetection() {
        try {
            val modelDir =
                "sherpa-onnx-kws-zipformer-gigaspeech-3.3M-2024-01-01"

            val modelConfig = OnlineModelConfig(
                transducer = OnlineTransducerModelConfig(
                    encoder = "$modelDir/encoder-epoch-12-avg-2-chunk-16-left-64.onnx",
                    decoder = "$modelDir/decoder-epoch-12-avg-2-chunk-16-left-64.onnx",
                    joiner = "$modelDir/joiner-epoch-12-avg-2-chunk-16-left-64.onnx"
                ),
                tokens = "$modelDir/tokens.txt",
                numThreads = 2,
                provider = "cpu",
                modelType = "zipformer2"
            )

            val config = KeywordSpotterConfig(
                featConfig = FeatureConfig(
                    sampleRate = sampleRate,
                    featureDim = 80
                ),
                modelConfig = modelConfig,
                keywordsFile = "$modelDir/keywords.txt",
                keywordsScore = 2.0f,
                keywordsThreshold = 0.25f
            )

            val kws = KeywordSpotter(
                assetManager = assets,
                config = config
            )

            spotter = kws

            val minBuffer = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            if (minBuffer <= 0) {
                throw IllegalStateException("Invalid microphone buffer size")
            }

            val recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuffer * 2
            )

            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                recorder.release()
                throw IllegalStateException("Microphone initialization failed")
            }

            audioRecord = recorder
            recorder.startRecording()

            isRecording = true

            recordingThread = thread(
                start = true,
                name = "LotusWakeWord"
            ) {
                listenForWakeWord(kws, recorder)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Wake word initialization failed", e)
            isRecording = false
            stopSelf()
        }
    }

    private fun listenForWakeWord(
        kws: KeywordSpotter,
        recorder: AudioRecord
    ) {
        val stream = kws.createStream()
        val buffer = ShortArray(1600)

        try {
            while (isRecording) {

                val count = recorder.read(
                    buffer,
                    0,
                    buffer.size
                )

                if (count > 0) {

                    val samples = FloatArray(count) {
                        buffer[it] / 32768.0f
                    }

                    stream.acceptWaveform(
                        samples,
                        sampleRate = sampleRate
                    )

                    while (kws.isReady(stream)) {
                        kws.decode(stream)

                        val result = kws.getResult(stream)
                        val keyword = result.keyword

                        if (keyword.isNotBlank()) {
                            Log.i(TAG, "Detected: $keyword")

                            kws.reset(stream)

                            showDetectedNotification()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Wake word listening error", e)
        } finally {
            try {
                stream.release()
            } catch (_: Exception) {}

            try {
                recorder.stop()
            } catch (_: Exception) {}

            recorder.release()

            if (audioRecord === recorder) {
                audioRecord = null
            }

            kws.release()
            spotter = null
        }
    }

    private fun showDetectedNotification() {
        val manager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        manager.notify(
            NOTIFICATION_ID,
            buildNotification("Hey Lotus detected! Tap to open Lotus AI.")
        )
    }

    private fun buildNotification(message: String): Notification {
        val openApp = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Lotus AI")
            .setContentText(message)
            .setSmallIcon(R.drawable.lotus_icon)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Lotus Voice Assistant",
                NotificationManager.IMPORTANCE_LOW
            )

            val manager =
                getSystemService(NotificationManager::class.java)

            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        isRecording = false

        try {
            audioRecord?.stop()
        } catch (_: Exception) {}

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
