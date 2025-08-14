package org.steam5.http;

import lombok.Getter;

@Getter
public class ReviewGameException extends IllegalStateException {

    private final int statusCode;

    public ReviewGameException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

}


