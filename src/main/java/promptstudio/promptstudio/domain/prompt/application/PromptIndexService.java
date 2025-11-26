package promptstudio.promptstudio.domain.prompt.application;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import promptstudio.promptstudio.domain.prompt.domain.entity.Prompt;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PromptIndexService {

    private final VectorStore vectorStore;

    private String stableId(Long promptId) {
        return UUID.nameUUIDFromBytes(
                ("prompt-" + promptId).getBytes(StandardCharsets.UTF_8)
        ).toString();
    }


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
                .id(stableId(prompt.getId()))
                .text(embeddingText)
                .metadata(metadata)
                .build();

        vectorStore.add(List.of(doc));
    }

    public void deletePrompt(Long promptId) {
        vectorStore.delete(List.of(stableId(promptId)));
    }
}

