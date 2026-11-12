package br.com.bomgas.bina;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import com.google.android.material.appbar.MaterialToolbar;

/** Settings owns only its form; monitoring and delivery retain their existing owners. */
public final class SettingsActivity extends AppCompatActivity {
    private SettingsController controller;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_settings);
        MaterialToolbar toolbar = findViewById(R.id.settingsToolbar);
        toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
        controller = new SettingsController(this, message ->
                LocalBroadcastManager.getInstance(this).sendBroadcast(
                        new Intent("br.com.bomgas.bina.NEW_LOG").putExtra("message", message)), () -> {});
        controller.bindSettings(findViewById(R.id.settingsRoot),
                state != null ? state.getBundle("draft") : readDraft(), this::storeDraft, () -> {
                    drafts().edit().clear().apply();
                    finish();
                });
    }

    private SharedPreferences drafts() {
        return getSharedPreferences("settings-draft", Context.MODE_PRIVATE);
    }

    void storeDraft(Bundle draft) {
        currentDraft = draft;
        SharedPreferences.Editor editor = drafts().edit().clear().putBoolean("present", true);
        for (String key : new String[]{"address", "port", "name", "base"}) {
            editor.putString(key, draft.getString(key));
        }
        editor.putBoolean("local", draft.getBoolean("local"));
        if (draft.containsKey("revision")) editor.putLong("revision", draft.getLong("revision"));
        editor.apply();
    }

    private Bundle currentDraft;

    private Bundle readDraft() {
        SharedPreferences prefs = drafts();
        if (!prefs.getBoolean("present", false)) return null;
        Bundle draft = new Bundle();
        for (String key : new String[]{"address", "port", "name", "base"}) {
            draft.putString(key, prefs.getString(key, ""));
        }
        draft.putBoolean("local", prefs.getBoolean("local", false));
        if (prefs.contains("revision")) draft.putLong("revision", prefs.getLong("revision", 0));
        return draft;
    }

    @Override protected void onSaveInstanceState(@NonNull Bundle state) {
        controller.saveDraft();
        state.putBundle("draft", currentDraft);
        super.onSaveInstanceState(state);
    }

    @Override protected void onStop() {
        controller.saveDraft();
        super.onStop();
    }

    @Override protected void onResume() {
        super.onResume();
        LocationTracking.ensure(this);
        LocationSettings.bind(this, findViewById(R.id.settingsRoot));
    }

    @Override public void onRequestPermissionsResult(int code, @NonNull String[] permissions,
            @NonNull int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        LocationSettings.onPermissionResult(this, code);
    }
}
