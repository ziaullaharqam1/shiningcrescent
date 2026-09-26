package com.shiningcrescent.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class FileStorage {
    private static final Set<String> ALLOWED = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");

    private final Path root;

    public FileStorage(@Value("${app.upload-dir}") String uploadDir) throws IOException {
        this.root = Path.of(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    public String store(String folder, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Choose an image file.");
        }
        String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED.contains(type)) {
            throw new IllegalArgumentException("Use a JPG, PNG, WEBP, or GIF image.");
        }
        String ext = switch (type) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".gif";
        };
        try {
            Path dir = root.resolve(folder).normalize();
            if (!dir.startsWith(root)) {
                throw new IllegalArgumentException("Invalid upload path.");
            }
            Files.createDirectories(dir);
            String name = UUID.randomUUID() + ext;
            Path dest = dir.resolve(name);
            try (var in = file.getInputStream()) {
                Files.copy(in, dest);
            }
            return "/media/" + folder + "/" + name;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (IOException e) {
            throw new IllegalStateException("The image could not be saved. Please try again.");
        }
    }
}
