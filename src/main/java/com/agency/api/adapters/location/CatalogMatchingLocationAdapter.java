package com.agency.api.adapters.location;

import com.agency.api.application.matching.MatchingLocationPort;
import com.agency.api.service.MozambiqueLocationCatalog;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class CatalogMatchingLocationAdapter implements MatchingLocationPort {
    private final MozambiqueLocationCatalog catalog;

    public CatalogMatchingLocationAdapter(MozambiqueLocationCatalog catalog) {
        this.catalog = catalog;
    }

    @Override
    public boolean isValid(String area, String district) {
        return catalog.isValid(area, district);
    }

    @Override
    public String canonicalArea(String area) {
        return catalog.canonicalArea(area);
    }

    @Override
    public Optional<Coordinates> coordinates(String area, String district) {
        return catalog.coordinates(area, district)
                .map(value -> new Coordinates(value.latitude(), value.longitude()));
    }
}
