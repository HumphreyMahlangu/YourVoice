package io.github.humphreymahlangu.yourvoice.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.humphreymahlangu.yourvoice.dto.*;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class SensitiveDtoLoggingTest {
    static Stream<Class<?>> sensitiveDtos() {
        return Stream.of(RegisterRequest.class, LoginRequest.class, AdminBootstrapRequest.class, CreateAdminAccountRequest.class,
                RefreshTokenRequest.class, AuthResponse.class, BallotCastRequest.class, VotingCredentialRequest.class,
                VotingCredentialResponse.class, CreateSimulationVoterRequest.class, RequestLocationCorrection.class,
                ReviewLocationCorrection.class, ProfileVerificationResponse.class, LocationCorrectionResponse.class,
                ProfileAuditResponse.class, SimulationVoterResponse.class, ElectionRegistrationResponse.class, UserAccountResponse.class);
    }
    static Object fixture(Class<?> type) throws Exception {
        if (type == String.class) return "SENSITIVE_FIXTURE";
        if (type == boolean.class) return true;
        if (type == Integer.class || type == int.class) return 1;
        if (type == UUID.class) return UUID.fromString("11111111-1111-1111-1111-111111111111");
        if (type == Instant.class) return Instant.parse("2026-10-29T12:00:00Z");
        if (type == List.class) return List.of();
        if (type.isEnum()) return type.getEnumConstants()[0];
        var components = type.getRecordComponents();
        Object[] args = new Object[components.length];
        for (int n = 0; n < args.length; n++) args[n] = fixture(components[n].getType());
        return type.getDeclaredConstructor(Arrays.stream(components).map(java.lang.reflect.RecordComponent::getType).toArray(Class<?>[]::new)).newInstance(args);
    }
    @ParameterizedTest
    @MethodSource("sensitiveDtos")
    void frameworkDebugStringsAreRedactedButJsonRemainsUsable(Class<?> type) throws Exception {
        Object dto = fixture(type);
        assertThat(dto.toString()).isEqualTo(type.getSimpleName() + "[redacted]");
        var mapper = new ObjectMapper().findAndRegisterModules();
        var json = mapper.valueToTree(dto);
        assertThat(json.size()).isEqualTo(type.getRecordComponents().length);
        assertThat(json.toString()).contains("SENSITIVE_FIXTURE");
    }
}
