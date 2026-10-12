package vn.rikkei.exam.vehiclereservation.service.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class CorpusIngestionService {

    public static final String CORPUS_FILE = "tai_lieu_noi_bo.md";

    private final PolicyDocumentChunker chunker;
    private final VectorStore vectorStore;

    public CorpusIngestionService(PolicyDocumentChunker chunker, VectorStore vectorStore) {
        this.chunker = chunker;
        this.vectorStore = vectorStore;
    }

    public int ingest() {
        Resource corpus = new ClassPathResource(CORPUS_FILE);
        String content;
        try {
            content = corpus.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Không đọc được corpus: " + CORPUS_FILE, e);
        }
        List<Document> documents = chunker.chunk(CORPUS_FILE, content);
        if (!documents.isEmpty()) {
            vectorStore.add(documents);
        }
        return documents.size();
    }
}
