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

import android.content.Intent;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;

import com.google.common.base.Strings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.dispatch.OnComplete;
import akka.pattern.Patterns;
import akka.util.Timeout;
import fr.acinq.eclair.io.NodeURI;
import fr.acinq.eclair.io.Peer;
import fr.acinq.eclair.wallet.BuildConfig;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.databinding.ActivityOpenConnectionBinding;
import fr.acinq.eclair.wallet.utils.Constants;
import scala.Option;
import scala.concurrent.duration.Duration;

public class OpenConnectionActivity extends EclairActivity {

  public final static String EXTRA_CONN_NODE_ID = BuildConfig.APPLICATION_ID + ".CONN_NODE_ID";
  private final Logger log = LoggerFactory.getLogger(OpenConnectionActivity.class);
  private ActivityOpenConnectionBinding mBinding;
  private String expectedNodeId = "";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    mBinding = DataBindingUtil.setContentView(this, R.layout.activity_open_connection);
    expectedNodeId = getIntent().getStringExtra(EXTRA_CONN_NODE_ID);
    mBinding.nodeAddress.addTextChangedListener(new TextWatcher() {
      @Override
      public void beforeTextChanged(CharSequence s, int start, int count, int after) {
      }

      @Override
      public void onTextChanged(CharSequence s, int start, int before, int count) {
        mBinding.nodeAddressLayout.setError(null);
      }

      @Override
      public void afterTextChanged(Editable s) {
      }
    });
    mBinding.connectButton.setOnClickListener((v) -> {
      mBinding.nodeAddressLayout.setError(null);
      log.info("attempting connection to node=" + expectedNodeId + " with address=" + mBinding.nodeAddress.getText());
      mBinding.setConnectionStep(Constants.NODE_CONNECT_CONNECTING);
      try {
        final Timeout timeout = new Timeout(Duration.create(10, "seconds"));
        final NodeURI nodeUri = NodeURI.parse(expectedNodeId + "@" + mBinding.nodeAddress.getText().toString());
        Patterns.ask(app.appKit.eclairKit.switchboard(), new Peer.Connect(nodeUri, Option.apply(null)), timeout)
          .onComplete(new OnComplete<Object>() {
            @Override
            public void onComplete(Throwable failure, Object success) {
              runOnUiThread(() -> {
                if (failure != null) {
                  if (failure instanceof akka.pattern.AskTimeoutException) {
                    mBinding.nodeAddressLayout.setError(getString(R.string.openconn_error_timeout));
                  } else {
                    mBinding.nodeAddressLayout.setError(getString(R.string.openconn_error_fatal_connection, failure.getLocalizedMessage()));
                  }
                  mBinding.setConnectionStep(Constants.NODE_CONNECT_READY);
                } else {
                  mBinding.setConnectionStep(Constants.NODE_CONNECT_SUCCESS);
                  new Handler().postDelayed(() -> finish(), 1000);
                }
              });
            }
          }, app.system.dispatcher());
      } catch (Throwable t) {
        log.error("could not connect to node=" + expectedNodeId + " with address=" + mBinding.nodeAddress.getText(), t);
        mBinding.nodeAddressLayout.setError(getString(R.string.openconn_error));
        mBinding.setConnectionStep(Constants.NODE_CONNECT_READY);
      }
    });
    mBinding.scanButton.setOnClickListener((v) -> {
      final Intent intent = new Intent(this, ScanActivity.class);
      intent.putExtra(ScanActivity.EXTRA_SCAN_TYPE, ScanActivity.TYPE_URI_CONNECT);
      startActivityForResult(intent, Constants.OPEN_CONNECTION_REQUEST_CODE);
    });
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (Strings.isNullOrEmpty(expectedNodeId)) {
      finish();
    } else {
      mBinding.nodeId.setText(expectedNodeId);
    }
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (resultCode == RESULT_OK && requestCode == Constants.OPEN_CONNECTION_REQUEST_CODE) {
      final String scannedValue = data.getStringExtra(OpenChannelActivity.EXTRA_NEW_HOST_URI);
      try {
        final NodeURI nodeUri = NodeURI.parse(scannedValue);
        if (!expectedNodeId.equals(nodeUri.nodeId().toString())) {
          mBinding.nodeAddressLayout.setError(getString(R.string.openconn_error_unexpected_node_id));
        } else {
          mBinding.nodeAddress.setText(nodeUri.address().toString());
        }
      } catch (Exception e) {
        log.error("could not read scanned address=" + scannedValue);
        mBinding.nodeAddressLayout.setError(getString(R.string.openconn_error));
      }
    }
  }
}
