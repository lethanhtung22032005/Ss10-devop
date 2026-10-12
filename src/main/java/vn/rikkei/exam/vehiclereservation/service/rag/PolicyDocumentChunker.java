package vn.rikkei.exam.vehiclereservation.service.rag;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class PolicyDocumentChunker {

    public static final String META_SOURCE = "source";
    public static final String META_SECTION = "section";
    public static final String META_DOC_HASH = "doc_hash";

    public List<Document> chunk(String sourceName, String content) {
        List<Document> documents = new ArrayList<>();
        Map<String, StringBuilder> sections = splitSections(content);
        sections.forEach((title, body) -> {
            String bodyText = body.toString().strip();
            if (bodyText.isEmpty()) {
                return;
            }
            String chunkContent = "## " + title + "\n" + bodyText;
            String hash = md5Hex(sourceName + "|" + title);
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put(META_SOURCE, sourceName);
            metadata.put(META_SECTION, title);
            metadata.put(META_DOC_HASH, md5Hex(chunkContent));
            documents.add(new Document(hash, chunkContent, metadata));
        });
        return documents;
    }

    private Map<String, StringBuilder> splitSections(String content) {
        Map<String, StringBuilder> sections = new LinkedHashMap<>();
        String currentTitle = null;
        for (String line : content.replace("\r\n", "\n").split("\n", -1)) {
            String trimmed = line.strip();
            if (isSectionHeading(trimmed)) {
                currentTitle = trimmed.substring(3).strip();
                sections.put(currentTitle, new StringBuilder());
            } else if (currentTitle != null) {
                sections.get(currentTitle).append(line).append('\n');
            }
        }
        return sections;
    }

    private boolean isSectionHeading(String line) {
        return line.startsWith("## ") && line.length() > 3;
    }

    public static String md5Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 not available", e);
        }
    }
}
