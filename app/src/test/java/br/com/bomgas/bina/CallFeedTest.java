package br.com.bomgas.bina;

import org.junit.Test;
import static org.junit.Assert.*;

public class CallFeedTest {
    private static final long MIN = 60_000;

    @Test public void ringingCallBecomesTheHighlightedCurrentCall() {
        CallFeed feed = new CallFeed();
        feed.onEvent("RINGING", 1, "11987654321", 1000);
        CallFeed.Call current = feed.current(1000);
        assertEquals(CallFeed.Phase.RINGING, current.phase);
        assertEquals("11987654321", current.number);
    }

    @Test public void answeredThenEndedCallKeepsNumberAndDuration() {
        CallFeed feed = new CallFeed();
        feed.onEvent("RINGING", 1, "11987654321", 0);
        feed.onEvent("OFFHOOK", 1, "", 10_000);
        assertEquals(CallFeed.Phase.ANSWERED, feed.current(10_000).phase);
        feed.onEvent("IDLE", 1, "", 70_000);
        CallFeed.Call ended = feed.current(70_000);
        assertEquals(CallFeed.Phase.ENDED, ended.phase);
        assertEquals("11987654321", ended.number);
        assertEquals(60, ended.durationSeconds);
    }

    @Test public void ringingThatStopsWithoutAnswerIsMissed() {
        CallFeed feed = new CallFeed();
        feed.onEvent("RINGING", 1, "6133334444", 0);
        feed.onEvent("IDLE", 1, "6133334444", 20_000);
        assertEquals(CallFeed.Phase.MISSED, feed.current(20_000).phase);
    }

    @Test public void simultaneousLinesAreTrackedPerSubscription() {
        CallFeed feed = new CallFeed();
        feed.onEvent("RINGING", 1, "111", 0);
        feed.onEvent("RINGING", 2, "222", 1000);
        feed.onEvent("IDLE", 1, "", 2000);
        assertEquals(2, feed.recent().size());
        assertEquals("222", feed.recent().get(0).number);
        assertEquals(CallFeed.Phase.RINGING, feed.recent().get(0).phase);
        assertEquals(CallFeed.Phase.MISSED, feed.recent().get(1).phase);
        assertEquals(CallFeed.Phase.RINGING, feed.current(2000).phase);
    }

    @Test public void eventsWithoutAKnownRingingCallAreIgnored() {
        CallFeed feed = new CallFeed();
        feed.onEvent("OFFHOOK", 1, "", 0);
        feed.onEvent("IDLE", 1, "", 0);
        assertNull(feed.current(0));
        assertTrue(feed.recent().isEmpty());
    }

    @Test public void staleRingingIsNotShownAsRingingForever() {
        CallFeed feed = new CallFeed();
        feed.onEvent("RINGING", 1, "111", 0);
        assertEquals(CallFeed.Phase.RINGING, feed.current(2 * MIN).phase);
        assertEquals(CallFeed.Phase.UNKNOWN, feed.current(4 * MIN).phase);
    }

    @Test public void keepsOnlyTheLatestTwentyCalls() {
        CallFeed feed = new CallFeed();
        for (int i = 0; i < 25; i++) {
            feed.onEvent("RINGING", 1, "1" + i, i);
            feed.onEvent("IDLE", 1, "", i);
        }
        assertEquals(20, feed.recent().size());
        assertEquals("124", feed.recent().get(0).number);
    }

    @Test public void survivesSerializationAndIgnoresCorruptStorage() {
        CallFeed feed = new CallFeed();
        feed.onEvent("RINGING", 1, "111", 5);
        CallFeed restored = CallFeed.fromJson(feed.toJson());
        assertEquals("111", restored.current(5).number);
        assertTrue(CallFeed.fromJson("not json").recent().isEmpty());
        assertTrue(CallFeed.fromJson(null).recent().isEmpty());
    }
}
