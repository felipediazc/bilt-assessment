package com.rentrewards.challenge.service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which webhook events have already been processed, so that the
 * RewardsEngine can ignore duplicate deliveries from the payment processor.
 *
 * Every processed eventId is remembered, because redeliveries can arrive out
 * of order. Claiming an event is a single atomic operation, so when the same
 * event arrives on several workers at once only one of them can win.
 */
public class ProcessedEventStore {

    private final Set<String> processedEventIds = ConcurrentHashMap.newKeySet();

    /**
     * Atomically claims an event for processing.
     *
     * @return true if this call claimed the event, false if it was already claimed.
     */
    public boolean claim(String eventId) {
        return processedEventIds.add(eventId);
    }

    /**
     * Releases a claim so that a failed attempt can be retried.
     */
    public void release(String eventId) {
        processedEventIds.remove(eventId);
    }
}
