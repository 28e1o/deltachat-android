package org.thoughtcrime.securesms;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.loader.app.LoaderManager;
import com.b44t.messenger.DcContext;
import com.b44t.messenger.DcEvent;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.material.textfield.TextInputLayout;
import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.thoughtcrime.securesms.components.AvatarSelector;
import org.thoughtcrime.securesms.connect.DcEventCenter;
import org.thoughtcrime.securesms.connect.DcHelper;
import org.thoughtcrime.securesms.contacts.avatars.ResourceContactPhoto;
import org.thoughtcrime.securesms.mms.AttachmentManager;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.profiles.AvatarHelper;
import org.thoughtcrime.securesms.scribbles.ScribbleActivity;
import org.thoughtcrime.securesms.util.Prefs;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.views.ProgressDialog;

public class InstantOnboardingActivity extends BaseActionBarActivity
    implements DcEventCenter.DcEventDelegate {

  private static final String TAG = "InstantOnboardingActivity";
  private static final String RP_DOMAIN = "rp.local";

  private static final int REQUEST_CODE_AVATAR = 1;

  private ImageView avatar;
  private EditText name;
  private TextInputLayout nameInputLayout;
  private TextView invitationText;
  private TextView privacyPolicyBtn;
  private Button signUpBtn;

  private boolean avatarChanged;
  private boolean imageLoaded;
  private String profileName;

  private AttachmentManager attachmentManager;
  private Bitmap avatarBmp;

  private @Nullable ProgressDialog progressDialog;
  private boolean cancelled;

  private DcContext dcContext;

  private ExecutorService executor = Executors.newSingleThreadExecutor();

  @Override
  public void onCreate(Bundle bundle) {
    super.onCreate(bundle);
    setContentView(R.layout.instant_onboarding_activity);
    getSupportActionBar().setDisplayHomeAsUpEnabled(true);

    boolean configured = DcHelper.getContext(this).isConfigured() == 1;

    if (configured) {
      finish();
      return;
    }

    attachmentManager = new AttachmentManager(this, () -> {});
    avatarChanged = false;
    profileName = null;
    registerForEvents();
    initializeResources();
    initializeProfile();
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    menu.clear();
    getMenuInflater().inflate(R.menu.instant_onboarding_menu, menu);
    return super.onPrepareOptionsMenu(menu);
  }

  @Override
  public boolean onOptionsItemSelected(@NonNull MenuItem item) {
    super.onOptionsItemSelected(item);

    int itemId = item.getItemId();
    if (itemId == android.R.id.home) {
      getOnBackPressedDispatcher().onBackPressed();
      return true;
    } else if (itemId == R.id.menu_view_log) {
      startActivity(new Intent(this, LogViewActivity.class));
      return true;
    }

    return false;
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);

    if (resultCode != RESULT_OK) {
      return;
    }

    switch (requestCode) {
      case REQUEST_CODE_AVATAR:
        Uri inputFile = (data != null ? data.getData() : null);
        onFileSelected(inputFile);
        break;

      case ScribbleActivity.SCRIBBLE_REQUEST_CODE:
        setAvatarView(data.getData());
        break;
    }
  }

  @Override
  public void onRequestPermissionsResult(
      int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults);
  }

  @Override
  protected void onPause() {
    super.onPause();

    // Save display name and avatar in the unconfigured profile.
    // If the currently selected profile is configured, then this means that
    // rollbackAccountCreation()
    // was called (see handleOnBackPressed() above), i.e. the newly created profile was removed
    // already
    // and we can't save the display name & avatar.
    if (DcHelper.getContext(this).isConfigured() == 0) {
      final String displayName = name.getText().toString();
      DcHelper.set(
          this, DcHelper.CONFIG_DISPLAY_NAME, TextUtils.isEmpty(displayName) ? null : displayName);

      if (avatarChanged) {
        try {
          AvatarHelper.setSelfAvatar(InstantOnboardingActivity.this, avatarBmp);
          Prefs.setProfileAvatarId(InstantOnboardingActivity.this, new SecureRandom().nextInt());
          avatarChanged = false;
        } catch (IOException e) {
          Log.e(TAG, "Failed to save avatar", e);
        }
      }
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    invalidateOptionsMenu();
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
    DcHelper.getEventCenter(this).removeObservers(this);
    executor.shutdown();
  }

  private void handleIntent() {}

  private void setAvatarView(Uri output) {
    GlideApp.with(this)
        .asBitmap()
        .load(output)
        .skipMemoryCache(true)
        .diskCacheStrategy(DiskCacheStrategy.NONE)
        .centerCrop()
        .override(AvatarHelper.AVATAR_SIZE, AvatarHelper.AVATAR_SIZE)
        .into(
            new CustomTarget<Bitmap>() {
              @Override
              public void onResourceReady(
                  @NonNull Bitmap resource, Transition<? super Bitmap> transition) {
                avatarChanged = true;
                imageLoaded = true;
                avatarBmp = resource;
              }

              @Override
              public void onLoadCleared(@Nullable Drawable placeholder) {}
            });
    GlideApp.with(this)
        .load(output)
        .circleCrop()
        .skipMemoryCache(true)
        .diskCacheStrategy(DiskCacheStrategy.NONE)
        .into(avatar);
  }

  private void onFileSelected(Uri inputFile) {
    if (inputFile == null) {
      inputFile = attachmentManager.getImageCaptureUri();
    }

    AvatarHelper.cropAvatar(this, inputFile);
  }

  private void initializeResources() {
    this.avatar = findViewById(R.id.avatar);
    this.name = findViewById(R.id.name_text);
    this.nameInputLayout = findViewById(R.id.name);
    this.invitationText = findViewById(R.id.invitation_label);
    this.privacyPolicyBtn = findViewById(R.id.privacy_policy_button);
    this.signUpBtn = findViewById(R.id.signup_button);

    // add padding to avoid content hidden behind system bars
    ViewUtil.applyWindowInsets(findViewById(R.id.container));

    invitationText.setVisibility(View.GONE);
    privacyPolicyBtn.setVisibility(View.GONE);

    signUpBtn.setOnClickListener(view -> createProfile());
  }

  private void createOfflineProfile() {
    if (progressDialog != null) {
      progressDialog.dismiss();
      progressDialog = null;
    }

    cancelled = false;

    progressDialog = new ProgressDialog(this);
    progressDialog.setMessage(getResources().getString(R.string.one_moment));
    progressDialog.setCanceledOnTouchOutside(false);
    progressDialog.setCancelable(false);
    progressDialog.show();

    DcHelper.getEventCenter(this).captureNextError();

    new Thread(
            () -> {
              try {
                // Buat akun offline (pseudo configured): tidak ada server, tidak ada SMTP.
                // Cukup untuk self-chat roleplay.
                String localName =
                    profileName == null ? "roleplay" : profileName.trim().toLowerCase(Locale.ROOT);
                localName = localName.replaceAll("[^a-z0-9_.-]", "");
                if (localName.isEmpty()) {
                  localName = "roleplay";
                }
                String addr = localName + "@" + RP_DOMAIN;
                dcContext.setConfig(DcHelper.CONFIG_CONFIGURED_ADDRESS, addr);
                dcContext.setConfig(DcHelper.CONFIG_SELF_STATUS, "Roleplay offline");
                DcHelper.getEventCenter(this).endCaptureNextError();
                progressSuccess();
              } catch (Exception e) {
                DcHelper.getEventCenter(this).endCaptureNextError();
                if (!cancelled) {
                  Util.runOnMain(() -> progressError(e.getMessage()));
                }
              }
            })
        .start();
  }

  private void initializeProfile() {
    File avatarFile = AvatarHelper.getSelfAvatarFile(this);
    if (avatarFile.exists() && avatarFile.length() > 0) {
      imageLoaded = true;
      GlideApp.with(this).load(avatarFile).circleCrop().into(avatar);
    } else {
      imageLoaded = false;
      avatar.setImageDrawable(
          new ResourceContactPhoto(R.drawable.ic_camera_alt_white_24dp)
              .asDrawable(this, getResources().getColor(R.color.grey_400)));
    }
    avatar.setOnClickListener(
        view ->
            new AvatarSelector(
                    this,
                    LoaderManager.getInstance(this),
                    new AvatarSelectedListener(),
                    imageLoaded)
                .show(this, avatar));

    name.setText(DcHelper.get(this, DcHelper.CONFIG_DISPLAY_NAME));
    nameInputLayout.setHint(R.string.pref_your_name);
    getSupportActionBar().setTitle(R.string.onboarding_create_instant_account);
  }

  private void registerForEvents() {
    dcContext = DcHelper.getContext(this);
    DcEventCenter eventCenter = DcHelper.getEventCenter(this);
    eventCenter.addObserver(DcContext.DC_EVENT_CONFIGURE_PROGRESS, this);
  }

  @Override
  public void handleEvent(@NonNull DcEvent event) {
    int eventId = event.getId();

    if (eventId == DcContext.DC_EVENT_CONFIGURE_PROGRESS) {
      long progress = event.getData1Int();
      progressUpdate((int) progress);
    }
  }

  private void progressUpdate(int progress) {
    int percent = progress / 10;
    if (progressDialog != null) {
      progressDialog.setMessage(
          getResources().getString(R.string.one_moment) + String.format(" %d%%", percent));
    }
  }

  private void progressError(String data2) {
    if (progressDialog != null) {
      try {
        progressDialog.dismiss();
      } catch (IllegalArgumentException e) {
        // see https://stackoverflow.com/a/5102572/4557005
        Log.w(TAG, e);
      }
    }
    WelcomeActivity.maybeShowConfigurationError(this, data2);
  }

  private void progressSuccess() {
    if (progressDialog != null) {
      progressDialog.dismiss();
    }

    Intent intent = new Intent(getApplicationContext(), ConversationListActivity.class);
    intent.putExtra(ConversationListActivity.FROM_WELCOME, true);

    startActivity(intent);
    finishAffinity();
  }

  private void createProfile() {
    if (TextUtils.isEmpty(this.name.getText())) {
      Toast.makeText(this, R.string.please_enter_name, Toast.LENGTH_LONG).show();
      return;
    }
    final String name = this.name.getText().toString();
    profileName = name;

    executor.execute(
        () -> {
          Context context = InstantOnboardingActivity.this;
          DcHelper.set(context, DcHelper.CONFIG_DISPLAY_NAME, name);

          boolean result = true;
          if (avatarChanged) {
            try {
              AvatarHelper.setSelfAvatar(InstantOnboardingActivity.this, avatarBmp);
              Prefs.setProfileAvatarId(
                  InstantOnboardingActivity.this, new SecureRandom().nextInt());
            } catch (IOException e) {
              Log.w(TAG, e);
              result = false;
            }
          }

          boolean finalResult = result;
          runOnUiThread(
              () -> {
                if (finalResult) {
                  attachmentManager.cleanup();
                  createOfflineProfile();
                } else {
                  Toast.makeText(InstantOnboardingActivity.this, R.string.error, Toast.LENGTH_LONG)
                      .show();
                }
              });
        });
  }

  private class AvatarSelectedListener implements AvatarSelector.AttachmentClickedListener {
    @Override
    public void onClick(int type) {
      switch (type) {
        case AvatarSelector.ADD_GALLERY:
          AttachmentManager.selectImage(InstantOnboardingActivity.this, REQUEST_CODE_AVATAR);
          break;
        case AvatarSelector.REMOVE_PHOTO:
          avatarBmp = null;
          imageLoaded = false;
          avatarChanged = true;
          avatar.setImageDrawable(
              new ResourceContactPhoto(R.drawable.ic_camera_alt_white_24dp)
                  .asDrawable(
                      InstantOnboardingActivity.this, getResources().getColor(R.color.grey_400)));
          break;
        case AvatarSelector.TAKE_PHOTO:
          attachmentManager.capturePhoto(InstantOnboardingActivity.this, REQUEST_CODE_AVATAR);
          break;
      }
    }

    @Override
    public void onQuickAttachment(Uri inputFile) {
      onFileSelected(inputFile);
    }
  }
}
