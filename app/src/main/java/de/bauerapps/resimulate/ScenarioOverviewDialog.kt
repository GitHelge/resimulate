package de.bauerapps.resimulate

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import de.bauerapps.resimulate.adapters.ScenarioOverviewAdapter
import de.bauerapps.resimulate.databinding.ScenarioOverviewDialogBinding
import de.bauerapps.resimulate.helper.ESApplication
import de.bauerapps.resimulate.helper.getScenarioArrayList
import de.bauerapps.resimulate.views.ESDialog
import kotlin.math.roundToInt

@SuppressLint("SetTextI18n", "InflateParams")
class ScenarioOverviewDialog(val context: AppCompatActivity) : ScenarioUploadDialog.Callback {

  private var dialog: ESDialog = ESDialog(context, R.style.NoAnimDialog)

  private var dialogView = ScenarioOverviewDialogBinding.inflate(context.layoutInflater)

  private var scenarioUploadDialog = ScenarioUploadDialog(this)
  private var adapter: ScenarioOverviewAdapter = ScenarioOverviewAdapter(
    scenarioUploadDialog, getScenarioArrayList()
  )

  private var layoutManager = LinearLayoutManager(context)

  init {

    dialogView.apply {

      rvScenarios.layoutManager = layoutManager
      rvScenarios.adapter = adapter

      bCheck.setOnClickListener {

        for (scenario in adapter.scenarios)
          ESApplication.updateActiveMap(scenario.name, scenario.isUsed)

        ESApplication.writeActiveMap()

        //TODO: Save the list
        dialog.dismiss()
      }

      bCancel.setOnClickListener { dialog.dismiss() }
    }

    dialog.setContentView(dialogView.root)

    scenarioUploadDialog.callback = this

  }

  fun openDialog() {
    val currentScenarioList = getScenarioArrayList()
    if (adapter.scenarios != currentScenarioList) {
      adapter.scenarios = currentScenarioList
      adapter.downloadList = ESApplication.getDownloads()
      adapter.notifyDataSetChanged()
    }

    if (!ESApplication.getBoolean(R.bool.is600dp)) {
      val width = (context.resources.displayMetrics.widthPixels * 0.90).roundToInt()
      dialog.window?.setLayout(width, LinearLayout.LayoutParams.MATCH_PARENT)
    }
    dialog.show()
  }

  override fun updateEntry(scenario: Pathology) {
    adapter.notifyItemChanged(adapter.scenarios.indexOf(scenario))
  }
}