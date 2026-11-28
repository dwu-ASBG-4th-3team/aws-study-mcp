package com.asbg.awsstudymcp.study;

public class GenerationException extends RuntimeException {

    public enum Code {
        AI_RATE_LIMITED,
        AI_GENERATION_FAILED
    }

    private final Code code;

    public GenerationException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code getCode() {
        return code;
    }
}