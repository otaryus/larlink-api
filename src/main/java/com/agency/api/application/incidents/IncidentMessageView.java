package com.agency.api.application.incidents;

import java.time.Instant;
import java.util.UUID;

public record IncidentMessageView(UUID id, String author, String message, boolean hasAttachment, Instant createdAt) {
}
