package de.bauerapps.resimulate.adapters

import android.content.Context
import android.view.LayoutInflater
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import de.bauerapps.resimulate.R
import de.bauerapps.resimulate.helper.ESBrand
import de.bauerapps.resimulate.helper.inflate
import de.bauerapps.resimulate.views.ESBootstrapButton
import com.beardedhen.androidbootstrap.AwesomeTextView
import de.bauerapps.resimulate.databinding.ConnectionListItemBinding


data class NCListDevice(var name: String, var id: String, var isConnected: Boolean = false)

// Nearby Connections Devies Adapter
class NCDevicesAdapter(
  val ncDevices: ArrayList<NCListDevice> = ArrayList(),
  val itemClickListener: (Int, Boolean) -> Unit
) :
  RecyclerView.Adapter<NCDevicesAdapter.NCDevicesViewHolder>() {

  private var context: Context? = null

  fun foundConnection(name: String, id: String) {

    val index = ncDevices.indexOf(ncDevices.find { it.id == id })
    if (index != -1) {
      ncDevices[index].name = name
      notifyItemChanged(index)
    } else {
      val newDevice = NCListDevice(name, id)
      ncDevices.add(newDevice)
      notifyItemInserted(ncDevices.size - 1)
    }
  }

  fun removeAllItems() {
    if (ncDevices.size < 1) return
    ncDevices.clear()
    notifyDataSetChanged()
  }

  fun lostConnection(id: String) {
    if (ncDevices.size < 1) return

    val index = ncDevices.indexOf(ncDevices.find { it.id == id })
    if (index == -1) return
    ncDevices.removeAt(index)
    notifyItemRemoved(index)
  }

  fun setConnected(id: String) {
    if (ncDevices.size < 1) return

    val index = ncDevices.indexOf(ncDevices.find { it.id == id })
    if (index == -1) return
    ncDevices[index].isConnected = true
    notifyItemChanged(index)
  }

  fun setDisconnected() {
    ncDevices.forEachIndexed { index, ncListDevice ->
      ncListDevice.isConnected = false
      notifyItemChanged(index)
    }
  }

  fun setDisconnected(id: String) {
    if (ncDevices.size < 1) return

    val index = ncDevices.indexOf(ncDevices.find { it.id == id })

    if (index == -1) {
      setDisconnected()
      return
    }

    ncDevices[index].isConnected = false
    notifyItemChanged(index)
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NCDevicesViewHolder {
    val binding = ConnectionListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    context = parent.context
    return NCDevicesViewHolder(binding)
  }

  override fun getItemCount() = ncDevices.size

  override fun onBindViewHolder(holder: NCDevicesViewHolder, position: Int) {
    holder.bindDevice(ncDevices[position])
  }

  inner class NCDevicesViewHolder(v: ConnectionListItemBinding) : RecyclerView.ViewHolder(v.root) {

    private var twEndpointName: TextView? = null
    private var twConnectionIcon: AwesomeTextView? = null
    private var twEndpointID: TextView? = null
    private var bConnectionToggle: ESBootstrapButton? = null

    init {
      this.twEndpointName = v.twEndpointName
      this.twConnectionIcon = v.twConnectionIcon
      this.twEndpointID = v.twEndpointId
      this.bConnectionToggle = v.bConnectionToggle
    }

    fun bindDevice(ncListDevice: NCListDevice) {
      this.twEndpointName?.text = "Name: ${ncListDevice.name}"
      this.twEndpointID?.text = "ID: ${ncListDevice.id}"

      if (ncListDevice.isConnected) {
        bConnectionToggle?.bootstrapBrand = ESBrand.DANGER

        context?.let {
          twConnectionIcon?.setTextColor(
            ContextCompat.getColor(
              it,
              R.color.bootstrap_brand_success
            )
          )
        }
        bConnectionToggle?.text = context?.getString(R.string.disconnect)
      } else {
        bConnectionToggle?.bootstrapBrand = ESBrand.SUCCESS
        bConnectionToggle?.text = context?.getString(R.string.connect)
      }

      bConnectionToggle?.setOnClickListener {
        itemClickListener(adapterPosition, ncListDevice.isConnected)
      }
    }

  }
}