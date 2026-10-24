package com.fourgtss.mnp.model;

/**
 * Who owns a phone number's range and who serves the number now.
 *
 * @param rangeOwner    the operator whose number range contains the number
 * @param currentHolder the operator serving the number now; the range owner unless it was ported away
 */
public record NumberOwnership(Operator rangeOwner, Operator currentHolder) {

    /** A number ported back to its range owner counts as not ported. */
    public boolean isPorted() {
        return !currentHolder.equals(rangeOwner);
    }
}
