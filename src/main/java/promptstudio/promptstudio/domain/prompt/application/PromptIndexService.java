package promptstudio.promptstudio.domain.prompt.application;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import promptstudio.promptstudio.domain.prompt.domain.entity.Prompt;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PromptIndexService {

    private final VectorStore vectorStore;

    public void indexPrompt(Prompt prompt, List<String> categories) {

        categories = (categories == null) ? List.of() : categories; //null 방지

        String categoriesForEmbedding = String.join(", ", categories);

        String embeddingText = """
                [CATEGORIES]
                %s

                [TITLE]
                %s

                [INTRO]
                %s

                [CONTENT]
                %s
                """.formatted(
                categoriesForEmbedding,
                prompt.getTitle(),
                prompt.getIntroduction(),
                prompt.getContent()
        );

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("promptId", prompt.getId());
        metadata.put("memberId", prompt.getMember().getId());
        metadata.put("categories", categories);

        Document doc = Document.builder()
                .id(UUID.randomUUID().toString())
                .text(embeddingText)
                .metadata(metadata)
                .build();

        vectorStore.add(List.of(doc));
    }
}

