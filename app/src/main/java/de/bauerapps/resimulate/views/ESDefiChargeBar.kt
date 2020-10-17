package de.bauerapps.resimulate.views

import android.animation.Animator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.os.Bundle
import android.os.Parcelable
import androidx.annotation.ColorInt
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import de.bauerapps.resimulate.helper.ESBrandStyle
import de.bauerapps.resimulate.R
import com.beardedhen.androidbootstrap.api.attributes.BootstrapBrand
import com.beardedhen.androidbootstrap.api.defaults.DefaultBootstrapSize
import com.beardedhen.androidbootstrap.api.view.BootstrapBrandView
import com.beardedhen.androidbootstrap.api.view.BootstrapSizeView
import com.beardedhen.androidbootstrap.api.view.ProgressView
import com.beardedhen.androidbootstrap.api.view.RoundableView
import com.beardedhen.androidbootstrap.utils.ColorUtils
import com.beardedhen.androidbootstrap.utils.DimenUtils


class ESDefiChargeBar : View, ProgressView, BootstrapBrandView, RoundableView, BootstrapSizeView,
  Animator.AnimatorListener, ValueAnimator.AnimatorUpdateListener {

  private var progressPaint: Paint? = null
  private var bgPaint: Paint? = null

  var updateAnimationDuration: Long = 300

  private var userProgress: Int = 0
  private var drawnProgress: Int = 0

  private var maxProgress: Int = 0

  private var striped: Boolean = false
  private var animated: Boolean = false
  private var rounded: Boolean = false

  //used for progressbarGroup so that only the currect corners will be rounded
  internal var cornerRoundingLeft = true
    private set
  internal var cornerRoundingRight = true
    private set

  private var progressAnimator: ValueAnimator? = null
  private var tilePaint: Paint? = null
  private val baselineHeight = DimenUtils.pixelsFromDpResource(
    context,
    R.dimen.bootstrap_progress_bar_height
  )

  private var bootstrapBrand: BootstrapBrand? = null

  private var progressCanvas: Canvas? = null
  private var progressBitmap: Bitmap? = null
  private var onProgressAnimationEnded: ((View) -> (Unit))? = null
  private var onProgressAnimationUpdate: ((Int) -> (Unit))? = null
  private var stripeTile: Bitmap? = null

  private var bootstrapSize: Float = 0.toFloat()
  private var showPercentage: Boolean = false

  constructor(context: Context) : super(context) {
    initialise(null)
  }

  constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
    initialise(attrs)
  }

  constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(
    context,
    attrs,
    defStyleAttr
  ) {
    initialise(attrs)
  }

  private fun initialise(attrs: AttributeSet?) {
    ValueAnimator.setFrameDelay(15) // attempt 60fps
    tilePaint = Paint()

    progressPaint = Paint().apply {
      style = Paint.Style.FILL
      isAntiAlias = true
    }

    bgPaint = Paint().apply {
      style = Paint.Style.FILL
      color = ColorUtils.resolveColor(R.color.bootstrap_gray_light, context)
    }

    // get attributes
    val a = context.obtainStyledAttributes(attrs, R.styleable.ESDefiChargeBar)

    try {
      this.animated = a.getBoolean(R.styleable.ESDefiChargeBar_animated, false)
      this.rounded = a.getBoolean(R.styleable.ESDefiChargeBar_roundedCorners, false)
      this.striped = false
      this.showPercentage = false
      this.userProgress = a.getInt(R.styleable.ESDefiChargeBar_bootstrapProgress, 0)
      this.maxProgress = a.getInt(R.styleable.ESDefiChargeBar_bootstrapMaxProgress, 100)

      val sizeOrdinal = -1

      this.bootstrapSize = DefaultBootstrapSize.fromAttributeValue(sizeOrdinal).scaleFactor()
      this.bootstrapBrand =
        ESBrandStyle(R.color.danger, R.color.white)
      this.drawnProgress = userProgress
    } finally {
      a.recycle()
    }

    updateBootstrapState()
    setMaxProgress(this.maxProgress)
  }

  public override fun onSaveInstanceState(): Parcelable? {
    val bundle = Bundle()
    bundle.putParcelable(TAG, super.onSaveInstanceState())

    bundle.putInt(ProgressView.KEY_USER_PROGRESS, userProgress)
    bundle.putInt(ProgressView.KEY_DRAWN_PROGRESS, drawnProgress)
    bundle.putBoolean(ProgressView.KEY_STRIPED, striped)
    bundle.putBoolean(ProgressView.KEY_ANIMATED, animated)
    bundle.putBoolean(RoundableView.KEY, rounded)
    bundle.putFloat(BootstrapSizeView.KEY, bootstrapSize)
    bundle.putSerializable(BootstrapBrand.KEY, bootstrapBrand)
    return bundle
  }

  public override fun onRestoreInstanceState(state: Parcelable?) {
    var mutableState = state
    if (mutableState is Bundle) {
      val bundle = mutableState as Bundle?

      val brand = bundle!!.getSerializable(BootstrapBrand.KEY)

      if (brand is BootstrapBrand) {
        bootstrapBrand = brand
      }

      this.userProgress = bundle.getInt(ProgressView.KEY_USER_PROGRESS)
      this.drawnProgress = bundle.getInt(ProgressView.KEY_DRAWN_PROGRESS)
      this.striped = bundle.getBoolean(ProgressView.KEY_STRIPED)
      this.animated = bundle.getBoolean(ProgressView.KEY_ANIMATED)
      this.rounded = bundle.getBoolean(RoundableView.KEY)
      this.bootstrapSize = bundle.getFloat(BootstrapSizeView.KEY)

      mutableState = bundle.getParcelable(TAG)
    }
    super.onRestoreInstanceState(mutableState)
    updateBootstrapState()
    progress = userProgress
  }

  private fun getStripeColor(@ColorInt color: Int): Int {
    return Color.argb(STRIPE_ALPHA, Color.red(color), Color.green(color), Color.blue(color))
  }

  /**
   * Starts an animation which moves the progress bar from one value to another, in response to
   * a call to setProgress(). Animation update callbacks allow the interpolator value to be used
   * to calculate the current progress value, which is stored in a temporary variable. The view is
   * then invalidated.
   *
   *
   * If this method is called when a progress update animation is already running, the previous
   * animation will be cancelled, and the currently drawn progress recorded. A new animation will
   * then be started from the last drawn point.
   */
  private fun startProgressUpdateAnimation() {
    clearAnimation()

    progressAnimator =
      ValueAnimator.ofFloat(drawnProgress.toFloat(), userProgress.toFloat()).apply {
        duration = updateAnimationDuration
        repeatCount = 0
        repeatMode = ValueAnimator.RESTART
        interpolator = LinearInterpolator()

        addUpdateListener(this@ESDefiChargeBar)

        // start striped animation after progress update if needed
        addListener(this@ESDefiChargeBar)
        start()
      }

  }

  override fun onAnimationUpdate(animation: ValueAnimator) {
    drawnProgress = (animation.animatedValue as Float).toInt()
    this.onProgressAnimationUpdate?.invoke(drawnProgress)
    invalidate()
  }

  override fun onAnimationStart(animation: Animator) {}

  fun setOnProgressAnimationEndedListener(listener: (View) -> Unit) {
    this.onProgressAnimationEnded = listener
  }

  fun setOnProgressAnimationUpdateListener(listener: (Int) -> Unit) {
    this.onProgressAnimationUpdate = listener
  }

  override fun onAnimationEnd(animation: Animator) {
    if (drawnProgress == maxProgress) onProgressAnimationEnded?.invoke(this)
    startStripedAnimationIfNeeded() // start striped animation after progress update
  }

  override fun onAnimationCancel(animation: Animator) {}

  override fun onAnimationRepeat(animation: Animator) {}

  /**
   * Starts an infinite animation cycle which provides the visual effect of stripes moving
   * backwards. The current system time is used to offset tiled bitmaps of the progress background,
   * producing the effect that the stripes are moving backwards.
   */
  private fun startStripedAnimationIfNeeded() {
    if (!striped || !animated) {
      return
    }

    clearAnimation()

    progressAnimator = ValueAnimator.ofFloat(0f, 0f).apply {
      duration = updateAnimationDuration
      repeatCount = ValueAnimator.INFINITE
      repeatMode = ValueAnimator.RESTART

      interpolator = LinearInterpolator()
      addUpdateListener { invalidate() }
      start()
    }

  }

  /*
   * Custom Measuring/Drawing
   */

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    // restrict view to default progressbar height

    val width = MeasureSpec.getSize(widthMeasureSpec)
    var height = MeasureSpec.getSize(heightMeasureSpec)

    val heightMode = MeasureSpec.getMode(heightMeasureSpec)

    when (heightMode) {
      MeasureSpec.EXACTLY -> {
      }
      MeasureSpec.AT_MOST // prefer default height, if not all available use as much as possible
      -> {
        val desiredHeight = baselineHeight * bootstrapSize
        height = if (height > desiredHeight) desiredHeight.toInt() else height
      }
      else -> height = (baselineHeight * bootstrapSize).toInt()
    }
    setMeasuredDimension(width, height)
  }

  override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
    if (h != oldh) {
      stripeTile = null // dereference cached bitmap
    }

    super.onSizeChanged(w, h, oldw, oldh)
  }

  override fun onDraw(canvas: Canvas) {
    val w = width.toFloat()
    val h = height.toFloat()

    if (w <= 0 || h <= 0) {
      return
    }

    val ratio = drawnProgress / maxProgress.toFloat()
    val lineEnd = (w * ratio).toInt()

    if (progressBitmap == null) {
      progressBitmap = Bitmap.createBitmap(w.toInt(), h.toInt(), Bitmap.Config.ARGB_8888)
    }
    if (progressCanvas == null) {
      progressCanvas = Canvas(progressBitmap!!)
    }
    progressCanvas?.apply {
      drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
      drawRect(0f, 0f, lineEnd.toFloat(), h, progressPaint!!)
      drawRect(lineEnd.toFloat(), 0f, w, h, bgPaint!!) // draw bg
    }

    val corners = if (rounded) h / 10f else 0f
    val round = createRoundedBitmap(
      progressBitmap!!,
      corners,
      cornerRoundingRight,
      cornerRoundingLeft
    )
    canvas.drawBitmap(round, 0f, 0f, tilePaint)

  }

  private fun updateBootstrapState() {
    val color = bootstrapBrand!!.defaultFill(context)
    progressPaint!!.color = color
    invalidateDrawCache()
    invalidate()
  }

  private fun invalidateDrawCache() {
    stripeTile = null
    progressBitmap = null
    progressCanvas = null
  }

  /*
   * Getters/Setters
   */


  @SuppressLint("DefaultLocale")
  override fun setProgress(progress: Int) {

    if (progress < 0 || progress > maxProgress) {
      throw IllegalArgumentException(
        String.format(
          "Invalid value '%d' - progress must be an integer in the range 0-%d",
          progress,
          maxProgress
        )
      )
    }

    this.userProgress = progress

    if (animated) {
      startProgressUpdateAnimation()
    } else {
      this.drawnProgress = progress
      invalidate()
    }
  }

  override fun getProgress(): Int {
    return userProgress
  }

  override fun setStriped(striped: Boolean) {
    this.striped = striped
    invalidate()
    startStripedAnimationIfNeeded()
  }

  override fun isStriped(): Boolean {
    return striped
  }

  override fun setAnimated(animated: Boolean) {
    this.animated = animated
    invalidate()
    startStripedAnimationIfNeeded()
  }

  override fun isAnimated(): Boolean {
    return animated
  }

  override fun setBootstrapBrand(bootstrapBrand: BootstrapBrand) {
    this.bootstrapBrand = bootstrapBrand
    updateBootstrapState()
  }

  override fun getBootstrapBrand(): BootstrapBrand {
    return bootstrapBrand!!
  }

  override fun setRounded(rounded: Boolean) {
    this.rounded = rounded
    updateBootstrapState()
  }

  override fun isRounded(): Boolean {
    return rounded
  }

  override fun getBootstrapSize(): Float {
    return bootstrapSize
  }

  override fun setBootstrapSize(bootstrapSize: Float) {
    this.bootstrapSize = bootstrapSize
    requestLayout()
    updateBootstrapState()
  }

  override fun setBootstrapSize(bootstrapSize: DefaultBootstrapSize) {
    setBootstrapSize(bootstrapSize.scaleFactor())
  }

  /**
   *
   * @return int, the max progress.
   */
  override fun getMaxProgress(): Int {
    return maxProgress
  }

  /**
   * Used for settings the maxprogress. Also check if currentProgress is smaller than newMaxProgress.
   * @param newMaxProgress the maxProgress value
   */
  override fun setMaxProgress(newMaxProgress: Int) {
    if (progress <= newMaxProgress) {
      maxProgress = newMaxProgress
    } else {
      throw IllegalArgumentException(
        String.format(
          "MaxProgress cant be smaller than the current progress %d<%d",
          progress,
          newMaxProgress
        )
      )
    }
    invalidate()
    //val parent = parent as? BootstrapProgressBarGroup
  }

  internal fun setCornerRounding(left: Boolean, right: Boolean) {
    cornerRoundingLeft = left
    cornerRoundingRight = right
  }

  companion object {

    private const val TAG = "com.beardedhen.androidbootstrap.AwesomeTextView"

    private const val STRIPE_ALPHA = 150

    /**
     * Creates a rounded bitmap with transparent corners, from a square bitmap.
     * See [StackOverflow](http://stackoverflow.com/questions/4028270)
     *
     * @param bitmap       the original bitmap
     * @param cornerRadius the radius of the corners
     * @param roundRight if you should round the corners on the right, note that if set to true and cornerRadius == 0 it will create a square
     * @param roundLeft if you should round the corners on the right, note that if set to true and cornerRadius == 0 it will create a square
     * @return a rounded bitmap
     */
    private fun createRoundedBitmap(
      bitmap: Bitmap, cornerRadius: Float,
      roundRight: Boolean, roundLeft: Boolean
    ): Bitmap {

      val roundedBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)

      val frame = Rect(0, 0, bitmap.width, bitmap.height)

      val leftRect = Rect(0, 0, bitmap.width / 2, bitmap.height)
      val rightRect = Rect(bitmap.width / 2, 0, bitmap.width, bitmap.height)

      val paint = Paint().apply {
        // prepare canvas for transfer
        isAntiAlias = true
        color = -0x1
        style = Paint.Style.FILL
      }

      Canvas(roundedBitmap).apply {
        drawARGB(0, 0, 0, 0)
        drawRoundRect(RectF(frame), cornerRadius, cornerRadius, paint)

        if (!roundLeft) drawRect(leftRect, paint)
        if (!roundRight) drawRect(rightRect, paint)

        // draw bitmap
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)

        drawBitmap(bitmap, frame, frame, paint)
      }

      return roundedBitmap
    }
  }
}