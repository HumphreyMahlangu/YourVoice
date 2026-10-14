package io.github.humphreymahlangu.votetrust.controller;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;

/**
 * Shared pagination conventions for list endpoints: optional {@code page} (0-based) and {@code size} query
 * parameters, a plain JSON array body so existing clients keep working, and the total in {@code X-Total-Count}.
 */
final class PageRequestParameters {

    static final String TOTAL_COUNT_HEADER = "X-Total-Count";
    static final String DEFAULT_PAGE = "0";
    static final String DEFAULT_SIZE = "200";
    static final int MAX_SIZE = 500;

    private PageRequestParameters() {
    }

    static <T> ResponseEntity<List<T>> toResponse(Page<T> page) {
        return ResponseEntity.ok()
                .header(TOTAL_COUNT_HEADER, Long.toString(page.getTotalElements()))
                .body(page.getContent());
    }
}
