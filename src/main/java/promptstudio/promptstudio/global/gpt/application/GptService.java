package promptstudio.promptstudio.global.gpt.application;

import promptstudio.promptstudio.domain.history.dto.GptRunResult;

import java.util.List;

public interface GptService {
    //TODO:문장 업그레이드 기능 변경
    String upgradeText(String selectedText, String direction, String fullContext);

    //Maker GPT runPrompt TODO:Home에서 같이 쓸지는 고민
    GptRunResult runPrompt(String prompt);

    GptRunResult runPromptWithImages(String prompt, List<String> imageUrls);

    String generateHistoryTitle(String currentTitle, String currentContent,
                                String previousTitle, String previousContent);
}