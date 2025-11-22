package promptstudio.promptstudio.global.dall_e.application;

public interface ImageService {
    String generateImage(String prompt);
    String generateImageHD(String prompt);
    String generateImageRealistic(String prompt);

}
