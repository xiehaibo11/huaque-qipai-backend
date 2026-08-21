package com.nanbei.entertainment.backend.roomtools.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nanbei.entertainment.backend.common.crypto.CryptoService;
import com.nanbei.entertainment.backend.common.error.ApiException;
import com.nanbei.entertainment.backend.common.error.ErrorCode;
import com.nanbei.entertainment.backend.gameplay.domain.GameSessionEntity;
import com.nanbei.entertainment.backend.gameplay.domain.GameSessionSeatEntity;
import com.nanbei.entertainment.backend.gameplay.infrastructure.GameSessionRepository;
import com.nanbei.entertainment.backend.gameplay.infrastructure.GameSessionSeatRepository;
import com.nanbei.entertainment.backend.room.application.RoomPayType;
import com.nanbei.entertainment.backend.room.domain.GameRoomEntity;
import com.nanbei.entertainment.backend.room.infrastructure.GameRoomRepository;
import com.nanbei.entertainment.backend.roomtools.domain.RoomToolMessageEntity;
import com.nanbei.entertainment.backend.roomtools.infrastructure.RoomToolMessageRepository;
import com.nanbei.entertainment.backend.roomtools.infrastructure.RoomToolOperationRepository;
import com.nanbei.entertainment.backend.roomtools.infrastructure.RoomToolReservationRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.nanbei.entertainment.backend.gamehome.infrastructure.PlayerWalletRepository;
import com.nanbei.entertainment.backend.shop.infrastructure.ShopInventoryItemRepository;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class RoomToolsServiceTest {
    private static final UUID USER_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID OUTSIDER_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final Instant NOW = Instant.parse("2026-08-11T10:00:00Z");

    @Mock GameRoomRepository roomRepository;
    @Mock GameSessionRepository sessionRepository;
    @Mock GameSessionSeatRepository seatRepository;
    @Mock RoomToolReservationRepository reservationRepository;
    @Mock RoomToolMessageRepository messageRepository;
    @Mock RoomToolOperationRepository operationRepository;
    @Mock PlayerWalletRepository walletRepository;
    @Mock ShopInventoryItemRepository inventoryRepository;

    RoomToolsService service;
    GameRoomEntity room;
    GameSessionEntity session;
    GameSessionSeatEntity seat;

    @BeforeEach
    void setUp() {
        service =
                new RoomToolsService(
                        roomRepository,
                        sessionRepository,
                        seatRepository,
                        reservationRepository,
                        messageRepository,
                        operationRepository,
                        walletRepository,
                        inventoryRepository,
                        new CryptoService(),
                        new ObjectMapper(),
                        Clock.fixed(NOW, ZoneOffset.UTC));
        room =
                new GameRoomEntity(
                        "123456", USER_ID, 900023L, 30109L, "{}", "台州麻将", "{}", 0,
                        2, 8, RoomPayType.ALL, 100, "room-key", "room-hash");
        session = new GameSessionEntity(room.getId(), 30109L, NOW);
        seat = new GameSessionSeatEntity(session.getId(), 1, USER_ID, NOW);
    }

    @Test
    void stateReturnsServerQuickPhrasesAndAtMostFiftyOldestToNewestMessages() {
        arrangeMember(USER_ID);
        List<RoomToolMessageEntity> newestFirst =
                java.util.stream.IntStream.range(0, 50)
                        .mapToObj(index -> RoomToolMessageEntity.quickPhrase(
                                session.getId(), USER_ID, index % 9, NOW.minusSeconds(index)))
                        .toList();
        when(messageRepository.findLatest(session.getId(), 50)).thenReturn(newestFirst);
        when(seatRepository.findByIdSessionIdOrderByIdSeatNumber(session.getId()))
                .thenReturn(List.of(seat));

        RoomToolsStateResponse state = service.state(USER_ID, "123456");

        assertThat(state.quickPhrases()).hasSize(9);
        assertThat(state.quickPhrases().getFirst()).isEqualTo("快点啊，都等到我花儿都谢了！");
        assertThat(state.messages()).hasSize(50);
        assertThat(state.messages().getFirst().createdAt())
                .isBefore(state.messages().getLast().createdAt());
    }

    @Test
    void quickMessageAcceptsOnlyServerCatalogIndexAndIsIdempotent() {
        arrangeMember(USER_ID);
        when(operationRepository.findByUserIdAndIdempotencyKey(USER_ID, "message-1"))
                .thenReturn(Optional.empty());
        when(messageRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(operationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        RoomMessageResponse response =
                service.sendMessage(
                        USER_ID,
                        "123456",
                        "message-1",
                        new RoomMessageRequest(RoomMessageType.QUICK_PHRASE, 0));

        assertThat(response.message().text()).isEqualTo("快点啊，都等到我花儿都谢了！");
        ArgumentCaptor<RoomToolMessageEntity> saved =
                ArgumentCaptor.forClass(RoomToolMessageEntity.class);
        verify(messageRepository).save(saved.capture());
        assertThat(saved.getValue().getContentIndex()).isZero();

        assertThatThrownBy(
                        () -> service.sendMessage(
                                USER_ID,
                                "123456",
                                "message-2",
                                new RoomMessageRequest(RoomMessageType.QUICK_PHRASE, 9)))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.ROOM_TOOLS_MESSAGE_INVALID);
    }

    @Test
    void reservationTargetsNextRoundAndNeverTouchesWallets() {
        arrangeMember(USER_ID);
        when(operationRepository.findByUserIdAndIdempotencyKey(USER_ID, "reserve-1"))
                .thenReturn(Optional.empty());
        when(reservationRepository.findBySessionIdAndUserIdAndToolTypeAndTargetRound(
                        session.getId(), USER_ID, RoomToolType.CHANGE_CARD, 1))
                .thenReturn(Optional.empty());
        when(reservationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(operationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        RoomToolReservationResponse response =
                service.setReservation(
                        USER_ID, "123456", RoomToolType.CHANGE_CARD, "reserve-1", true);

        verify(operationRepository)
                .acquireOperationLock(
                        "room-tool-reservation:"
                                + session.getId()
                                + ":"
                                + USER_ID
                                + ":CHANGE_CARD:1");
        assertThat(response.targetRound()).isEqualTo(1);
        assertThat(response.active()).isTrue();
        // 空钱包空背包时，原版 getShowType() 的兜底分支是钻石（随后按钮显示余额不足）。
        assertThat(response.priceCurrency()).isEqualTo(RoomToolCurrency.DIAMOND);
        assertThat(response.priceAmount()).isEqualTo(RoomToolPricing.CHANGE_CARD_DIAMOND);
    }

    @Test
    void voiceRequiresMp4FourHundredMillisAndAtMostFiveHundredTwelveKib() {
        arrangeMember(USER_ID);
        byte[] valid = new byte[512];
        when(operationRepository.findByUserIdAndIdempotencyKey(USER_ID, "voice-1"))
                .thenReturn(Optional.empty());
        when(messageRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(operationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        RoomMessageResponse response =
                service.sendVoice(
                        USER_ID, "123456", "voice-1", "audio/mp4", 1_000, valid);
        assertThat(response.message().durationMillis()).isEqualTo(1_000);

        assertVoiceRejected("audio/aac", 1_000, valid);
        assertVoiceRejected("audio/mp4", 399, valid);
        assertVoiceRejected("audio/mp4", 30_001, valid);
        assertVoiceRejected("audio/mp4", 1_000, new byte[512 * 1024 + 1]);
    }

    @Test
    void nonMemberCannotReadRoomToolsOrVoice() {
        when(roomRepository.findByRoomNumber("123456")).thenReturn(Optional.of(room));
        when(sessionRepository.findByRoomId(room.getId())).thenReturn(Optional.of(session));
        when(seatRepository.findByIdSessionIdAndUserId(session.getId(), OUTSIDER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.state(OUTSIDER_ID, "123456"))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.GAMEPLAY_FORBIDDEN);
        verify(messageRepository, never()).findLatest(any(), any(Integer.class));
    }

    private void assertVoiceRejected(String mediaType, int durationMillis, byte[] bytes) {
        assertThatThrownBy(
                        () -> service.sendVoice(
                                USER_ID,
                                "123456",
                                UUID.randomUUID().toString(),
                                mediaType,
                                durationMillis,
                                bytes))
                .isInstanceOf(ApiException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.ROOM_TOOLS_VOICE_INVALID);
    }

    private void arrangeMember(UUID userId) {
        when(roomRepository.findByRoomNumber("123456")).thenReturn(Optional.of(room));
        when(sessionRepository.findByRoomId(room.getId())).thenReturn(Optional.of(session));
        when(seatRepository.findByIdSessionIdAndUserId(session.getId(), userId))
                .thenReturn(Optional.of(seat));
    }
}
