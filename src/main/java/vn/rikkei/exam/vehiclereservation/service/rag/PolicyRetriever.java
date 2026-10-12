package vn.rikkei.exam.vehiclereservation.service.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PolicyRetriever {

    private final VectorStore vectorStore;
    private final int topK;
    private final double similarityThreshold;

    public PolicyRetriever(VectorStore vectorStore,
                           @Value("${app.rag.top-k:4}") int topK,
                           @Value("${app.rag.similarity-threshold:0.25}") double similarityThreshold) {
        this.vectorStore = vectorStore;
        this.topK = topK;
        this.similarityThreshold = similarityThreshold;
    }

    public List<Document> search(String query) {
        return search(query, topK);
    }

    public List<Document> search(String query, int topKOverride) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topKOverride)
                .similarityThreshold(similarityThreshold)
                .build();
        return vectorStore.similaritySearch(request);
    }
}
