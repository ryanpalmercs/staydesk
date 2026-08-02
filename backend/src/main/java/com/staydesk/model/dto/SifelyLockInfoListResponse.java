package com.staydesk.model.dto;

import java.util.List;

public record SifelyLockInfoListResponse(List<SifelyLockInfo> list, int pageNo, int pageSize, int pages, int total) {
}
