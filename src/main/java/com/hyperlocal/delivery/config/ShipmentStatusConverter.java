package com.hyperlocal.delivery.config;

import com.hyperlocal.delivery.model.ShipmentStatus;
import org.springframework.core.convert.converter.Converter;

/**
 * Binds request-param/path-variable strings (e.g. {@code ?status=picked_up})
 * to {@link ShipmentStatus}, accepting either the lowercase snake_case wire
 * value or the raw enum name, case-insensitively.
 */
public class ShipmentStatusConverter implements Converter<String, ShipmentStatus> {
    @Override
    public ShipmentStatus convert(String source) {
        return ShipmentStatus.fromWireValue(source);
    }
}
