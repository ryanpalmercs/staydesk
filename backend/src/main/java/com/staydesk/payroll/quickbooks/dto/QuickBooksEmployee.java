package com.staydesk.payroll.quickbooks.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record QuickBooksEmployee(@JsonProperty("Id") String id,
                                 @JsonProperty("SyncToken") String syncToken,
                                 @JsonProperty("GivenName") String givenName,
                                 @JsonProperty("FamilyName") String familyName,
                                 @JsonProperty("DisplayName") String displayName,
                                 @JsonProperty("Active") Boolean active) {
}
