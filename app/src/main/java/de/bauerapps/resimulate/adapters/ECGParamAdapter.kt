package de.bauerapps.resimulate.adapters

import android.content.Context
import androidx.appcompat.widget.AppCompatSeekBar
import androidx.recyclerview.widget.RecyclerView
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.SeekBar
import de.bauerapps.resimulate.DVS
import de.bauerapps.resimulate.R
import de.bauerapps.resimulate.helper.inflate
import com.beardedhen.androidbootstrap.BootstrapEditText
import kotlinx.android.synthetic.main.vital_param_list_item.view.*
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.roundToInt
import android.view.inputmethod.InputMethodManager
import de.bauerapps.resimulate.adapters.ECGParam.Companion.TAG
import de.bauerapps.resimulate.helper.ESApplication
import de.bauerapps.resimulate.views.ESTextView


enum class ECGParam(
  val pName: String,
  var max: Double,
  var value: Double,
  var min: Double = 0.0,
  var types: Array<String>
) {

  HR("Heart Rate", 250.0, 60.0, 20.0, arrayOf("ecg")),

  SPO2(
    ESApplication.getString(R.string.spo2),
    100.0,
    DVS.spo2.toDouble(),
    types = arrayOf("ecg", "oxy")
  ),
  ETCO2(
    ESApplication.getString(R.string.etco2),
    60.0,
    DVS.etco2.toDouble(),
    types = arrayOf("cap")
  ),
  RESPRATE(
    ESApplication.getString(R.string.resp_rate),
    40.0,
    DVS.respRate.toDouble(),
    types = arrayOf("cap")
  ),
  NIBP_SYS(
    ESApplication.getString(R.string.sys_nibp),
    300.0,
    DVS.nibpSys.toDouble(),
    types = arrayOf("oxy")
  ),
  NIBP_DIA(
    ESApplication.getString(R.string.dia_nibp),
    260.0,
    DVS.nibpDia.toDouble(),
    types = arrayOf("oxy")
  ),

  PWaveAmp(ESApplication.getString(R.string.p_wave_amp), 0.5, DVS.pWaveAmp, -0.5, arrayOf("ecg")),
  PWaveDuration(
    ESApplication.getString(R.string.p_wave_dur),
    1.0,
    DVS.pWaveDur,
    types = arrayOf("ecg")
  ),
  PWaveStartTime(
    ESApplication.getString(R.string.p_wave_start_time),
    0.5,
    DVS.pWaveST,
    -0.5,
    arrayOf("ecg")
  ),

  QWaveDuration(
    ESApplication.getString(R.string.q_wave_dur),
    0.5,
    DVS.qWaveDur,
    types = arrayOf("ecg")
  ),
  QWaveStartTime(
    ESApplication.getString(R.string.q_wave_start_time),
    0.5,
    DVS.qWaveST,
    -0.5,
    arrayOf("ecg")
  ),
  QWaveAmp(ESApplication.getString(R.string.q_wave_amp), 0.5, DVS.qWaveAmp, -0.5, arrayOf("ecg")),

  QRSDuration(ESApplication.getString(R.string.qrs_dur), 0.2, DVS.qrsDur, 0.0, arrayOf("ecg")),
  QRSStartTime(
    ESApplication.getString(R.string.qrs_start_time),
    1.0,
    DVS.qrsST,
    -0.5,
    arrayOf("ecg")
  ),
  QRSWaveAmp(ESApplication.getString(R.string.qrs_amp), 3.0, DVS.qrsAmp, -3.0, arrayOf("ecg")),
  /*QRSAmplitudeOffset(ESApplication.getString(R.string.qrs_amp_offset), 2.0, 0.0, -2.0, arrayOf("ecg")),*/
  //QRSDurationOffset("QRS Duration Offset", 5.0, 0.0),

  SWaveDuration(
    ESApplication.getString(R.string.s_wave_dur),
    0.2,
    DVS.sWaveDur,
    types = arrayOf("ecg")
  ),
  SWaveStartTime(
    ESApplication.getString(R.string.s_wave_start_time),
    0.5,
    DVS.sWaveST,
    -0.5,
    types = arrayOf("ecg")
  ),
  SWaveAmp(ESApplication.getString(R.string.s_wave_amp), 0.5, DVS.sWaveAmp, -0.5, arrayOf("ecg")),

  TWaveDuration(
    ESApplication.getString(R.string.t_wave_dur),
    0.5,
    DVS.tWaveDur,
    types = arrayOf("ecg")
  ),
  TWaveStartTime(
    ESApplication.getString(R.string.t_wave_start_time),
    1.0,
    DVS.tWaveST,
    types = arrayOf("ecg")
  ),
  TWaveAmp(ESApplication.getString(R.string.t_wave_amp), 1.0, DVS.tWaveAmp, -1.0, arrayOf("ecg")),

  STWaveSeparationDuration(
    ESApplication.getString(R.string.st_wave_sep_dur),
    0.2,
    DVS.stWaveSeparationDuration,
    types = arrayOf("ecg")
  ),

  UWaveAmp(ESApplication.getString(R.string.u_wave_amp), 0.5, DVS.uWaveAmp, -0.5, arrayOf("ecg")),
  UWaveDuration(
    ESApplication.getString(R.string.u_wave_dur),
    0.2,
    DVS.uWaveDur,
    types = arrayOf("ecg")
  ),
  UWaveStartTime(
    ESApplication.getString(R.string.u_wave_start_time),
    1.0,
    DVS.uWaveST,
    types = arrayOf("ecg")
  ),

  // TODO: This value is not descriptive!
  XValOffset(
    ESApplication.getString(R.string.x_val_offset),
    0.4,
    DVS.xValOffset,
    -0.4,
    arrayOf("all")
  ),
  EcgNoise(ESApplication.getString(R.string.noise), 0.5, DVS.ecgNoise, types = arrayOf("ecg")),
  OxyNoise(ESApplication.getString(R.string.oxy_noise), 1.0, DVS.oxyNoise, types = arrayOf("oxy")),
  CapNoise(ESApplication.getString(R.string.cap_noise), 1.5, DVS.capNoise, types = arrayOf("cap"));


  companion object {

    fun getParams(): ArrayList<ECGParam> {
      return values().toCollection(ArrayList())
    }

    fun getParamsFor(types: List<String>): ArrayList<ECGParam> {

      val params = ArrayList<ECGParam>()
      for (type in types) {
        params.addAll(values().filter {
          it.types.contains(type) or (it.types.contains("all") and !params.contains(it))
        })
      }
      //params.addAll(values().filter { it.types.contains("all") })

      return params
    }

    const val TAG = "ECGParamAdapter"
  }
}

