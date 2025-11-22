package promptstudio.promptstudio.global.gpt.application;

import org.springframework.ai.document.Document;

import java.util.List;

public interface GptService {
    String upgradeText( String fullContext, String selectedText, String direction);
    String upgradeTextWithContext(String fullContext, String selectedText, String direction, String ragContext);
    String reupgradeText(String fullContext, String selectedText, String prevDirection, String prevResult, String direction);
    String reupgradeTextWithContext(String fullContext, String selectedText, String prevDirection, String prevResult, String direction, String ragContext);
    String generateSearchQuery(String fullText, String selectedText, String direction);
    List<Document> retrieve(String query, int topK, double threshold);
}