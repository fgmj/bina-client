package br.com.bomgas.bina;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class IncomingCallTrackerTest {
    @Test
    public void answeredCallProducesOneEventPerTransitionAndConversationDuration() {
        IncomingCallTracker tracker = new IncomingCallTracker();
        assertEquals("CALL_RECEIVED", tracker.ringing(1, "1130047717", "11999990000").type);
        assertEquals("CALL_ANSWERED_INCOMING", tracker.answered(1, 10000).type);
        IncomingCallTracker.Event ended = tracker.ended(1, 22500);
        assertEquals("CALL_ENDED", ended.type);
        assertEquals(12, ended.durationSeconds);
        assertEquals("1130047717", ended.phoneNumber);
        assertEquals("11999990000", ended.receivingNumber);
    }

    @Test
    public void repeatedBroadcastsDoNotReopenOrRestartTheConversation() {
        IncomingCallTracker tracker = new IncomingCallTracker();
        tracker.ringing(1, "1130047717", "11999990000");
        assertNull(tracker.ringing(1, "1130047717", "11999990000"));
        tracker.answered(1, 10000);
        assertNull(tracker.answered(1, 15000));
        assertNull(tracker.ringing(1, "1130047717", "11999990000"));
        assertEquals(10, tracker.ended(1, 20000).durationSeconds);
        assertNull(tracker.ended(1, 21000));
    }

    @Test
    public void unansweredCallIsMissedWithNoConversation() {
        IncomingCallTracker tracker = new IncomingCallTracker();
        tracker.ringing(1, "1130047717", "11999990000");
        IncomingCallTracker.Event missed = tracker.ended(1, 30000);
        assertEquals("CALL_MISSED", missed.type);
        assertEquals(0, missed.durationSeconds);
    }

    @Test
    public void endingOneSimDoesNotEndTheOther() {
        IncomingCallTracker tracker = new IncomingCallTracker();
        tracker.ringing(1, "1130047717", "11999990000");
        tracker.ringing(2, "1130047718", "11999990001");
        tracker.answered(2, 10000);
        assertEquals("CALL_MISSED", tracker.ended(1, 15000).type);
        IncomingCallTracker.Event ended = tracker.ended(2, 20000);
        assertEquals("1130047718", ended.phoneNumber);
        assertEquals(10, ended.durationSeconds);
    }

    @Test
    public void unknownSubscriptionDoesNotEndAnArbitraryKnownSim() {
        IncomingCallTracker tracker = new IncomingCallTracker();
        tracker.ringing(1, "1130047717", "11999990000");
        assertNull(tracker.answered(-1, 10000));
        assertNull(tracker.ended(-1, 20000));
        assertEquals("CALL_MISSED", tracker.ended(1, 30000).type);
    }

    @Test
    public void hiddenNumberStillProducesAReceivedAndMissedEvent() {
        IncomingCallTracker tracker = new IncomingCallTracker();
        assertEquals("", tracker.ringing(1, null, "11999990000").phoneNumber);
        assertEquals("CALL_MISSED", tracker.ended(1, 10000).type);
    }

    @Test
    public void enrichedRingRetainsTheNumberWithoutASecondReceivedEvent() {
        IncomingCallTracker tracker = new IncomingCallTracker();
        tracker.ringing(1, "", "11999990000");
        assertNull(tracker.ringing(1, "1130047717", "11999990000"));
        assertEquals("1130047717", tracker.ended(1, 10000).phoneNumber);
    }

    @Test
    public void successiveCallsFromTheSameNumberRemainIndependent() {
        IncomingCallTracker tracker = new IncomingCallTracker();
        tracker.ringing(1, "1130047717", "11999990000");
        tracker.answered(1, 1000);
        assertEquals(1, tracker.ended(1, 2000).durationSeconds);
        assertEquals("CALL_RECEIVED", tracker.ringing(1, "1130047717", "11999990000").type);
        assertEquals("CALL_MISSED", tracker.ended(1, 5000).type);
    }

    @Test
    public void offhookWithoutAnObservedIncomingCallDoesNotInventAnAnswer() {
        IncomingCallTracker tracker = new IncomingCallTracker();
        assertNull(tracker.answered(1, 1000));
        assertNull(tracker.ended(1, 2000));
    }

    @Test
    public void durationCannotBeNegative() {
        IncomingCallTracker tracker = new IncomingCallTracker();
        tracker.ringing(-1, "1130047717", "11999990000");
        tracker.answered(-1, 2000);
        assertEquals(0, tracker.ended(-1, 1000).durationSeconds);
    }
}
