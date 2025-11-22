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

    public void indexPrompt(Prompt prompt) {

        String embeddingText = """
                [TITLE]
                %s

                [INTRO]
                %s

                [CONTENT]
                %s
                """.formatted(
                prompt.getTitle(),
                prompt.getIntroduction(),
                prompt.getContent()
        );

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("promptId", prompt.getId());
        metadata.put("memberId", prompt.getMember().getId());
        metadata.put("category", prompt.getCategory());

        Document doc = Document.builder()
                .id(UUID.randomUUID().toString())
                .text(embeddingText)
                .metadata(metadata)
                .build();

        vectorStore.add(List.of(doc));
    }
}

