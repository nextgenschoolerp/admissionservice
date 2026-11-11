package com.kvs.admission.service;

import com.kvs.admission.entity.TenantIdFormatConfig;
import com.kvs.admission.repo.TenantIdFormatConfigRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfigurableIdGeneratorTest {

    private static final String TENANT = "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11";

    @Mock
    private TenantIdFormatConfigRepository configRepository;

    @InjectMocks
    private ConfigurableIdGenerator generator;

    private static TenantIdFormatConfig config(String prefix, String separator, boolean includeYear,
                                               String counterMode, int counterLength, long nextValue) {
        try {
            TenantIdFormatConfig c = new TenantIdFormatConfig();
            set(c, "prefix", prefix);
            set(c, "separator", separator);
            set(c, "includeYear", includeYear);
            set(c, "counterMode", counterMode);
            set(c, "counterLength", counterLength);
            set(c, "nextValue", nextValue);
            return c;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void set(Object obj, String fieldName, Object value) throws Exception {
        Field f = obj.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(obj, value);
    }

    @Nested
    @DisplayName("Mapped prefixes (ADM, APP, OFFER, EXAM, ADMIT)")
    class MappedPrefixes {

        @Test
        @DisplayName("ADM -> admission_number: uses DB config")
        void admissionNumber() {
            when(configRepository.findByTenantIdAndFormatKey(TENANT, "admission_number"))
                    .thenReturn(Optional.of(config("DADM", "/", true, "SEQUENTIAL", 6, 100)));

            String id = generator.next(TENANT, "ADM");

            assertThat(id).isEqualTo("DADM/" + LocalDate.now().getYear() + "/" + String.format("%06d", 100));
        }

        @Test
        @DisplayName("APP -> application_number: uses generateApplicationNumber convenience method")
        void applicationNumber() {
            when(configRepository.findByTenantIdAndFormatKey(TENANT, "application_number"))
                    .thenReturn(Optional.of(config("APLY", "-", true, "SEQUENTIAL", 5, 42)));

            String id = generator.generateApplicationNumber(TENANT);

            assertThat(id).isEqualTo("APLY-" + LocalDate.now().getYear() + "-" + String.format("%05d", 42));
        }

        @Test
        @DisplayName("OFFER -> offer_code: reads config")
        void offerCode() {
            when(configRepository.findByTenantIdAndFormatKey(TENANT, "offer_code"))
                    .thenReturn(Optional.of(config("OFR", "-", true, "SEQUENTIAL", 4, 7)));

            String id = generator.next(TENANT, "OFFER");

            assertThat(id).isEqualTo("OFR-" + LocalDate.now().getYear() + "-" + String.format("%04d", 7));
        }

        @Test
        @DisplayName("EXAM -> exam_code: reads config")
        void examCode() {
            when(configRepository.findByTenantIdAndFormatKey(TENANT, "exam_code"))
                    .thenReturn(Optional.of(config("EX", "-", false, "SEQUENTIAL", 5, 999)));

            String id = generator.next(TENANT, "EXAM");

            assertThat(id).isEqualTo("EX-" + String.format("%05d", 999));
        }

        @Test
        @DisplayName("ADMIT -> admit_card: random mode")
        void admitCardRandom() {
            when(configRepository.findByTenantIdAndFormatKey(TENANT, "admit_card"))
                    .thenReturn(Optional.of(config("AC", "-", true, "RANDOM", 8, 0)));

            String id = generator.next(TENANT, "ADMIT");

            assertThat(id).startsWith("AC-" + LocalDate.now().getYear() + "-");
            String random = id.substring(id.lastIndexOf('-') + 1);
            assertThat(random).hasSize(8).matches("[A-F0-9]{8}");
        }

        @Test
        @DisplayName("falls back to ReferenceNumberGenerator when no config row for mapped prefix")
        void fallbackWhenNoConfigRow() {
            when(configRepository.findByTenantIdAndFormatKey(TENANT, "admission_number"))
                    .thenReturn(Optional.empty());

            String id = generator.next(TENANT, "ADM");

            assertThat(id).startsWith("ADM-" + LocalDate.now().getYear() + "-");
            assertThat(id.substring(id.lastIndexOf('-') + 1)).hasSize(8);
        }
    }

    @Nested
    @DisplayName("Unmapped prefixes (MERIT, RCPT)")
    class UnmappedPrefixes {

        @Test
        @DisplayName("MERIT falls back to ReferenceNumberGenerator directly")
        void meritFallback() {
            String id = generator.next(TENANT, "MERIT");

            assertThat(id).startsWith("MERIT-" + LocalDate.now().getYear() + "-");
            assertThat(id.substring(id.lastIndexOf('-') + 1)).hasSize(8);
        }

        @Test
        @DisplayName("RCPT falls back to ReferenceNumberGenerator directly")
        void rcptFallback() {
            String id = generator.next(TENANT, "RCPT");

            assertThat(id).startsWith("RCPT-" + LocalDate.now().getYear() + "-");
        }
    }

    @Nested
    @DisplayName("Config options")
    class ConfigOptions {

        @Test
        @DisplayName("omits year when includeYear is false")
        void noYear() {
            when(configRepository.findByTenantIdAndFormatKey(TENANT, "admission_number"))
                    .thenReturn(Optional.of(config("ADM", "-", false, "SEQUENTIAL", 5, 1)));

            String id = generator.next(TENANT, "ADM");

            assertThat(id).isEqualTo("ADM-" + String.format("%05d", 1));
            assertThat(id).doesNotContain(String.valueOf(LocalDate.now().getYear()));
        }

        @Test
        @DisplayName("uses custom separator")
        void customSeparator() {
            when(configRepository.findByTenantIdAndFormatKey(TENANT, "application_number"))
                    .thenReturn(Optional.of(config("APP", "/", true, "SEQUENTIAL", 4, 55)));

            String id = generator.generateApplicationNumber(TENANT);

            assertThat(id).isEqualTo("APP/" + LocalDate.now().getYear() + "/" + String.format("%04d", 55));
        }
    }
}
