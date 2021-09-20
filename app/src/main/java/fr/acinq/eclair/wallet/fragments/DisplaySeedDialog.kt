/*
 * Copyright 2019 ACINQ SAS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package fr.acinq.eclair.wallet.fragments

import android.app.Dialog
import android.content.Context
import fr.acinq.eclair.wallet.R
import com.google.common.net.HostAndPort
import androidx.databinding.DataBindingUtil
import android.view.LayoutInflater
import android.content.DialogInterface
import android.text.Html
import android.view.Gravity
import android.view.View
import android.widget.TableRow
import android.widget.TextView
import com.google.common.base.Strings
import fr.acinq.eclair.wallet.databinding.DialogDisplaySeedBinding
import java.lang.Exception

class DisplaySeedDialog(context: Context?) : Dialog(
  context!!, R.style.CustomDialog
) {

  val mBinding: DialogDisplaySeedBinding = DataBindingUtil.inflate(LayoutInflater.from(context), R.layout.dialog_display_seed, null, false)

  init {
    setContentView(mBinding.root)
    setOnCancelListener { d: DialogInterface? -> dismiss() }
    mBinding.btnOk.setOnClickListener { v: View? -> dismiss() }
  }

  fun cleanUp() {
    loadMnemonics(emptyList(), null)
  }

  fun loadMnemonics(words: List<String>, passphrase: String?) {
    setWords(words)
    if (passphrase.isNullOrEmpty()) {
      mBinding.passphraseLabel.visibility = View.GONE
      mBinding.passphraseValue.visibility = View.GONE
    } else {
      mBinding.passphraseLabel.visibility = View.VISIBLE
      mBinding.passphraseValue.visibility = View.VISIBLE
      mBinding.passphraseValue.text = passphrase
    }
  }

  private fun setWords(words: List<String>) {
    val bottomPadding: Int = context.resources.getDimensionPixelSize(R.dimen.word_list_padding)
    val rightPadding: Int = context.resources.getDimensionPixelSize(R.dimen.space_lg)
    mBinding.wordsTable.removeAllViews()
    var i = 0
    while (i < words.size / 2) {
      val tr = TableRow(context)
      tr.gravity = Gravity.CENTER
      tr.layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT)
      val t1 = TextView(context)
      t1.layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT)
      t1.text = Html.fromHtml(context.getString(R.string.createwallet_single_word_display, i + 1, words.get(i)))
      t1.setPadding(0, 0, rightPadding, bottomPadding)
      tr.addView(t1)
      val t2 = TextView(context)
      t2.text = Html.fromHtml(context.getString(R.string.createwallet_single_word_display, i + words.size / 2 + 1, words.get(i + words.size / 2)))
      t2.layoutParams = TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT)
      t2.setPadding(0, 0, 0, bottomPadding)
      tr.addView(t2)
      mBinding.wordsTable.addView(tr)
      i += 1
    }
  }
}
