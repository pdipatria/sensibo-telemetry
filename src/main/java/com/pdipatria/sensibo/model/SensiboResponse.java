package com.pdipatria.sensibo.model;

public record SensiboResponse(
    String status,
    HistoricalMeasurements result
) {}
