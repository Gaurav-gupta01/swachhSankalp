package CleanVision.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import lombok.Getter;

@Service
public class ImageStorageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "bmp", "webp");
    private static final long MAX_BYTES = 8L * 1024 * 1024;

    @Getter
    private final Path uploadDir;

    public ImageStorageService(@Value("${cleanvision.upload-dir}") String uploadDir) {
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    @PostConstruct
    void init() throws IOException {
        Files.createDirectories(uploadDir);
    }

    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException("Please choose an image to upload");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new AppException("Image is larger than 8 MB");
        }

        String original = file.getOriginalFilename() == null ? "image" : file.getOriginalFilename();
        String extension = extensionOf(original);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new AppException("Only JPG, PNG, GIF, BMP or WEBP images are supported");
        }

        String filename = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        Path target = resolve(filename);
        try {
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new AppException("Could not save the uploaded image", ex);
        }
        return filename;
    }

    public Path resolve(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new AppException("Invalid image reference");
        }
        String safe = Paths.get(filename).getFileName().toString();
        Path path = uploadDir.resolve(safe).normalize();
        if (!path.startsWith(uploadDir)) {
            throw new AppException("Invalid image reference");
        }
        return path;
    }

    public boolean exists(String filename) {
        try {
            return filename != null && Files.isRegularFile(resolve(filename));
        } catch (AppException ex) {
            return false;
        }
    }

    public void delete(String filename) {
        if (filename == null || filename.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(resolve(filename));
        } catch (IOException | AppException ignored) {
            // orphan files are harmless
        }
    }

    private String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
