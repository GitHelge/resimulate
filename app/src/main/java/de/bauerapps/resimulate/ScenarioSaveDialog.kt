package de.bauerapps.resimulate

import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import cn.pedant.SweetAlert.SweetAlertDialog
import com.google.gson.Gson
import de.bauerapps.resimulate.helper.ESApplication
import de.bauerapps.resimulate.views.ESDialog
import kotlinx.android.synthetic.main.scenario_save_dialog.view.*

class ScenarioSaveDialog(val context: AppCompatActivity) {

  interface Callback {
    fun reloadDropdown(downloadPathology: DownloadPathology? = null)
  }

  private val dialogView: View = LayoutInflater.from(context)
    .inflate(R.layout.scenario_save_dialog, null)
  private val dialog: ESDialog = ESDialog(context, R.style.NoAnimDialog)

  private var knownScenarioNames = getScenarioNames()

  private lateinit var tempVS: VitalSigns
  private var downloadPathology: DownloadPathology? = null

  private fun getScenarioNames(): List<String> {
    val list = mutableListOf<String>()
    list.addAll(PType.values().map { it.pname })
    list.addAll(ESApplication.saveMap.keys.toList())
    return list
  }

  var callback: Callback? = null

  init {

    dialogView.apply {

      b_check.setOnClickListener {

        when {
          et_scenario_name.text.isEmpty() -> {
            tw_scenario_save_description.blinkWarning()
            return@setOnClickListener
          }
          knownScenarioNames.contains(et_scenario_name.text.toString()) -> {

            SweetAlertDialog(this@ScenarioSaveDialog.context, SweetAlertDialog.ERROR_TYPE)
              .setTitleText(
                context.getString(
                  R.string.scenario_name_already_found,
                  et_scenario_name.text
                )
              )
              .show()

            return@setOnClickListener
          }
          else -> {

            if (downloadPathology != null) {
              downloadPathology!!.name = et_scenario_name.text.toString()
              ESApplication.updateSavedMap(downloadPathology!!)
              ESApplication.saveScenarios(this@ScenarioSaveDialog.context)

            } else {
              tempVS.pathology.name = et_scenario_name.text.toString()
              ESApplication.updateSavedMap(tempVS.pathology, tempVS)
              ESApplication.saveScenarios(this@ScenarioSaveDialog.context)
            }

            knownScenarioNames = getScenarioNames()
            callback?.reloadDropdown(downloadPathology)
          }
        }

        dialog.dismiss()
      }

      b_cancel.setOnClickListener { dialog.dismiss() }

      dialog.setContentView(dialogView)
    }
  }

  fun openDialog(downloadPathology: DownloadPathology) {

    this.downloadPathology = downloadPathology
    tempVS = Gson().fromJson(downloadPathology.vs, VitalSigns::class.java)

    dialogView.tw_header.text = context.getString(R.string.rename_scenario)
    dialogView.tw_header_desc.text =
      context.getString(R.string.doublicate_name_give_other, downloadPathology.name)
    dialogView.et_scenario_name.setText(downloadPathology.name)

    dialog.show()
  }

  fun openDialog(vitalSigns: VitalSigns) {
    tempVS = vitalSigns.deepCopy()

    dialog.show()
  }

}