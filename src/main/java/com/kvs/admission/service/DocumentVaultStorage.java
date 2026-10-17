package com.kvs.admission.service;

import com.kvs.core.exception.BadRequestException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/**
 * Stores applicant documents on the file system vault (US-02004).
 * File names are generated server-side, so client-supplied names never reach the file system path.
 */
@Component
@Slf4j
public class DocumentVaultStorage {

    /** Allowed content types and the extension used when storing them. */
    static final Map<String, String> ALLOWED_CONTENT_TYPES = Map.of(
            "application/pdf", ".pdf",
            "image/jpeg", ".jpg",
            "image/png", ".png");

    private final Path basePath;
    private final long maxFileSizeBytes;

    public DocumentVaultStorage(@Value("${admission.document-vault.base-path:./document-vault}") String basePath,
                                @Value("${admission.document-vault.max-file-size-bytes:5242880}") long maxFileSizeBytes) {
        this.basePath = Path.of(basePath).toAbsolutePath().normalize();
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    /**
     * Validates and writes the file to {@code <base>/<tenant>/<enquiry>/<uuid>.<ext>}.
     *
     * @return the stored file's vault-relative path and SHA-256 checksum
     */
    public StoredFile store(String tenantId, UUID enquiryId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Uploaded file must not be empty");
        }
        if (file.getSize() > maxFileSizeBytes) {
            throw new BadRequestException("Uploaded file exceeds the maximum size of " + maxFileSizeBytes + " bytes");
        }
        String extension = ALLOWED_CONTENT_TYPES.get(file.getContentType());
        if (extension == null) {
            throw new BadRequestException("Unsupported file type " + file.getContentType()
                    + ". Allowed types: " + ALLOWED_CONTENT_TYPES.keySet());
        }
        if (!hasMatchingSignature(file, file.getContentType())) {
            throw new BadRequestException("File content does not match the declared type " + file.getContentType());
        }

        // tenantId comes from the enquiry record; strip anything that is not a safe path segment
        String tenantSegment = tenantId.replaceAll("[^A-Za-z0-9_-]", "_");
        Path relative = Path.of(tenantSegment, enquiryId.toString(), UUID.randomUUID() + extension);
        Path target = basePath.resolve(relative).normalize();
        if (!target.startsWith(basePath)) {
            throw new BadRequestException("Invalid document storage path");
        }

        try {
            Files.createDirectories(target.getParent());
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = new DigestInputStream(file.getInputStream(), digest)) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            String checksum = HexFormat.of().formatHex(digest.digest());
            log.debug("Stored document {} ({} bytes) in vault", relative, file.getSize());
            return new StoredFile(relative.toString().replace('\\', '/'), checksum);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store document in vault", e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    /**
     * Best-effort removal of a previously stored file (used when a document is replaced).
     */
    public void delete(String relativePath) {
        try {
            Path target = basePath.resolve(relativePath).normalize();
            if (target.startsWith(basePath)) {
                Files.deleteIfExists(target);
            }
        } catch (IOException e) {
            log.warn("Failed to delete superseded vault file {}: {}", relativePath, e.getMessage());
        }
    }

    /**
     * Checks the leading magic bytes so a renamed executable cannot pass as a PDF or image.
     */
    private boolean hasMatchingSignature(MultipartFile file, String contentType) {
        byte[] expected = switch (contentType) {
            case "application/pdf" -> new byte[]{'%', 'P', 'D', 'F'};
            case "image/jpeg" -> new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
            case "image/png" -> new byte[]{(byte) 0x89, 'P', 'N', 'G'};
            default -> null;
        };
        if (expected == null) {
            return false;
        }
        try (InputStream in = file.getInputStream()) {
            byte[] header = in.readNBytes(expected.length);
            return java.util.Arrays.equals(header, expected);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read uploaded document", e);
        }
    }

    public record StoredFile(String relativePath, String checksumSha256) {
    }
}
