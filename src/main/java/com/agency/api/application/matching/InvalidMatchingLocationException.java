package com.agency.api.application.matching;

public class InvalidMatchingLocationException extends RuntimeException {
    public InvalidMatchingLocationException() {
        super("Seleccione um distrito pertencente à província ou cidade escolhida.");
    }
}
