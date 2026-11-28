package com.asbg.awsstudymcp.qa.dto.res;

import com.asbg.awsstudymcp.common.Timestamps;
import com.asbg.awsstudymcp.qa.QaItem;
import com.asbg.awsstudymcp.qa.enums.Domain;
import com.asbg.awsstudymcp.qa.enums.EmbeddingStatus;
import com.asbg.awsstudymcp.qa.enums.Rating;

public record QaSummary(String qaId, String question, Domain domain, EmbeddingStatus embeddingStatus,
		Rating latestFeedback, String createdAt) {

	public static QaSummary from(QaItem item) {
		return new QaSummary(item.getQaId(), item.getQuestion(), item.getDomain(), item.getEmbeddingStatus(),
				item.getLatestFeedback(), Timestamps.format(item.getCreatedAt()));
	}

}
