package com.asbg.awsstudymcp.common.pagination;

import com.asbg.awsstudymcp.common.ApiException;
import com.asbg.awsstudymcp.common.CommonErrorCode;

// 목록 API의 limit 공통 규칙
public final class PageLimit {

	public static final int DEFAULT = 20;
	public static final int MAX = 50;

	private PageLimit() {
	}

	// 값이 없으면 기본값, 범위를 벗어나면 400
	public static int resolve(Integer limit) {
		if (limit == null) {
			return DEFAULT;
		}
		if (limit < 1 || limit > MAX) {
			throw new ApiException(CommonErrorCode.VALIDATION_FAILED, "limit은 1~" + MAX + " 사이여야 합니다.");
		}
		return limit;
	}

}
