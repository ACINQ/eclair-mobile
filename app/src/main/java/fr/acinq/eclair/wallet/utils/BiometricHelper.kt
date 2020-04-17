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

package fr.acinq.eclair.wallet.utils

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import fr.acinq.eclair.wallet.R
import org.slf4j.LoggerFactory

object BiometricHelper {

  val log = LoggerFactory.getLogger(BiometricHelper::class.java)

  @JvmStatic
  fun canUseBiometric(context: Context): Boolean =
    Prefs.useBiometrics(context) && BiometricManager.from(context).canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N


  @JvmStatic
  fun getBiometricAuth(activity: FragmentActivity, titleResId: Int = R.string.biometricprompt_title, negativeResId: Int = R.string.biometricprompt_negative, descResId: Int? = null, cancelCallback: () -> Unit, negativeCallback: () -> Unit, successCallback: () -> Unit): BiometricPrompt {
    val biometricPromptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle(activity.getString(titleResId))
        .setDeviceCredentialAllowed(false)
        .setNegativeButtonText(activity.getString(negativeResId))

    descResId?.let { biometricPromptInfo.setDescription(activity.getString(descResId)) }

    val biometricPrompt = BiometricPrompt(activity, { runnable -> Handler(Looper.getMainLooper()).post(runnable) }, object : BiometricPrompt.AuthenticationCallback() {
      override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
        super.onAuthenticationError(errorCode, errString)
        log.info("biometric auth error ($errorCode): $errString")
        if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
          negativeCallback()
        } else {
          cancelCallback()
        }
      }

      override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
        super.onAuthenticationSucceeded(result)
        try {
          successCallback()
        } catch (e: Exception) {
          log.error("could not handle successful biometric auth callback: ", e)
        }
      }
    })

    biometricPrompt.authenticate(biometricPromptInfo.build())
    return biometricPrompt
  }

}
