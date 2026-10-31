package com.pdipatria.sensibo.model;

import java.time.Instant;

public record Measurement(
    Instant time,
    double value
) {}
