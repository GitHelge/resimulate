package de.bauerapps.resimulate

import android.Manifest
import android.content.pm.ActivityInfo
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.view.View
import android.view.WindowManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import com.google.android.material.snackbar.Snackbar
import androidx.recyclerview.widget.LinearLayoutManager
import android.util.Log
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import cn.pedant.SweetAlert.SweetAlertDialog
import de.bauerapps.resimulate.adapters.NCDevicesAdapter
import de.bauerapps.resimulate.helper.FullscreenHelper
import de.bauerapps.resimulate.helper.SimpleTextWatcher
import de.bauerapps.resimulate.helper.UserType
import de.bauerapps.resimulate.threads.NCS
import de.bauerapps.resimulate.threads.NCSEndpointState
import de.bauerapps.resimulate.threads.NCSState
import de.bauerapps.resimulate.threads.NearbyConnectionService
import com.beardedhen.androidbootstrap.api.defaults.DefaultBootstrapBrand
import com.permissionx.guolindev.PermissionX
import de.bauerapps.resimulate.databinding.ActivityStartupBinding
import de.bauerapps.resimulate.helper.ESApplication


class StartupActivity : AppCompatActivity(),
  NearbyConnectionService.NCSEndpointCallback, NearbyConnectionService.NCSCallback,
  View.OnClickListener {

  companion object {
    const val TAG = "StartupActivity"
  }

  private var ncDevicesAdapter: NCDevicesAdapter? = null
  private var ncService: NearbyConnectionService? = null

  private var bound: Boolean? = null
  private var isActiveActivity: Boolean? = null
  //private var needsReconnection: Boolean? = null
  private var fullscreenHelper: FullscreenHelper? = null
  private var scenarioOverviewDialog: ScenarioOverviewDialog? = null
  private var scenarioDownloadDialog: ScenarioDownloadDialog? = null
  
  private lateinit var binding: ActivityStartupBinding

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    binding = ActivityStartupBinding.inflate(layoutInflater)
    setContentView(binding.root)

    // Sets interface to portrait or landscape
    if (resources.getBoolean(R.bool.forceLandscape)) {
      requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      window.insetsController?.hide(WindowInsets.Type.statusBars())
    } else {
      window.setFlags(
        WindowManager.LayoutParams.FLAG_FULLSCREEN,
        WindowManager.LayoutParams.FLAG_FULLSCREEN
      )
    }
    
    
    binding.bTraineeChoice.setOnCheckedChangedListener { _, isChecked ->
      if (isChecked) traineeChosen()
    }

    binding.bTrainerChoice.setOnCheckedChangedListener { _, isChecked ->
      if (isChecked) trainerChosen()
    }

    binding.bTraineeLogin.setOnClickListener(this)
    /*b_trainee_search.setOnClickListener(this)
    b_trainer_search.setOnClickListener(this)*/
    binding.bRestart.setOnClickListener(this)
    binding.bSingleMode.setOnClickListener(this)
    binding.bScenarioDesigner.setOnClickListener(this)
    binding.bScenarioOverview.setOnClickListener(this)
    binding.bScenarios.setOnClickListener(this)
    binding.bScenarioGetMore.setOnClickListener(this)

    binding.twLocationDescription.text = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      getString(R.string.fine_location_description)
    } else {
      getString(R.string.coarse_location_description)
    }

    binding.etTraineeName.setOnEditorActionListener { view, _, event ->
      if (event != null &&
        event.keyCode == EditorInfo.IME_ACTION_DONE &&
        view.text.isNotEmpty()
      ) {
        binding.bTraineeLogin.callOnClick()
        return@setOnEditorActionListener true
      }
      return@setOnEditorActionListener false
    }

    fullscreenHelper = FullscreenHelper(binding.CLWhole)
    scenarioOverviewDialog = ScenarioOverviewDialog(this)
    scenarioDownloadDialog = ScenarioDownloadDialog(this)
  }

  private var hasWatcher = false
  private var traineeNameListener = object : SimpleTextWatcher {
    override fun afterTextChanged(text: String) {
      if (text.isNotEmpty() && !binding.bTraineeLogin.isEnabled) {
        binding.bTraineeLogin.isEnabled = true
        binding.bTraineeLogin.bootstrapBrand = DefaultBootstrapBrand.SUCCESS
      } else if (text.isEmpty() && binding.bTraineeLogin.isEnabled) {
        binding.bTraineeLogin.isEnabled = false
        binding.bTraineeLogin.bootstrapBrand = DefaultBootstrapBrand.REGULAR
      }
    }
  }

  override fun onClick(view: View?) {
    if (view == null) return
    when (view) {
      binding.bTraineeLogin -> {
        if (ncService == null) return
        if (ncService!!.needPermissions()) {
          val desc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getString(R.string.fine_location_description)
          } else {
            getString(R.string.coarse_location_description)
          }
          Snackbar.make(view, desc, Snackbar.LENGTH_LONG).show()
        } else {
          require(binding.etTraineeName.text != null) { "Trainee Name is null" }
          ncService?.updateUser(UserType.Trainee, binding.etTraineeName.text.toString())

          if (NCS.ncsState == NCSState.IDLE)
            ncService?.updateState(NCSState.SEARCHING)

          binding.bRestart.visibility = View.VISIBLE
          binding.llTraineeWaitingForTrainer.visibility = View.VISIBLE
        }
        if (currentFocus != null) {
          val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
          imm.hideSoftInputFromWindow(currentFocus!!.windowToken, 0)
          fullscreenHelper?.hide()
        }
      }
      /*b_trainee_search -> {
          when(NCS.ncsState) {
              NCSState.SEARCHING -> ncService?.updateState(NCSState.IDLE)
              NCSState.IDLE -> ncService?.updateState(NCSState.SEARCHING)
              else -> return
          }
      }
      b_trainer_search -> {
          when(NCS.ncsState) {
              NCSState.SEARCHING -> ncService?.updateState(NCSState.IDLE)
              NCSState.IDLE -> ncService?.updateState(NCSState.SEARCHING)
              else -> return
          }
      }*/
      binding.bGivePermission -> askPermission(view)
      binding.bRestart -> {
        restart()
        binding.bRestart.visibility = View.GONE
      }
      binding.bSingleMode -> {
        restart()
        val intent = Intent(this, SingleModeActivity::class.java)
        startActivity(intent)
      }
      binding.bScenarioDesigner -> {
        restart()
        val intent = Intent(this, ScenarioDesignActivity::class.java)
        startActivity(intent)
      }
      binding.bScenarios -> {
        val isVisible = binding.llScenarioViewToggle.visibility == View.VISIBLE
        binding.llScenarioViewToggle.visibility = if (isVisible) View.INVISIBLE else View.VISIBLE
      }
      binding.bScenarioOverview -> scenarioOverviewDialog?.openDialog()
      binding.bScenarioGetMore -> scenarioDownloadDialog?.openDialog()
    }
  }

  private fun restart() {
    // When a restart is required, the search is restarted
    ncService?.updateState(NCSState.IDLE)
    ncService?.updateEndpointState(NCSEndpointState.UNKNOWN)

    ncDevicesAdapter?.removeAllItems()

    // Reset Radio Buttons
    binding.bTraineeChoice.isSelected = false
    binding.bTrainerChoice.isSelected = false

    // Reset UI:
    binding.llTraineeNameLogin.visibility = View.GONE
    binding.llTrainerDevices.visibility = View.GONE
    binding.llTraineeWaitingForTrainer.visibility = View.GONE
    binding.llTrainerSearching.visibility = View.GONE
  }

  private fun traineeChosen() {
    ncService?.updateUser(type = UserType.Trainee)

    if (binding.llTrainerDevices.visibility == View.VISIBLE) {

      Handler(Looper.getMainLooper()).postDelayed({
        binding.llTrainerDevices.visibility = View.GONE
        binding.llTraineeNameLogin.visibility = View.VISIBLE
      }, 100)

    } else {
      binding.llTraineeNameLogin.visibility = View.VISIBLE
    }

    if (!hasWatcher) {
      binding.etTraineeName.addTextChangedListener(traineeNameListener)
      hasWatcher = true
    }

    if (NCS.user.name != "Trainer")
      binding.etTraineeName.setText(NCS.user.name)

    if (ncService == null) return
    if (ncService!!.needPermissions()) {
      binding.llPermissionRequest.visibility = View.VISIBLE
    }
  }

  private fun trainerChosen() {
    ncService?.updateUser(type = UserType.Trainer, name = "Trainer")
    binding.bRestart.visibility = View.VISIBLE

    if (binding.llTraineeNameLogin.visibility == View.VISIBLE) {

      if (binding.etTraineeName.isFocused) {
        // Hide Softkeyboard
        if (currentFocus != null) {
          val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
          imm.hideSoftInputFromWindow(currentFocus!!.windowToken, 0)
          fullscreenHelper?.hide()
        }

      }

      Handler(Looper.getMainLooper()).postDelayed({
        binding.llTraineeNameLogin.visibility = View.GONE
        binding.llTrainerDevices.visibility = View.VISIBLE
        binding.llTraineeWaitingForTrainer.visibility = View.GONE


      }, 100)
    } else {
      binding.llTrainerDevices.visibility = View.VISIBLE
    }

    if (ncService == null) return
    if (ncService!!.needPermissions()) {
      binding.llPermissionRequest.visibility = View.VISIBLE
    } else {
      binding.llTrainerSearching.visibility = View.VISIBLE
      fillNearbyConnectionsList()
    }
  }

  private var mConnection = object : ServiceConnection {
    override fun onServiceConnected(className: ComponentName, service: IBinder) {
      Log.i(TAG, "NCS connected")
      ncService = (service as NearbyConnectionService.LocalBinder).ncService
      ncService!!.ncsEndpointCallback = this@StartupActivity
      ncService!!.ncsCallback = this@StartupActivity

      if (ncService!!.needPermissions())
        binding.bGivePermission.setOnClickListener(this@StartupActivity)

      bound = true
    }

    override fun onServiceDisconnected(className: ComponentName) {
      Log.i(TAG, "NCS disconnected")
      ncService = null
      bound = false
    }
  }

  override fun onEndpointStateUpdate(state: NCSEndpointState) {

    when (state) {
      NCSEndpointState.FOUND -> {
        val found =
          NCS.discoveredEndpoints.values.filter { it.endpointState == NCSEndpointState.FOUND }
        found.forEach { ncDevicesAdapter?.foundConnection(it.name, it.id) }
      }
      NCSEndpointState.LOST -> {
        val lost =
          NCS.discoveredEndpoints.values.filter { it.endpointState == NCSEndpointState.LOST }
        lost.forEach { ncDevicesAdapter?.lostConnection(it.id) }
      }
      NCSEndpointState.CONNECTED -> {
        val connected =
          NCS.discoveredEndpoints.values.find { it.endpointState == NCSEndpointState.CONNECTED }
            ?: return
        ncDevicesAdapter?.setConnected(connected.id)

        if (NCS.user.type == UserType.Trainee) {
          val intent = Intent(this, TraineeActivity::class.java)
          isActiveActivity = false
          startActivity(intent)
        } else {
          //ll_trainer_searching.visibility = View.GONE
          val intent = Intent(this, TrainerActivity::class.java)
          isActiveActivity = false
          startActivity(intent)
        }
      }
      NCSEndpointState.DISCONNECTED -> {
        val disconnected =
          NCS.discoveredEndpoints.values.filter { it.endpointState == NCSEndpointState.DISCONNECTED }
        disconnected.forEach { ncDevicesAdapter?.setDisconnected(it.id) }
      }
      NCSEndpointState.WAITING -> {
      }
      NCSEndpointState.UNKNOWN -> {
      }
    }

    if (NCS.chosenEndpoint != null) {
      if (NCS.user.type == UserType.Trainer)
        binding.twTrainerSearch.text = NCSEndpointState.getDesc(NCS.chosenEndpoint)
      else {
        binding.twTraineeSearch.text = NCSEndpointState.getDesc(NCS.chosenEndpoint)
      }
    }

  }

  override fun onStateUpdate() {

    if (NCS.ncsState == NCSState.CONNECTED || NCS.ncsState == NCSState.DISCONNECTED) {
      if (NCS.user.type == UserType.Trainee) {
        //b_trainee_search.visibility = View.INVISIBLE
        binding.pbTraineeSearching.visibility = View.INVISIBLE
      } else {
        //b_trainer_search.visibility = View.INVISIBLE
        binding.pbTrainerSearching.visibility = View.INVISIBLE
      }
      return
    }


    val isActive = NCS.ncsState == NCSState.SEARCHING

    if (NCS.user.type == UserType.Trainee) {
      /*b_trainee_search.visibility = if (isActive) View.VISIBLE else View.INVISIBLE
      if (isActive)
          Handler().postDelayed( {b_trainee_search.visibility = View.VISIBLE}, 2000)

      b_trainee_search.setFontAwesomeIcon(if (isActive) FontAwesome.FA_TIMES else FontAwesome.FA_REPEAT)*/

      binding.pbTraineeSearching.visibility = if (isActive) View.VISIBLE else View.INVISIBLE
      binding.twTraineeSearch.text = if (isActive)
        getString(R.string.searching_for_trainer)
      else
        getString(R.string.searching_stopped)

    } else {
      /*b_trainer_search.visibility = if (isActive) View.INVISIBLE else View.VISIBLE
      if (isActive)
          Handler().postDelayed( {b_trainer_search.visibility = View.VISIBLE}, 2000)

      b_trainer_search.setFontAwesomeIcon(if (isActive) FontAwesome.FA_TIMES else FontAwesome.FA_REPEAT)*/
      binding.pbTrainerSearching.visibility = if (isActive) View.VISIBLE else View.INVISIBLE
      binding.twTrainerSearch.text = if (isActive)
        getString(R.string.searching_for_trainee)
      else
        getString(R.string.searching_stopped)
    }
  }

  override fun endpointChanged() {
    // TODO: Update UI
  }

  override fun reloadDeviceList() {
    ncDevicesAdapter?.notifyDataSetChanged()
  }

  override fun receive(payload: ByteArray) {}


  override fun error(title: String, description: String) {
    ncService?.updateState(NCSState.IDLE)
    ncService?.updateEndpointState(NCSEndpointState.UNKNOWN)
    ncDevicesAdapter?.removeAllItems()
    showRestartDeviceDialog(title, description)
  }

  private fun showRestartDeviceDialog(title: String, description: String) {

    SweetAlertDialog(this, SweetAlertDialog.ERROR_TYPE)
      .setTitleText(title)
      .setContentText(description)
      .setConfirmText(ESApplication.getString(R.string.ok))
      .setConfirmClickListener {
        it.dismissWithAnimation()
      }
      .show()
  }

  private fun fillNearbyConnectionsList() {
    NCS.discoveredEndpoints.clear()
    ncService?.updateState(NCSState.SEARCHING)

    if (ncDevicesAdapter == null) {
      ncDevicesAdapter = NCDevicesAdapter { pos, isConnected ->

        val id = ncDevicesAdapter!!.ncDevices[pos].id

        if (isConnected) {
          ncDevicesAdapter!!.setDisconnected(id)
          ncService?.updateState(NCSState.DISCONNECTED)
          ncService?.updateEndpointState(NCSEndpointState.DISCONNECTED)
          Log.i(TAG, "Disconnected from $id")

        } else {
          binding.pbTrainerSearching.visibility = View.VISIBLE
          Log.i(TAG, "Requested Connection to Id: $id")
          ncService?.requestConnection(id)
        }
      }
    } else {
      ncDevicesAdapter?.removeAllItems()
    }
    binding.rwNearbyDevices.layoutManager = LinearLayoutManager(this)
    binding.rwNearbyDevices.adapter = ncDevicesAdapter
  }


  override fun onStart() {
    super.onStart()

    isActiveActivity = true
    bindService(
      Intent(this, NearbyConnectionService::class.java), mConnection,
      Context.BIND_AUTO_CREATE
    )

    restart()

    /*if (needsReconnection == true) {
        ncService?.updateState(NCSState.SEARCHING)
    }

    if (NCS.ncsState == NCSState.DISCONNECTED) {
        ncDevicesAdapter?.setDisconnected()
    }

    if (NCS.chosenEndpoint?.endpointState == NCSEndpointState.LOST)
        ncDevicesAdapter?.lostConnection(NCS.chosenEndpoint!!.id)*/
  }

  override fun onStop() {
    super.onStop()

    if (isActiveActivity == true) {
      ncDevicesAdapter?.setDisconnected()
      //needsReconnection = true
      ncService?.updateState(NCSState.IDLE)
      ncService?.updateEndpointState(NCSEndpointState.UNKNOWN)
    } else {
      //needsReconnection = false
    }

    if (bound == true) {
      unbindService(mConnection)
      Log.i(TAG, "Service unbound")
      bound = false
    }
  }

  private fun askPermission(view: View) {

    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      PermissionX.init(this)
        .permissions(Manifest.permission.NEARBY_WIFI_DEVICES, Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION)
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      PermissionX.init(this)
        .permissions(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_WIFI_STATE)

    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      PermissionX.init(this)
        .permissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_WIFI_STATE)

    } else {
      PermissionX.init(this)
        .permissions(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    permission
      .request { allGranted, granted, denied ->
        if (allGranted) {
          binding.llPermissionRequest.visibility = View.GONE

          if (NCS.user.type == UserType.Trainer) {
            binding.llTrainerSearching.visibility = View.VISIBLE
            fillNearbyConnectionsList()
          }
        } else {
          val desc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            getString(R.string.fine_location_description)
          } else {
            getString(R.string.coarse_location_description)
          }
          val snack = Snackbar.make(view, desc, Snackbar.LENGTH_LONG)
            .setAction("Settings") {
              val myAppSettings = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + this@StartupActivity.packageName)
              )
              myAppSettings.addCategory(Intent.CATEGORY_DEFAULT)
              myAppSettings.flags = Intent.FLAG_ACTIVITY_NEW_TASK
              this@StartupActivity.startActivity(myAppSettings)
            }
          val tw = snack.view.findViewById(com.google.android.material.R.id.snackbar_text) as TextView
          tw.maxLines = 3
          tw.maxWidth = 600
          snack.show()
        }
      }
  }

  override fun onPostCreate(savedInstanceState: Bundle?) {
    super.onPostCreate(savedInstanceState)

    fullscreenHelper?.hide()
  }

  override fun onPause() {
    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    super.onPause()
  }

  override fun onResume() {
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    fullscreenHelper?.hide()
    super.onResume()
  }

}
