package promptstudio.promptstudio.global.dall_e.application;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.openai.OpenAiImageModel;
import org.springframework.ai.openai.OpenAiImageOptions;
import org.springframework.ai.openai.api.OpenAiImageApi;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import promptstudio.promptstudio.global.s3.service.S3StorageService;

import java.time.LocalDate;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ImageServiceImpl implements ImageService {

    private final OpenAiImageModel openAiImageModel;
    private final S3StorageService s3StorageService;

    @Override
    public String generateImage(String prompt) {
        return generateAndUpload(prompt, "standard", "natural");
    }

    @Override
    public String generateImageHD(String prompt) {
        return generateAndUpload(prompt, "hd", "vivid");
    }

    @Override
    public String generateImageRealistic(String prompt) {
        return generateAndUpload(prompt, "hd", "natural");
    }

    private String generateAndUpload(String prompt, String quality, String style) {
        try {
            System.out.println("=== DALL-E 이미지 생성 시작 ===");
            System.out.println("Quality: " + quality + ", Style: " + style);

            ImageResponse response = openAiImageModel.call(
                    new ImagePrompt(prompt,
                            OpenAiImageOptions.builder()
                                    .model("dall-e-3")
                                    .quality(quality)
                                    .width(1024)
                                    .height(1024)
                                    .style(style)
                                    .responseFormat("b64_json")  // Base64로 받기
                                    .build()
                    )
            );

            // Base64 데이터 추출
            String b64Data = response.getResult().getOutput().getB64Json();

            if (b64Data == null || b64Data.isEmpty()) {
                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "DALL-E에서 이미지 데이터를 받지 못했습니다."
                );
            }

            System.out.println("Base64 데이터 수신 완료: " + b64Data.length() + " chars");

            // Base64 디코딩
            byte[] imageBytes = Base64.getDecoder().decode(b64Data);
            System.out.println("디코딩 완료: " + imageBytes.length + " bytes");

            // S3에 업로드
            String key = "dalle/%s/%s.png".formatted(LocalDate.now(), UUID.randomUUID());
            String s3Url = s3StorageService.uploadBytes(imageBytes, key, "image/png", "max-age=31536000");

            System.out.println("S3 업로드 완료: " + s3Url);
            System.out.println("=== DALL-E 이미지 생성 완료 ===");

            return s3Url;

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "이미지 생성 중 오류가 발생했습니다: " + e.getMessage(),
                    e
            );
        }
    }
}