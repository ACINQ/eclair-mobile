/*
 * Copyright 2018 ACINQ SAS
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

package fr.acinq.eclair.wallet.activities;

import akka.dispatch.OnComplete;
import akka.pattern.Patterns;
import akka.util.Timeout;
import android.content.Context;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;
import fr.acinq.bitcoin.MilliSatoshi;
import fr.acinq.bitcoin.Satoshi;
import fr.acinq.eclair.io.NodeURI;
import fr.acinq.eclair.io.Peer;
import fr.acinq.eclair.wallet.BuildConfig;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.databinding.ActivityOpenChannelBinding;
import fr.acinq.eclair.wallet.fragments.PinDialog;
import fr.acinq.eclair.wallet.fragments.openchannel.OpenChannelCapacityFragment;
import fr.acinq.eclair.wallet.fragments.openchannel.OpenChannelLiquidityFragment;
import fr.acinq.eclair.wallet.tasks.NodeURIReaderTask;
import org.greenrobot.eventbus.util.AsyncExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.concurrent.duration.Duration;

public class OpenChannelActivity extends EclairActivity implements NodeURIReaderTask.AsyncNodeURIReaderTaskResponse, OpenChannelCapacityFragment.OnCapacityConfirmListener, OpenChannelLiquidityFragment.OnLiquidityConfirmListener {

  public static final String EXTRA_NEW_HOST_URI = BuildConfig.APPLICATION_ID + "NEW_HOST_URI";
  public static final String EXTRA_USE_DNS_SEED = BuildConfig.APPLICATION_ID + "USE_DNS_SEED";
  private final Logger log = LoggerFactory.getLogger(OpenChannelActivity.class);
  private ActivityOpenChannelBinding mBinding;

  private PinDialog pinDialog;
  private OpenChannelCapacityFragment mCapacityFragment;
  private OpenChannelLiquidityFragment mLiquidityFragment;

  public enum Steps {
    SET_CAPACITY, SET_FEES, REQUEST_LIQUIDITY, CONFIRM
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    mBinding = DataBindingUtil.setContentView(this, R.layout.activity_open_channel);
    final boolean useDnsSeed = getIntent().getBooleanExtra(EXTRA_USE_DNS_SEED, false);
    if (useDnsSeed) {
      startDNSDiscovery();
    } else {
      new NodeURIReaderTask(this, getIntent().getStringExtra(EXTRA_NEW_HOST_URI)).execute();
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    checkInit();
  }

  @Override
  protected void onPause() {
    // dismiss the pin dialog if it exists to prevent leak.
    if (pinDialog != null) {
      pinDialog.dismiss();
    }
    super.onPause();
  }

  @Override
  public void onAttachFragment(Fragment fragment) {
    if (fragment instanceof OpenChannelCapacityFragment) {
      OpenChannelCapacityFragment f = (OpenChannelCapacityFragment) fragment;
      f.setListener(this);
    }
    if (fragment instanceof OpenChannelLiquidityFragment) {
      OpenChannelLiquidityFragment f = (OpenChannelLiquidityFragment) fragment;
      f.setListener(this);
    }
  }

  @Override
  public void processNodeURIFinish(final NodeURI uri) {
    if (uri == null || uri.address() == null || uri.nodeId() == null) {
      mBinding.setErrorMessage(getString(R.string.openchannel_error_address));
    } else {
      mBinding.setNodeURI(uri);
      mCapacityFragment = new OpenChannelCapacityFragment();
      mCapacityFragment.setNodeURI(uri);
      mLiquidityFragment = new OpenChannelLiquidityFragment();

      final FragmentManager fragmentManager = getSupportFragmentManager();
      fragmentManager.beginTransaction()
        .add(mBinding.fragmentContainer.getId(), mCapacityFragment)
        .add(mBinding.fragmentContainer.getId(), mLiquidityFragment)
        .show(mCapacityFragment)
        .hide(mLiquidityFragment)
        .commit();
    }
  }

  private void startDNSDiscovery() {
    mBinding.setErrorMessage(getString(R.string.openchannel_dns_seed));
  }

  private void goToCapacityPage() {
    final FragmentManager fragmentManager = getSupportFragmentManager();
    fragmentManager.beginTransaction()
      .hide(mLiquidityFragment)
      .show(mCapacityFragment)
      .addToBackStack(null)
      .commit();
  }

  private void goToLiquidityPage(final Satoshi capacity, final Long feesSatPerByte) {
    final InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
    if (imm != null) {
      imm.hideSoftInputFromWindow(mBinding.getRoot().getWindowToken(), 0);
    }
    final FragmentManager fragmentManager = getSupportFragmentManager();
    mLiquidityFragment.setCapacityAndFees(capacity, feesSatPerByte);
    fragmentManager.beginTransaction()
      .hide(mCapacityFragment)
      .show(mLiquidityFragment)
      .addToBackStack(null)
      .commit();
  }

  @Override
  public void onCapacityBack() {
    finish();
  }

  @Override
  public void onCapacityConfirm(final Satoshi capacity, final long feesSatPerKW) {
    goToLiquidityPage(capacity, feesSatPerKW);
  }

  @Override
  public void onLiquidityBack() {
    goToCapacityPage();
  }

  @Override
  public void onLiquidityConfirm(final Satoshi capacity, final Long feesSatPerKW, final MilliSatoshi push) {
    openChannel_secure(capacity, feesSatPerKW, push);
  }

  private void openChannel_secure(final Satoshi capacity, final Long feesSatPerKW, final MilliSatoshi push) {
    if (isPinRequired()) {
      pinDialog = new PinDialog(OpenChannelActivity.this, R.style.FullScreenDialog, new PinDialog.PinDialogCallback() {
        @Override
        public void onPinConfirm(final PinDialog dialog, final String pinValue) {
          if (isPinCorrect(pinValue, dialog)) {
            doOpenChannel(mBinding.getNodeURI(), capacity, feesSatPerKW, push);
          } else {
            mBinding.setErrorMessage(getString(R.string.payment_error_incorrect_pin));
          }
        }

        @Override
        public void onPinCancel(PinDialog dialog) {
          // nothing
        }
      });
      pinDialog.show();
    } else {
      doOpenChannel(mBinding.getNodeURI(), capacity, feesSatPerKW, push);
    }
  }

  private void doOpenChannel(final NodeURI nodeURI, final Satoshi capacity, final Long feesSatPerKW, final MilliSatoshi push) {
    try {
      log.info("opening channel with {} with capacity={} fees={} push={}", nodeURI, capacity, feesSatPerKW, push);
      final Peer.OpenChannel open = new Peer.OpenChannel(nodeURI.nodeId(), capacity, push, scala.Option.apply(feesSatPerKW), scala.Option.apply(null));
      AsyncExecutor.create().execute(
        () -> {
          final Timeout timeout = new Timeout(Duration.create(30, "seconds"));
          final OnComplete<Object> onComplete = new OnComplete<Object>() {
            @Override
            public void onComplete(Throwable throwable, Object o) {
              if (throwable != null && !(throwable instanceof akka.pattern.AskTimeoutException)) {
                runOnUiThread(() -> Toast.makeText(getApplicationContext(), getString(R.string.home_toast_openchannel_failed) + throwable.getMessage(), Toast.LENGTH_LONG).show());
              }
            }
          };
          final OnComplete<Object> onConnectComplete = new OnComplete<Object>() {
            @Override
            public void onComplete(Throwable throwable, Object result) {
              if (throwable != null) {
                runOnUiThread(() -> Toast.makeText(getApplicationContext(), getString(R.string.home_toast_openchannel_failed) + throwable.getMessage(), Toast.LENGTH_LONG).show());
              } else if ("connected".equals(result.toString()) || "already connected".equals(result.toString())) {
                Patterns.ask(app.appKit.eclairKit.switchboard(), open, timeout).onComplete(onComplete, app.system.dispatcher());
              } else {
                runOnUiThread(() -> Toast.makeText(getApplicationContext(), getString(R.string.home_toast_openchannel_failed) + result.toString(), Toast.LENGTH_LONG).show());
              }
            }
          };
          Patterns.ask(app.appKit.eclairKit.switchboard(), new Peer.Connect(nodeURI), timeout)
            .onComplete(onConnectComplete, app.system.dispatcher());
        });
      finish();
    } catch (Throwable t) {
      mBinding.setErrorMessage(t.getLocalizedMessage());
    }
  }
}
