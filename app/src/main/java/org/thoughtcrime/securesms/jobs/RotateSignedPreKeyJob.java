package org.thoughtcrime.securesms.jobs;


import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.crypto.IdentityKeyUtil;
import org.thoughtcrime.securesms.crypto.PreKeyUtil;
import org.thoughtcrime.securesms.dependencies.InjectableType;
import org.thoughtcrime.securesms.jobmanager.Data;
import org.thoughtcrime.securesms.jobmanager.Job;
import org.thoughtcrime.securesms.jobmanager.impl.NetworkConstraint;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.session.libsignal.libsignal.IdentityKeyPair;
import org.session.libsignal.libsignal.state.SignedPreKeyRecord;
import org.session.libsignal.service.api.SignalServiceAccountManager;
import org.session.libsignal.service.api.push.exceptions.PushNetworkException;

import javax.inject.Inject;

public class RotateSignedPreKeyJob extends BaseJob implements InjectableType {

  public static final String KEY = "RotateSignedPreKeyJob";

  private static final String TAG = RotateSignedPreKeyJob.class.getSimpleName();

  @Inject SignalServiceAccountManager accountManager;

  public RotateSignedPreKeyJob() {
    this(new Job.Parameters.Builder()
                           .addConstraint(NetworkConstraint.KEY)
                           .setMaxAttempts(3)
                           .build());
  }

  private RotateSignedPreKeyJob(@NonNull Job.Parameters parameters) {
    super(parameters);
  }

  @Override
  public @NonNull Data serialize() {
    return Data.EMPTY;
  }

  @Override
  public @NonNull String getFactoryKey() {
    return KEY;
  }

  @Override
  public void onRun() throws Exception {
    Log.i(TAG, "Rotating signed prekey...");

    if (!IdentityKeyUtil.hasIdentityKey(context)) { return; }

    IdentityKeyPair    identityKey        = IdentityKeyUtil.getIdentityKeyPair(context);
    SignedPreKeyRecord signedPreKeyRecord = PreKeyUtil.generateSignedPreKey(context, identityKey, false);

    // Loki - Don't upload the new signed pre key
    // accountManager.setSignedPreKey(signedPreKeyRecord);

    PreKeyUtil.setActiveSignedPreKeyId(context, signedPreKeyRecord.getId());
    TextSecurePreferences.setSignedPreKeyRegistered(context, true);
    TextSecurePreferences.setSignedPreKeyFailureCount(context, 0);

    ApplicationContext.getInstance(context)
                      .getJobManager()
                      .add(new CleanPreKeysJob());
  }

  @Override
  public boolean onShouldRetry(@NonNull Exception exception) {
    return exception instanceof PushNetworkException;
  }

  @Override
  public void onCanceled() {
    TextSecurePreferences.setSignedPreKeyFailureCount(context, TextSecurePreferences.getSignedPreKeyFailureCount(context) + 1);
  }

  public static final class Factory implements Job.Factory<RotateSignedPreKeyJob> {
    @Override
    public @NonNull RotateSignedPreKeyJob create(@NonNull Parameters parameters, @NonNull Data data) {
      return new RotateSignedPreKeyJob(parameters);
    }
  }
}