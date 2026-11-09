package io.github.mugishajc.shortener.store;

public class CodeTakenException extends RuntimeException {

    public CodeTakenException(String code) {
        super("Short code '" + code + "' is already taken");
    }
}
