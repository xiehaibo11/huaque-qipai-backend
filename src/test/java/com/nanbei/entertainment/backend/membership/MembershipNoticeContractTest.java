package com.nanbei.entertainment.backend.membership;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MembershipNoticeContractTest {
    @Test
    void migrationOwnsTheOriginalNoticeCopyAndFirstPartyAgreement() throws Exception {
        String sql = Files.readString(migration("V18__membership_notice.sql"));

        assertThat(sql).contains("CREATE TABLE membership_notice_configs");
        assertThat(sql).contains("items JSONB NOT NULL");
        assertThat(sql).contains("version INTEGER NOT NULL");
        assertThat(sql).contains("1.购买会员卡后可立即获得对应会员");
        assertThat(sql).contains("2.每日领取福利需要在【会员权益】界面手动领取");
        assertThat(sql).contains("4.续费或叠加购买同种会员卡");
        assertThat(sql).contains("如在您会员卡生效期间");
        assertThat(sql).contains("https://www.nanbeiyule.com/terms");
    }

    @Test
    void controllerExposesTheOwnedAuthenticatedNoticeContract() throws Exception {
        String controller = source("api/MembershipController.java");
        String service = source("application/MembershipNoticeService.java");
        String response = source("application/MembershipNoticeResponse.java");
        String repository = source("infrastructure/MembershipNoticeRepository.java");

        assertThat(controller).contains("@GetMapping(\"/notice\")");
        assertThat(controller).contains("membershipNoticeService.current()");
        assertThat(service).contains("public MembershipNoticeResponse current()");
        assertThat(repository).contains("from membership_notice_configs");
        assertThat(repository).contains("where id = 1 and active = true");
        assertThat(response).contains("int version");
        assertThat(response).contains("List<String> items");
        assertThat(response).contains("String changeNotice");
        assertThat(response).contains("String agreementUrl");
    }

    private static Path migration(String fileName) {
        return Path.of("src/main/resources/db/migration").resolve(fileName);
    }

    private static String source(String relative) throws Exception {
        return Files.readString(
                Path.of("src/main/java/com/nanbei/entertainment/backend/membership")
                        .resolve(relative));
    }
}
