package promptstudio.promptstudio.global.dall_e.application;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.openai.OpenAiImageModel;
import org.springframework.ai.openai.OpenAiImageOptions;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ImageServiceImpl implements ImageService {

    private final OpenAiImageModel openAiImageModel;

    @Override
    public String generateImage(String prompt) {
        try {
            ImageResponse response = openAiImageModel.call(
                    new ImagePrompt(prompt,
                            OpenAiImageOptions.builder()
                                    .model("dall-e-3")
                                    .quality("standard")  // "standard" or "hd"
                                    .width(1024)
                                    .height(1024)
                                    .style("natural")     // "natural" or "vivid"
                                    .build()
                    )
            );

            return response.getResult().getOutput().getUrl();

        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "이미지 생성 중 오류가 발생했습니다: " + e.getMessage(),
                    e
            );
        }
    }

    @Override
    public String generateImageHD(String prompt) {
        try {
            ImageResponse response = openAiImageModel.call(
                    new ImagePrompt(prompt,
                            OpenAiImageOptions.builder()
                                    .model("dall-e-3")
                                    .quality("hd")  // HD 품질
                                    .width(1024)
                                    .height(1024)
                                    .style("vivid")  // 더 생생한 결과
                                    .build()
                    )
            );

            // DALL-E가 수정한 프롬프트 확인 (디버깅용)
            if (response.getMetadata() != null) {
                Object revisedPrompt = response.getMetadata().get("revisedPrompt");
                if (revisedPrompt != null) {
                    System.out.println("=== DALL-E가 수정한 프롬프트 ===");
                    System.out.println(revisedPrompt);
                    System.out.println("================================");
                }
            }

            return response.getResult().getOutput().getUrl();

        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "HD 이미지 생성 중 오류가 발생했습니다: " + e.getMessage(),
                    e
            );
        }
    }
}
