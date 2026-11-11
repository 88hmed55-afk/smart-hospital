package com.smarthospital.common.api;

import java.util.List;

public class PageResponse<T> {
    public List<T> items;
    public long total;
    public int offset;
    public int limit;

    public PageResponse() {
    }

    public PageResponse(List<T> items, long total, int offset, int limit) {
        this.items = items;
        this.total = total;
        this.offset = offset;
        this.limit = limit;
    }
}
