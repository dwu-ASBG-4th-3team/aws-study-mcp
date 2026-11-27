package com.asbg.awsstudymcp.qa.dto.res;

import com.asbg.awsstudymcp.qa.enums.Rating;

public record FeedbackRes(String qaId, Rating latestFeedback, String updatedAt) {
}
