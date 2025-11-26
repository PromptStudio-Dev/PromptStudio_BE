package promptstudio.promptstudio.global.gpt.application;

import org.springframework.ai.document.Document;
import promptstudio.promptstudio.domain.history.dto.GptRunResult;

import java.util.List;


public interface GptService {
    String upgradeText( String fullContext, String selectedText, String direction);
    String upgradeTextWithContext(String fullContext, String selectedText, String direction, String ragContext);
    String reupgradeText(String fullContext, String selectedText, String prevDirection, String prevResult, String direction);
    String reupgradeTextWithContext(String fullContext, String selectedText, String prevDirection, String prevResult, String direction, String ragContext);
    String generateSearchQuery(String fullText, String selectedText, String direction);
    List<Document> retrieve(String query, int topK, double threshold);

    //Maker GPT runPrompt TODO:Home에서 같이 쓸지는 고민
    GptRunResult runPrompt(String prompt);

    GptRunResult runPromptWithImages(String prompt, List<String> imageUrls);

    String generateHistoryTitle(String currentTitle, String currentContent,
                                String previousTitle, String previousContent);
}