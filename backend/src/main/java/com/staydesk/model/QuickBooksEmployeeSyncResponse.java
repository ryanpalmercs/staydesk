package com.staydesk.model;

import java.util.List;

public record QuickBooksEmployeeSyncResponse(List<String> created, List<String> matched, List<String> failed) {
}
