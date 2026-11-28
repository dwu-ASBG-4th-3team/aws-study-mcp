package com.asbg.awsstudymcp.qa.dto.res;

import java.util.List;

import com.asbg.awsstudymcp.common.Timestamps;
import com.asbg.awsstudymcp.qa.QaItem;
import com.asbg.awsstudymcp.qa.enums.Domain;
import com.asbg.awsstudymcp.qa.enums.EmbeddingStatus;
import com.asbg.awsstudymcp.qa.enums.Rating;
import com.asbg.awsstudymcp.qa.enums.Source;
import com.asbg.awsstudymcp.qa.enums.Verification;

public record QaDetailRes(String qaId, String question, String answer, Domain domain, List<String> tags,
		Source source, Verification verification, String sourceUrl, EmbeddingStatus embeddingStatus,
		Rating latestFeedback, String createdAt, String updatedAt) {

	public static QaDetailRes from(QaItem item) {
		return new QaDetailRes(item.getQaId(), item.getQuestion(), item.getAnswer(), item.getDomain(),
				item.getTags() == null ? List.of() : item.getTags(), item.getSource(), item.getVerification(),
				item.getSourceUrl(), item.getEmbeddingStatus(), item.getLatestFeedback(),
				Timestamps.format(item.getCreatedAt()), Timestamps.format(item.getUpdatedAt()));
	}

}
