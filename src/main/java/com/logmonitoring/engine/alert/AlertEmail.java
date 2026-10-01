package com.logmonitoring.engine.alert;

import java.util.List;

public record AlertEmail(List<String> to, String subject, String html, String text) {
}
