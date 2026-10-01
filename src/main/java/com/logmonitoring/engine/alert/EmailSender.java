package com.logmonitoring.engine.alert;

/** Delivers one email or throws; callers decide what a failure means. */
public interface EmailSender {

    void send(AlertEmail email);
}
