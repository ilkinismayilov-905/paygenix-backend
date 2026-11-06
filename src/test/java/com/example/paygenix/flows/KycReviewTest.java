package com.example.paygenix.flows;

import com.example.paygenix.entity.User;
import com.example.paygenix.enums.Role;
import com.example.paygenix.service.EmailService;
import com.example.paygenix.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// T-070/T-071: identity documents go to a private bucket and wait for a manual admin review.
class KycReviewTest extends IntegrationTest {

    @MockitoBean MinioClient minio;
    @MockitoBean EmailService emailService;

    @BeforeEach
    void presign() throws Exception {
        reset(minio);
        when(minio.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class))).thenReturn("https://storage.test/kyc/doc?sig=1");
    }

    private static MockMultipartFile photo(String type, int bytes) {
        return com.example.paygenix.support.TestFiles.file("document", type, bytes);
    }

    private ResultActions submit(User user, MockMultipartFile photo) throws Exception {
        return mockMvc.perform(multipart("/api/kyc").file(photo).param("documentType", "ID_CARD")
                .header("Authorization", bearer(user)));
    }

    private ResultActions kycOf(User user) throws Exception {
        return mockMvc.perform(get("/api/kyc/me").header("Authorization", bearer(user)));
    }

    private Long pendingIdOf(User user) {
        return jdbc.queryForObject("SELECT id FROM kyc_submissions WHERE user_id = ? AND status = 'PENDING'", Long.class, user.getId());
    }

    @Test
    void newUsersAreUnverifiedAndHaveNotSubmitted() throws Exception {
        User user = createUser();
        assertThat(userRepository.findById(user.getId()).orElseThrow().getVerificationStatus()).isEqualTo(User.VerificationStatus.UNVERIFIED);
        kycOf(user).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("NOT_SUBMITTED"));
    }

    @Test
    void submitStoresThePhotoUnderAGeneratedKeyAndWaitsForReview() throws Exception {
        User user = createUser();
        submit(user, photo("image/png", 1000))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.documentType").value("ID_CARD"));

        ArgumentCaptor<PutObjectArgs> put = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minio).putObject(put.capture());
        assertThat(put.getValue().bucket()).isEqualTo("kyc");
        assertThat(put.getValue().object()).matches("kyc/" + user.getId() + "/[0-9a-f-]{36}\\.png");
        kycOf(user).andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.documentUrl").doesNotExist());

        submit(user, photo("image/jpeg", 10)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("KYC_ALREADY_PENDING"));
    }

    @Test
    void badPhotosAreRefusedBeforeAnythingIsStored() throws Exception {
        User user = createUser();
        submit(user, photo("application/pdf", 10)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        submit(user, photo("image/jpeg", 0)).andExpect(status().isBadRequest());
        submit(user, photo("image/jpeg", 5 * 1024 * 1024 + 1)).andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("DOCUMENT_TOO_LARGE"));
        mockMvc.perform(multipart("/api/kyc").file(photo("image/jpeg", 10)).param("documentType", "SELFIE")
                .header("Authorization", bearer(user))).andExpect(status().isBadRequest());
        verify(minio, never()).putObject(any());
        kycOf(user).andExpect(jsonPath("$.data.status").value("NOT_SUBMITTED"));
    }

    @Test
    void approvalVerifiesTheUserAndTellsThem() throws Exception {
        User user = createUser();
        User admin = createUser(Role.ADMIN_ROLE);
        submit(user, photo("image/jpeg", 100)).andExpect(status().isCreated());
        Long id = pendingIdOf(user);

        mockMvc.perform(get("/api/admin/kyc").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.id == %d)].username".formatted(id)).value(user.getPublicUsername()))
                .andExpect(jsonPath("$.data.items[?(@.id == %d)].documentUrl".formatted(id)).value(org.hamcrest.Matchers.contains((Object) null)));
        mockMvc.perform(get("/api/admin/kyc/{id}", id).header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.documentUrl").value("https://storage.test/kyc/doc?sig=1"))
                .andExpect(jsonPath("$.data.email").value(user.getEmail()));

        mockMvc.perform(post("/api/admin/kyc/{id}/approve", id).header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("APPROVED"));

        assertThat(userRepository.findById(user.getId()).orElseThrow().getVerificationStatus()).isEqualTo(User.VerificationStatus.VERIFIED);
        kycOf(user).andExpect(jsonPath("$.data.status").value("APPROVED"));
        verify(emailService, timeout(2000)).sendKycDecision(user.getEmail(), true, null);
        assertThat(jdbc.queryForObject("SELECT reviewed_by FROM kyc_submissions WHERE id = ?", Long.class, id)).isEqualTo(admin.getId());

        mockMvc.perform(post("/api/admin/kyc/{id}/reject", id).header("Authorization", bearer(admin))
                        .contentType("application/json").content("{\"reason\":\"late\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("KYC_ALREADY_REVIEWED"));
        submit(user, photo("image/jpeg", 100)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("KYC_ALREADY_VERIFIED"));
    }

    @Test
    void rejectionGivesTheReasonAndAllowsANewDocument() throws Exception {
        User user = createUser();
        User admin = createUser(Role.ADMIN_ROLE);
        submit(user, photo("image/jpeg", 100));
        Long id = pendingIdOf(user);

        mockMvc.perform(post("/api/admin/kyc/{id}/reject", id).header("Authorization", bearer(admin))
                .contentType("application/json").content("{\"reason\":\" \"}")).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/admin/kyc/{id}/reject", id).header("Authorization", bearer(admin))
                        .contentType("application/json").content("{\"reason\":\" The photo is blurred. \"}"))
                .andExpect(status().isOk());

        kycOf(user).andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.rejectionReason").value("The photo is blurred."));
        verify(emailService, timeout(2000)).sendKycDecision(user.getEmail(), false, "The photo is blurred.");
        assertThat(userRepository.findById(user.getId()).orElseThrow().getVerificationStatus()).isEqualTo(User.VerificationStatus.UNVERIFIED);

        submit(user, photo("image/webp", 100)).andExpect(status().isCreated()).andExpect(jsonPath("$.data.status").value("PENDING"));
        mockMvc.perform(get("/api/admin/kyc").param("status", "REJECTED").header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.data.items[?(@.id == %d)].rejectionReason".formatted(id)).value("The photo is blurred."));
    }

    @Test
    void onlyAdminsReviewAndNeverTheirOwnDocument() throws Exception {
        User user = createUser();
        submit(user, photo("image/jpeg", 100));
        Long id = pendingIdOf(user);

        mockMvc.perform(get("/api/admin/kyc").header("Authorization", bearer(user))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/kyc/{id}", id).header("Authorization", bearer(user))).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/kyc/{id}/approve", id).header("Authorization", bearer(user))).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/kyc/{id}/approve", id)).andExpect(status().isUnauthorized());

        User admin = createUser(Role.ADMIN_ROLE);
        submit(admin, photo("image/jpeg", 100));
        mockMvc.perform(post("/api/admin/kyc/{id}/approve", pendingIdOf(admin)).header("Authorization", bearer(admin)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("KYC_OWN_SUBMISSION"));
        mockMvc.perform(post("/api/admin/kyc/{id}/approve", 999_999_999L).header("Authorization", bearer(admin)))
                .andExpect(status().isNotFound());
    }

    @Test
    void storageDownIsARetryableOutageAndStoresNothing() throws Exception {
        User user = createUser();
        when(minio.putObject(any())).thenThrow(new RuntimeException("connection refused"));
        submit(user, photo("image/jpeg", 100)).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("MEDIA_UPLOAD_FAILED")).andExpect(header().exists("Retry-After"));
        kycOf(user).andExpect(jsonPath("$.data.status").value("NOT_SUBMITTED"));
    }

    @Test
    void contentMustMatchTheDeclaredType() throws Exception {
        User user = createUser();
        submit(user, new MockMultipartFile("document", "id.jpg", "image/jpeg", "<html>".getBytes()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        verify(minio, never()).putObject(any());
    }

    @Test
    void refusedOrFailedSubmitsDoNotUseUpTheDailyLimit() throws Exception {
        User user = createUser();
        for (int i = 0; i < 6; i++) submit(user, photo("application/pdf", 10)).andExpect(status().isBadRequest());
        when(minio.putObject(any())).thenThrow(new RuntimeException("connection refused"));
        for (int i = 0; i < 6; i++) submit(user, photo("image/jpeg", 100)).andExpect(status().isServiceUnavailable());
        reset(minio);
        submit(user, photo("image/jpeg", 100)).andExpect(status().isCreated());
    }

    @Test
    void atMostFiveDocumentsADay() throws Exception {
        User user = createUser();
        User admin = createUser(Role.ADMIN_ROLE);
        for (int i = 0; i < 5; i++) {
            submit(user, photo("image/jpeg", 100)).andExpect(status().isCreated());
            mockMvc.perform(post("/api/admin/kyc/{id}/reject", pendingIdOf(user)).header("Authorization", bearer(admin))
                    .contentType("application/json").content("{\"reason\":\"blurred\"}")).andExpect(status().isOk());
        }
        submit(user, photo("image/jpeg", 100)).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("KYC_SUBMIT_LIMIT")).andExpect(header().exists("Retry-After"));
    }

    @Test
    void parallelSubmitsLeaveOnePending() throws Exception {
        User user = createUser();
        String auth = bearer(user);
        Callable<Integer> one = () -> mockMvc.perform(multipart("/api/kyc").file(photo("image/jpeg", 100))
                .param("documentType", "PASSPORT").header("Authorization", auth)).andReturn().getResponse().getStatus();
        try (var pool = Executors.newFixedThreadPool(4)) {
            var results = pool.invokeAll(IntStream.range(0, 4).mapToObj(i -> one).toList());
            long created = 0;
            for (Future<Integer> r : results) if (r.get() == 201) created++;
            assertThat(created).isEqualTo(1);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM kyc_submissions WHERE user_id = ?", Integer.class, user.getId())).isEqualTo(1);
    }

    @Test
    void theOldSelfCheckIsGone() throws Exception {
        mockMvc.perform(post("/api/verify").header("Authorization", bearer(createUser())).contentType("application/json")
                .content("{\"fullName\":\"x\",\"dateOfBirth\":\"01-01-1990\",\"gender\":\"MALE\"}")).andExpect(status().isNotFound());
        String body = kycOf(createUser()).andReturn().getResponse().getContentAsString();
        assertThat((String) JsonPath.read(body, "$.data.status")).isEqualTo("NOT_SUBMITTED");
        verify(emailService, never()).sendKycDecision(anyString(), eq(true), any());
    }
}
