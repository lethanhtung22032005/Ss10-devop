package vn.rikkei.exam.vehiclereservation.service.rag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyDocumentChunkerTest {

    private static final String CORPUS = """
            # Sổ tay nội bộ — Điều phối xe công tác

            ## Tiêu chuẩn xe công tác
            Nhóm STANDARD phục vụ tối đa 2 người. Nhóm PREMIUM phục vụ tối đa 4 người. Nhóm PREMIUM chỉ dành cho yêu cầu có từ 2 người trở lên.

            ## Chính sách công tác
            Một yêu cầu tối đa 14 ngày. Mục đích phải mô tả rõ từ 10 đến 200 ký tự. Chỉ yêu cầu PENDING được quản lý xử lý.

            ## Hủy và phê duyệt
            Yêu cầu được phê duyệt khi còn khả dụng trong toàn bộ khoảng thời gian và tuân thủ sức chứa.
            """;

    private final PolicyDocumentChunker chunker = new PolicyDocumentChunker();

    @Test
    void chunksByPolicySectionWithSourceMetadata() {
        List<Document> documents = chunker.chunk("tai_lieu_noi_bo.md", CORPUS);

        assertThat(documents).hasSize(3);
        assertThat(documents.get(0).getText()).startsWith("## Tiêu chuẩn xe công tác");
        assertThat(documents.get(0).getMetadata())
                .containsEntry(PolicyDocumentChunker.META_SOURCE, "tai_lieu_noi_bo.md")
                .containsEntry(PolicyDocumentChunker.META_SECTION, "Tiêu chuẩn xe công tác");
        assertThat(documents.get(1).getMetadata().get(PolicyDocumentChunker.META_SECTION)).isEqualTo("Chính sách công tác");
        assertThat(documents.get(2).getMetadata().get(PolicyDocumentChunker.META_SECTION)).isEqualTo("Hủy và phê duyệt");
    }

    @Test
    void idsAreDeterministicAcrossRuns() {
        List<Document> first = chunker.chunk("tai_lieu_noi_bo.md", CORPUS);
        List<Document> second = chunker.chunk("tai_lieu_noi_bo.md", CORPUS);

        assertThat(first).extracting(Document::getId).containsExactlyElementsOf(
                second.stream().map(Document::getId).toList());
        assertThat(first.get(0).getId()).hasSize(32);
    }
}
