package de.bauerapps.resimulate.adapters

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import cn.pedant.SweetAlert.SweetAlertDialog
import de.bauerapps.resimulate.PType
import de.bauerapps.resimulate.Pathology
import de.bauerapps.resimulate.R
import de.bauerapps.resimulate.ScenarioUploadDialog
import de.bauerapps.resimulate.helper.ESApplication
import de.bauerapps.resimulate.helper.inflate
import de.bauerapps.resimulate.views.ESBootstrapButton
import de.bauerapps.resimulate.views.ESTextView
import kotlinx.android.synthetic.main.scenario_overview_list_item.view.*

class ScenarioOverviewAdapter(
  val dialog: ScenarioUploadDialog?,
  var scenarios: ArrayList<Pathology>
) :
  RecyclerView.Adapter<ScenarioOverviewAdapter.ScenarioEditViewHolder>() {

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScenarioEditViewHolder {
    val inflatedView = parent.inflate(R.layout.scenario_overview_list_item, false)
    return ScenarioEditViewHolder(inflatedView)
  }

  override fun getItemCount(): Int {
    return scenarios.size
  }

  override fun onBindViewHolder(holder: ScenarioEditViewHolder, position: Int) {
    holder.bind(scenarios[position])
  }

  private var noEditList = PType.values().map { it.pname }
  var downloadList = ESApplication.getDownloads()


  private fun deleteScenario(scenario: Pathology) {
    val index = scenarios.indexOf(scenario)
    scenarios.remove(scenario)
    notifyItemRemoved(index)
    notifyItemRangeChanged(index, scenarios.size)
    ESApplication.deleteScenario(scenario)
  }

  inner class ScenarioEditViewHolder(v: View) : RecyclerView.ViewHolder(v) {

    private var twScenarioName: ESTextView? = null
    private var bDelete: ESBootstrapButton? = null
    private var bUse: ESBootstrapButton? = null
    private var bShare: ESBootstrapButton? = null

    init {
      twScenarioName = v.tw_scenario_name
      bDelete = v.b_delete_scenario
      bUse = v.b_use_scenario
      bShare = v.b_share_scenario
    }

    fun bind(scenario: Pathology) {
      this.twScenarioName?.text = scenario.name

      if (scenario.name == ESApplication.getString(R.string.sinus_rhythm)) {
        this.bUse?.visibility = View.INVISIBLE
        this.bDelete?.visibility = View.GONE
        this.bShare?.visibility = View.GONE
        return
      } else {
        this.bUse?.visibility = View.VISIBLE
        this.bDelete?.visibility = View.VISIBLE
        this.bShare?.visibility = View.VISIBLE
      }

      this.bUse?.setActiveBackground(scenario.isUsed)

      this.bUse?.setOnClickListener {
        val isActive = this.bUse!!.isActive
        scenario.isUsed = !isActive
        this.bUse!!.setActiveBackground(!isActive)
      }

      if (noEditList.contains(scenario.name)) {
        this.bDelete?.visibility = View.GONE
        this.bShare?.visibility = View.GONE
      } else {
        this.bDelete?.visibility = View.VISIBLE

        this.bShare?.visibility = if (downloadList.contains(scenario.name))
          View.GONE
        else
          View.VISIBLE


        this.bDelete?.setOnClickListener {
          SweetAlertDialog(dialog?.scenarioOverviewDialog?.context, SweetAlertDialog.NORMAL_TYPE)
            .setTitleText(ESApplication.getString(R.string.delete_scenario_header))
            .setContentText(ESApplication.getString(R.string.delete_scenario_desc))
            .setConfirmText(ESApplication.getString(R.string.yes))
            .setCancelText(ESApplication.getString(R.string.no))
            .setConfirmClickListener {
              it.dismissWithAnimation()
              deleteScenario(scenario)
            }
            .show()
        }

        if (ESApplication.saveMap[scenario.name]?.isUploaded == true)
          this.bShare?.setActiveBackground(true)


        this.bShare?.setOnClickListener {

          if (ESApplication.saveMap[scenario.name]?.isUploaded == true) {
            SweetAlertDialog(dialog?.scenarioOverviewDialog?.context, SweetAlertDialog.NORMAL_TYPE)
              .setTitleText(ESApplication.getString(R.string.already_uploaded))
              .setContentText(ESApplication.getString(R.string.already_shared_desc))
              .show()
          } else {
            dialog?.openDialog(scenario)
          }
        }
      }

    }

  }
}
