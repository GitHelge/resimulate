package de.bauerapps.resimulate

import android.Manifest
import android.content.pm.ActivityInfo
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.view.View
import android.view.WindowManager
import com.karumi.dexter.Dexter
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
import android.provider.Settings
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import android.util.Log
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
import com.beardedhen.androidbootstrap.font.FontAwesome
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.single.PermissionListener
import de.bauerapps.resimulate.helper.ESApplication
import kotlinx.android.synthetic.main.activity_startup.*


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

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    setContentView(R.layout.activity_startup)

    // Sets interface to portrait or landscape
    if (resources.getBoolean(R.bool.forceLandscape)) {
      requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    }

    window?.setFlags(
      WindowManager.LayoutParams.FLAG_FULLSCREEN,
      WindowManager.LayoutParams.FLAG_FULLSCREEN
    )

    b_trainee_choice.setOnCheckedChangedListener { _, isChecked ->
      if (isChecked) traineeChosen()
    }

    b_trainer_choice.setOnCheckedChangedListener { _, isChecked ->
      if (isChecked) trainerChosen()
    }

    b_trainee_login.setOnClickListener(this)
    /*b_trainee_search.setOnClickListener(this)
    b_trainer_search.setOnClickListener(this)*/
    b_restart.setOnClickListener(this)
    b_single_mode.setOnClickListener(this)
    b_scenario_designer.setOnClickListener(this)
    b_scenario_overview.setOnClickListener(this)
    b_scenarios.setOnClickListener(this)
    b_scenario_get_more.setOnClickListener(this)

    et_trainee_name.setOnEditorActionListener { view, _, event ->
      if (event != null &&
        event.keyCode == EditorInfo.IME_ACTION_DONE &&
        view.text.isNotEmpty()
      ) {
        b_trainee_login.callOnClick()
        return@setOnEditorActionListener true
      }
      return@setOnEditorActionListener false
    }

    fullscreenHelper = FullscreenHelper(CL_whole)
    scenarioOverviewDialog = ScenarioOverviewDialog(this)
    scenarioDownloadDialog = ScenarioDownloadDialog(this)
  }

  private var hasWatcher = false
  private var traineeNameListener = object : SimpleTextWatcher {
    override fun afterTextChanged(text: String) {
      if (text.isNotEmpty() && !b_trainee_login.isEnabled) {
        b_trainee_login.isEnabled = true
        b_trainee_login.bootstrapBrand = DefaultBootstrapBrand.SUCCESS
      } else if (text.isEmpty() && b_trainee_login.isEnabled) {
        b_trainee_login.isEnabled = false
        b_trainee_login.bootstrapBrand = DefaultBootstrapBrand.REGULAR
      }
    }
  }

  override fun onClick(view: View?) {
    if (view == null) return
    when (view) {
      b_trainee_login -> {
        if (ncService == null) return
        if (ncService!!.needPermissions()) {
          Snackbar.make(view, getString(R.string.coarse_location_description), Snackbar.LENGTH_LONG)
            .show()
        } else {
          require(et_trainee_name.text != null) { "Trainee Name is null" }
          ncService?.updateUser(UserType.Trainee, et_trainee_name.text.toString())

          if (NCS.ncsState == NCSState.IDLE)
            ncService?.updateState(NCSState.SEARCHING)

          b_restart.visibility = View.VISIBLE
          ll_trainee_waiting_for_trainer.visibility = View.VISIBLE
        }
        if (currentFocus != null) {
          val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
          imm.hideSoftInputFromWindow(currentFocus!!.windowToken, 0)
          fullscreenHelper?.delayedHide(0)
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
      b_give_permission -> askPermission(view)
      b_restart -> {
        restart()
        b_restart.visibility = View.GONE
      }
      b_single_mode -> {
        restart()
        val intent = Intent(this, SingleModeActivity::class.java)
        startActivity(intent)
      }
      b_scenario_designer -> {
        restart()
        val intent = Intent(this, ScenarioDesignActivity::class.java)
        startActivity(intent)
      }
      b_scenarios -> {
        val isVisible = ll_scenario_view_toggle.visibility == View.VISIBLE
        ll_scenario_view_toggle.visibility = if (isVisible) View.INVISIBLE else View.VISIBLE
      }
      b_scenario_overview -> scenarioOverviewDialog?.openDialog()
      b_scenario_get_more -> scenarioDownloadDialog?.openDialog()
    }
  }

  private fun restart() {
    // When a restart is required, the search is restarted
    ncService?.updateState(NCSState.IDLE)
    ncService?.updateEndpointState(NCSEndpointState.UNKNOWN)

    ncDevicesAdapter?.removeAllItems()

    // Reset Radio Buttons
    b_trainee_choice.isSelected = false
    b_trainer_choice.isSelected = false

    // Reset UI:
    ll_trainee_name_login.visibility = View.GONE
    ll_trainer_devices.visibility = View.GONE
    ll_trainee_waiting_for_trainer.visibility = View.GONE
    ll_trainer_searching.visibility = View.GONE
  }

  private fun traineeChosen() {
    ncService?.updateUser(type = UserType.Trainee)

    if (ll_trainer_devices.visibility == View.VISIBLE) {

      Handler().postDelayed({
        ll_trainer_devices.visibility = View.GONE
        ll_trainee_name_login.visibility = View.VISIBLE
      }, 100)

    } else {
      ll_trainee_name_login.visibility = View.VISIBLE
    }

    if (!hasWatcher) {
      et_trainee_name.addTextChangedListener(traineeNameListener)
      hasWatcher = true
    }

    if (NCS.user.name != "Trainer")
      et_trainee_name.setText(NCS.user.name)

    if (ncService == null) return
    if (ncService!!.needPermissions()) {
      ll_permission_request.visibility = View.VISIBLE
    }
  }

  private fun trainerChosen() {
    ncService?.updateUser(type = UserType.Trainer, name = "Trainer")
    b_restart.visibility = View.VISIBLE

    if (ll_trainee_name_login.visibility == View.VISIBLE) {

      if (et_trainee_name.isFocused) {
        // Hide Softkeyboard
        if (currentFocus != null) {
          val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
          imm.hideSoftInputFromWindow(currentFocus!!.windowToken, 0)
          fullscreenHelper?.delayedHide(0)
        }

      }

      Handler().postDelayed({
        ll_trainee_name_login.visibility = View.GONE
        ll_trainer_devices.visibility = View.VISIBLE
        ll_trainee_waiting_for_trainer.visibility = View.GONE


      }, 100)
    } else {
      ll_trainer_devices.visibility = View.VISIBLE
    }

    if (ncService == null) return
    if (ncService!!.needPermissions()) {
      ll_permission_request.visibility = View.VISIBLE
    } else {
      ll_trainer_searching.visibility = View.VISIBLE
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
        b_give_permission.setOnClickListener(this@StartupActivity)

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
        tw_trainer_search.text = NCSEndpointState.getDesc(NCS.chosenEndpoint)
      else {
        tw_trainee_search.text = NCSEndpointState.getDesc(NCS.chosenEndpoint)
      }
    }

  }

  override fun onStateUpdate() {

    if (NCS.ncsState == NCSState.CONNECTED || NCS.ncsState == NCSState.DISCONNECTED) {
      if (NCS.user.type == UserType.Trainee) {
        //b_trainee_search.visibility = View.INVISIBLE
        pb_trainee_searching.visibility = View.INVISIBLE
      } else {
        //b_trainer_search.visibility = View.INVISIBLE
        pb_trainer_searching.visibility = View.INVISIBLE
      }
      return
    }


    val isActive = NCS.ncsState == NCSState.SEARCHING

    if (NCS.user.type == UserType.Trainee) {
      /*b_trainee_search.visibility = if (isActive) View.VISIBLE else View.INVISIBLE
      if (isActive)
          Handler().postDelayed( {b_trainee_search.visibility = View.VISIBLE}, 2000)

      b_trainee_search.setFontAwesomeIcon(if (isActive) FontAwesome.FA_TIMES else FontAwesome.FA_REPEAT)*/

      pb_trainee_searching.visibility = if (isActive) View.VISIBLE else View.INVISIBLE
      tw_trainee_search.text = if (isActive)
        getString(R.string.searching_for_trainer)
      else
        getString(R.string.searching_stopped)

    } else {
      /*b_trainer_search.visibility = if (isActive) View.INVISIBLE else View.VISIBLE
      if (isActive)
          Handler().postDelayed( {b_trainer_search.visibility = View.VISIBLE}, 2000)

      b_trainer_search.setFontAwesomeIcon(if (isActive) FontAwesome.FA_TIMES else FontAwesome.FA_REPEAT)*/
      pb_trainer_searching.visibility = if (isActive) View.VISIBLE else View.INVISIBLE
      tw_trainer_search.text = if (isActive)
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
          pb_trainer_searching.visibility = View.VISIBLE
          Log.i(TAG, "Requested Connection to Id: $id")
          ncService?.requestConnection(id)
        }
      }
    } else {
      ncDevicesAdapter?.removeAllItems()
    }
    rw_nearby_devices.layoutManager = LinearLayoutManager(this)
    rw_nearby_devices.adapter = ncDevicesAdapter
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

    val dialogPermissionListener = object : PermissionListener {
      override fun onPermissionGranted(response: PermissionGrantedResponse) {
        ll_permission_request.visibility = View.GONE

        if (NCS.user.type == UserType.Trainer) {
          ll_trainer_searching.visibility = View.VISIBLE
          fillNearbyConnectionsList()
        }
      }

      override fun onPermissionDenied(response: PermissionDeniedResponse) {
        val snack =
          Snackbar.make(view, getString(R.string.coarse_location_description), Snackbar.LENGTH_LONG)
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

      override fun onPermissionRationaleShouldBeShown(
        permission: PermissionRequest,
        token: PermissionToken
      ) {
        token.continuePermissionRequest()
      }
    }

    Dexter.withActivity(this)
      .withPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
      .withListener(dialogPermissionListener).check()

  }

  override fun onPostCreate(savedInstanceState: Bundle?) {
    super.onPostCreate(savedInstanceState)

    fullscreenHelper?.delayedHide(100)
  }

  override fun onPause() {
    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    super.onPause()
  }

  override fun onResume() {
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    fullscreenHelper?.delayedHide(100)
    super.onResume()
  }

}