class ECGParamAdapter(var ecgParams: ArrayList<ECGParam> = ECGParam.getParamsFor(listOf("ecg"))) :
  RecyclerView.Adapter<ECGParamAdapter.ECGParamViewHolder>() {

  interface Callback {
    fun onProgressChanged(ecgParam: ECGParam, value: Int)
  }

  var callback: Callback? = null

  private var context: Context? = null

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ECGParamViewHolder {
    val inflatedView = parent.inflate(R.layout.vital_param_list_item, false)
    context = parent.context
    return ECGParamViewHolder(inflatedView)
  }

  override fun getItemCount(): Int {
    return ecgParams.size
  }

  override fun onBindViewHolder(holder: ECGParamViewHolder, position: Int) {
    holder.bindParam(ecgParams[position])
  }

  fun updateParams(options: Map<String, Boolean>) {
    for (option in options) {
      showParam[option.key] = option.value
    }
    ecgParams = ECGParam.getParamsFor(showParam.filter { it.value }.keys.toList())
    notifyDataSetChanged()
  }

  private var showParam = mutableMapOf(
    "ecg" to true, "oxy" to false, "cap" to false
  )

  inner class ECGParamViewHolder(v: View) : RecyclerView.ViewHolder(v) {

    private var twParamName: ESTextView? = null
    private var twParamValue: BootstrapEditText? = null
    private var twParamMin: ESTextView? = null
    private var twParamMax: ESTextView? = null
    private var sbParamChange: AppCompatSeekBar? = null

    private val intParams = listOf(
      ECGParam.HR.pName, ECGParam.SPO2.pName, ECGParam.ETCO2.pName,
      ECGParam.RESPRATE.pName, ECGParam.NIBP_SYS.pName, ECGParam.NIBP_DIA.pName
    )

    init {
      this.twParamName = v.tw_param_name
      this.twParamValue = v.tw_param_value
      this.twParamMin = v.tw_param_min
      this.twParamMax = v.tw_param_max
      this.sbParamChange = v.sb_param_change
    }


    fun bindParam(ecgParam: ECGParam) {

      this.twParamName?.text = ecgParam.pName

      if (intParams.contains(twParamName?.text)) {
        twParamValue?.setText(ecgParam.value.roundToInt().toString())
      } else {
        twParamValue?.setText(String.format(Locale.US, "%.2f", ecgParam.value))
      }

      this.twParamMax?.text = ecgParam.max.toString()
      this.twParamMin?.text = ecgParam.min.toString()

      this.sbParamChange?.apply {

        val difference = ecgParam.max - ecgParam.min

        max = (difference * 100).roundToInt()

        progress = ((ecgParam.value - ecgParam.min) * 100).roundToInt()
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
          override fun onProgressChanged(p0: SeekBar?, value: Int, fromUser: Boolean) {
            if (!fromUser) return
            val formattedValue = String.format(Locale.US, "%.2f", value / 100.0 + ecgParam.min)
            //if (twParamName?.text != ECGParam.HR.pName && twParamValue?.text == formattedValue)
            //    return

            //dynChangeDialogView.tw_slider_label.text = "Manual Change Duration: ${p1}s"
            ecgParams[adapterPosition].value = (value / 100.0 + ecgParam.min)
            callback?.onProgressChanged(ecgParam, (value / 100.0 + ecgParam.min).roundToInt())
            if (intParams.contains(twParamName?.text)) {
              twParamValue?.setText((value / 100.0 + ecgParam.min).roundToInt().toString())
            } else {
              twParamValue?.setText(formattedValue)
            }
          }

          override fun onStartTrackingTouch(p0: SeekBar?) {}
          override fun onStopTrackingTouch(p0: SeekBar?) {}
        })
      }

      this.twParamValue?.setOnEditorActionListener { v, actionID, _ ->

        if (actionID == EditorInfo.IME_ACTION_DONE) {
          var input = v.text.toString().toDoubleOrNull()

          Log.i(TAG, "Input: $input")

          if (input != null) {

            if (input > ecgParam.max) {
              input = ecgParam.max
              this.twParamValue?.setText(input.toString())
            }

            if (input < ecgParam.min) {
              input = ecgParam.min
              this.twParamValue?.setText(input.toString())
            }

            ecgParams[adapterPosition].value = input
            this.sbParamChange?.progress = ((ecgParam.value - ecgParam.min) * 100).roundToInt()
            callback?.onProgressChanged(ecgParam, input.roundToInt())
          } else
            this.twParamValue?.setText(ecgParam.value.toString())
        }

        v.clearFocus()
        val imm = v.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(v.windowToken, 0)
        //v.clearFocus()

        return@setOnEditorActionListener true
      }
    }
  }


}