package fr.acinq.eclair.wallet.jobs;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.evernote.android.job.Job;
import com.evernote.android.job.JobCreator;

public class EclairJobCreator implements JobCreator {

  @Nullable
  @Override
  public Job create(@NonNull String tag) {
    switch (tag) {
      default:
        return null;
    }
  }
}
