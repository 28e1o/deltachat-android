package org.thoughtcrime.securesms.contacts;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import com.google.android.material.textfield.TextInputEditText;
import org.thoughtcrime.securesms.CharacterCreator;
import org.thoughtcrime.securesms.ConversationActivity;
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.connect.DcHelper;
import org.thoughtcrime.securesms.util.ViewUtil;

public class NewContactActivity extends PassphraseRequiredActionBarActivity {

  public static final String CONTACT_ID_EXTRA = "contact_id";

  private TextInputEditText nameInput;
  private TextInputEditText descriptionInput;

  @Override
  protected void onCreate(Bundle state, boolean ready) {
    setContentView(R.layout.new_contact_activity);

    ActionBar actionBar = getSupportActionBar();
    if (actionBar != null) {
      actionBar.setTitle(R.string.create_character);
      actionBar.setDisplayHomeAsUpEnabled(true);
      actionBar.setHomeAsUpIndicator(R.drawable.ic_close_white_24dp);
    }

    // add padding to avoid content hidden behind system bars
    ViewUtil.applyWindowInsets(findViewById(R.id.content_container));

    nameInput = ViewUtil.findById(this, R.id.name_text);
    descriptionInput = ViewUtil.findById(this, R.id.email_text);
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    MenuInflater inflater = this.getMenuInflater();
    menu.clear();
    inflater.inflate(R.menu.new_contact, menu);
    super.onPrepareOptionsMenu(menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(@NonNull MenuItem item) {
    super.onOptionsItemSelected(item);
    int itemId = item.getItemId();
    if (itemId == android.R.id.home) {
      finish();
      return true;
    } else if (itemId == R.id.menu_create_contact) {
      String name = nameInput.getText() == null ? "" : nameInput.getText().toString().trim();
      String description =
          descriptionInput.getText() == null ? "" : descriptionInput.getText().toString().trim();
      if (name.isEmpty()) {
        Toast.makeText(this, R.string.please_enter_name, Toast.LENGTH_LONG).show();
        return true;
      }
      CharacterCreator.Character character =
          CharacterCreator.createCharacter(this, name, description);
      if (character == null) {
        Toast.makeText(this, R.string.character_create_error, Toast.LENGTH_LONG).show();
        return true;
      }
      if (getCallingActivity() != null) {
        Intent intent = new Intent();
        intent.putExtra(CONTACT_ID_EXTRA, character.contactId);
        setResult(RESULT_OK, intent);
      } else {
        int chatId = DcHelper.getContext(this).createChatByContactId(character.contactId);
        Intent intent = new Intent(this, ConversationActivity.class);
        intent.putExtra(ConversationActivity.CHAT_ID_EXTRA, chatId);
        startActivity(intent);
      }
      finish();
      return true;
    }
    return false;
  }
}
