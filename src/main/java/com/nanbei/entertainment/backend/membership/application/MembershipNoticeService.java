package com.nanbei.entertainment.backend.membership.application;

import com.nanbei.entertainment.backend.membership.infrastructure.MembershipNoticeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MembershipNoticeService {
    private final MembershipNoticeRepository membershipNoticeRepository;

    public MembershipNoticeService(MembershipNoticeRepository membershipNoticeRepository) {
        this.membershipNoticeRepository = membershipNoticeRepository;
    }

    @Transactional(readOnly = true)
    public MembershipNoticeResponse current() {
        MembershipNoticeRepository.Configuration configuration =
                membershipNoticeRepository
                        .findActive()
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Active membership notice configuration is missing"));
        return new MembershipNoticeResponse(
                configuration.version(),
                configuration.title(),
                configuration.items(),
                configuration.changeNotice(),
                configuration.agreementTitle(),
                configuration.agreementUrl(),
                configuration.updatedAt());
    }
}
