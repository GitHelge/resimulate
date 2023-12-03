package de.bauerapps.resimulate

import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.*
import com.google.gson.Gson
import de.bauerapps.resimulate.adapters.ScenarioDownloadAdapter
import de.bauerapps.resimulate.databinding.ScenarioDownloadOverviewDialogBinding
import de.bauerapps.resimulate.helper.ESApplication
import de.bauerapps.resimulate.simulations.*
import de.bauerapps.resimulate.views.*
import kotlin.math.roundToInt

class ScenarioDownloadDialog(val context: AppCompatActivity) :
  ScenarioDownloadAdapter.Callback,
  ESSurfaceView.Callback, ScenarioSaveDialog.Callback {

  private var dialog: ESDialog = ESDialog(context, R.style.NoAnimDialog)

  private var dialogView =  ScenarioDownloadOverviewDialogBinding.inflate(context.layoutInflater)

  private var adapter: ScenarioDownloadAdapter

  private var scenarioSaveDialog: ScenarioSaveDialog = ScenarioSaveDialog(context)

  private var simConfig = SimConfig()
    set(value) {
      onUpdateSimConfig(value.deepCopy())
      field = value
    }

  private var ecgCalculation = ECGCalculation(simConfig, CPRCalculation(CPRType.ECG))
  private var oxyCalculation =
    OxyCalculation(simConfig, CPRCalculation(CPRType.SPO2), ecgCalculation)
  private var capCalculation = CAPCalculation(simConfig, CPRCalculation(CPRType.ETCO2))

  val downloadPathologies = mutableMapOf<String, DownloadPathology>()

  private var layoutManager = LinearLayoutManager(context)
  private var scenarioCount = 20

  init {
    scenarioSaveDialog.callback = this
    adapter = ScenarioDownloadAdapter(context, ArrayList(downloadPathologies.values))
    adapter.callback = this

    dialogView.apply {
      rvScenarioDownload.layoutManager = layoutManager
      rvScenarioDownload.adapter = adapter
      rvScenarioDownload.setHasFixedSize(true)
      //rvScenarioDownload.addOnScrollListener(scrollListener)

      rvScenarioDownload.addOnScrollListener(object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
          super.onScrolled(recyclerView, dx, dy)
          val id = layoutManager.findLastCompletelyVisibleItemPosition()
          if (id >= scenarioCount - 10) {
            addNewScenario(adapter.getLastScenarioId())
          }
        }
      })

      bCheck.setOnClickListener {
        // TODO: Necessary?
        setVitalSignView(ESViewType.ECG, false)
        setVitalSignView(ESViewType.PLETH, false)
        setVitalSignView(ESViewType.CAP, false)
        dialog.dismiss()
      }
    }

    dialog.setContentView(dialogView.root)

    simConfig.simState.changeDuration = 1

    initGraphs()
  }

  fun addNewScenario(id: String) {

    val ref = FirebaseDatabase.getInstance().reference
      .child("data")
      .child("scenarios")
      //.orderByChild("date")
      .orderByKey()
      .startAt(id)
      .limitToFirst(20)

    ref.addListenerForSingleValueEvent(object : ValueEventListener {
      override fun onCancelled(p0: DatabaseError) {}

      override fun onDataChange(dataSnapshot: DataSnapshot) {
        if (dataSnapshot.hasChildren()) {
          val iter = dataSnapshot.children.iterator()
          while (iter.hasNext()) {
            val snap = iter.next()
            val scenario = snap.getValue(DownloadPathology::class.java)
            if (!downloadPathologies.contains(snap.key) && scenario != null) {
              downloadPathologies[scenario.id] = scenario
              adapter.pathologies.add(scenario)
              scenarioCount += 1
              adapter.notifyItemInserted(adapter.itemCount - 1)
            }

          }
        }
      }

    })
  }

  private fun addScenarios(mPosts: Int) {
    val ref = FirebaseDatabase.getInstance().reference
      .child("data")
      .child("scenarios")
      .orderByKey()
      //.orderByChild("date")
      .limitToFirst(mPosts)

    dialogView.twNoDownloadableScenarios.visibility = View.INVISIBLE
    dialogView.rvScenarioDownload.visibility = View.INVISIBLE
    dialogView.pwScenariosLoading.visibility = View.VISIBLE

    ref.addListenerForSingleValueEvent(object : ValueEventListener {
      override fun onCancelled(p0: DatabaseError) {}

      override fun onDataChange(dataSnapshot: DataSnapshot) {
        if (dataSnapshot.hasChildren()) {
          val iter = dataSnapshot.children.iterator()
          while (iter.hasNext()) {
            val snap = iter.next()
            val scenario = snap.getValue(DownloadPathology::class.java)
            if (scenario != null) {
              if (!ESApplication.saveMap.containsKey(scenario.name)) {
                downloadPathologies[scenario.id] = scenario
                adapter.pathologies.add(scenario)
                adapter.notifyItemInserted(adapter.itemCount - 1)

                dialogView.rvScenarioDownload.visibility = View.VISIBLE
                dialogView.pwScenariosLoading.visibility = View.INVISIBLE
              }
            }
          }
        }

        if (downloadPathologies.values.isEmpty()) {
          // Already downloaded all available Scenarios
          dialogView.twNoDownloadableScenarios.visibility = View.VISIBLE
          dialogView.pwScenariosLoading.visibility = View.INVISIBLE
        } else {
          dialogView.twNoDownloadableScenarios.visibility = View.INVISIBLE
        }
      }

    })
  }

  private fun onUpdateSimConfig(newValue: SimConfig) {

    ecgCalculation.pendingSimConfig = newValue
    capCalculation.simConfig = newValue
    oxyCalculation.simConfig = newValue
  }

  private fun initGraphs() {

    dialogView.apply {
      vsgEcg.callback = this@ScenarioDownloadDialog
      vsgOxy.callback = this@ScenarioDownloadDialog
      vsgCap.callback = this@ScenarioDownloadDialog

      ecgCalculation.currentHR = simConfig.vitalSigns.ecg.hr
      oxyCalculation.currentNIBP =
        NIBP(simConfig.vitalSigns.nibp.sys, simConfig.vitalSigns.nibp.dia)

      vsgEcg.setup(ESViewType.ECG, ESColor.HR, 2.0, -2.0, true)
      vsgEcg.setZOrderOnTop(true)
      vsgOxy.setup(ESViewType.PLETH, ESColor.SPO2, 150.0, 50.0, true)
      vsgOxy.setZOrderOnTop(true)
      vsgCap.setup(ESViewType.CAP, ESColor.ETCO2, 50.0, -5.0, true)
      vsgCap.setZOrderOnTop(true)
    }
  }

  private fun setVitalSignView(type: ESViewType, isChecked: Boolean) {
    dialogView.apply {
      when (type) {
        ESViewType.ECG -> {
          vsgEcg.isToggledOn = isChecked
          if (isChecked) vsgEcg.restart() else vsgEcg.clearStop()
        }
        ESViewType.PLETH -> {
          vsgOxy.isToggledOn = isChecked
          if (isChecked) vsgOxy.restart() else vsgOxy.clearStop()
        }
        ESViewType.CAP -> {
          vsgCap.isToggledOn = isChecked
          if (isChecked) vsgCap.restart() else vsgCap.clearStop()
        }
      }
    }
  }

  override fun pullValue(type: ESViewType, timestep: Double): Double {
    return when (type) {
      ESViewType.ECG -> ecgCalculation.calc(timestep)
      ESViewType.PLETH -> oxyCalculation.calc(timestep)
      ESViewType.CAP -> capCalculation.calc(timestep)
    }
  }

  override fun reloadDropdown(downloadPathology: DownloadPathology?) {
    downloadPathologies.remove(downloadPathology?.id)

    val index = adapter.pathologies.indexOf(downloadPathology)
    adapter.pathologies.remove(downloadPathology)

    adapter.notifyItemRemoved(index)
    adapter.notifyItemRangeChanged(index, adapter.pathologies.size)

    if (adapter.pathologies.isEmpty()) {
      dialogView.twNoDownloadableScenarios.visibility = View.VISIBLE
    }
  }

  override fun requestSync() {
    dialogView.vsgOxy.performECGSync()
  }

  override fun onDownloadClick(item: DownloadPathology) {
    // TODO: Download
    // Check if Name already exists
    // Make sure that it cant be reuploaded

    if (ESApplication.saveMap.containsKey(item.name)) {
      // Name already exists:
      // Rename the saved file automatically
      scenarioSaveDialog.openDialog(item)
    } else {
      ESApplication.updateSavedMap(item)
      ESApplication.saveScenarios(context)
      reloadDropdown(item)
      setVitalSignView(ESViewType.ECG, false)
      setVitalSignView(ESViewType.PLETH, false)
      setVitalSignView(ESViewType.CAP, false)
    }

  }

  override fun onClick(pathology: DownloadPathology, isActive: Boolean) {

    val vs = Gson().fromJson(pathology.vs, VitalSigns::class.java)

    if (!isActive) simConfig.vitalSigns = vs

    setVitalSignView(ESViewType.ECG, !isActive)
    setVitalSignView(ESViewType.PLETH, !isActive)
    setVitalSignView(ESViewType.CAP, !isActive)
  }

  /*private var prevButton: ESBootstrapButton? = null
  override fun onPreviewClick(btn: ESBootstrapButton?,  item: DownloadPathology) {
      // TODO: Start preview

      if (prevButton!=null && prevButton != btn)
          prevButton?.setActiveBackground(false)

      prevButton = btn

      val vs = Gson().fromJson(item.vs, VitalSigns::class.java)

      val isActive = btn?.isActive

      isActive?.let {
          if (!it) simConfig.vitalSigns = vs

          btn.setActiveBackground(!it)
          setVitalSignView(ESViewType.ECG, !it)
          setVitalSignView(ESViewType.PLETH, !it)
          setVitalSignView(ESViewType.CAP, !it)
      }
  }*/

  fun openDialog() {

    // Load data from Firebase.
    //loadData()
    if (downloadPathologies.isEmpty())
      addScenarios(scenarioCount)

    val factor = if (!ESApplication.getBoolean(R.bool.is600dp)) 0.9 else 0.7
    val width = (context.resources.displayMetrics.widthPixels * factor).roundToInt()
    dialog.window?.setLayout(width, LinearLayout.LayoutParams.MATCH_PARENT)

    dialog.show()
  }

}