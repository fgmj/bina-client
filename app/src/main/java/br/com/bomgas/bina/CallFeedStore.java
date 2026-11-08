package br.com.bomgas.bina;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

/**
 * Persists the display-only CallFeed. Called after the outbox committed an event; any failure here
 * is swallowed so it can never affect recording or delivery.
 */
final class CallFeedStore {
    private static final String PREFS = "call-feed";
    private static final String KEY = "feed";
    private static final Object LOCK = new Object();

    private CallFeedStore() {}

    static void record(Context context, String transition, int subscription, String number, long occurredAt) {
        try {
            synchronized (LOCK) {
                SharedPreferences prefs = prefs(context);
                CallFeed feed = CallFeed.fromJson(prefs.getString(KEY, null));
                feed.onEvent(transition, subscription, number, occurredAt);
                prefs.edit().putString(KEY, feed.toJson()).commit();
            }
        } catch (RuntimeException e) {
            Log.w("CallFeedStore", "Falha ao atualizar a lista de chamadas exibida", e);
        }
    }

    static CallFeed load(Context context) {
        synchronized (LOCK) {
            return CallFeed.fromJson(prefs(context).getString(KEY, null));
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
