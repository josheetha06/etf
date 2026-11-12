package com.epictalenttrust.hr.message;

import java.util.UUID;

/** Published inside a transaction; the email goes out only after that transaction commits. */
public record OutboundEmailRequested(UUID messageId) {
}
