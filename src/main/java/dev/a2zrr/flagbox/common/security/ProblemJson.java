package dev.a2zrr.flagbox.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Writes RFC 7807 problem bodies from filters, where the MVC exception handler is not reached. */
public final class ProblemJson {

    private ProblemJson() {
    }

    public static void write(HttpServletResponse response, ObjectMapper mapper, HttpStatus status,
                             String title, String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/problem+json");
        mapper.writeValue(response.getOutputStream(), Map.of(
            "type", "about:blank",
            "title", title,
            "status", status.value(),
            "detail", detail));
    }
}
