package vn.rikkei.exam.vehiclereservation.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.rikkei.exam.vehiclereservation.dto.ChatRequest;
import vn.rikkei.exam.vehiclereservation.dto.ChatResponse;
import vn.rikkei.exam.vehiclereservation.dto.ChatSource;
import vn.rikkei.exam.vehiclereservation.exception.GlobalExceptionHandler;
import vn.rikkei.exam.vehiclereservation.service.chat.ChatService;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ChatControllerTest {

    private MockMvc mockMvc;
    private ChatService chatService;

    @BeforeEach
    void setUp() {
        chatService = mock(ChatService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ChatController(chatService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void chatEndpointReturnsAnswerConversationSourcesAndToolsUsed() throws Exception {
        ChatResponse response = new ChatResponse("conv-1", "Xe còn khả dụng.",
                List.of(new ChatSource("tai_lieu_noi_bo.md", "Tiêu chuẩn xe công tác", "nội dung", 0.9)),
                List.of("getVehicleAvailability"));
        when(chatService.chat(any(ChatRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"conv-1\",\"message\":\"Xe STD còn khả dụng?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Xe còn khả dụng."))
                .andExpect(jsonPath("$.conversationId").value("conv-1"))
                .andExpect(jsonPath("$.sources[0].source").value("tai_lieu_noi_bo.md"))
                .andExpect(jsonPath("$.toolsUsed[0]").value("getVehicleAvailability"));
    }

    @Test
    void chatEndpointRejectsBlankMessage() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }
}
