package dev.a2zrr.flagbox.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Calls the handler methods directly. Their failure paths are hard to reach over
 * HTTP without test-only endpoints, and the contract they implement matters.
 */
class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void unexpectedErrorsReturnAReferenceAndNeverTheInternalMessage() {
        ResponseEntity<ProblemDetail> response = handler.handleUnexpected(
            new IllegalStateException("database password is hunter2"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().getTitle()).isEqualTo("Internal server error");
        assertThat(response.getBody().getDetail()).contains("Reference: ");
        assertThat(response.getBody().getDetail()).doesNotContain("hunter2");
    }

    @Test
    void springOwnErrorsKeepTheirStatus() {
        ResponseEntity<ProblemDetail> response = handler.handleUnexpected(
            new ResponseStatusException(HttpStatus.METHOD_NOT_ALLOWED, "nope"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
    }

    @Test
    void unreadableBodyUsesTheCauseMessageWhenThereIsOne() {
        HttpMessageNotReadableException ex = new HttpMessageNotReadableException(
            "wrapper", new IllegalArgumentException("EQUALS takes exactly one value"), null);

        ResponseEntity<ProblemDetail> response = handler.handleUnreadableBody(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getDetail()).isEqualTo("EQUALS takes exactly one value");
    }

    @Test
    void unreadableBodyWithoutACauseGetsAGenericMessage() {
        ResponseEntity<ProblemDetail> response = handler.handleUnreadableBody(
            new HttpMessageNotReadableException("bad json", (Throwable) null, null));

        assertThat(response.getBody().getDetail()).contains("missing or is not valid JSON");
    }

    @Test
    void aMissingRequiredParameterIsABadRequestNamingIt() {
        ResponseEntity<ProblemDetail> response = handler.handleMissingParameter(
            new org.springframework.web.bind.MissingServletRequestParameterException("flagKey", "String"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().getDetail()).isEqualTo("Missing required parameter 'flagKey'");
    }

    @Test
    void dataIntegrityViolationsBecomeConflicts() {
        ResponseEntity<ProblemDetail> response = handler.handleDataIntegrity(
            new org.springframework.dao.DataIntegrityViolationException("unique key uk_flags_project_key"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().getDetail()).doesNotContain("uk_flags_project_key");
    }
}
