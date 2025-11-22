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
                                    .quality("standard")
                                    .width(1024)
                                    .height(1024)
                                    .style("natural")
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
                                    .quality("hd")
                                    .width(1024)
                                    .height(1024)
                                    .style("vivid")
                                    .build()
                    )
            );

            return response.getResult().getOutput().getUrl();

        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "HD 이미지 생성 중 오류가 발생했습니다: " + e.getMessage(),
                    e
            );
        }
    }

    @Override
    public String generateImageRealistic(String prompt) {
        try {
            ImageResponse response = openAiImageModel.call(
                    new ImagePrompt(prompt,
                            OpenAiImageOptions.builder()
                                    .model("dall-e-3")
                                    .quality("hd")
                                    .width(1024)
                                    .height(1024)
                                    .style("natural")
                                    .build()
                    )
            );

            return response.getResult().getOutput().getUrl();

        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Realistic 이미지 생성 중 오류가 발생했습니다: " + e.getMessage(),
                    e
            );
        }
    }
}