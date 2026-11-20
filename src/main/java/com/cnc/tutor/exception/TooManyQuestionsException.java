package com.cnc.tutor.exception;

public class TooManyQuestionsException extends RuntimeException {

    public TooManyQuestionsException(int perMinute, long waitSeconds) {
        super("Limite atteinte : " + perMinute + " questions par minute. Réessayez dans "
                + waitSeconds + " s.");
    }
}
