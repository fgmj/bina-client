package br.com.bomgas.bina;

import android.content.Context;
import android.widget.EditText;
import android.widget.RadioGroup;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Lifecycle coverage requires Android; JVM domain models are unchanged by this migration. */
@RunWith(AndroidJUnit4.class)
public class SettingsActivityTest {
    private final Context context = ApplicationProvider.getApplicationContext();

    @Before public void prepare() {
        clearDraft();
        new AppSettings(context).select(EnvironmentConfig.production());
    }

    @After public void clearDraft() {
        context.getSharedPreferences("settings-draft", Context.MODE_PRIVATE).edit().clear().commit();
    }

    private void edit(SettingsActivity activity) {
        ((RadioGroup) activity.findViewById(R.id.environmentChoice)).check(R.id.chooseLocal);
        ((EditText) activity.findViewById(R.id.localAddress)).setText("192.168.1.42");
        ((EditText) activity.findViewById(R.id.localPort)).setText("9090");
        ((EditText) activity.findViewById(R.id.deviceName)).setText("Recepção em edição");
    }

    private void assertDraft(SettingsActivity activity) {
        assertEquals(R.id.chooseLocal, ((RadioGroup) activity.findViewById(R.id.environmentChoice)).getCheckedRadioButtonId());
        assertEquals("192.168.1.42", ((EditText) activity.findViewById(R.id.localAddress)).getText().toString());
        assertEquals("9090", ((EditText) activity.findViewById(R.id.localPort)).getText().toString());
        assertEquals("Recepção em edição", ((EditText) activity.findViewById(R.id.deviceName)).getText().toString());
        assertFalse(new AppSettings(activity).active().local);
    }

    @Test public void rotationKeepsDraftWithoutActivatingEnvironment() {
        try (ActivityScenario<SettingsActivity> scenario = ActivityScenario.launch(SettingsActivity.class)) {
            scenario.onActivity(this::edit);
            scenario.recreate();
            scenario.onActivity(this::assertDraft);
        }
    }

    @Test public void backAndReopeningKeepUnsavedTyping() {
        try (ActivityScenario<SettingsActivity> scenario = ActivityScenario.launch(SettingsActivity.class)) {
            scenario.onActivity(this::edit);
            scenario.onActivity(activity -> activity.getOnBackPressedDispatcher().onBackPressed());
        }
        try (ActivityScenario<SettingsActivity> scenario = ActivityScenario.launch(SettingsActivity.class)) {
            scenario.onActivity(this::assertDraft);
        }
    }
}
