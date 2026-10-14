package io.github.humphreymahlangu.votetrust.service;

import io.github.humphreymahlangu.votetrust.dto.BallotCastRequest;
import io.github.humphreymahlangu.votetrust.dto.BallotCastResponse;
import io.github.humphreymahlangu.votetrust.dto.BallotKeyResponse;
import io.github.humphreymahlangu.votetrust.dto.VotingCredentialRequest;
import io.github.humphreymahlangu.votetrust.dto.VotingCredentialResponse;
import io.github.humphreymahlangu.votetrust.entity.Contest;
import io.github.humphreymahlangu.votetrust.entity.ContestOption;
import io.github.humphreymahlangu.votetrust.entity.ContestSigningKey;
import io.github.humphreymahlangu.votetrust.entity.ContestStatus;
import io.github.humphreymahlangu.votetrust.entity.Election;
import io.github.humphreymahlangu.votetrust.entity.ElectionRegistration;
import io.github.humphreymahlangu.votetrust.entity.ElectionStatus;
import io.github.humphreymahlangu.votetrust.entity.RegistrationStatus;
import io.github.humphreymahlangu.votetrust.entity.VoterProfile;
import io.github.humphreymahlangu.votetrust.entity.VotingDistrict;
import io.github.humphreymahlangu.votetrust.entity.VotingRight;
import io.github.humphreymahlangu.votetrust.exception.DuplicateResourceException;
import io.github.humphreymahlangu.votetrust.exception.EligibilityException;
import io.github.humphreymahlangu.votetrust.exception.InvalidBallotTokenException;
import io.github.humphreymahlangu.votetrust.exception.InvalidVotingCredentialException;
import io.github.humphreymahlangu.votetrust.exception.ResourceNotFoundException;
import io.github.humphreymahlangu.votetrust.exception.VotingClosedException;
import io.github.humphreymahlangu.votetrust.repository.AnonymousVotingCredentialRepository;
import io.github.humphreymahlangu.votetrust.repository.ContestOptionRepository;
import io.github.humphreymahlangu.votetrust.repository.ContestRepository;
import io.github.humphreymahlangu.votetrust.repository.ElectionRegistrationRepository;
import io.github.humphreymahlangu.votetrust.repository.VotingRightRepository;
import io.github.humphreymahlangu.votetrust.security.BlindSignatureService;
import io.github.humphreymahlangu.votetrust.security.VoteCredentialService;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VotingService {

    private final ContestRepository contestRepository;
    private final ContestOptionRepository contestOptionRepository;
    private final ElectionRegistrationRepository electionRegistrationRepository;
    private final VotingRightRepository votingRightRepository;
    private final AnonymousVotingCredentialRepository anonymousVotingCredentialRepository;
    private final VoteCredentialService voteCredentialService;
    private final BlindSignatureService blindSignatureService;
    private final BallotSigningKeyService ballotSigningKeyService;
    private final VoteHashChainService voteHashChainService;
    private final Clock clock;

    public VotingService(
            ContestRepository contestRepository,
            ContestOptionRepository contestOptionRepository,
            ElectionRegistrationRepository electionRegistrationRepository,
            VotingRightRepository votingRightRepository,
            AnonymousVotingCredentialRepository anonymousVotingCredentialRepository,
            VoteCredentialService voteCredentialService,
            BlindSignatureService blindSignatureService,
            BallotSigningKeyService ballotSigningKeyService,
            VoteHashChainService voteHashChainService,
            Clock clock
    ) {
        this.contestRepository = contestRepository;
        this.contestOptionRepository = contestOptionRepository;
        this.electionRegistrationRepository = electionRegistrationRepository;
        this.votingRightRepository = votingRightRepository;
        this.anonymousVotingCredentialRepository = anonymousVotingCredentialRepository;
        this.voteCredentialService = voteCredentialService;
        this.blindSignatureService = blindSignatureService;
        this.ballotSigningKeyService = ballotSigningKeyService;
        this.voteHashChainService = voteHashChainService;
        this.clock = clock;
    }

    /**
     * Returns the contest's public ballot key once voting has opened. Keys are normally created when the contest
     * opens; contests opened before blind signatures existed get theirs on first request.
     */
    @Transactional
    public BallotKeyResponse getBallotKey(UUID electionId, UUID contestId) {
        Contest contest = findContest(electionId, contestId);
        ContestSigningKey signingKey = switch (contest.getStatus()) {
            case DRAFT -> throw new VotingClosedException("The ballot key is published when voting opens");
            case OPEN -> ballotSigningKeyService.getOrCreate(contestId);
            case CLOSED -> ballotSigningKeyService.find(contestId)
                    .orElseThrow(() -> new ResourceNotFoundException("This contest has no ballot key"));
        };
        return new BallotKeyResponse(
                contestId,
                signingKey.getModulus(),
                signingKey.getPublicExponent(),
                BlindSignatureService.fingerprint(BlindSignatureService.decodeInteger(signingKey.getModulus())),
                BlindSignatureService.TOKEN_DOMAIN
        );
    }

    /**
     * Signs a blinded ballot token for an eligible voter, once per contest. Repeating the request with the same
     * blinded token returns the same signature, so a voter whose response was lost can retry safely; a different
     * blinded token is refused, so nobody can obtain two ballot signatures.
     */
    @Transactional
    public VotingCredentialResponse issueVotingCredential(
            UUID userAccountId,
            UUID electionId,
            UUID contestId,
            VotingCredentialRequest request
    ) {
        Contest contest = findContest(electionId, contestId);
        Election election = contest.getElection();
        assertVotingOpen(contest, election);

        ElectionRegistration registration = electionRegistrationRepository
                .findByVoterProfileUserAccountIdAndElectionIdAndStatus(
                        userAccountId,
                        electionId,
                        RegistrationStatus.ACTIVE
                )
                .orElseThrow(() -> new EligibilityException("Voter is not registered for this election"));
        VoterProfile voterProfile = registration.getVoterProfile();
        assertOldEnoughToVote(voterProfile, election);
        assertVotingDistrictEligible(contest, registration.getVotingDistrict());

        ContestSigningKey signingKey = ballotSigningKeyService.getOrCreate(contestId);
        BigInteger modulus = BlindSignatureService.decodeInteger(signingKey.getModulus());
        BigInteger blindedToken = decodeBlindedToken(request.blindedToken());
        if (!blindSignatureService.isValidBlindedToken(blindedToken, modulus)) {
            throw new InvalidBallotTokenException("Blinded ballot token is not valid for this contest's ballot key");
        }
        String blindedTokenHash = sha256Hex(BlindSignatureService.encodeInteger(blindedToken));

        votingRightRepository.insertIfAbsent(UUID.randomUUID(), voterProfile.getId(), contestId);
        VotingRight votingRight = votingRightRepository
                .findByVoterProfileIdAndContestId(voterProfile.getId(), contestId)
                .orElseThrow(() -> new IllegalStateException("Voting right was not created"));
        if (votingRight.hasCredentialIssued()) {
            if (!blindedTokenHash.equals(votingRight.getBlindedTokenHash())) {
                throw new DuplicateResourceException("A voting credential has already been issued for this contest");
            }
        } else {
            votingRight.markCredentialIssued(blindedTokenHash);
        }

        BigInteger blindSignature = blindSignatureService.signBlindedToken(
                blindedToken,
                ballotSigningKeyService.privateKey(signingKey)
        );
        return new VotingCredentialResponse(
                election.getId(),
                contest.getId(),
                BlindSignatureService.encodeInteger(blindSignature),
                BlindSignatureService.fingerprint(modulus),
                election.getVotingEndAt()
        );
    }

    @Transactional
    public BallotCastResponse castBallot(BallotCastRequest request) {
        ContestOption contestOption = contestOptionRepository
                .findByIdAndContestId(request.contestOptionId(), request.contestId())
                .orElseThrow(() -> new ResourceNotFoundException("Contest option not found"));
        Contest contest = contestOption.getContest();
        Election election = contest.getElection();
        assertVotingOpen(contest, election);

        ContestSigningKey signingKey = ballotSigningKeyService.find(contest.getId())
                .orElseThrow(InvalidVotingCredentialException::new);
        boolean signatureValid = blindSignatureService.verify(
                contest.getId(),
                request.ballotToken(),
                BlindSignatureService.decodeInteger(request.signature()),
                BlindSignatureService.decodeInteger(signingKey.getModulus()),
                BlindSignatureService.decodeInteger(signingKey.getPublicExponent())
        );
        if (!signatureValid) {
            throw new InvalidVotingCredentialException();
        }

        String spentTokenHash = voteCredentialService.hashCredential(contest.getId() + "|" + request.ballotToken());
        int spent = anonymousVotingCredentialRepository.insertSpentIfAbsent(
                UUID.randomUUID(),
                contest.getId(),
                spentTokenHash,
                election.getVotingEndAt()
        );
        if (spent == 0) {
            throw new DuplicateResourceException("Voting credential has already been used");
        }

        voteHashChainService.appendVote(contest, contestOption, Instant.now(clock));

        return new BallotCastResponse(
                contest.getId(),
                true,
                "Ballot accepted"
        );
    }

    private Contest findContest(UUID electionId, UUID contestId) {
        return contestRepository.findByIdAndElectionId(contestId, electionId)
                .orElseThrow(() -> new ResourceNotFoundException("Contest not found"));
    }

    private BigInteger decodeBlindedToken(String blindedToken) {
        try {
            return BlindSignatureService.decodeInteger(blindedToken);
        } catch (IllegalArgumentException exception) {
            throw new InvalidBallotTokenException("Blinded ballot token must be unpadded base64url");
        }
    }

    private void assertVotingOpen(Contest contest, Election election) {
        Instant now = Instant.now(clock);
        boolean withinVotingWindow = !now.isBefore(election.getVotingStartAt())
                && !now.isAfter(election.getVotingEndAt());

        if (election.getStatus() != ElectionStatus.VOTING_OPEN
                || contest.getStatus() != ContestStatus.OPEN
                || !withinVotingWindow) {
            throw new VotingClosedException("Election voting period is closed");
        }
    }

    private void assertOldEnoughToVote(VoterProfile voterProfile, Election election) {
        LocalDate votingDate = LocalDate.ofInstant(election.getVotingStartAt(), ZoneOffset.UTC);
        if (voterProfile.getDateOfBirth().plusYears(18).isAfter(votingDate)) {
            throw new EligibilityException("Voters must be at least 18 years old to vote");
        }
    }

    private void assertVotingDistrictEligible(Contest contest, VotingDistrict votingDistrict) {
        if (!ContestEligibility.isEligible(contest, votingDistrict)) {
            throw new EligibilityException("Voter is not eligible for this contest based on registered voting district");
        }
    }

    private String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
