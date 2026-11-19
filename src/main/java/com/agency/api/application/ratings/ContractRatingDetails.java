package com.agency.api.application.ratings;

import com.agency.api.domain.ContractRating;
import java.util.List;

public record ContractRatingDetails(boolean eligible, List<ContractRating> ratings,
        double average, boolean submittedByMe) { }
