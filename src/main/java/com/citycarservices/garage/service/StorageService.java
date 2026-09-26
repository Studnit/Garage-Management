package com.citycarservices.garage.service;

import com.citycarservices.garage.config.GarageProperties;
import com.citycarservices.garage.config.NotFoundException;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/** Stores uploaded inspection photos and videos on local disk (mount a volume in production). */
@Service
public class StorageService {

    private final Path root;

    public StorageService(GarageProperties props) {
        this.root = Paths.get(props.getUploadDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create upload directory " + root, e);
        }
    }

    public String store(MultipartFile file, String extension) throws IOException {
        String name = UUID.randomUUID() + extension;
        Path target = root.resolve(name).normalize();
        if (!target.startsWith(root)) {
            throw new IOException("Invalid path");
        }
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target);
        }
        return name;
    }

    public Resource load(String storedName) {
        Path p = root.resolve(storedName).normalize();
        if (!p.startsWith(root) || !Files.isReadable(p)) {
            throw new NotFoundException("File not found");
        }
        return new FileSystemResource(p);
    }

    public void delete(String storedName) {
        try {
            Path p = root.resolve(storedName).normalize();
            if (p.startsWith(root)) {
                Files.deleteIfExists(p);
            }
        } catch (IOException ignored) {
            // best effort
        }
    }
}
