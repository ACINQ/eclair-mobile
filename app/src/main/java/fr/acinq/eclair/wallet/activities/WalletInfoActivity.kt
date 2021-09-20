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
package fr.acinq.eclair.wallet.activities

import fr.acinq.eclair.wallet.utils.WalletUtils.getBlockHeight
import fr.acinq.eclair.wallet.utils.WalletUtils.getNetworkDBFile
import fr.acinq.eclair.wallet.utils.WalletUtils.getWalletDBFile
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout.OnRefreshListener
import fr.acinq.eclair.wallet.fragments.CustomElectrumServerDialog
import android.os.Bundle
import androidx.databinding.DataBindingUtil
import fr.acinq.eclair.wallet.R
import org.greenrobot.eventbus.ThreadMode
import fr.acinq.eclair.wallet.events.XpubEvent
import fr.acinq.eclair.wallet.events.NetworkChannelsCountEvent
import android.content.DialogInterface
import android.os.Handler
import android.preference.PreferenceManager
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import fr.acinq.eclair.wallet.databinding.ActivityWalletInfoBinding
import fr.acinq.eclair.wallet.fragments.DisplaySeedDialog
import fr.acinq.eclair.wallet.fragments.PinDialog
import fr.acinq.eclair.wallet.utils.Constants
import fr.acinq.eclair.wallet.utils.EncryptedSeed
import fr.acinq.eclair.wallet.utils.WalletUtils
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.slf4j.LoggerFactory
import java.io.File
import java.security.GeneralSecurityException
import java.text.DateFormat
import java.text.NumberFormat
import java.util.*

