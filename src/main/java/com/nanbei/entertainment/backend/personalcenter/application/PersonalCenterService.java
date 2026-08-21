package com.nanbei.entertainment.backend.personalcenter.application;

import com.nanbei.entertainment.backend.gamehome.application.GameHomeService;
import com.nanbei.entertainment.backend.gamehome.application.GameHomeSnapshot;
import com.nanbei.entertainment.backend.user.domain.UserIdentityEntity;
import com.nanbei.entertainment.backend.user.infrastructure.UserIdentityRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PersonalCenterService {
    private final GameHomeService gameHomeService;
    private final UserIdentityRepository identityRepository;
    private final PersonalCenterFunctionService functionService;

    public PersonalCenterService(
            GameHomeService gameHomeService,
            UserIdentityRepository identityRepository,
            PersonalCenterFunctionService functionService) {
        this.gameHomeService = gameHomeService;
        this.identityRepository = identityRepository;
        this.functionService = functionService;
    }

    @Transactional(readOnly = true)
    public PersonalCenterSnapshot load(UUID userId) {
        GameHomeSnapshot home = gameHomeService.load(userId);
        List<UserIdentityEntity> identities =
                identityRepository.findByUser_IdOrderByCreatedAtAsc(userId);
        String phone = firstPhoneNumber(identities);

        return new PersonalCenterSnapshot(
                new PersonalCenterSnapshot.Player(
                        home.player().userId(),
                        home.player().publicPlayerId(),
                        home.player().displayName(),
                        home.player().avatarKey(),
                        home.player().membershipLevel()),
                new PersonalCenterSnapshot.Wallet(
                        home.wallet().roomCards(),
                        home.wallet().boundRoomCards(),
                        home.wallet().coins(),
                        home.wallet().diamonds()),
                new PersonalCenterSnapshot.Account(
                        !phone.isEmpty(),
                        maskPhone(phone),
                        providerNames(identities)),
                new PersonalCenterSnapshot.Region(
                        home.region().lobbyId(),
                        home.region().areaName()),
                new PersonalCenterSnapshot.Capabilities(
                        true,
                        true,
                        true,
                        false,
                        false,
                        false),
                functionService.loadPrivacy(userId));
    }

    private static String firstPhoneNumber(
            List<UserIdentityEntity> identities) {
        for (UserIdentityEntity identity : identities) {
            String phoneNumber = identity.getPhoneNumber();
            if (phoneNumber != null && !phoneNumber.isBlank()) {
                return phoneNumber.trim();
            }
        }
        return "";
    }

    private static List<String> providerNames(
            List<UserIdentityEntity> identities) {
        Set<String> providers = new LinkedHashSet<>();
        for (UserIdentityEntity identity : identities) {
            providers.add(identity.getProvider().name());
        }
        return List.copyOf(providers);
    }

    private static String maskPhone(String phone) {
        if (phone.length() < 7) {
            return "";
        }
        return phone.substring(0, 3)
                + "****"
                + phone.substring(phone.length() - 4);
    }
}
