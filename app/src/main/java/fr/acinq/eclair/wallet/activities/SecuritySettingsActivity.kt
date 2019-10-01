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

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.widget.Toolbar
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.databinding.DataBindingUtil
import fr.acinq.eclair.wallet.R
import fr.acinq.eclair.wallet.databinding.ActivitySecuritySettingsBinding
import fr.acinq.eclair.wallet.fragments.PinDialog
import fr.acinq.eclair.wallet.utils.*
import org.slf4j.LoggerFactory
import java.io.File
import java.security.GeneralSecurityException

class SecuritySettingsActivity : EclairActivity(), EclairActivity.EncryptSeedCallback {

  private val log = LoggerFactory.getLogger(SecuritySettingsActivity::class.java)
  private var securityPrefsListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
  private lateinit var mBinding: ActivitySecuritySettingsBinding

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    mBinding = DataBindingUtil.setContentView(this, R.layout.activity_security_settings)

    val toolbar = findViewById<Toolbar>(R.id.toolbar)
    setSupportActionBar(toolbar)
    val ab = supportActionBar
    ab!!.setDisplayHomeAsUpEnabled(true)

    mBinding.pinRequiredPaymentLayout.setOnClickListener {
      val isPinDefined = isPinRequired
      if (isPinDefined && mBinding.pinRequiredPaymentSwitch.isChecked) {
        disablePinSensitiveAction()
      } else if (!isPinDefined && !mBinding.pinRequiredPaymentSwitch.isChecked) {
        applicationContext.getSharedPreferences(Constants.SETTINGS_SECURITY_FILE, Context.MODE_PRIVATE).edit().putBoolean(Constants.SETTING_ASK_PIN_FOR_SENSITIVE_ACTIONS, true).apply()
      } else {
        mBinding.pinRequiredPaymentSwitch.isChecked = isPinRequired
      }
    }

    securityPrefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
      mBinding.biometricSwitch.isChecked = Prefs.useBiometrics(applicationContext)
      mBinding.pinRequiredPaymentSwitch.isChecked = isPinRequired
    }

    mBinding.biometricSwitch.isChecked = Prefs.useBiometrics(applicationContext)

    mBinding.changePinLayout.setOnClickListener { changePassword() }

    when (BiometricManager.from(applicationContext).canAuthenticate()) {
      BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> unavailableBiometrics(R.string.security_biometric_support_no_hw)
      BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> unavailableBiometrics(R.string.security_biometric_support_hw_unavailable)
      BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> unavailableBiometrics(R.string.security_biometric_support_none_enrolled)
      BiometricManager.BIOMETRIC_SUCCESS -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        mBinding.biometricLayout.setOnClickListener { v ->
          if (mBinding.biometricSwitch.isChecked) {
            disableBiometrics(v.context)
          } else {
            enrollBiometrics(v.context)
          }
        }
      } else {
        unavailableBiometrics(R.string.security_biometric_unavailable)
      }
    }
  }

  override fun onResume() {
    super.onResume()
    if (checkInit()) {
      mBinding.pinRequiredPaymentSwitch.isChecked = isPinRequired
      getSharedPreferences(Constants.SETTINGS_SECURITY_FILE, Context.MODE_PRIVATE).registerOnSharedPreferenceChangeListener(securityPrefsListener)
    }
  }

  override fun onPause() {
    super.onPause()
    getSharedPreferences(Constants.SETTINGS_SECURITY_FILE, Context.MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(securityPrefsListener)
  }

  private fun unavailableBiometrics(messageResId: Int) {
    mBinding.biometricSwitch.visibility = View.GONE
    mBinding.biometricDesc.text = getString(messageResId)
  }

  /**
   * Disable PIN verification for sensitive actions. User has to verify the PIN first.
   */
  private fun disablePinSensitiveAction() {
    val removePinDialog = PinDialog(this@SecuritySettingsActivity, R.style.FullScreenDialog, object : PinDialog.PinDialogCallback {
      override fun onPinConfirm(dialog: PinDialog, pinValue: String) {
        if (isPinCorrect(pinValue, dialog)) {
          applicationContext.getSharedPreferences(Constants.SETTINGS_SECURITY_FILE, Context.MODE_PRIVATE).edit()
              .putBoolean(Constants.SETTING_ASK_PIN_FOR_SENSITIVE_ACTIONS, false).apply()
        } else {
          Toast.makeText(applicationContext, getString(R.string.security_pin_failure), Toast.LENGTH_SHORT).show()
        }
      }

      override fun onPinCancel(dialog: PinDialog) {}
    })
    removePinDialog.show()
  }

  private fun changePassword() {
    PinDialog(this@SecuritySettingsActivity, R.style.FullScreenDialog, object : PinDialog.PinDialogCallback {
      override fun onPinConfirm(dialog: PinDialog, pinValue: String) {
        dialog.dismiss()
        try {
          val datadir = File(filesDir, Constants.ECLAIR_DATADIR)
          val seed = WalletUtils.readSeedFile(datadir, pinValue)
          encryptWallet(this@SecuritySettingsActivity, true, datadir, seed)
        } catch (e: GeneralSecurityException) {
          Toast.makeText(applicationContext, R.string.security_pin_failure, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
          log.error("failed to read seed")
          Toast.makeText(applicationContext, R.string.seed_read_general_failure, Toast.LENGTH_SHORT).show()
        }
      }

      override fun onPinCancel(dialog: PinDialog) {}
    }).show()
  }

  private var biometricPrompt: BiometricPrompt? = null

  @RequiresApi(Build.VERSION_CODES.O)
  private fun disableBiometrics(context: Context) {
    biometricPrompt = BiometricHelper.getBiometricAuth(this, R.string.security_biometric_disable_prompt_title, R.string.security_biometric_disable_prompt_negative, R.string.security_biometric_disable_prompt_desc, {
      mBinding.isUpdatingBiometrics = false
    }, {
      mBinding.isUpdatingBiometrics = false
    }, {
      try {
        mBinding.isUpdatingBiometrics = true
        Prefs.useBiometrics(context, false)
        KeystoreHelper.deleteKeyForPin()
        mBinding.isUpdatingBiometrics = false
      } catch (e: Exception) {
        log.error("could not disable biometric auth: ", e)
      }
    })
  }

  private var promptPin: PinDialog? = null

  @RequiresApi(Build.VERSION_CODES.O)
  private fun enrollBiometrics(context: Context) {
    promptPin = PinDialog(this@SecuritySettingsActivity, R.style.FullScreenDialog, object : PinDialog.PinDialogCallback {
      override fun onPinConfirm(dialog: PinDialog, pinValue: String) {
        dialog.dismiss()
        try {
          // check that PIN is correct
          WalletUtils.readSeedFile(File(filesDir, Constants.ECLAIR_DATADIR), pinValue)
          KeystoreHelper.deleteKeyForPin()
          KeystoreHelper.generateKeyForPin()
          biometricPrompt = BiometricHelper.getBiometricAuth(this@SecuritySettingsActivity, R.string.security_biometric_enable_prompt_title, R.string.security_biometric_enable_prompt_negative, null, {
            mBinding.isUpdatingBiometrics = false
          }, {
            mBinding.isUpdatingBiometrics = false
          }, {
            try {
              KeystoreHelper.encryptPin(context, pinValue)
              Prefs.useBiometrics(context, true)
              Toast.makeText(applicationContext, R.string.security_biometric_enabled, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
              log.error("could not encrypt pin in keystore: ", e)
              Toast.makeText(applicationContext, R.string.security_biometric_error, Toast.LENGTH_SHORT).show()
            } finally {
              mBinding.isUpdatingBiometrics = false
            }
          })
        } catch (e: GeneralSecurityException) {
          Toast.makeText(applicationContext, R.string.security_pin_failure, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
          log.error("failed to read seed")
          Toast.makeText(applicationContext, R.string.security_biometric_error, Toast.LENGTH_SHORT).show()
        }
      }

      override fun onPinCancel(dialog: PinDialog) {
        mBinding.isUpdatingBiometrics = false
      }
    })

    promptPin?.reset()
    promptPin?.show()
  }

  override fun onEncryptSeedFailure(message: String) {
    Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
  }

  override fun onEncryptSeedSuccess() {
    Toast.makeText(applicationContext, getString(R.string.security_change_pin_success), Toast.LENGTH_SHORT).show()
  }
}
