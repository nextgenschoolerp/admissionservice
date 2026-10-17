package com.kvs.admission.service;

import com.kvs.core.exception.BadRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link DocumentVaultStorage} (US-02004).
 */
class DocumentVaultStorageTest {

    @TempDir
    Path vault;

    private DocumentVaultStorage storage(long maxBytes) {
        return new DocumentVaultStorage(vault.toString(), maxBytes);
    }

    @Test
    @DisplayName("Stores a valid PDF under tenant/enquiry with a generated name and SHA-256 checksum")
    void storesPdf() throws Exception {
        UUID enquiryId = UUID.randomUUID();
        MockMultipartFile pdf = new MockMultipartFile("file", "../../etc/passwd.pdf", "application/pdf",
                "%PDF-1.7 hello".getBytes());

        DocumentVaultStorage.StoredFile stored = storage(1024).store("tenant/../x", enquiryId, pdf);

        assertThat(stored.relativePath()).startsWith("tenant____x/" + enquiryId + "/").endsWith(".pdf");
        assertThat(stored.relativePath()).doesNotContain("passwd");
        assertThat(stored.checksumSha256()).hasSize(64);
        Path written = vault.resolve(stored.relativePath());
        assertThat(written.normalize()).startsWith(vault);
        assertThat(Files.readString(written)).isEqualTo("%PDF-1.7 hello");
    }

    @Test
    @DisplayName("Rejects files whose bytes do not match the declared content type")
    void rejectsSpoofedContentType() {
        MockMultipartFile fake = new MockMultipartFile("file", "cert.png", "image/png", "MZ executable".getBytes());

        assertThatThrownBy(() -> storage(1024).store("t1", UUID.randomUUID(), fake))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("does not match");
    }

    @Test
    @DisplayName("Rejects unsupported types, empty files and oversized files")
    void rejectsInvalidFiles() {
        DocumentVaultStorage storage = storage(10);
        UUID enquiryId = UUID.randomUUID();

        assertThatThrownBy(() -> storage.store("t1", enquiryId,
                new MockMultipartFile("file", "a.docx", "application/msword", "data".getBytes())))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Unsupported file type");
        assertThatThrownBy(() -> storage.store("t1", enquiryId,
                new MockMultipartFile("file", "a.pdf", "application/pdf", new byte[0])))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("must not be empty");
        assertThatThrownBy(() -> storage.store("t1", enquiryId,
                new MockMultipartFile("file", "a.pdf", "application/pdf", "%PDF-1.7 too large".getBytes())))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("maximum size");
    }

    @Test
    @DisplayName("Delete removes a stored file and ignores paths outside the vault")
    void deletesInsideVaultOnly() throws Exception {
        DocumentVaultStorage storage = storage(1024);
        DocumentVaultStorage.StoredFile stored = storage.store("t1", UUID.randomUUID(),
                new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1}));
        Path outside = Files.writeString(vault.getParent().resolve("outside-" + UUID.randomUUID() + ".txt"), "keep");

        storage.delete(stored.relativePath());
        storage.delete("../" + outside.getFileName());

        assertThat(vault.resolve(stored.relativePath())).doesNotExist();
        assertThat(outside).exists();
        Files.delete(outside);
    }
}
