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

import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.widget.Toast;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;

import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.databinding.ActivityToolsBinding;
import fr.acinq.eclair.wallet.events.XpubEvent;
import fr.acinq.eclair.wallet.utils.Constants;

public class ToolsActivity extends EclairActivity {
  private static final String TAG = "StartupActivity";
  private ActivityToolsBinding mBinding;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    mBinding = DataBindingUtil.setContentView(this, R.layout.activity_tools);
    Toolbar toolbar = findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);
    ActionBar ab = getSupportActionBar();
    ab.setDisplayHomeAsUpEnabled(true);

    mBinding.deleteNetworkDB.actionButton.setOnClickListener(v -> deleteNetworkDB());
  }

  @Override
  public void onResume() {
    super.onResume();
    if (checkInit()) {
      app.getXpubFromWallet();
      if (!EventBus.getDefault().isRegistered(this)) {
        EventBus.getDefault().register(this);
      }
    }
  }

  @Override
  protected void onPause() {
    EventBus.getDefault().unregister(this);
    super.onPause();
  }

  private void deleteNetworkDB() {
    final File datadir = new File(getFilesDir(), Constants.ECLAIR_DATADIR);
    final File networkDB = new File(datadir, "testnet/network.sqlite");
    if (networkDB.delete()) {
      Toast.makeText(getApplicationContext(), "Successfully deleted network DB", Toast.LENGTH_SHORT).show();
    }
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void handleRawDataEvent(XpubEvent event) {
    if (event == null || event.xpub == null) {
      mBinding.xpub.setValue("Could not get wallet xpub.");
    } else {
      mBinding.xpub.setValue(event.xpub.xpub() + "\n\n" + event.xpub.path());
    }
  }

}
