package de.bauerapps.resimulate.adapters

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import de.bauerapps.resimulate.DownloadPathology
import de.bauerapps.resimulate.Pathology
import de.bauerapps.resimulate.helper.ESApplication
import de.bauerapps.resimulate.helper.inflate
import de.bauerapps.resimulate.views.ESBootstrapButton
import de.bauerapps.resimulate.views.ESTextView
import kotlinx.android.synthetic.main.scenario_download_list_item.view.*
import de.bauerapps.resimulate.R
import de.bauerapps.resimulate.helper.formatTime
import de.bauerapps.resimulate.helper.toDate


class ScenarioDownloadAdapter(
  var context: AppCompatActivity,
  var pathologies: ArrayList<DownloadPathology>
) :
  RecyclerView.Adapter<ScenarioDownloadAdapter.ScenarioDownloadViewHolder>() {

  interface Callback {
    //fun onPreviewClick(btn: ESBootstrapButton?, item: DownloadPathology)
    fun onDownloadClick(item: DownloadPathology)

    fun onClick(pathology: DownloadPathology, isActive: Boolean)
  }

  var callback: Callback? = null

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScenarioDownloadViewHolder {
    val inflatedView = parent.inflate(R.layout.scenario_download_list_item, false)
    return ScenarioDownloadViewHolder(inflatedView)
  }

  override fun getItemCount(): Int {
    return pathologies.size
  }

  override fun onBindViewHolder(holder: ScenarioDownloadViewHolder, position: Int) {
    holder.bind(pathologies[position])
  }

  fun getLastScenarioId(): String {
    return pathologies[itemCount - 1].id
  }

  private var prevClickedView: View? = null
  private var isActiveMap = mutableMapOf<String, Boolean>()
  private fun checkActive(v: View, p: DownloadPathology): Boolean {
    if (prevClickedView != null && prevClickedView!! != v) {
      isActiveMap.clear()
      prevClickedView?.setBackgroundColor(
        ContextCompat.getColor(
          context,
          R.color.bootstrap_gray_dark
        )
      )
    }

    prevClickedView = v

    val isActive: Boolean

    if (isActiveMap.containsKey(p.id)) {
      isActive = isActiveMap[p.id]!!
    } else {
      isActiveMap[p.id] = false
      isActive = false
    }

    isActiveMap[p.id] = !isActive

    if (!isActive) {
      v.setBackgroundColor(ContextCompat.getColor(context, R.color.white))
    } else {
      v.setBackgroundColor(ContextCompat.getColor(context, R.color.bootstrap_gray_dark))
    }
    return isActive
  }


  inner class ScenarioDownloadViewHolder(v: View) : RecyclerView.ViewHolder(v) {

    private var twScenarioName: ESTextView? = null
    private var twAuthorName: ESTextView? = null
    //private var bPreview: ESBootstrapButton? = null
    private var bDownload: ESBootstrapButton? = null
    private var twTimestamp: ESTextView? = null
    private var llPreview: LinearLayout? = null

    init {
      twScenarioName = v.tw_scenario_name
      twAuthorName = v.tw_author_name
      twTimestamp = v.tw_timestamp
      llPreview = v.ll_scenario_download_item
      //bPreview = v.b_preview
      bDownload = v.b_download
    }

    fun bind(pathology: DownloadPathology) {
      twScenarioName?.text = pathology.name

      val author = pathology.author[ESApplication.AUTHOR_NAME]
      if (author != null) {
        twAuthorName?.text = context.getString(R.string.author, author)
        twAuthorName?.visibility = View.VISIBLE
      } else {
        twAuthorName?.visibility = View.GONE
      }
      /*twAuthorName?.text = context.getString(R.string.author,
          pathology.author[ESApplication.AUTHOR_NAME] ?: ESApplication.getString(R.string.anonymous))*/

      if (pathology.timeStamp != 0L) {
        twTimestamp?.text = pathology.timeStamp.toDate
        twTimestamp?.visibility = View.VISIBLE
      } else {
        twTimestamp?.visibility = View.GONE
      }

      llPreview?.setOnClickListener {
        callback?.onClick(pathology, checkActive(it, pathology))
      }

      if (llPreview != null && isActiveMap.containsKey(pathology.id) && isActiveMap[pathology.id]!!) {
        llPreview?.setBackgroundColor(ContextCompat.getColor(context, R.color.white))
      } else {
        llPreview?.setBackgroundColor(ContextCompat.getColor(context, R.color.bootstrap_gray_dark))
      }
      /*bPreview?.setOnClickListener {
          callback?.onPreviewClick(bPreview, pathology)
      }*/

      bDownload?.setOnClickListener {
        callback?.onDownloadClick(pathology)
      }
    }
  }
}
