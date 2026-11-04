package com.example.raglab.application;

/** Generic login failure that does not reveal whether an account exists. */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("The username or password is incorrect");
    }
}
