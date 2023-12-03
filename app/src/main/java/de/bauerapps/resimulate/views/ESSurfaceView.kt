package de.bauerapps.resimulate.views

import android.content.Context
import android.graphics.*
import androidx.core.content.ContextCompat
import android.util.AttributeSet
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import de.bauerapps.resimulate.R
import de.bauerapps.resimulate.threads.VitalSignThread


enum class ESViewType { ECG, PLETH, CAP }

// TODO: Create Attributes and add them in XML
class ESSurfaceView : SurfaceView, SurfaceHolder.Callback {
  constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
  constructor(context: Context, attrs: AttributeSet, defStyle: Int) : super(
    context,
    attrs,
    defStyle
  )

  constructor(context: Context) : super(context)

  interface Callback {
    fun pullValue(type: ESViewType, timestep: Double): Double
    fun requestSync()
  }

  companion object {
    private const val TAG = "ESSurfaceView"
  }

  private var yMaxValue: Double? = null
  private var yMinValue: Double? = null
  private var pixelPerIncrement = 0f
  private var yRange: Double? = null
  private var simPaint: Paint? = null
  private var clearPaint: Paint? = null
  private var bitmap: Bitmap? = null
  private var bitmapCanvas: Canvas? = null
  private lateinit var type: ESViewType

  // Interface
  var callback: Callback? = null

  private var vsThread: VitalSignThread
  private var isSetup = false
  private var point = Point(0f, 0f)
  private var oldPoint = Point(0f, 0f)
  private var isPaused = false
  private var prepareStop = false
  private var syncPeakPixelOffset = 0f
  private var ecgSyncPending = false

  private val timestep = 1.0 / VitalSignThread.FPS

  var drawSyncPeak = false
  var drawPacerPeak = false
  var drawZeroIndicator = false
  var hasZeroIndicator = false
  var pacerEnergy = 10
  var isToggledOn = false
  var simulationStarted = false
  var needsGraphReset = false

  init {
    holder.addCallback(this)
    vsThread = VitalSignThread(holder, this)
  }


  fun setup(
    type: ESViewType,
    esColor: ESColor,
    yMaxValue: Double,
    yMinValue: Double,
    isTrainer: Boolean = false
  ) {

    this.yMaxValue = yMaxValue
    this.yMinValue = yMinValue
    this.type = type
    yRange = yMaxValue - yMinValue
    simPaint = createSimPaint(esColor)
    clearPaint = createClearPaint()

    simulationStarted = isTrainer
    isSetup = true
  }

