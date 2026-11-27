package com.asbg.awsstudymcp.qa.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;

// 문자열로 받아 직접 변환한다. enum으로 바로 받으면 숫자(순번)도 허용될 수 있다.
public record FeedbackReq(
		@Schema(allowableValues = { "UNDERSTOOD", "CONFUSED", "WRONG" }, requiredMode = Schema.RequiredMode.REQUIRED)
		String rating) {
}
