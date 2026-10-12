package vn.rikkei.exam.vehiclereservation.service.rag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PolicyRetrieverTest {

    private final VectorStore vectorStore = mock(VectorStore.class);
    private final PolicyRetriever retriever = new PolicyRetriever(vectorStore, 4, 0.25);

    @Test
    void delegatesToSimilaritySearchWithConfiguredThreshold() {
        Document doc = new Document("nội dung");
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(doc));

        List<Document> result = retriever.search("xe standard chở được mấy người?");

        assertThat(result).containsExactly(doc);
        SearchRequest captured = argumentCaptor();
        assertThat(captured.getTopK()).isEqualTo(4);
        assertThat(captured.getSimilarityThreshold()).isEqualTo(0.25);
        assertThat(captured.getQuery()).isEqualTo("xe standard chở được mấy người?");
    }

    @Test
    void returnsEmptyWhenStoreHasNoMatch() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        assertThat(retriever.search("không có trong tài liệu")).isEmpty();
    }

    @Test
    void blankQueryReturnsEmptyWithoutTouchingStore() {
        assertThat(retriever.search("   ")).isEmpty();
        verifyNoInteractions(vectorStore);
    }

    private SearchRequest argumentCaptor() {
        org.mockito.ArgumentCaptor<SearchRequest> captor =
                org.mockito.ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        return captor.getValue();
    }
}
