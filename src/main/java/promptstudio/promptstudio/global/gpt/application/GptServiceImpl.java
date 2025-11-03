package promptstudio.promptstudio.global.gpt.application;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class GptServiceImpl implements GptService {

    private final ChatClient.Builder chatClientBuilder;

    private static final String SYSTEM_MESSAGE = """
            당신은 프롬프트 작성 전문가입니다.
            사용자가 제공한 텍스트를 지정된 방향성에 맞게 개선해주세요.
            개선된 텍스트만 출력하고, 추가 설명이나 인사말은 하지 마세요.
            원본 텍스트의 핵심 의미는 유지하되, 방향성에 맞게 표현을 개선하세요.
            """;

    private static final String USER_MESSAGE_TEMPLATE = """
            전체 맥락:
            {fullContext}
            
            ---
            
            업그레이드할 텍스트:
            {selectedText}
            
            개선 방향:
            {direction}
            
            위 텍스트를 개선 방향에 맞게 다시 작성해주세요.
            개선된 텍스트만 출력하세요.
            """;

    @Override
    public String upgradeText(String selectedText, String direction, String fullContext) {
        try {
            ChatClient chatClient = chatClientBuilder.build();

            PromptTemplate promptTemplate = new PromptTemplate(USER_MESSAGE_TEMPLATE);
            Prompt prompt = promptTemplate.create(Map.of(
                    "selectedText", selectedText,
                    "direction", direction,
                    "fullContext", fullContext != null ? fullContext : ""
            ));

            String result = chatClient.prompt(prompt)
                    .system(SYSTEM_MESSAGE)
                    .call()
                    .content();

            return result != null ? result.trim() : selectedText;

        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "텍스트 업그레이드 중 오류가 발생했습니다: " + e.getMessage(),
                    e
            );
        }
    }
}