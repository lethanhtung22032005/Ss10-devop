package vn.rikkei.exam.vehiclereservation.service.chat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import vn.rikkei.exam.vehiclereservation.dto.ChatResponse;
import vn.rikkei.exam.vehiclereservation.service.rag.PolicyDocumentChunker;
import vn.rikkei.exam.vehiclereservation.service.rag.PolicyRetriever;
import vn.rikkei.exam.vehiclereservation.tool.ToolExecutionRecorder;
import vn.rikkei.exam.vehiclereservation.tool.VehicleReservationToolRegistry;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatServiceTest {

    private static final String FALLBACK = "Không đủ căn cứ trong tài liệu nội bộ.";

    private final PolicyRetriever retriever = mock(PolicyRetriever.class);
    private final VehicleReservationToolRegistry toolRegistry = mock(VehicleReservationToolRegistry.class);
    private final MessageWindowChatMemory memory = MessageWindowChatMemory.builder()
            .chatMemoryRepository(new InMemoryChatMemoryRepository())
            .maxMessages(20)
            .build();

    private ChatService serviceWithAnswer(String modelAnswer) {
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class)))
                .thenAnswer(invocation -> new org.springframework.ai.chat.model.ChatResponse(
                        List.of(new Generation(new AssistantMessage(modelAnswer)))));
        when(model.getOptions()).thenReturn(ChatOptions.builder().build());
        when(toolRegistry.getRecordingCallbacks(any(ToolExecutionRecorder.class))).thenReturn(List.of());
        return new ChatService(ChatClient.builder(model), retriever, toolRegistry, memory, FALLBACK);
    }

    @Test
    void returnsFallbackMessageWhenNoEvidenceAndNoTool() {
        when(retriever.search(anyString())).thenReturn(List.of());
        ChatService service = serviceWithAnswer("Tôi không biết.");

        ChatResponse response = service.chat(new vn.rikkei.exam.vehiclereservation.dto.ChatRequest("conv-1", "Thời tiết hôm nay thế nào?"));

        assertThat(response.conversationId()).isEqualTo("conv-1");
        assertThat(response.answer()).isEqualTo(FALLBACK);
        assertThat(response.sources()).isEmpty();
        assertThat(response.toolsUsed()).isEmpty();
    }

    @Test
    void usesRetrievedDocumentsAndExposesRealSources() {
        Document doc = new Document("id-1",
                "## Tiêu chuẩn xe công tác\nNhóm STANDARD phục vụ tối đa 2 người.",
                Map.of(PolicyDocumentChunker.META_SOURCE, "tai_lieu_noi_bo.md",
                        PolicyDocumentChunker.META_SECTION, "Tiêu chuẩn xe công tác"));
        when(retriever.search(anyString())).thenReturn(List.of(doc));
        ChatService service = serviceWithAnswer("Nhóm STANDARD phục vụ tối đa 2 người.");

        ChatResponse response = service.chat(new vn.rikkei.exam.vehiclereservation.dto.ChatRequest(null,
                "Standard chở được tối đa mấy người?"));

        assertThat(response.conversationId()).isNotBlank();
        assertThat(response.answer()).isEqualTo("Nhóm STANDARD phục vụ tối đa 2 người.");
        assertThat(response.sources()).hasSize(1);
        assertThat(response.sources().get(0).source()).isEqualTo("tai_lieu_noi_bo.md");
        assertThat(response.sources().get(0).section()).isEqualTo("Tiêu chuẩn xe công tác");
        verify(retriever).search("Standard chở được tối đa mấy người?");
    }

    @Test
    void conversationMemoryIsKeptPerConversationId() {
        when(retriever.search(anyString())).thenReturn(List.of(
                new Document("id-1", "nội dung", Map.of(PolicyDocumentChunker.META_SOURCE, "s", PolicyDocumentChunker.META_SECTION, "mục"))));
        ChatService service = serviceWithAnswer("Đã rõ.");

        service.chat(new vn.rikkei.exam.vehiclereservation.dto.ChatRequest("conv-mem", "Xe PREMIUM tối đa mấy người?"));
        ChatResponse second = service.chat(new vn.rikkei.exam.vehiclereservation.dto.ChatRequest("conv-mem", "Tối đa 4 người phải không?"));

        assertThat(second.conversationId()).isEqualTo("conv-mem");
        assertThat(memory.get("conv-mem")).hasSizeGreaterThanOrEqualTo(4);
    }
}
