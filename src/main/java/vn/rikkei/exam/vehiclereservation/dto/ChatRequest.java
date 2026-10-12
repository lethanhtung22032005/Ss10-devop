package vn.rikkei.exam.vehiclereservation.dto;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(String conversationId,
                          @NotBlank(message = "message không được để trống") String message) {
}
