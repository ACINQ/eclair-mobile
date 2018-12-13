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
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.text.Html;

import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.databinding.ActivityAboutBinding;

public class AboutActivity extends EclairActivity {
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    final ActivityAboutBinding binding = DataBindingUtil.setContentView(this, R.layout.activity_about);

    Toolbar toolbar = findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);
    ActionBar ab = getSupportActionBar();
    ab.setDisplayHomeAsUpEnabled(true);

    binding.version.setValue(app.getVersion());
    binding.faq.actionButton.setOnClickListener(v ->
      startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ACINQ/eclair-wallet/wiki/FAQ"))));
    binding.about.setText(Html.fromHtml(getString(R.string.about_acinq_text)));
  }
}
