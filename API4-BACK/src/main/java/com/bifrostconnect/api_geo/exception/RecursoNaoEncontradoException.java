package com.bifrostconnect.api_geo.exception;

// US09 - Tarefa 8: vira 404 padronizado no GlobalExceptionHandler
public class RecursoNaoEncontradoException extends RuntimeException {

    public RecursoNaoEncontradoException(String message) {
        super(message);
    }
}
