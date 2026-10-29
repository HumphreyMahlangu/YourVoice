package io.github.humphreymahlangu.yourvoice.service;

import io.github.humphreymahlangu.yourvoice.entity.ElectionRegistration;
import io.github.humphreymahlangu.yourvoice.entity.RegisteredLocation;
import io.github.humphreymahlangu.yourvoice.entity.VerificationStatus;
import io.github.humphreymahlangu.yourvoice.entity.VoterVerification;
import io.github.humphreymahlangu.yourvoice.entity.VotingDistrict;

/** Explicit legacy recovery only narrows the originally claimed geography, never changes its boundaries. */
public final class RegistrationLocationPolicy {
    private RegistrationLocationPolicy() { }
    public static RegisteredLocation forRegistration(VoterVerification state, ElectionRegistration previous) {
        if (state.getLocationStatus() != VerificationStatus.VERIFIED) return RegisteredLocation.nationalOnly();
        RegisteredLocation verified = state.getLocation();
        if (previous == null) return verified;
        VotingDistrict claimed = previous.getVotingDistrict();
        if (claimed == null || !same(claimed.getProvince(), verified.provinceName())) return RegisteredLocation.nationalOnly();
        if (!same(claimed.getMunicipality(), verified.municipalityName())) return new RegisteredLocation(verified.getProvince(), null, null);
        if (verified.getDistrict() == null || !claimed.getId().equals(verified.getDistrict().getId()))
            return new RegisteredLocation(verified.getProvince(), verified.getMunicipality(), null);
        return verified;
    }
    private static boolean same(String left, String right) { return left != null && right != null && left.trim().equalsIgnoreCase(right.trim()); }
}
