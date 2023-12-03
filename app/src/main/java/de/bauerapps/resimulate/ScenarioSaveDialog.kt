package de.bauerapps.resimulate

import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import cn.pedant.SweetAlert.SweetAlertDialog
import com.google.gson.Gson
import de.bauerapps.resimulate.databinding.ScenarioSaveDialogBinding
import de.bauerapps.resimulate.helper.ESApplication
import de.bauerapps.resimulate.views.ESDialog

class ScenarioSaveDialog(val context: AppCompatActivity) {

  interface Callback {
    fun reloadDropdown(downloadPathology: DownloadPathology? = null)
  }

  private val dialogView = ScenarioSaveDialogBinding.inflate(context.layoutInflater)
  private val dialog: ESDialog = ESDialog(context, R.style.NoAnimDialog)

  private var knownScenarioNames = getScenarioNames()

  private lateinit var tempVS: VitalSigns
  private var downloadPathology: DownloadPathology? = null

  private fun getScenarioNames(): List<String> {
    val list = mutableListOf<String>()
    list.addAll(PType.entries.map { it.pname })
    list.addAll(ESApplication.saveMap.keys.toList())
    return list
  }

  var callback: Callback? = null

  init {

    dialogView.apply {
      bCheck.setOnClickListener {

        when {
          etScenarioName.text.isEmpty() -> {
            twScenarioSaveDescription.blinkWarning()
            return@setOnClickListener
          }
          knownScenarioNames.contains(etScenarioName.text.toString()) -> {

            SweetAlertDialog(this@ScenarioSaveDialog.context, SweetAlertDialog.ERROR_TYPE)
              .setTitleText(
                context.getString(
                  R.string.scenario_name_already_found,
                  etScenarioName.text
                )
              )
              .show()

            return@setOnClickListener
          }
          else -> {

            if (downloadPathology != null) {
              downloadPathology!!.name = etScenarioName.text.toString()
              ESApplication.updateSavedMap(downloadPathology!!)
              ESApplication.saveScenarios(this@ScenarioSaveDialog.context)

            } else {
              tempVS.pathology.name = etScenarioName.text.toString()
              ESApplication.updateSavedMap(tempVS.pathology, tempVS)
              ESApplication.saveScenarios(this@ScenarioSaveDialog.context)
            }

            knownScenarioNames = getScenarioNames()
            callback?.reloadDropdown(downloadPathology)
          }
        }

        dialog.dismiss()
      }

      bCancel.setOnClickListener { dialog.dismiss() }

      dialog.setContentView(dialogView.root)
    }
  }

  fun openDialog(downloadPathology: DownloadPathology) {

    this.downloadPathology = downloadPathology
    tempVS = Gson().fromJson(downloadPathology.vs, VitalSigns::class.java)

    dialogView.twHeader.text = context.getString(R.string.rename_scenario)
    dialogView.twHeaderDesc.text =
      context.getString(R.string.doublicate_name_give_other, downloadPathology.name)
    dialogView.etScenarioName.setText(downloadPathology.name)

    dialog.show()
  }

  fun openDialog(vitalSigns: VitalSigns) {
    tempVS = vitalSigns.deepCopy()

    dialog.show()
  }

}