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

package fr.acinq.eclair.wallet.fragments;

import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.text.Html;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TableRow;
import android.widget.TextView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.databinding.FragmentWalletCreateSeedBinding;

public class WalletCreateSeedFragment extends Fragment {

  private final Logger log = LoggerFactory.getLogger(WalletCreateSeedFragment.class);
  public FragmentWalletCreateSeedBinding mBinding;
  public final static String BUNDLE_ARG_MNEMONICS = "mnemonics";

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setHasOptionsMenu(false);
  }

  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    mBinding = DataBindingUtil.inflate(inflater, R.layout.fragment_wallet_create_seed, container, false);

    try {
      final Bundle args = getArguments();
      final ArrayList<String> mnemonics = args.getStringArrayList(BUNDLE_ARG_MNEMONICS);
      final int bottomPadding = getResources().getDimensionPixelSize(R.dimen.word_list_padding);
      final int rightPadding = getResources().getDimensionPixelSize(R.dimen.space_lg);
      for (int i = 0; i < mnemonics.size() / 2; i = i + 1) {
        TableRow tr = new TableRow(getContext());
        tr.setGravity(Gravity.CENTER);
        tr.setLayoutParams(new TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT));
        TextView t1 = new TextView(getContext());
        t1.setLayoutParams(new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT));
        t1.setText(Html.fromHtml(getString(R.string.createwallet_single_word_display, i + 1, mnemonics.get(i))));
        t1.setPadding(0, 0, rightPadding, bottomPadding);
        tr.addView(t1);
        TextView t2 = new TextView(getContext());
        t2.setText(Html.fromHtml(getString(R.string.createwallet_single_word_display, i + (mnemonics.size() / 2) + 1, mnemonics.get(i + (mnemonics.size() / 2)))));
        t2.setLayoutParams(new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT));
        t2.setPadding(0, 0, 0, bottomPadding);
        tr.addView(t2);
        mBinding.wordsTable.addView(tr);
      }
    } catch (Exception e) {
      log.error("could not initialiaze view", e);
    }

    return mBinding.getRoot();
  }

}

