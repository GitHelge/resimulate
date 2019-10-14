package de.bauerapps.resimulate

import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import cn.pedant.SweetAlert.SweetAlertDialog
//import cn.pedant.SweetAlert.SweetAlertDialog
import com.google.firebase.auth.FirebaseAuth
import de.bauerapps.resimulate.views.ESDialog
import kotlinx.android.synthetic.main.scenario_upload_dialog.view.*
import com.google.firebase.database.FirebaseDatabase
import com.google.gson.Gson
import de.bauerapps.resimulate.helper.ESApplication
import kotlin.math.roundToInt


class ScenarioUploadDialog(val scenarioOverviewDialog: ScenarioOverviewDialog) {

  interface Callback {
    fun updateEntry(scenario: Pathology)
  }

  private var dialog: ESDialog = ESDialog(scenarioOverviewDialog.context, R.style.NoAnimDialog)
  private lateinit var scenario: Pathology

  private var dialogView: View = LayoutInflater.from(scenarioOverviewDialog.context)
    .inflate(R.layout.scenario_upload_dialog, null)

  private var auth: FirebaseAuth? = null
  private var database: FirebaseDatabase? = null

  private var alert: SweetAlertDialog? = null

  var callback: Callback? = null

  init {

    dialogView.apply {

      b_check.setOnClickListener {

        if (et_scenario_name.text.isEmpty()) return@setOnClickListener
        if (b_with_name.isSelected && et_user_name.text.isEmpty()) return@setOnClickListener

        if (auth == null) auth = FirebaseAuth.getInstance()
        if (database == null) database = FirebaseDatabase.getInstance()
        alert = SweetAlertDialog(scenarioOverviewDialog.context, SweetAlertDialog.PROGRESS_TYPE)
        alert?.show()
        dialog.dismiss()

        if (auth?.currentUser != null) {
          startUpload()
        } else {
          auth?.signInAnonymously()
            ?.addOnCompleteListener(scenarioOverviewDialog.context) { task ->
              if (task.isSuccessful) {
                startUpload()
              } else {
                // If sign in fails, display a message to the user.
                alert?.setTitleText(ESApplication.getString(R.string.authentication_failed))
                  ?.changeAlertType(SweetAlertDialog.ERROR_TYPE)
                alert?.show()
              }
            }
        }

      }

      b_anonymous.setOnCheckedChangedListener { _, isChecked ->
        if (isChecked) {
          et_user_name.setText("")
          ll_uploader_name.visibility = View.GONE
        }
      }

      b_with_name.setOnCheckedChangedListener { _, isChecked ->
        if (isChecked) {
          ll_uploader_name.visibility = View.VISIBLE
        }
      }

      b_cancel.setOnClickListener { dialog.dismiss() }
    }

    dialog.setContentView(dialogView)
  }

  private fun startUpload() {
    val reference = database?.reference
    val path = reference?.child("data")?.child("scenarios")
    val key = path?.push()?.key

    key?.let {
      val downloadPathology = createUploadMap(key)
      path.child(key).setValue(downloadPathology)
        .addOnSuccessListener {
          alert?.setTitleText(ESApplication.getString(R.string.upload_successfull))
            ?.setContentText(ESApplication.getString(R.string.thanks_for_sharing))
            ?.changeAlertType(SweetAlertDialog.SUCCESS_TYPE)
          alert?.show()

          ESApplication.updateSavedMap(downloadPathology)

          callback?.updateEntry(scenario)
        }
        .addOnFailureListener {
          alert?.setTitleText(ESApplication.getString(R.string.upload_failed))
            ?.changeAlertType(SweetAlertDialog.ERROR_TYPE)
          alert?.show()
        }

    }
  }

  private fun createUploadMap(id: String): DownloadPathology {
    val user = auth?.currentUser

    val author = mutableMapOf(ESApplication.AUTHOR_ID to user?.uid)
    if (dialogView.b_with_name.isSelected)
      author[ESApplication.AUTHOR_NAME] = dialogView.et_user_name.text.toString()

    val appVersion = ESApplication.saveMap[scenario.name]?.appVersion ?: 0

    return DownloadPathology(
      id,
      dialogView.et_scenario_name.text.toString(),
      Gson().toJson(DVS.fromPathology(scenario)),
      appVersion, author, System.currentTimeMillis(), true
    )
  }

  fun openDialog(scenario: Pathology) {
    this.scenario = scenario
    if (!ESApplication.getBoolean(R.bool.is600dp)) {
      val width =
        (scenarioOverviewDialog.context.resources.displayMetrics.widthPixels * 0.70).roundToInt()
      dialog.window?.setLayout(width, LinearLayout.LayoutParams.WRAP_CONTENT)
    }

    dialogView.et_scenario_name.setText(scenario.name)
    dialog.show()
  }
}