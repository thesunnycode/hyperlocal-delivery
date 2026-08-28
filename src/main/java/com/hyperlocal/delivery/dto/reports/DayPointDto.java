package com.hyperlocal.delivery.dto.reports;

import com.hyperlocal.delivery.dto.analytics.DailyVolume;

/**
 * A single day's shipment volume, shaped for the frontend's volume chart
 * and trend table (field {@code progress} == {@link DailyVolume#getInProgress()}).
 */
public record DayPointDto(String date, long total, long delivered, long failed, long progress) {

    public static DayPointDto from(DailyVolume dv) {
        return new DayPointDto(dv.getDate(), dv.getTotal(), dv.getDelivered(), dv.getFailed(), dv.getInProgress());
    }
}
