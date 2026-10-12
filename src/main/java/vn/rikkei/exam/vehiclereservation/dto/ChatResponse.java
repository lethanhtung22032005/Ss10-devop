package vn.rikkei.exam.vehiclereservation.dto;

import java.util.List;

public record ChatResponse(String conversationId, String answer, List<ChatSource> sources, List<String> toolsUsed) {
}