class WalletInfoActivity : EclairActivity(), OnRefreshListener {
  private lateinit var mBinding: ActivityWalletInfoBinding
  private var mElectrumDialog: CustomElectrumServerDialog? = null
  private var mDisplaySeedDialog: DisplaySeedDialog? = null
  private val log = LoggerFactory.getLogger(WalletInfoActivity::class.java)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    mBinding = DataBindingUtil.setContentView(this, R.layout.activity_wallet_info)
    setSupportActionBar(findViewById(R.id.toolbar))
    supportActionBar?.setDisplayHomeAsUpEnabled(true)
    mBinding.swipeRefresh.setColorSchemeResources(R.color.primary, R.color.green, R.color.accent)
    mBinding.swipeRefresh.setOnRefreshListener(this)
    mBinding.networkChannelsCount.actionButton.setOnClickListener { v: View? -> deleteNetworkDB() }
    mBinding.electrumAddress.actionButton.setOnClickListener { v: View? -> setCustomElectrum() }
    mBinding.xpub.actionButton.setOnClickListener { v: View? -> deleteElectrumDB() }
    if (WalletUtils.readSeedFile(WalletUtils.getDatadir(applicationContext), WalletUtils.SEED_NAME).version == EncryptedSeed.SEED_FILE_VERSION_2) {
      mBinding.displaySeed.actionButton.setOnClickListener { decryptWallet() }
    } else {
      mBinding.displaySeed.visibility = View.GONE
    }
  }

  override fun onRefresh() {
    refreshData()
  }

  private fun refreshData() {
    val blockHeight = getBlockHeight(applicationContext)
    if (blockHeight == 0L) {
      mBinding.blockCount.setValue(getString(R.string.walletinfo_block_unknown))
    } else if (app.blockTimestamp == 0L) {
      mBinding.blockCount.setValue(NumberFormat.getInstance().format(blockHeight))
    } else {
      mBinding.blockCount.setHtmlValue(
        getString(
          R.string.walletinfo_block,
          NumberFormat.getInstance().format(blockHeight),  // block height
          DateFormat.getDateTimeInstance().format(Date(app.blockTimestamp * 1000))
        )
      ) // block timestamp
    }
    val customElectrumServer = PreferenceManager.getDefaultSharedPreferences(applicationContext)
      .getString(Constants.CUSTOM_ELECTRUM_SERVER, "")
    val currentElectrumServer = app.electrumServerAddress
    if (currentElectrumServer?.toString()?.isBlank() == null || currentElectrumServer.toString().isBlank()) {
      // not yet connected...
      if (customElectrumServer.isNullOrBlank()) {
        mBinding.electrumAddress.setValue(getString(R.string.walletinfo_electrum_address_connecting))
      } else {
        mBinding.electrumAddress.setValue(getString(R.string.walletinfo_electrum_address_connecting_to_custom, customElectrumServer))
      }
    } else {
      mBinding.electrumAddress.setValue(currentElectrumServer.toString())
    }
    if (customElectrumServer.isNullOrBlank()) {
      mBinding.electrumAddress.setActionLabel(getString(R.string.walletinfo_electrum_address_set_custom))
    } else {
      mBinding.electrumAddress.setActionLabel(getString(R.string.walletinfo_electrum_address_change_custom))
    }
    if (app?.appKit != null) {
      mBinding.feeRate.setValue("${NumberFormat.getInstance().format(app.appKit.eclairKit.nodeParams().onChainFeeConf().feeEstimator().getFeeratePerKw(1))} sat/kw")
    }
    app.getNetworkChannelsCount()
    mBinding.swipeRefresh.isRefreshing = false
  }

  public override fun onResume() {
    super.onResume()
    if (checkInit()) {
      if (!EventBus.getDefault().isRegistered(this)) {
        EventBus.getDefault().register(this)
      }
      app.getXpubFromWallet()
      mBinding.nodeId.setValue(app.nodePublicKey())
      refreshData()
    }
  }

  override fun onPause() {
    mElectrumDialog?.dismiss()
    mDisplaySeedDialog?.cleanUp()
    mDisplaySeedDialog?.dismiss()
    EventBus.getDefault().unregister(this)
    super.onPause()
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun handleXpubEvent(event: XpubEvent?) {
    mBinding.xpub.setValue("${event?.xpub?.xpub() ?: getString(R.string.unknown)}\n\n${event?.xpub?.path() ?: getString(R.string.unknown)}")
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  fun handleNetworkChannelsCountEvent(event: NetworkChannelsCountEvent) {
    if (event.count == -1) {
      mBinding.networkChannelsCount.setValue(resources.getString(R.string.unknown))
    } else {
      mBinding.networkChannelsCount.setValue(Integer.toString(event.count))
    }
  }

  private fun deleteNetworkDB() {
    getCustomDialog(R.string.walletinfo_networkdb_confirm)
      .setPositiveButton(R.string.btn_ok) { dialog: DialogInterface?, which: Int ->
        object : Thread() {
          override fun run() {
            val networkDB = getNetworkDBFile(applicationContext)
            if (networkDB.delete()) {
              runOnUiThread {
                Toast.makeText(applicationContext, R.string.walletinfo_networkdb_toast, Toast.LENGTH_SHORT).show()
                restart()
              }
            }
          }
        }.start()
      }
      .setNegativeButton(R.string.btn_cancel) { dialog: DialogInterface?, which: Int -> }
      .create()
      .show()
  }

  private fun setCustomElectrum() {
    mElectrumDialog = CustomElectrumServerDialog(this@WalletInfoActivity) { serverAddress: String ->
      handleCustomElectrumSubmit(serverAddress)
    }
    mElectrumDialog!!.show()
  }

  /**
   * Displays a message to the user and restart the app after 3s.
   */
  private fun handleCustomElectrumSubmit(serverAddress: String) {
    val message = if (serverAddress.isNullOrBlank()) {
      getString(R.string.walletinfo_electrum_confirm_message_default)
    } else {
      getString(R.string.walletinfo_electrum_confirm_message, serverAddress)
    }
    getCustomDialog(message).setCancelable(false).show()
    Handler().postDelayed({ restart() }, 3000)
  }

  private fun deleteElectrumDB() {
    getCustomDialog(R.string.walletinfo_electrumdb_confirm)
      .setPositiveButton(R.string.btn_ok) { dialog: DialogInterface?, which: Int ->
        object : Thread() {
          override fun run() {
            val walletDB = getWalletDBFile(applicationContext)
            if (walletDB.delete()) {
              runOnUiThread {
                app.dbHelper.deleteAllOnchainTxs()
                Toast.makeText(applicationContext, R.string.walletinfo_electrumdb_toast, Toast.LENGTH_SHORT).show()
                restart()
              }
            }
          }
        }.start()
      }
      .setNegativeButton(R.string.btn_cancel) { dialog: DialogInterface?, which: Int -> }
      .create()
      .show()
  }

  private fun decryptWallet() {
    PinDialog(this@WalletInfoActivity, R.style.FullScreenDialog, object : PinDialog.PinDialogCallback {
      override fun onPinConfirm(dialog: PinDialog, pinValue: String) {
        dialog.dismiss()
        try {
          val datadir = File(filesDir, Constants.ECLAIR_DATADIR)
          val (seed, decryptedPayload) = WalletUtils.readSeedAndDecrypt(datadir, pinValue)
          if (seed.version == EncryptedSeed.SEED_FILE_VERSION_2) {
            val (words, passphrase) = WalletUtils.decodeV2MnemonicsBlob(decryptedPayload)
            if (mDisplaySeedDialog == null) {
              mDisplaySeedDialog = DisplaySeedDialog(this@WalletInfoActivity)
            }
            mDisplaySeedDialog?.window?.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
            mDisplaySeedDialog?.let {
              it.loadMnemonics(words, passphrase)
              it.show()
            }
          } else {
            Toast.makeText(applicationContext, R.string.walletinfo_seed_cannot_read_v1, Toast.LENGTH_SHORT).show()
          }
        } catch (e: GeneralSecurityException) {
          Toast.makeText(applicationContext, R.string.security_pin_failure, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
          log.error("failed to read seed: ", e)
          Toast.makeText(applicationContext, R.string.seed_read_general_failure, Toast.LENGTH_SHORT).show()
        }
      }

      override fun onPinCancel(dialog: PinDialog) {}
    }).show()
  }
}
