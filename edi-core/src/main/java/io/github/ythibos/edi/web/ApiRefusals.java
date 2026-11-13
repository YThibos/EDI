package io.github.ythibos.edi.web;

import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import io.github.ythibos.edi.adapter.process.ProgramFailure;
import io.github.ythibos.edi.adapter.registry.UnreadableRegistry;
import io.github.ythibos.edi.adapter.tracker.TrackerUnavailable;
import io.github.ythibos.edi.domain.Refusal;
import io.github.ythibos.edi.domain.decision.UnknownDecision;
import io.github.ythibos.edi.domain.rule.Rule;
import io.github.ythibos.edi.domain.plan.UnknownPlan;
import io.github.ythibos.edi.domain.spark.UnknownSpark;
import io.github.ythibos.edi.domain.task.UnknownTask;
import io.github.ythibos.edi.web.access.CallerFirings;
import io.github.ythibos.edi.web.access.NotPermitted;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
class ApiRefusals {

    private static final MediaType PLAIN_TEXT = new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8);

    private final CallerFirings firings;

    ApiRefusals(CallerFirings firings) {
        this.firings = firings;
    }

    @ExceptionHandler(Refusal.class)
    ResponseEntity<String> refused(Refusal refusal, HttpServletRequest request) {
        firings.refused(refusal.rule(), request, refusal.getMessage());
        return sentence(HttpStatus.CONFLICT, refusal.getMessage());
    }

    @ExceptionHandler(NotPermitted.class)
    ResponseEntity<String> notPermitted(NotPermitted notPermitted, HttpServletRequest request) {
        firings.refused(notPermitted.rule(), request, notPermitted.getMessage());
        return sentence(HttpStatus.FORBIDDEN, notPermitted.getMessage());
    }

    @ExceptionHandler({UnknownTask.class, UnknownDecision.class, UnknownSpark.class, UnknownPlan.class})
    ResponseEntity<String> unknown(RuntimeException unknown) {
        return sentence(HttpStatus.NOT_FOUND, unknown.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<String> invalid(IllegalArgumentException invalid, HttpServletRequest request) {
        return malformed(request, invalid.getMessage());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<String> incomplete(MissingServletRequestParameterException incomplete, HttpServletRequest request) {
        return malformed(request, "The call needs its " + incomplete.getParameterName() + " parameter.");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<String> mistyped(MethodArgumentTypeMismatchException mistyped, HttpServletRequest request) {
        return malformed(request, "The call's " + mistyped.getName() + " parameter cannot be " + mistyped.getValue() + ".");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<String> unreadable(HttpServletRequest request) {
        return malformed(request, "The request body is not the JSON this call expects.");
    }

    @ExceptionHandler(TrackerUnavailable.class)
    ResponseEntity<String> unreachable(TrackerUnavailable unavailable) {
        return sentence(HttpStatus.SERVICE_UNAVAILABLE, unavailable.getMessage());
    }

    @ExceptionHandler({UnreadableRegistry.class, ProgramFailure.class})
    ResponseEntity<String> unable(RuntimeException unable) {
        return sentence(HttpStatus.INTERNAL_SERVER_ERROR, unable.getMessage());
    }

    private ResponseEntity<String> malformed(HttpServletRequest request, String reason) {
        firings.refused(Rule.REQUEST_VALID, request, reason);
        return sentence(HttpStatus.BAD_REQUEST, reason);
    }

    private static ResponseEntity<String> sentence(HttpStatus status, String sentence) {
        return ResponseEntity.status(status).contentType(PLAIN_TEXT).body(sentence + "\n");
    }
}
