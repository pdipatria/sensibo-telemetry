package com.pdipatria.sensibo.model;

import java.util.List;

public record HistoricalMeasurements(
    List<Measurement> temperature,
    List<Measurement> humidity
) {}
