package com.rentrewards.challenge.service;

import com.rentrewards.challenge.model.MemberAccount;
import com.rentrewards.challenge.model.PaymentEvent;
import com.rentrewards.challenge.model.PointsResult;
import com.rentrewards.challenge.model.ProcessingOutcome;

import java.time.YearMonth;

/**
 * Orchestrates the processing of an incoming payment webhook event:
 *
 *  1. Claim the event, or ignore it if it is a duplicate delivery.
 *  2. Calculate base points (with linked-account multiplier).
 *  3. Apply streak bonus if the member is eligible.
 *  4. Enforce the monthly points cap per member.
 *  5. Record the points. If anything fails before that, release the claim
 *     so the processor's retry can still award them.
 */
public class RewardsEngine {

    private static final long MONTHLY_POINTS_CAP = 100_000L;

    private final PointsCalculator pointsCalculator;
    private final ProcessedEventStore processedEventStore;

    public RewardsEngine(PointsCalculator pointsCalculator, ProcessedEventStore processedEventStore) {
        this.pointsCalculator = pointsCalculator;
        this.processedEventStore = processedEventStore;
    }

    public PointsResult processPayment(PaymentEvent event, MemberAccount member) {
        if (!processedEventStore.claim(event.getEventId())) {
            return new PointsResult(member.getMemberId(), 0, ProcessingOutcome.DUPLICATE);
        }

        long pointsToAward;
        try {
            pointsToAward = awardPoints(event, member);
        } catch (RuntimeException e) {
            processedEventStore.release(event.getEventId());
            throw e;
        }

        ProcessingOutcome outcome = pointsToAward == 0
                ? ProcessingOutcome.CAPPED
                : ProcessingOutcome.AWARDED;
        return new PointsResult(member.getMemberId(), pointsToAward, outcome);
    }

    private long awardPoints(PaymentEvent event, MemberAccount member) {
        long basePoints = pointsCalculator.calculateBasePoints(event);
        long pointsWithBonus = pointsCalculator.applyStreakBonusIfEligible(
                basePoints, member.getCurrentStreakMonths());

        YearMonth month = YearMonth.from(event.getPaymentDate());
        // Reading the remaining cap and recording the points must be one step;
        // otherwise concurrent events for the same member could all see room
        // under the cap and together exceed it.
        synchronized (member) {
            long alreadyEarnedThisMonth = member.getPointsForMonth(month);
            long remainingCap = Math.max(0, MONTHLY_POINTS_CAP - alreadyEarnedThisMonth);
            long pointsToAward = Math.min(pointsWithBonus, remainingCap);

            member.addPointsForMonth(month, pointsToAward);
            return pointsToAward;
        }
    }
}
