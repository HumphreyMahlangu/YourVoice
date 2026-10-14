package io.github.humphreymahlangu.votetrust.service;

import io.github.humphreymahlangu.votetrust.entity.Contest;
import io.github.humphreymahlangu.votetrust.entity.VotingDistrict;

/**
 * Geographic eligibility rule shared by credential issuance and turnout calculation, so both always agree on
 * which registered voters a contest covers.
 */
public final class ContestEligibility {

    private ContestEligibility() {
    }

    public static boolean isEligible(Contest contest, VotingDistrict votingDistrict) {
        return isEligible(contest, votingDistrict.getProvince(), votingDistrict.getMunicipality(), votingDistrict.getWardNumber());
    }

    public static boolean isEligible(Contest contest, String province, String municipality, Integer wardNumber) {
        return switch (contest.getType()) {
            case NATIONAL -> true;
            case PROVINCIAL -> sameText(contest.getScopeProvince(), province);
            case MUNICIPAL_PR -> sameText(contest.getScopeProvince(), province)
                    && sameText(contest.getScopeMunicipality(), municipality);
            case MUNICIPAL_WARD -> sameText(contest.getScopeProvince(), province)
                    && sameText(contest.getScopeMunicipality(), municipality)
                    && contest.getScopeWardNumber() != null
                    && contest.getScopeWardNumber().equals(wardNumber);
        };
    }

    private static boolean sameText(String expected, String actual) {
        return expected != null && actual != null && expected.trim().equalsIgnoreCase(actual.trim());
    }
}
