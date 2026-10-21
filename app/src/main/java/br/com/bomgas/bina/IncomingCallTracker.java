package br.com.bomgas.bina;

import java.util.HashMap;
import java.util.Map;

/** Tracks observed incoming calls per subscription; DurableCalls persists this state atomically. */
final class IncomingCallTracker {
    static final class Event {
        final String type;
        final String phoneNumber;
        final String receivingNumber;
        final long durationSeconds;

        Event(String type, Call call, long durationSeconds) {
            this.type = type;
            this.phoneNumber = call.phoneNumber;
            this.receivingNumber = call.receivingNumber;
            this.durationSeconds = durationSeconds;
        }
    }

    private static final class Call {
        String phoneNumber;
        final String receivingNumber;
        Long answeredAt;

        Call(String phoneNumber, String receivingNumber) {
            this.phoneNumber = phoneNumber;
            this.receivingNumber = receivingNumber;
        }
    }

    private final Map<Integer, Call> calls = new HashMap<>();

    void forget(int subscriptionId) { calls.remove(subscriptionId); }

    Event ringing(int subscriptionId, String phoneNumber, String receivingNumber) {
        String number = phoneNumber == null ? "" : phoneNumber;
        Call existing = calls.get(subscriptionId);
        if (existing != null) {
            // An enriched repeat must not produce a second ring or reset the answer time.
            if (existing.phoneNumber.isEmpty()) {
                existing.phoneNumber = number;
            }
            return null;
        }
        Call call = new Call(number, receivingNumber);
        calls.put(subscriptionId, call);
        return new Event("CALL_RECEIVED", call, 0);
    }

    Event answered(int subscriptionId, long elapsedMillis) {
        Call call = calls.get(subscriptionId);
        if (call == null || call.answeredAt != null) {
            return null;
        }
        call.answeredAt = elapsedMillis;
        return new Event("CALL_ANSWERED_INCOMING", call, 0);
    }

    Event ended(int subscriptionId, long elapsedMillis) {
        Call call = calls.remove(subscriptionId);
        if (call == null) {
            return null;
        }
        if (call.answeredAt == null) {
            return new Event("CALL_MISSED", call, 0);
        }
        long seconds = Math.max(0, elapsedMillis - call.answeredAt) / 1000;
        return new Event("CALL_ENDED", call, seconds);
    }
}
