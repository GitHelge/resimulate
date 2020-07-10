package de.bauerapps.resimulate.threads

import android.Manifest
import android.annotation.TargetApi
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import de.bauerapps.resimulate.R
import de.bauerapps.resimulate.helper.ESApplication
import de.bauerapps.resimulate.helper.User
import de.bauerapps.resimulate.helper.UserType
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.google.gson.Gson
import java.io.File


typealias NCS = NearbyConnectionService

enum class NCSState {
  SEARCHING, IDLE, CONNECTED, DISCONNECTED
}

enum class NCSEndpointState {
  FOUND, LOST, CONNECTED, DISCONNECTED, WAITING, UNKNOWN;

  companion object {
    fun getDesc(connection: Endpoint?): String {
      if (connection == null) return ESApplication.getString(R.string.not_connected_to_any_endpoint)

      var desc = when (connection.endpointState) {
        FOUND -> ESApplication.getString(R.string.found_desc)
        LOST -> ESApplication.getString(R.string.lost_desc)
        CONNECTED -> ESApplication.getString(R.string.connected_to_desc)
        DISCONNECTED -> ESApplication.getString(R.string.disconnected_from_desc)
        WAITING -> ESApplication.getString(R.string.waiting_for_desc)
        UNKNOWN -> ESApplication.getString(R.string.unknown_endpoint_desc)
      }

      desc += " ${connection.name}"

      return desc
    }
  }
}

data class Endpoint(val id: String, var name: String, var endpointState: NCSEndpointState)

class NearbyConnectionService : Service() {

  companion object {
    const val TAG = "NCS"
    private val STRATEGY = Strategy.P2P_POINT_TO_POINT
    private var chosenEndpointId: String? = null

    var discoveredEndpoints = mutableMapOf<String, Endpoint>()
    val user = User(UserType.Trainer, "Trainer", null)

    val chosenEndpoint get() = discoveredEndpoints[chosenEndpointId]
    var lastFoundEndpoint = chosenEndpoint
      private set

    var ncsState: NCSState = NCSState.IDLE
  }

  interface NCSCallback {
    fun receive(payload: ByteArray)
    fun onStateUpdate() {}
    fun endpointChanged() {}
    fun reloadDeviceList() {}
  }

  interface NCSEndpointCallback {
    fun onEndpointStateUpdate(state: NCSEndpointState)
    fun error(title: String, description: String) {}
  }

  var ncsCallback: NCSCallback? = null
  var ncsEndpointCallback: NCSEndpointCallback? = null

  // Our handle to Nearby Connections
  private var connectionsClient: ConnectionsClient? = null

  private val mBinder = LocalBinder()

  inner class LocalBinder : Binder() {
    internal val ncService: NearbyConnectionService
      get() = this@NearbyConnectionService
  }

  override fun onBind(intent: Intent): IBinder? {
    Log.i(TAG, "onBind")
    return mBinder
  }

  fun updateUser(type: UserType? = null, name: String? = null, id: String? = null) {
    val user = user.copy()
    if (type != null) user.type = type
    if (name != null) user.name = name
    if (id != null) user.id = id
    updateUser(user)
  }

  private fun updateUser(user: User?) {
    if (user == null) return
    val needsConnectionUpdate = user.type != Companion.user.type

    Companion.user.type = user.type
    Companion.user.name = user.name
    Companion.user.id = user.id

    if (!needsConnectionUpdate) return

    Log.i(TAG, "Switching role to ${user.type.name}.")

    if (needPermissions()) return

    // Switch new UserType:
    when (ncsState) {
      NCSState.SEARCHING -> {
        // Restart search as other type:
        updateState(NCSState.IDLE)
        if (user.type == UserType.Trainer) updateState(NCSState.SEARCHING)
      }
      NCSState.CONNECTED,
      NCSState.DISCONNECTED -> {
        updateEndpointState(NCSEndpointState.UNKNOWN)
        if (user.type == UserType.Trainer) updateState(NCSState.SEARCHING)
      }
      else -> return
    }
  }

  fun updateState(new: NCSState) {
    if (ncsState == new) return

    when (new) {
      NCSState.SEARCHING -> {
        if (user.type == UserType.Trainee) startAdvertising() else startDiscovery()
        Log.i(TAG, "Search as ${user.type.name} started.")
      }
      NCSState.IDLE -> {
        //if (user.type == UserType.Trainee) stopAdvertising() else stopDiscovery()
        stopAdvertising()
        stopDiscovery()

        Log.i(TAG, "Search as ${user.type.name} stopped.")
      }
      NCSState.CONNECTED -> {
      }
      NCSState.DISCONNECTED -> {
      }
    }

    ncsState = new
    Log.i(TAG, "Changed to state: ${new.name}")
    ncsCallback?.onStateUpdate()
  }