  override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
    Log.i(TAG, "onSizeChanged to w: $width, h: $height")
    bitmap = null
  }

  override fun surfaceDestroyed(holder: SurfaceHolder) {
    var retry = true
    while (retry) {
      try {
        vsThread.setRunning(false)
        vsThread.join()

      } catch (e: InterruptedException) {
        e.printStackTrace()
      }

      retry = false
    }
  }


  override fun surfaceCreated(holder: SurfaceHolder) {
  }

  private fun getXrange(width: Int): Float {
    return when {
      width <= 400 -> 2f
      width <= 800 -> ((width - 200) / 100).toFloat()
      width <= 1400 -> ((width - 300) / 100).toFloat()
      // Truncation is expected.
      else -> ((width - 300) / 200).toFloat()
    }

  }

  fun performECGSync() {
    ecgSyncPending = true
  }

  fun update() {
    if (!simulationStarted) return
    if (bitmap == null) {
      // Initiate Bitmap
      bitmap = Bitmap.createBitmap(this.measuredWidth, this.measuredHeight, Bitmap.Config.RGB_565)

      pixelPerIncrement = this.measuredWidth / getXrange(this.measuredWidth) / VitalSignThread.FPS
      Log.i(TAG, "Measured width: ${this.measuredWidth}, Pixel per increment: $pixelPerIncrement")
      val test = callback?.pullValue(type, 0.0)
      val norm = normalizeY(test)
      point = Point(0f, norm)

      bitmapCanvas = Canvas(bitmap!!)
    }

    if (ecgSyncPending) {
      point.x = 0f
      ecgSyncPending = false
    }

    if (point.x > width) {
      point.x = 0f
      if (type == ESViewType.ECG) {
        // Send sync request to Oxy
        callback?.requestSync()
      }
    }

    oldPoint.x = point.x
    oldPoint.y = point.y

    point.y = normalizeY(callback?.pullValue(type, timestep))

  }

  private fun resetCanvas() {
    bitmapCanvas?.drawRect(0f, 0f, width.toFloat(), height.toFloat(), clearPaint!!)
    point.x = 0f
  }

  override fun draw(canvas: Canvas) {
    super.draw(canvas)

    if (!simulationStarted) return
    if (isInEditMode) return

    bitmapCanvas?.save()

    if (needsGraphReset) {
      needsGraphReset = false
      resetCanvas()
    }

    bitmapCanvas?.drawRect(point.x, 0f, point.x + 60f, height.toFloat(), clearPaint!!)

    point.x += if (type == ESViewType.CAP) pixelPerIncrement / 2 else pixelPerIncrement

    bitmapCanvas?.drawLine(oldPoint.x, oldPoint.y, point.x, point.y, simPaint!!)

    if (drawSyncPeak) {
      syncPeakPixelOffset += pixelPerIncrement
      if (syncPeakPixelOffset >= 5) {
        val pair = getSyncPeakPath()
        bitmapCanvas?.drawPath(pair.first, pair.second)
        drawSyncPeak = false
        syncPeakPixelOffset = 0f
      }
    }

    if (drawPacerPeak) {
      val pair = drawPacerPeak()
      bitmapCanvas?.drawPath(pair.first, pair.second)
      drawPacerPeak = false
    }

    drawZeroIndicator = if (hasZeroIndicator && drawZeroIndicator) {
      val pair = drawZeroIndicator()
      bitmapCanvas?.drawPath(pair.first, pair.second)
      false
    } else false

    if (prepareStop) {
      resetCanvas()
      vsThread.setRunning(false)
    }

    bitmapCanvas?.restore()

    canvas?.drawBitmap(bitmap!!, 0f, 0f, simPaint)
  }

  fun pause() {
    isPaused = true
    vsThread.setRunning(false)
  }

  fun resume() {
    if (!isSetup) return
    if (!isToggledOn) return

    isPaused = false
    restart()
  }

  fun restart() {
    if (!isSetup) return
    if (vsThread.isAlive) return

    prepareStop = false
    needsGraphReset = true

    if (vsThread.state == Thread.State.TERMINATED)
      vsThread = VitalSignThread(holder, this)

    vsThread.start()
    vsThread.setRunning(true)
  }

  fun clearStop() {
    if (!isSetup) return

    prepareStop = true
    point.x = 0f
    point.y = normalizeY(0.0)
  }

  private fun normalizeY(yValue: Double?): Float {
    var yVal = yValue
    if (yVal == null) yVal = 0.0
    val normalized = (yVal - yMinValue!!) / yRange!!.toFloat()
    return (height * (-normalized) + height).toFloat()
  }

  private fun createSimPaint(esColor: ESColor): Paint {
    return Paint().apply {
      color = ContextCompat.getColor(context, esColor.getColor())
      strokeWidth = 4f
      style = Paint.Style.STROKE
      strokeCap = Paint.Cap.ROUND
      strokeJoin = Paint.Join.ROUND
    }
  }

  private fun createClearPaint(): Paint {
    return Paint().apply {
      style = Paint.Style.FILL
      strokeWidth = 0f
      color = Color.BLACK
    }
  }

  private inner class Point(var x: Float, var y: Float) {
    override fun toString(): String = "x: $x, y: $y"
  }

  /** Draws the synchronization triangle over the ECG peak. */
  private fun getSyncPeakPath(): Pair<Path, Paint> {
    val yValueMax = 0f//normalizeY(yMaxValue?:0-0.1) //normalizeY(0.3)
    val yTrianglePeak = 15f//normalizeY(yMaxValue?:0-0.3)

    val path = Path().apply {
      moveTo(point.x - 12, yValueMax)
      lineTo(point.x - 6, yTrianglePeak)
      lineTo(point.x, yValueMax)
      close()
    }

    val syncPaint = Paint(simPaint).apply { style = Paint.Style.FILL }
    return Pair(path, syncPaint)
  }

  /** Draws the vertical pacer line in the ECG. */
  private fun drawPacerPeak(): Pair<Path, Paint> {

    val norm = pacerEnergy / 150f * 20f

    val yGroundValue = point.y + norm
    val yTopValue = point.y - norm * 3

    val path = Path().apply {
      moveTo(point.x - 3f, yGroundValue)
      lineTo(point.x - 3f, yTopValue)
      close()
    }

    val pacerPaint = Paint(simPaint)

    return Pair(path, pacerPaint)
  }

  private fun drawZeroIndicator(): Pair<Path, Paint> {

    val yGroundValue = point.y + height
    val yTopValue = point.y - height

    val path = Path().apply {
      moveTo(point.x - 3f, yGroundValue)
      lineTo(point.x - 3f, yTopValue)
      close()
    }

    val pacerPaint = Paint(simPaint).apply {
      strokeWidth = 2f
      color = ContextCompat.getColor(context, R.color.white)
    }

    return Pair(path, pacerPaint)
  }

}