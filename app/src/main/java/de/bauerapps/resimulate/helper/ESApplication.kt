package de.bauerapps.resimulate.helper

import android.app.Application
import android.content.res.Resources
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import cn.pedant.SweetAlert.SweetAlertDialog
import com.beardedhen.androidbootstrap.TypefaceProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import de.bauerapps.resimulate.*
import java.io.File
import java.lang.Exception

class ESApplication : Application() {

  companion object {

    const val TAG = "ESApplication"
    const val AUTHOR_ID = "id"
    const val AUTHOR_NAME = "name"

    var esFilesDir: File? = null
    private lateinit var res: Resources

    //var saveMap = mutableMapOf<String, MutableMap<String, Any>>()
    var saveMap = mutableMapOf<String, DownloadPathology>()
      private set

    var activeMap = mutableMapOf<String, Boolean>()
      private set

    fun getString(id: Int): String {
      return res.getString(id)
    }

    fun getBoolean(id: Int): Boolean {
      return res.getBoolean(id)
    }

    fun deleteScenario(scenario: Pathology) {
      saveMap.remove(scenario.name)
      activeMap.remove(scenario.name)

      writeSaveMap()
      writeActiveMap()
    }

    fun updateSavedMap(scenario: Pathology, vitalSigns: VitalSigns) {

      if (!saveMap.containsKey(scenario.name))
        saveMap[scenario.name] = DownloadPathology()

      saveMap[scenario.name]?.apply {
        vs = Gson().toJson(vitalSigns)
        appVersion = BuildConfig.VERSION_CODE
        isUploaded = false
      }

      writeSaveMap()
      Log.i(TAG, "saveMap contains: $saveMap")
    }

    fun updateSavedMap(pathology: DownloadPathology) {

      saveMap[pathology.name] = pathology
    }

    fun getDownloads(): List<String> {

      val user = FirebaseAuth.getInstance().currentUser
      val storedDownloads = mutableListOf<String>()

      for (scenario in saveMap) {
        val author = scenario.value.author
        if (author[AUTHOR_ID] != user?.uid) {
          // Is download
          storedDownloads.add(scenario.value.name)
        }
      }

      Log.i(TAG, "Stored downloads found: $storedDownloads")
      return storedDownloads

    }

    private fun writeSaveMap() {
      val customScenarioFile = File(esFilesDir, "custom_scenarios.txt")
      try {
        customScenarioFile.writeText(Gson().toJson(saveMap))
      } catch (e: Exception) {
        Log.i(TAG, e.localizedMessage)
      }
    }

    fun saveScenarios(context: AppCompatActivity) {
      val customScenarioFile = File(esFilesDir, "custom_scenarios.txt")

      if (!customScenarioFile.exists()) customScenarioFile.createNewFile()

      try {
        customScenarioFile.writeText(Gson().toJson(saveMap))
        SweetAlertDialog(context, SweetAlertDialog.SUCCESS_TYPE)
          .setTitleText(getString(R.string.scenario_saved))
          .show()
      } catch (e: Exception) {
        SweetAlertDialog(context, SweetAlertDialog.ERROR_TYPE)
          .setTitleText(e.localizedMessage)
          .show()
      }
    }

    fun updateActiveMap(scenarioName: String, isActive: Boolean) {
      activeMap[scenarioName] = isActive
    }

    fun writeActiveMap() {
      val activeFile = File(esFilesDir, "scenario_active.txt")

      if (!activeFile.exists()) activeFile.createNewFile()

      try {
        activeFile.writeText(Gson().toJson(activeMap))
      } catch (e: Exception) {
        Log.i(TAG, e.localizedMessage)
      }

    }
  }

  override fun onCreate() {
    super.onCreate()
    res = resources
    esFilesDir = filesDir
    TypefaceProvider.registerDefaultIconSets()

    val customScenarioFile = File(esFilesDir, "custom_scenarios.txt")
    if (customScenarioFile.exists()) {
      if (customScenarioFile.readText().isNotEmpty()) {
        saveMap = Gson().fromJson(customScenarioFile.readText(),
          object : TypeToken<MutableMap<String, DownloadPathology>>() {}.type
        )
      }
    }

    val activeFile = File(esFilesDir, "scenario_active.txt")
    if (activeFile.exists()) {
      if (activeFile.readText().isNotEmpty()) {
        activeMap = Gson().fromJson(activeFile.readText(),
          object : TypeToken<MutableMap<String, Boolean>>() {}.type
        )
      }
    }

  }

}