  fun updateEndpointState(state: NCSEndpointState) {
    //if (state == discoveredEndpoints[chosenEndpointId]?.endpointState) return

    when (state) {
      NCSEndpointState.CONNECTED -> {
        if (user.type == UserType.Trainee) stopAdvertising() else stopDiscovery()
        Log.i(TAG, "Connection to ${chosenEndpoint?.name ?: "Trainer"} established.")
      }
      NCSEndpointState.DISCONNECTED -> {
        if (user.type == UserType.Trainee) startAdvertising() else startDiscovery()
        Log.i(TAG, "Disconnected from ${chosenEndpoint?.name ?: "Trainer"}. Reconnecting ...")
      }
      NCSEndpointState.UNKNOWN -> {
        stopAllEndpoints()
      }
      NCSEndpointState.FOUND -> {
      }
      NCSEndpointState.LOST -> {
      }
      NCSEndpointState.WAITING -> {
      }
    }

    discoveredEndpoints[chosenEndpointId]?.endpointState = state
    Log.i(
      TAG,
      "Changed ${discoveredEndpoints[chosenEndpointId]?.name} to EndpointState: ${state.name}"
    )
    ncsEndpointCallback?.onEndpointStateUpdate(state)
  }

  private fun stopAdvertising() {
    connectionsClient?.stopAdvertising()
  }

  private fun stopDiscovery() {
    connectionsClient?.stopDiscovery()
  }

