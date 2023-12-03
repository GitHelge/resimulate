package de.bauerapps.resimulate.adapters

import android.content.Context
import android.view.LayoutInflater
import androidx.appcompat.widget.AppCompatSeekBar
import androidx.recyclerview.widget.RecyclerView
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView
import de.bauerapps.resimulate.config.AlarmLevel
import de.bauerapps.resimulate.R
import de.bauerapps.resimulate.databinding.AlarmLevelListItemBinding
import de.bauerapps.resimulate.helper.inflate
import kotlin.collections.ArrayList

class AlarmLevelAdapter(val alarms: ArrayList<AlarmLevel>) :
  RecyclerView.Adapter<AlarmLevelAdapter.AlarmLevelViewHolder>() {

  private var context: Context? = null

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlarmLevelViewHolder {
    val binding = AlarmLevelListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    context = parent.context
    return AlarmLevelViewHolder(binding)
  }

  override fun getItemCount(): Int {
    return alarms.size
  }

  override fun onBindViewHolder(holder: AlarmLevelViewHolder, position: Int) {
    holder.bindDevice(alarms[position])
  }

  inner class AlarmLevelViewHolder(v: AlarmLevelListItemBinding) : RecyclerView.ViewHolder(v.root) {

    private var twAlarmLevelName: TextView? = null
    private var twAlarmLevelValue: TextView? = null
    private var twAlarmLevelMin: TextView? = null
    private var twAlarmLevelMax: TextView? = null
    private var sbAlarmLevel: AppCompatSeekBar? = null

    init {
      this.twAlarmLevelName = v.twAlarmName
      this.twAlarmLevelValue = v.twAlarmValue
      this.twAlarmLevelMin = v.twAlarmMin
      this.twAlarmLevelMax = v.twAlarmMax
      this.sbAlarmLevel = v.sbAlarmChange
    }

    fun bindDevice(alarm: AlarmLevel) {

      this.twAlarmLevelName?.text = alarm.name
      this.twAlarmLevelMin?.text = alarm.min.toString()
      this.twAlarmLevelMax?.text = alarm.max.toString()
      this.twAlarmLevelValue?.text = alarm.value.toString()

      this.sbAlarmLevel?.apply {

        val difference = alarm.max - alarm.min

        max = difference

        progress = (alarm.value - alarm.min)
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
          override fun onProgressChanged(p0: SeekBar?, value: Int, fromUser: Boolean) {
            if (!fromUser) return
            val formattedValue = "${value + alarm.min}"

            alarms[adapterPosition].value = value + alarm.min

            twAlarmLevelValue?.text = formattedValue
          }

          override fun onStartTrackingTouch(p0: SeekBar?) {}
          override fun onStopTrackingTouch(p0: SeekBar?) {}
        })
      }
    }
  }

}