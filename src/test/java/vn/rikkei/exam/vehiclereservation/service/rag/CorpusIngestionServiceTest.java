package vn.rikkei.exam.vehiclereservation.service.rag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class CorpusIngestionServiceTest {

    private final VectorStore vectorStore = mock(VectorStore.class);
    private final CorpusIngestionService service =
            new CorpusIngestionService(new PolicyDocumentChunker(), vectorStore);

    @Test
    void ingestsPolicySectionsFromClasspathCorpus() {
        int count = service.ingest();

        assertThat(count).isEqualTo(3);
        verify(vectorStore).add(anyList());
    }

    @Test
    void repeatedIngestUsesSameDeterministicIds() {
        service.ingest();
        service.ingest();

        verify(vectorStore, times(2)).add(anyList());
        List<Document> first = captureAdd();
        List<Document> second = captureSecondAdd();
        assertThat(first).extracting(Document::getId)
                .containsExactlyElementsOf(second.stream().map(Document::getId).toList());
    }

    private List<Document> captureAdd() {
        org.mockito.ArgumentCaptor<List<Document>> captor = listCaptor();
        verify(vectorStore, times(2)).add(captor.capture());
        return captor.getAllValues().get(0);
    }

    private List<Document> captureSecondAdd() {
        org.mockito.ArgumentCaptor<List<Document>> captor = listCaptor();
        verify(vectorStore, times(2)).add(captor.capture());
        return captor.getAllValues().get(1);
    }

    @SuppressWarnings("unchecked")
    private org.mockito.ArgumentCaptor<List<Document>> listCaptor() {
        org.mockito.ArgumentCaptor<List<Document>> captor =
                org.mockito.ArgumentCaptor.forClass(List.class);
        return captor;
    }
}
