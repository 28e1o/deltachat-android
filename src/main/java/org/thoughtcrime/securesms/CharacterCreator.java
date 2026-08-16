package org.thoughtcrime.securesms;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.thoughtcrime.securesms.connect.DcHelper;
import com.b44t.messenger.DcContact;
import com.b44t.messenger.DcContext;

/**
 * Manages roleplay characters created by the user.
 *
 * <p>Characters are stored locally (SharedPreferences) keyed by their contact id. Each character
 * has a display name, a generated local address and an optional description.
 */
public class CharacterCreator {

  public static final String PREFS_NAME = "rp_characters";
  public static final String KEY_CHARACTERS = "characters";
  private static final String RP_DOMAIN = "rp.local";

  private CharacterCreator() {}

  /** Represents a single roleplay character. */
  public static class Character {
    public int contactId;
    public String name;
    public String address;
    public String description;

    Character(int contactId, String name, String address, String description) {
      this.contactId = contactId;
      this.name = name;
      this.address = address;
      this.description = description;
    }
  }

  public static String toJson(List<Character> characters) {
    JSONArray arr = new JSONArray();
    for (Character c : characters) {
      JSONObject o = new JSONObject();
      try {
        o.put("contactId", c.contactId);
        o.put("name", c.name);
        o.put("address", c.address);
        o.put("description", c.description);
      } catch (JSONException e) {
        // ignore
      }
      arr.put(o);
    }
    return arr.toString();
  }

  public static List<Character> fromJson(String json) {
    List<Character> result = new ArrayList<>();
    if (json == null || json.isEmpty()) return result;
    try {
      JSONArray arr = new JSONArray(json);
      for (int i = 0; i < arr.length(); i++) {
        JSONObject o = arr.getJSONObject(i);
        result.add(
            new Character(
                o.optInt("contactId", 0),
                o.optString("name", ""),
                o.optString("address", ""),
                o.optString("description", "")));
      }
    } catch (JSONException e) {
      // ignore
    }
    return result;
  }

  public static List<Character> getAll(Context context) {
    SharedPreferences prefs =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    return fromJson(prefs.getString(KEY_CHARACTERS, ""));
  }

  public static void save(Context context, List<Character> characters) {
    SharedPreferences prefs =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    prefs.edit().putString(KEY_CHARACTERS, toJson(characters)).apply();
  }

  /** Creates a new character contact with a generated local address. */
  public static Character createCharacter(Context context, String name, String description) {
    DcContext dcContext = DcHelper.getContext(context);
    String localName = name.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_.-]", "");
    if (localName.isEmpty()) localName = "character";
    String address = localName + "@" + RP_DOMAIN;
    int contactId = dcContext.createContact(name, address);
    if (contactId == 0) return null;

    Character character = new Character(contactId, name, address, description);
    List<Character> characters = getAll(context);
    characters.add(character);
    save(context, characters);
    return character;
  }

  public static void deleteCharacter(Context context, int contactId) {
    List<Character> characters = getAll(context);
    for (int i = 0; i < characters.size(); i++) {
      if (characters.get(i).contactId == contactId) {
        characters.remove(i);
        break;
      }
    }
    save(context, characters);
  }

  public static Character findById(Context context, int contactId) {
    for (Character c : getAll(context)) {
      if (c.contactId == contactId) return c;
    }
    return null;
  }

  public static boolean isCharacter(DcContact contact) {
    return contact.getAddr() != null && contact.getAddr().endsWith("@" + RP_DOMAIN);
  }
}