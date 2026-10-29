package io.github.humphreymahlangu.yourvoice.service;

import io.github.humphreymahlangu.yourvoice.entity.Contest;
import io.github.humphreymahlangu.yourvoice.entity.ElectionRegistration;
import io.github.humphreymahlangu.yourvoice.entity.RegisteredLocation;
import io.github.humphreymahlangu.yourvoice.entity.VotingDistrict;

/**
 * Geographic eligibility rule shared by credential issuance and turnout calculation, so both always agree on
 * which registered voters a contest covers.
 */
public final class ContestEligibility {

    private ContestEligibility() {
    }

    public static boolean isEligible(Contest contest, ElectionRegistration registration) {
        return registration.isIdentityVerified() && isEligible(contest, registration.getRegisteredProvince(),
                registration.getRegisteredMunicipality(), registration.getRegisteredWardNumber());
    }

    public static boolean isEligible(Contest contest, RegisteredLocation location) {
        return isEligible(contest, location.provinceName(), location.municipalityName(), location.wardNumber());
    }

    public static boolean isEligible(Contest contest, VotingDistrict votingDistrict) {
        if (votingDistrict == null) return isEligible(contest, null, null, null);
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
