package com.asbg.awsstudymcp.qa;

import com.asbg.awsstudymcp.common.BaseResponse;
import com.asbg.awsstudymcp.common.pagination.CursorPageRes;
import com.asbg.awsstudymcp.qa.dto.req.FeedbackReq;
import com.asbg.awsstudymcp.qa.dto.res.FeedbackRes;
import com.asbg.awsstudymcp.qa.dto.res.QaDetailRes;
import com.asbg.awsstudymcp.qa.dto.res.QaSummary;
import com.asbg.awsstudymcp.qa.enums.Domain;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

// QaController의 API 문서. 매핑과 구현은 Controller에 둔다.
@Tag(name = "QA", description = "학습 기록 API")
public interface QaControllerDoc {

	@Operation(summary = "목록 조회", description = "최신순. nextCursor가 null이면 마지막 페이지")
	BaseResponse<CursorPageRes<QaSummary>> list(
			@Parameter(description = "없으면 전체") Domain domain,
			@Parameter(description = "1~50, 기본 20") Integer limit,
			@Parameter(description = "이전 응답의 nextCursor. 같은 domain 조건에서만 사용") String cursor);

	@Operation(summary = "상세 조회")
	BaseResponse<QaDetailRes> get(String qaId);

	@Operation(summary = "이해도 평가")
	BaseResponse<FeedbackRes> rate(String qaId, FeedbackReq request);

	@Operation(summary = "삭제", description = "완전 삭제. 복구할 수 없다.")
	BaseResponse<QaService.DeleteResult> delete(String qaId);

}
