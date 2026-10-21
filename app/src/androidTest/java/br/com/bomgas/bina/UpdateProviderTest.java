package br.com.bomgas.bina;

import android.content.Context;
import android.net.Uri;
import androidx.core.content.FileProvider;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import java.io.File;
import static org.junit.Assert.*;

public class UpdateProviderTest {
    @Test public void privateApkUsesNonExportedContentProvider() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String authority = context.getPackageName() + ".updates";
        Uri uri = FileProvider.getUriForFile(context, authority, new File(context.getFilesDir(), "updates/test.apk"));
        assertEquals("content", uri.getScheme());
        assertEquals(authority, uri.getAuthority());
        assertFalse(context.getPackageManager().resolveContentProvider(authority, 0).exported);
    }
    @Test public void providerCannotExposeOtherPrivateFiles() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertThrows(IllegalArgumentException.class, () -> FileProvider.getUriForFile(context,
                context.getPackageName() + ".updates", new File(context.getFilesDir(), "private.txt")));
    }
}
