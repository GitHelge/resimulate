package de.bauerapps.resimulate.threads

import android.graphics.Canvas
import android.view.SurfaceHolder
import de.bauerapps.resimulate.views.ESSurfaceView


class VitalSignThread(
  private val surfaceHolder: SurfaceHolder,
  private val esSurfaceView: ESSurfaceView
) : Thread() {

  companion object {
    const val FPS = 50
    //var averageFPS = 50.0
  }

  private var running = false
  private var canvas: Canvas? = null

  override fun run() {
    var startTime: Long
    var timeMillis: Long
    var waitTime: Long
    //var totalTime: Long = 0
    //var frameCount = 0
    val targetTime = (1000 / FPS).toLong()

    while (running) {
      startTime = System.nanoTime()
      canvas = null

      //try locking the canvas for pixel editing
      try {
        canvas = this.surfaceHolder.lockCanvas()
        synchronized(surfaceHolder) {
          this.esSurfaceView.update()
          this.esSurfaceView.draw(canvas)
        }
      } catch (e: Exception) {
        e.printStackTrace()
      } finally {
        if (canvas != null) {
          try {
            surfaceHolder.unlockCanvasAndPost(canvas)
          } catch (e: Exception) {
            e.printStackTrace()
          }

        }
      }

      timeMillis = (System.nanoTime() - startTime) / 1000000
      waitTime = targetTime - timeMillis

      try {
        if (waitTime > 0) {
          sleep(waitTime)
        }
      } catch (e: Exception) {
        e.printStackTrace()
      }

      /*totalTime += System.nanoTime() - startTime
      frameCount++
      if (frameCount == FPS) {
          averageFPS = 1000.0 / (totalTime / frameCount.toDouble() / 1000000.0)
          frameCount = 0
          totalTime = 0
          println(averageFPS)
      }*/
    }
  }

  fun setRunning(b: Boolean) {
    running = b
  }
}