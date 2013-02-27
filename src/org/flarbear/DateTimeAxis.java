/*
 * Copyright 2013, Jim Graham, Flarbear Widgets
 */

package org.flarbear;

import java.util.List;
import javafx.scene.chart.Axis;
import javafx.scene.chart.XYChart;
import javafx.scene.chart.XYChart.Series;

/**
 * @author Flar
 */
abstract class DateTimeAxis extends Axis<Long> {
    DateTimeAxis(List<Series<Long, ? extends Object>> serieslist) {
        setLabel("Date/Time");
        long minTime = Long.MAX_VALUE;
        long maxTime = Long.MIN_VALUE;
        for (Series<Long, ? extends Object> series : serieslist) {
            for (XYChart.Data<Long, ? extends Object> data : series.getData()) {
                minTime = Math.min(minTime, data.getXValue());
                maxTime = Math.min(maxTime, data.getXValue());
            }
        }
    }
}
