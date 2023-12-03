package de.bauerapps.resimulate.threads

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.media.AudioManager
import androidx.annotation.RequiresApi
import kotlin.math.sin

/** This class is heavily inspired by the [Zentone Library](https://github.com/nisrulz/zentone)
 * from [Nishant Srivastava](https://github.com/nisrulz)
 *
 * It's task is to create a thread to play the beeping sounds for ecg and spo2 peaks while not blocking the ui.
 * */
class PlayToneThread(
  private val freqOfTone: Int,
  volumeFactor: Float
) : Thread() {

  private var isPlaying = false
  private var audioTrack: AudioTrack? = null
  private var volumeFactor = 0f
  private val sampleRate = 44100

  init {
    this.volumeFactor = volumeFactor
  }

  override fun run() {
    super.run()

    //Play tone
    playTone()
  }

  private fun playTone() {
    if (!isPlaying) {
      isPlaying = true

      val numSamples = 4410 * 2
      val samples = ShortArray(numSamples)

      val maxVolume = 32767f * volumeFactor

      val ramp = numSamples / 10f

      for (i in 0 until numSamples step 2) {

        val volume = when {
          i < ramp -> (maxVolume * i / ramp)
          i > numSamples - ramp -> (maxVolume * (numSamples - i) / ramp)
          else -> maxVolume
        }

        val sample =
          (sin(freqOfTone * 2.0 * Math.PI * i / sampleRate) * volume).toInt().toShort()
        samples[i] = sample
        samples[i + 1] = sample
      }


      try {
        val bufferSize = AudioTrack.getMinBufferSize(
          sampleRate, AudioFormat.CHANNEL_OUT_MONO,
          AudioFormat.ENCODING_PCM_16BIT
        )

        audioTrack = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
          getAudioTrack(bufferSize)
        } else {

          @Suppress("DEPRECATION")
          AudioTrack(
            AudioManager.STREAM_MUSIC, sampleRate, AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT, bufferSize, AudioTrack.MODE_STREAM
          )
        }

        audioTrack?.notificationMarkerPosition = numSamples
        audioTrack?.setPlaybackPositionUpdateListener(
          object : AudioTrack.OnPlaybackPositionUpdateListener {
            override fun onPeriodicNotification(track: AudioTrack) {
              // nothing to do
            }

            override fun onMarkerReached(track: AudioTrack) {
              stopTone()
            }
          })

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
          audioTrack?.setVolume(maxVolume)
        } else {
          @Suppress("DEPRECATION")
          audioTrack?.setStereoVolume(maxVolume, maxVolume)
        }

        audioTrack?.play() // Play the track
        audioTrack?.write(samples, 0, samples.size)    // Load the track
      } catch (e: Exception) {
        e.printStackTrace()
      }
    }
  }

  private fun stopTone() {
    audioTrack?.stop()
    audioTrack?.release()
    isPlaying = false
  }

  @RequiresApi(Build.VERSION_CODES.M)
  private fun getAudioTrack(bufferSize: Int): AudioTrack {

    return AudioTrack.Builder()
      .setAudioAttributes(
        AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_MEDIA)
          .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
          .build()
      )
      .setAudioFormat(
        AudioFormat.Builder()
          .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
          .setSampleRate(sampleRate)
          .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
          .build()
      )
      .setBufferSizeInBytes(bufferSize)
      .build()
  }
}