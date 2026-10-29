package io.github.humphreymahlangu.yourvoice.documentation;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.humphreymahlangu.yourvoice.controller.AdminAccountController;
import io.github.humphreymahlangu.yourvoice.controller.ProfileVerificationController;
import io.github.humphreymahlangu.yourvoice.controller.AdminProfileVerificationController;
import io.github.humphreymahlangu.yourvoice.controller.GeographicRecordController;
import io.github.humphreymahlangu.yourvoice.controller.AdminBootstrapController;
import io.github.humphreymahlangu.yourvoice.controller.AdminElectionManagementController;
import io.github.humphreymahlangu.yourvoice.controller.AdminSecurityAuditController;
import io.github.humphreymahlangu.yourvoice.controller.AuthController;
import io.github.humphreymahlangu.yourvoice.controller.BallotController;
import io.github.humphreymahlangu.yourvoice.controller.ContestController;
import io.github.humphreymahlangu.yourvoice.controller.ContestResultController;
import io.github.humphreymahlangu.yourvoice.controller.ElectionController;
import io.github.humphreymahlangu.yourvoice.controller.MyRegistrationController;
import io.github.humphreymahlangu.yourvoice.controller.VotingDistrictController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

class OpenApiAnnotationCoverageTest {

    private static final List<Class<?>> CONTROLLERS = List.of(
                ProfileVerificationController.class, AdminProfileVerificationController.class, GeographicRecordController.class,
            AdminAccountController.class,
            AdminBootstrapController.class,
            AdminElectionManagementController.class,
            AdminSecurityAuditController.class,
            AuthController.class,
            BallotController.class,
            ContestController.class,
            ContestResultController.class,
            ElectionController.class,
            MyRegistrationController.class,
            VotingDistrictController.class
    );

    @Test
    void everyRestControllerHasTagAndErrorResponses() {
        assertThat(CONTROLLERS).allSatisfy(controller -> {
            assertThat(controller.isAnnotationPresent(RestController.class))
                    .as(controller.getSimpleName() + " must be a REST controller")
                    .isTrue();

            Tag tag = controller.getAnnotation(Tag.class);
            assertThat(tag)
                    .as(controller.getSimpleName() + " must have a Swagger tag")
                    .isNotNull();
            assertThat(tag.name()).isNotBlank();
            assertThat(tag.description()).isNotBlank();

            ApiResponses responses = controller.getAnnotation(ApiResponses.class);
            assertThat(responses)
                    .as(controller.getSimpleName() + " must document common error responses")
                    .isNotNull();
            assertThat(responses.value()).isNotEmpty();
        });
    }

    @Test
    void everyMappedHandlerMethodHasOperationSummaryAndDescription() {
        for (Class<?> controller : CONTROLLERS) {
            for (Method method : controller.getDeclaredMethods()) {
                if (!isHandlerMethod(method)) {
                    continue;
                }

                Operation operation = method.getAnnotation(Operation.class);
                assertThat(operation)
                        .as(controller.getSimpleName() + "." + method.getName() + " must document its OpenAPI operation")
                        .isNotNull();
                assertThat(operation.summary()).isNotBlank();
                assertThat(operation.description()).isNotBlank();
            }
        }
    }

    @Test
    void authenticatedOperationsDeclareBearerSecurity() throws NoSuchMethodException {
        assertThat(AdminElectionManagementController.class.getAnnotation(SecurityRequirement.class).name())
                .isEqualTo("bearerAuth");
        assertThat(AdminSecurityAuditController.class.getAnnotation(SecurityRequirement.class).name())
                .isEqualTo("bearerAuth");
        assertThat(MyRegistrationController.class.getAnnotation(SecurityRequirement.class).name())
                .isEqualTo("bearerAuth");
        assertThat(AdminAccountController.class.getAnnotation(SecurityRequirement.class).name())
                .isEqualTo("bearerAuth");

        assertBearerSecurity(AuthController.class.getDeclaredMethod("me", io.github.humphreymahlangu.yourvoice.security.UserPrincipal.class));
        assertBearerSecurity(ElectionController.class.getDeclaredMethod(
                "registerForElection",
                io.github.humphreymahlangu.yourvoice.security.UserPrincipal.class,
                java.util.UUID.class,
                io.github.humphreymahlangu.yourvoice.dto.ElectionRegistrationRequest.class
        ));
        assertBearerSecurity(ContestController.class.getDeclaredMethod(
                "issueVotingCredential",
                io.github.humphreymahlangu.yourvoice.security.UserPrincipal.class,
                java.util.UUID.class,
                java.util.UUID.class,
                io.github.humphreymahlangu.yourvoice.dto.VotingCredentialRequest.class
        ));
    }

    private void assertBearerSecurity(Method method) {
        Operation operation = method.getAnnotation(Operation.class);
        assertThat(operation.security())
                .extracting(SecurityRequirement::name)
                .contains("bearerAuth");
    }

    private boolean isHandlerMethod(Method method) {
        for (Annotation annotation : method.getAnnotations()) {
            Class<? extends Annotation> annotationType = annotation.annotationType();
            if (annotationType == GetMapping.class
                    || annotationType == PostMapping.class
                    || annotationType == PutMapping.class
                    || annotationType == PatchMapping.class
                    || annotationType == DeleteMapping.class) {
                return true;
            }
        }
        return false;
    }
}
