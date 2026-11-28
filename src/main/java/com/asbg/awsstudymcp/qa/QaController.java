package com.asbg.awsstudymcp.qa;

import java.util.Arrays;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.asbg.awsstudymcp.auth.CurrentUserProvider;
import com.asbg.awsstudymcp.common.BaseResponse;
import com.asbg.awsstudymcp.common.pagination.CursorPageRes;
import com.asbg.awsstudymcp.qa.dto.req.FeedbackReq;
import com.asbg.awsstudymcp.qa.dto.res.FeedbackRes;
import com.asbg.awsstudymcp.qa.dto.res.QaDetailRes;
import com.asbg.awsstudymcp.qa.dto.res.QaSummary;
import com.asbg.awsstudymcp.qa.enums.Domain;
import com.asbg.awsstudymcp.qa.enums.Rating;

// 웹 대시보드용 학습 기록 API. userId는 요청에서 받지 않는다.
@RestController
@RequestMapping("/api/qa")
public class QaController implements QaControllerDoc {

	private final QaService qaService;
	private final CurrentUserProvider currentUser;

	public QaController(QaService qaService, CurrentUserProvider currentUser) {
		this.qaService = qaService;
		this.currentUser = currentUser;
	}

	@Override
	@GetMapping
	public BaseResponse<CursorPageRes<QaSummary>> list(@RequestParam(required = false) Domain domain,
			@RequestParam(required = false) Integer limit, @RequestParam(required = false) String cursor) {
		return BaseResponse.success(qaService.list(currentUser.currentUserId(), domain, limit, cursor));
	}

	@Override
	@GetMapping("/{qaId}")
	public BaseResponse<QaDetailRes> get(@PathVariable String qaId) {
		return BaseResponse.success(qaService.get(currentUser.currentUserId(), qaId));
	}

	@Override
	@PatchMapping("/{qaId}/feedback")
	public BaseResponse<FeedbackRes> rate(@PathVariable String qaId, @RequestBody FeedbackReq request) {
		// 허용되지 않은 값은 null이 되어 Service에서 400으로 처리한다.
		Rating rating = Arrays.stream(Rating.values())
			.filter(value -> value.name().equals(request.rating()))
			.findFirst()
			.orElse(null);
		return BaseResponse.success(qaService.rate(currentUser.currentUserId(), qaId, rating));
	}

	@Override
	@DeleteMapping("/{qaId}")
	public BaseResponse<QaService.DeleteResult> delete(@PathVariable String qaId) {
		return BaseResponse.success(qaService.delete(currentUser.currentUserId(), qaId));
	}

}
