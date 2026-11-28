package com.asbg.awsstudymcp.common.pagination;

import java.util.List;

// 커서 기반 목록의 공통 응답. 다음 항목이 없으면 nextCursor는 null
public record CursorPageRes<T>(List<T> items, String nextCursor) {
}
