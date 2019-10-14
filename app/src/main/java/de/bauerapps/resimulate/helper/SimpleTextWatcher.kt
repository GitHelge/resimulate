package de.bauerapps.resimulate.helper

import android.text.Editable
import android.text.TextWatcher


interface SimpleTextWatcher : TextWatcher {

  fun afterTextChanged(text: String)

  override fun afterTextChanged(p0: Editable?) {
    p0?.let {
      afterTextChanged(it.toString())
    }
  }

  override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}

  override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
}