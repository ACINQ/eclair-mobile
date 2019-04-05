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

package fr.acinq.eclair.wallet.fragments;

import android.annotation.SuppressLint;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.StrikethroughSpan;
import android.transition.TransitionManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import fr.acinq.bitcoin.MnemonicCode$;
import fr.acinq.eclair.wallet.R;
import fr.acinq.eclair.wallet.customviews.VirtualKeyboard;
import fr.acinq.eclair.wallet.databinding.FragmentWalletImportSeedBinding;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.collection.Iterator;

import java.util.ArrayList;
import java.util.List;

public class WalletImportSeedFragment extends Fragment {

  private final Logger log = LoggerFactory.getLogger(WalletImportSeedFragment.class);
  public FragmentWalletImportSeedBinding mBinding;
  final SpannableStringBuilder spanBuilder = new SpannableStringBuilder();
  final SpannableStringBuilder autocompleteSpanBuilder = new SpannableStringBuilder();

  @Override
  public void onCreate(@Nullable Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setHasOptionsMenu(false);
  }

  @SuppressLint("SetTextI18n")
  @Override
  public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    mBinding = DataBindingUtil.inflate(inflater, R.layout.fragment_wallet_import_seed, container, false);
    mBinding.virtualKeyboard.setOnKeyPressedListener(getOnKeyPressedListener());
    mBinding.autocomplete.setMovementMethod(LinkMovementMethod.getInstance());
    mBinding.mnemonicsInput.setCursorVisible(true);
    mBinding.seedError.setOnClickListener(v -> {
      TransitionManager.beginDelayedTransition(mBinding.transitionsLayout);
      mBinding.seedError.setVisibility(View.GONE);
    });
    return mBinding.getRoot();
  }

  @Override
  public void onResume() {
    super.onResume();
    if (mBinding != null) {
      mBinding.mnemonicsInput.setText(spanBuilder);
    }
  }

  /**
   * Apply error style to text if needed, and shows the autocomplete helper. If exact is true, word must be exactly
   * equal to a BIP39 word, otherwise we just check the start.
   */
  private void refreshStyleLastWord(final boolean exact) {
    if (this.spanBuilder.length() > 0) {
      final int lastWordStart = spanBuilder.toString().lastIndexOf(' ') + 1;
      final String lastWord = spanBuilder.subSequence(lastWordStart, spanBuilder.length()).toString();
      final StrikethroughSpan[] spans = spanBuilder.getSpans(lastWordStart, this.spanBuilder.length() - 1, StrikethroughSpan.class);
      for (StrikethroughSpan s : spans) {
        spanBuilder.removeSpan(s);
      }
      final List<String> matches = getBip39MatchingWords(lastWord);
      // -- apply error style if the word does not match any BIP39 words
      if (exact) {
        if (!MnemonicCode$.MODULE$.englishWordlist().contains(lastWord)) {
          spanBuilder.setSpan(new StrikethroughSpan(), lastWordStart, this.spanBuilder.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
      } else {
        if (matches.isEmpty()) {
          spanBuilder.setSpan(new StrikethroughSpan(), lastWordStart, this.spanBuilder.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
      }
      // -- show autocomplete feature if the word does match some BIP39 words
      if (matches.isEmpty()) {
        hideAutocomplete();
      } else if (lastWord.length() < 2) {
        hideAutocomplete();
      } else {
        showAutocomplete(matches);
      }
    }
  }

  private void hideAutocomplete() {
    autocompleteSpanBuilder.clear();
    mBinding.autocomplete.setText(autocompleteSpanBuilder);
  }

  private void showAutocomplete(final List<String> matches) {
    autocompleteSpanBuilder.clear();
    for (final String w : matches) {
      final int start = autocompleteSpanBuilder.length();
      autocompleteSpanBuilder.append(w);
      autocompleteSpanBuilder.setSpan(new ClickableSpan() {
        @Override
        public void onClick(@NonNull View v) {
          hideAutocomplete();
          final int lastWordStart = spanBuilder.toString().lastIndexOf(' ') + 1;
          spanBuilder.delete(lastWordStart, spanBuilder.length());
          spanBuilder.append(w).append(' ');
          mBinding.mnemonicsInput.setText(spanBuilder);
        }
      }, start, autocompleteSpanBuilder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
      autocompleteSpanBuilder.append("    ");
    }
    mBinding.autocomplete.setText(autocompleteSpanBuilder);
  }

  @NonNull
  private VirtualKeyboard.OnKeyPressedListener getOnKeyPressedListener() {
    return keyCode -> {
      // ==== A-Z characters
      if (Character.isAlphabetic(keyCode)) {
        spanBuilder.append(String.valueOf((char) keyCode));
        refreshStyleLastWord(false);
        mBinding.mnemonicsInput.setText(spanBuilder);
      }
      // ==== SPACE key
      else if (Character.isWhitespace(keyCode)) {
        if (spanBuilder.length() > 0 && !Character.isWhitespace(spanBuilder.charAt(spanBuilder.length() - 1))) {
          refreshStyleLastWord(true);
          spanBuilder.append(' ');
          mBinding.mnemonicsInput.setText(spanBuilder);
        }
      }
      // ==== DELETE key
      else if (keyCode == VirtualKeyboard.KEY_DELETE) {
        if (this.spanBuilder.length() == 0) {
          this.spanBuilder.clear();
        } else {
          spanBuilder.delete(this.spanBuilder.length() - 1, this.spanBuilder.length());
          refreshStyleLastWord(false);
        }
        mBinding.mnemonicsInput.setText(this.spanBuilder);
      }
    };
  }

  private @NonNull
  List<String> getBip39MatchingWords(final String start) {
    final List<String> matches = new ArrayList<>();
    final Iterator<String> it = MnemonicCode$.MODULE$.englishWordlist().iterator();
    while (it.hasNext()) {
      final String word = it.next();
      if (word != null && word.startsWith(start)) {
        matches.add(word);
      }
    }
    return matches;
  }

}

