package com.learncreator.aitutor.service;

import com.learncreator.courses.entity.Lesson;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Turns a lesson's transcript into embedded chunks in the vector store, used by the RAG
 * escalation path when a question needs to search beyond the current lesson.
 *
 * Deliberately NOT a JPA entity/repository — see AITUTOR.md section 4: Spring AI's VectorStore
 * owns and manages its own backing table; we only ever go through Document objects.
 */
@Service
@RequiredArgsConstructor
public class EmbeddingIndexService {

    private final VectorStore vectorStore;

    // Chunk by paragraph first; a lesson transcript with no blank-line breaks falls back to a
    // fixed-size sliding window so a single giant paragraph doesn't become one enormous chunk.
    private static final Pattern PARAGRAPH_SPLIT = Pattern.compile("\\n\\s*\\n");
    private static final int MAX_CHUNK_CHARS = 1200;

    public void indexTranscript(Lesson lesson, String transcriptText) {
        List<Document> documents = new ArrayList<>();
        List<String> chunks = chunk(transcriptText);

        for (int i = 0; i < chunks.size(); i++) {
            Map<String, Object> metadata = Map.of(
                    "lessonId", lesson.getId().toString(),
                    "lessonTitle", lesson.getTitle(),
                    "moduleId", lesson.getModule().getId().toString(),
                    "courseId", lesson.getModule().getCourse().getId().toString(),
                    "chunkIndex", i
            );
            documents.add(new Document(chunks.get(i), metadata));
        }

        if (!documents.isEmpty()) {
            vectorStore.add(documents);
        }
    }

    private List<String> chunk(String text) {
        List<String> paragraphs = List.of(PARAGRAPH_SPLIT.split(text.trim()));
        List<String> result = new ArrayList<>();

        StringBuilder current = new StringBuilder();
        for (String paragraph : paragraphs) {
            if (current.length() + paragraph.length() > MAX_CHUNK_CHARS && !current.isEmpty()) {
                result.add(current.toString().trim());
                current = new StringBuilder();
            }
            current.append(paragraph).append("\n\n");
        }
        if (!current.isEmpty()) {
            result.add(current.toString().trim());
        }
        return result;
    }
}
