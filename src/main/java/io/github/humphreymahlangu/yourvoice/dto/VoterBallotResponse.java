package io.github.humphreymahlangu.yourvoice.dto;

import java.util.UUID;
/** Own server-derived decisions; no cast receipts or choices. */
public record VoterBallotResponse(ElectionResponse election, ContestResponse contest, boolean eligible,
        boolean registered, boolean registrationVerified, boolean canRegister, boolean canVote,
        boolean credentialIssued, String message, String registeredProvince, String registeredMunicipality,
        Integer registeredWardNumber) { }