  override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
    Log.i(TAG, "onStartCommand")
    return START_STICKY
  }

  // Callbacks for receiving payloads
  private val payloadCallback = object : PayloadCallback() {
    override fun onPayloadReceived(endpointId: String, payload: Payload) {
      payload.asBytes()?.let {
        ncsCallback?.receive(it)
      }
    }

    override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
      if (update.status == PayloadTransferUpdate.Status.SUCCESS) {
      }
    }
  }

  fun requestConnection(endpointId: String?) {
    if (endpointId == null) return
    chosenEndpointId = endpointId

    updateEndpointState(NCSEndpointState.WAITING)

    connectionsClient?.requestConnection(user.name, endpointId, connectionLifecycleCallback)
      ?.addOnFailureListener {
        Log.i(TAG, it.localizedMessage ?: "")

        if ((it.localizedMessage ?: "").contains(
            ConnectionsStatusCodes.getStatusCodeString(
              ConnectionsStatusCodes.STATUS_BLUETOOTH_ERROR
            )
          )
        ) {
          ncsEndpointCallback?.error(
            getString(R.string.bt_error_occured),
            getString(R.string.try_restart_desc)
          )
        }
      }
  }

  // Callbacks for finding other devices
  private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
    override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
      Log.i(TAG, "Some endpoint found: $endpointId, ${info.endpointName}")
      if (info.serviceId != packageName) {
        Log.i(TAG, "Device with unknown serviceId found: ${info.serviceId}")
        return
      }

      discoveredEndpoints[endpointId] =
        Endpoint(endpointId, info.endpointName, NCSEndpointState.FOUND)
      lastFoundEndpoint = discoveredEndpoints[endpointId]

      if (ncsState != NCSState.CONNECTED || chosenEndpointId == endpointId || chosenEndpointId == null) {
        updateEndpointState(NCSEndpointState.FOUND)
      } else {
        ncsCallback?.reloadDeviceList()
      }
    }

    override fun onEndpointLost(endpointId: String) {

      discoveredEndpoints[endpointId]?.endpointState = NCSEndpointState.LOST

      if (ncsState != NCSState.CONNECTED || chosenEndpointId == endpointId || chosenEndpointId == null) {
        updateEndpointState(NCSEndpointState.LOST)
      } else {
        ncsCallback?.reloadDeviceList()
      }
    }
  }

  // Callbacks for discoveredEndpoints to other devices
  private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
    override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
      connectionsClient?.acceptConnection(endpointId, payloadCallback)
        ?.addOnFailureListener { e -> Log.i(TAG, "acceptConnection() failed.", e) }

      if (discoveredEndpoints[endpointId] == null)
        discoveredEndpoints[endpointId] =
          Endpoint(endpointId, connectionInfo.endpointName, NCSEndpointState.WAITING)
      else
        discoveredEndpoints[endpointId]?.endpointState = NCSEndpointState.WAITING

      val endpointChanged = chosenEndpointId != null && chosenEndpointId != endpointId

      chosenEndpointId = endpointId

      //updateEndpointState(NCSEndpointState.WAITING)

      if (endpointChanged)
        ncsCallback?.endpointChanged()
    }

    override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
      if (result.status.isSuccess) {
        //if (user.type == UserType.Trainer)
        //    connectionsClient?.acceptConnection(endpointId, payloadCallback)
        updateState(NCSState.CONNECTED)
        updateEndpointState(NCSEndpointState.CONNECTED)
      } else {
        Log.i(TAG, "Not connected. Status: ${result.status.statusCode}")

        updateState(NCSState.IDLE)
        updateEndpointState(NCSEndpointState.UNKNOWN)
      }
    }

    override fun onDisconnected(endpointId: String) {
      discoveredEndpoints[endpointId]?.endpointState = NCSEndpointState.DISCONNECTED
      updateState(NCSState.DISCONNECTED)
      updateEndpointState(NCSEndpointState.DISCONNECTED)
    }
  }

  private fun initNCS() {
    if (connectionsClient == null) {
      connectionsClient = Nearby.getConnectionsClient(this)
      Log.i(TAG, "Initialized NCS")
    }
  }

  private fun stopAllEndpoints() {
    connectionsClient?.stopAllEndpoints()
    discoveredEndpoints.clear()
  }

  override fun onDestroy() {
    Log.i(TAG, "onDestroy")
    val userFile = File(filesDir, "ncs_user.txt")
    userFile.writeText(Gson().toJson(user))

    val endpointFile = File(filesDir, "ncs_endpoint.txt")
    if (chosenEndpoint == null || chosenEndpoint?.endpointState == NCSEndpointState.LOST) {
      val deleted = endpointFile.delete()
      Log.i(
        TAG,
        "Chosen Endpoint was lost, ncs_endpoint.txt ${if (!deleted) "not " else ""}deleted."
      )
    } else {
      endpointFile.writeText(Gson().toJson(chosenEndpoint))
    }

    Log.i(
      TAG,
      "Files created with user: ${userFile.readText()} ${if (endpointFile.exists()) " and endpoint: ${endpointFile.readText()}" else ""}."
    )
    super.onDestroy()
  }

  override fun onCreate() {
    super.onCreate()
    initNCS()

    val userFile = File(filesDir, "ncs_user.txt")
    if (!userFile.exists()) return
    if (userFile.readLines().isEmpty()) return

    val user = Gson().fromJson(userFile.readText(), User::class.java)
    Log.i(TAG, "onCreate user name: ${user.name}, type ${user.type.name}, id: ${user.id}")

    Companion.user.id = user.id
    Companion.user.type = user.type
    Companion.user.name = user.name

    val endpointFile = File(filesDir, "ncs_endpoint.txt")
    if (!endpointFile.exists()) return
    if (endpointFile.readLines().isEmpty()) return

    val chosenEndpoint = Gson().fromJson(endpointFile.readText(), Endpoint::class.java) ?: return
    discoveredEndpoints[chosenEndpoint.id] = chosenEndpoint
    chosenEndpointId = chosenEndpoint.id
    ncsCallback?.reloadDeviceList()

    Log.i(TAG, "onCreate endpoint name: ${chosenEndpoint.name}, id: ${chosenEndpoint.id}")
  }

  private fun startDiscovery() {
    if (connectionsClient == null) Log.e(TAG, "Discovery not started, connectionsClient == null.")
    connectionsClient?.startDiscovery(
      packageName, endpointDiscoveryCallback,
      DiscoveryOptions.Builder().setStrategy(STRATEGY).build()
    )?.addOnFailureListener { e -> Log.e(TAG, e.message ?: "") }
  }

  private fun startAdvertising() {
    if (connectionsClient == null) Log.e(TAG, "Advertising not started, connectionsClient == null.")
    connectionsClient?.startAdvertising(
      user.name, packageName, connectionLifecycleCallback,
      AdvertisingOptions.Builder().setStrategy(STRATEGY).build()
    )?.addOnFailureListener { e -> Log.e(TAG, e.message ?: "") }
  }

  fun sendSomething(something: String) {

    val desc = NCSEndpointState.getDesc(chosenEndpoint)

    //val connection = discoveredEndpoints.values.find { it.endpointState == NCSEndpointState.CONNECTED }
    if (chosenEndpoint == null) {
      Log.i(TAG, desc)
      ncsEndpointCallback?.error(getString(R.string.no_connection), desc)
      return
    }

    val isConnected = chosenEndpoint?.endpointState == NCSEndpointState.CONNECTED

    if (isConnected) {
      Log.i(TAG, "Connection: $chosenEndpoint, send something!")
      connectionsClient?.sendPayload(
        chosenEndpoint!!.id,
        Payload.fromBytes(something.toByteArray(Charsets.UTF_8))
      )
    } else {
      Log.i(TAG, "Sending impossible: $desc")
      ncsEndpointCallback?.error(
        getString(R.string.update_impossible),
        getString(R.string.sending_update_impossible_desc) + desc
      )
    }
  }

  fun needPermissions(): Boolean {
    return when {
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
        !hasCoarseLocationPermission()
      }
      Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
        !hasFineLocationPermission()
      }
      else -> {
        false
      }
    }
  }

  @TargetApi(Build.VERSION_CODES.M)
  fun hasCoarseLocationPermission(): Boolean {

    val location = checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
    return location == PackageManager.PERMISSION_GRANTED
  }

  @TargetApi(Build.VERSION_CODES.Q)
  fun hasFineLocationPermission(): Boolean {

    val location = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    return location == PackageManager.PERMISSION_GRANTED
  }

}