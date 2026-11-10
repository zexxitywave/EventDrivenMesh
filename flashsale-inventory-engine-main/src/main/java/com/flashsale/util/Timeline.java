package com.flashsale.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Timestamped event log used to visualize the exact order in which
 * concurrent buyers READ, CHECK and WRITE the inventory row.
 *
 * Recording is DISABLED by default so normal tests and services are
 * unaffected. The two-buyer demonstration enables it with
 * {@link #enable()}, which resets the clock to t=0.
 *
 * Message prefixes are stable ("READ", "LOCK    request",
 * "LOCK    acquired", "CHECK", "SLEEP", "WRITE") — the demo test
 * relies on them for its automated analysis.
 */
public final class Timeline {

    private static volatile boolean enabled = false;

    private static volatile long epochNanos = System.nanoTime();

    private static final List<Event> EVENTS = new ArrayList<>();

    private Timeline() {
    }

    /** Enables recording, clears previous events, resets t=0. */
    public static synchronized void enable() {
        EVENTS.clear();
        epochNanos = System.nanoTime();
        enabled = true;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    /** Snapshot of the recorded events (empty unless enabled). */
    public static synchronized List<Event> events() {
        return new ArrayList<>(EVENTS);
    }

    /**
     * Records and prints one event attributed to the calling thread.
     * Callers are expected to pass thread names like "Buyer-1".
     */
    public static synchronized void event(String message) {
        if (!enabled) {
            return;
        }
        long ms = (System.nanoTime() - epochNanos) / 1_000_000;
        Event event =
                new Event(ms, Thread.currentThread().getName(), message);

        EVENTS.add(event);

        System.out.printf(
                "  [t=%5d ms] [%s] %s%n",
                ms,
                event.thread(),
                message
        );
    }

    /** A single timestamped reservation step. */
    public record Event(long ms, String thread, String message) {
    }
}
