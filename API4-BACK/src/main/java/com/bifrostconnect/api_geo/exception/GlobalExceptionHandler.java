package com.bifrostconnect.api_geo.exception;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationExceptions(
            MethodArgumentNotValidException ex) {

        Map<String, String> errors = new HashMap<>();

        ex.getBindingResult()
                .getFieldErrors()
                .forEach(error ->
                        errors.put(
                                error.getField(),
                                error.getDefaultMessage()
                        )
                );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(errors);
    }

    @ExceptionHandler(UnsupportedFileFormatException.class)
    public ResponseEntity<Map<String, String>> handleUnsupportedFile(
            UnsupportedFileFormatException ex) {

        Map<String, String> error = new HashMap<>();
        error.put("erro", ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(error);
    }

    // Tarefa 15: rede de segurança pra erro geoespacial que não foi pego no controller
    @ExceptionHandler(FalhaGeoespacialException.class)
    public ResponseEntity<Map<String, String>> handleFalhaGeoespacial(
            FalhaGeoespacialException ex) {

        Map<String, String> error = new HashMap<>();
        error.put("erro", ex.getReason());
        error.put("codigo", ex.getCodigoErro());
        error.put("operacao", ex.getOperacao());

        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(error); // <-- Faltava o body e o fechamento do método
    }

    // US09 - Tarefa 8: ID inexistente sai sempre no mesmo formato { "erro": ... } com 404
    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<Map<String, String>> handleRecursoNaoEncontrado(
            RecursoNaoEncontradoException ex) {

        Map<String, String> error = new HashMap<>();
        error.put("erro", ex.getMessage());

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(error);
    }

    @ExceptionHandler(TimeoutException.class)
    public ResponseEntity<Map<String, String>> handleTimeout(
            TimeoutException ex) {

        Map<String, String> error = new HashMap<>();

        error.put(
                "erro",
                "O serviço de leitura geoespacial está indisponível no momento."
        );

        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(error);
    }
}