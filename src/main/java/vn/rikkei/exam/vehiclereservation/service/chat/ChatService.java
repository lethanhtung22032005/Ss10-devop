package vn.rikkei.exam.vehiclereservation.service.chat;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vn.rikkei.exam.vehiclereservation.dto.ChatRequest;
import vn.rikkei.exam.vehiclereservation.dto.ChatResponse;
import vn.rikkei.exam.vehiclereservation.dto.ChatSource;
import vn.rikkei.exam.vehiclereservation.service.rag.PolicyDocumentChunker;
import vn.rikkei.exam.vehiclereservation.service.rag.PolicyRetriever;
import vn.rikkei.exam.vehiclereservation.tool.ToolExecutionRecorder;
import vn.rikkei.exam.vehiclereservation.tool.VehicleReservationToolRegistry;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ChatService {

    private static final String SYSTEM_PROMPT = """
            Bạn là trợ lý nội bộ của nghiệp vụ điều phối xe công tác.

            Quy tắc bắt buộc:
            1. Câu hỏi về chính sách/quy định nội bộ: chỉ trả lời dựa trên các đoạn "Tài liệu nội bộ" được cung cấp trong prompt này. Không bịa thêm hoặc suy diễn thông tin không có trong tài liệu.
            2. Câu hỏi tra cứu dữ liệu hoặc tạo yêu cầu nghiệp vụ (xe công tác còn khả dụng theo ngày, tạo yêu cầu đặt xe): bắt buộc gọi tool tương ứng để lấy dữ liệu thật. Tuyệt đối không tự suy đoán dữ liệu nghiệp vụ và không truy cập database trực tiếp.
            3. Nếu câu hỏi không nằm trong tài liệu nội bộ và không có tool phù hợp để xử lý, chỉ được trả lời đúng chuỗi sau, không thêm bớt ký tự: %s
            4. Trả lời ngắn gọn, đúng trọng tâm bằng tiếng Việt.
            """;

    private final ChatClient chatClient;
    private final PolicyRetriever policyRetriever;
    private final VehicleReservationToolRegistry toolRegistry;
    private final String fallbackMessage;

    public ChatService(ChatClient.Builder chatClientBuilder,
                       PolicyRetriever policyRetriever,
                       VehicleReservationToolRegistry toolRegistry,
                       ChatMemory chatMemory,
                       @Value("${app.chat.fallback-message:Không đủ căn cứ trong tài liệu nội bộ.}") String fallbackMessage) {
        this.chatClient = chatClientBuilder
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
        this.policyRetriever = policyRetriever;
        this.toolRegistry = toolRegistry;
        this.fallbackMessage = fallbackMessage;
    }

    public ChatResponse chat(ChatRequest request) {
        String conversationId = resolveConversationId(request.conversationId());
        String userMessage = request.message().strip();

        List<Document> retrievedDocuments = policyRetriever.search(userMessage);
        ToolExecutionRecorder recorder = new ToolExecutionRecorder();
        List<ToolCallback> toolCallbacks = toolRegistry.getRecordingCallbacks(recorder);

        ChatClientResponse response = chatClient.prompt()
                .system(buildSystemPrompt(retrievedDocuments))
                .user(userMessage)
                .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, conversationId))
                .tools(toolCallbacks.toArray())
                .call()
                .chatClientResponse();

        String answer = extractAnswer(response);
        List<String> toolsUsed = recorder.getExecutedToolNames();
        boolean grounded = !retrievedDocuments.isEmpty();

        if (answer == null || answer.isBlank()) {
            answer = fallbackMessage;
        } else if (!grounded && toolsUsed.isEmpty()) {
            answer = fallbackMessage;
        }

        return new ChatResponse(conversationId, answer, toSources(retrievedDocuments), toolsUsed);
    }

    private String buildSystemPrompt(List<Document> documents) {
        String prompt = SYSTEM_PROMPT.formatted(fallbackMessage);
        if (!documents.isEmpty()) {
            String context = documents.stream()
                    .map(doc -> {
                        String source = String.valueOf(doc.getMetadata().getOrDefault(PolicyDocumentChunker.META_SOURCE, "?"));
                        String section = String.valueOf(doc.getMetadata().getOrDefault(PolicyDocumentChunker.META_SECTION, ""));
                        return "[" + source + (section.isBlank() ? "" : " / " + section) + "]\n" + doc.getText();
                    })
                    .collect(Collectors.joining("\n\n"));
            prompt = prompt + "\n\nTài liệu nội bộ liên quan:\n" + context;
        }
        return prompt;
    }

    private String extractAnswer(ChatClientResponse response) {
        if (response == null || response.chatResponse() == null
                || response.chatResponse().getResult() == null
                || response.chatResponse().getResult().getOutput() == null) {
            return null;
        }
        return response.chatResponse().getResult().getOutput().getText();
    }

    private List<ChatSource> toSources(List<Document> documents) {
        return documents.stream()
                .map(doc -> new ChatSource(
                        String.valueOf(doc.getMetadata().getOrDefault(PolicyDocumentChunker.META_SOURCE, "?")),
                        String.valueOf(doc.getMetadata().getOrDefault(PolicyDocumentChunker.META_SECTION, "")),
                        doc.getText(),
                        doc.getScore()))
                .toList();
    }

    private String resolveConversationId(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return conversationId.strip();
    }
}
