package br.com.bomgas.bina;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Display model of recent calls, fed only after the durable outbox accepted an event.
 * It never decides what is recorded or delivered; phases mirror the observed transitions.
 */
public final class CallFeed {
    public enum Phase { RINGING, ANSWERED, ENDED, MISSED, UNKNOWN }

    public static final class Call {
        public String number = "";
        public Phase phase;
        public int subscription;
        public long startedAt, answeredAt, updatedAt;
        public long durationSeconds;

        Call copyAs(Phase shown) {
            Call copy = new Call();
            copy.number = number;
            copy.phase = shown;
            copy.subscription = subscription;
            copy.startedAt = startedAt;
            copy.answeredAt = answeredAt;
            copy.updatedAt = updatedAt;
            copy.durationSeconds = durationSeconds;
            return copy;
        }

        public boolean active() { return phase == Phase.RINGING || phase == Phase.ANSWERED; }
    }

    static final int LIMIT = 20;
    /** Without a later transition a ringing call cannot be trusted as still ringing. */
    static final long RINGING_STALE_MS = 3 * 60_000L;
    static final long ANSWERED_STALE_MS = 4 * 60 * 60_000L;
    private static final Gson GSON = new Gson();

    private List<Call> calls = new ArrayList<>();

    public void onEvent(String transition, int subscription, String number, long at) {
        String clean = number == null ? "" : number;
        if ("RINGING".equals(transition)) {
            Call call = new Call();
            call.number = clean;
            call.phase = Phase.RINGING;
            call.subscription = subscription;
            call.startedAt = at;
            call.updatedAt = at;
            calls.add(0, call);
            while (calls.size() > LIMIT) calls.remove(calls.size() - 1);
            return;
        }
        Call call = openCall(subscription);
        if (call == null) return;
        if (call.number.isEmpty() && !clean.isEmpty()) call.number = clean;
        call.updatedAt = at;
        if ("OFFHOOK".equals(transition) && call.phase == Phase.RINGING) {
            call.phase = Phase.ANSWERED;
            call.answeredAt = at;
        } else if ("IDLE".equals(transition)) {
            if (call.phase == Phase.ANSWERED) {
                call.phase = Phase.ENDED;
                call.durationSeconds = Math.max(0, (at - call.answeredAt) / 1000);
            } else {
                call.phase = Phase.MISSED;
            }
        }
    }

    private Call openCall(int subscription) {
        for (Call call : calls) if (call.subscription == subscription && call.active()) return call;
        return null;
    }

    /** The call to highlight: an active one first, otherwise the latest. Null when there is none. */
    public Call current(long now) {
        Call chosen = null;
        for (Call call : calls) if (call.active()) { chosen = call; break; }
        if (chosen == null && !calls.isEmpty()) chosen = calls.get(0);
        if (chosen == null) return null;
        long quiet = now - chosen.updatedAt;
        if ((chosen.phase == Phase.RINGING && quiet > RINGING_STALE_MS)
                || (chosen.phase == Phase.ANSWERED && quiet > ANSWERED_STALE_MS)) {
            return chosen.copyAs(Phase.UNKNOWN);
        }
        return chosen.copyAs(chosen.phase);
    }

    public List<Call> recent() { return Collections.unmodifiableList(calls); }

    public String toJson() { return GSON.toJson(this); }

    public static CallFeed fromJson(String json) {
        try {
            CallFeed feed = json == null ? null : GSON.fromJson(json, CallFeed.class);
            if (feed == null || feed.calls == null) return new CallFeed();
            feed.calls.removeIf(call -> call == null || call.phase == null);
            return feed;
        } catch (RuntimeException e) {
            return new CallFeed();
        }
    }
}
