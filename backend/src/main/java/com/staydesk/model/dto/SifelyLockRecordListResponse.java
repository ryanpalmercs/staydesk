package com.staydesk.model.dto;

import java.util.List;

public record SifelyLockRecordListResponse(List<SifelyLockRecord> list, int pageNo, int pageSize, int pages,
                                           int total) {
}