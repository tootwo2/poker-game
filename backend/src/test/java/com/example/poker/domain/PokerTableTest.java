package com.example.poker.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PokerTableTest {
    @Test
    void startsHeadsUpHandAndPostsBlinds() {
        PokerTable table = new PokerTable(UUID.randomUUID(), "测试桌", 6, 2_000, 10, 20);
        PlayerState alice = table.join("Alice");
        table.join("Bob");
        table.start(alice.id());
        assertThat(table.phase()).isEqualTo(GamePhase.PRE_FLOP);
        assertThat(table.pot()).isEqualTo(30);
        assertThat(table.players()).allMatch(player -> player.holeCards().size() == 2);
        assertThat(table.currentTurnSeat()).isEqualTo(table.dealerSeat());
        assertThat(table.actionDeadlineEpochMillis()).isGreaterThan(System.currentTimeMillis());
        assertThat(table.actionTimeSeconds()).isEqualTo(25);
    }

    @Test
    void rejectsActionFromWrongPlayer() {
        PokerTable table = new PokerTable(UUID.randomUUID(), "测试桌", 6, 2_000, 10, 20);
        PlayerState alice = table.join("Alice");
        PlayerState bob = table.join("Bob");
        table.start(alice.id());
        UUID wrongPlayer = table.currentTurnSeat() == alice.seat() ? bob.id() : alice.id();
        assertThatThrownBy(() -> table.act(wrongPlayer, ActionType.FOLD, null))
                .hasMessageContaining("还没轮到");
    }

    @Test
    void foldingAwardsTheCurrentPot() {
        PokerTable table = new PokerTable(UUID.randomUUID(), "测试桌", 6, 2_000, 10, 20);
        PlayerState alice = table.join("Alice");
        PlayerState bob = table.join("Bob");
        table.start(alice.id());
        table.act(alice.id(), ActionType.FOLD, null);
        assertThat(table.phase()).isEqualTo(GamePhase.SHOWDOWN);
        assertThat(table.pot()).isZero();
        assertThat(bob.chips()).isEqualTo(2_010);
        assertThat(table.actionDeadlineEpochMillis()).isZero();
        assertThat(table.showdownWinner(bob.id())).isTrue();
    }

    @Test
    void newPlayerWaitsOutTheCurrentHand() {
        PokerTable table = new PokerTable(UUID.randomUUID(), "测试桌", 6, 2_000, 10, 20);
        PlayerState alice = table.join("Alice");
        table.join("Bob");
        table.start(alice.id());
        int pot = table.pot();
        int turn = table.currentTurnSeat();

        PlayerState carol = table.join("Carol");

        assertThat(carol.status()).isEqualTo(PlayerStatus.SITTING);
        assertThat(carol.holeCards()).isEmpty();
        assertThat(carol.chips()).isEqualTo(2_000);
        assertThat(table.pot()).isEqualTo(pot);
        assertThat(table.currentTurnSeat()).isEqualTo(turn);
        assertThat(table.phase()).isEqualTo(GamePhase.PRE_FLOP);
        assertThat(table.message()).contains("下一局");
        assertThat(table.playedHand(carol.id())).isFalse();
        assertThatThrownBy(() -> table.act(carol.id(), ActionType.FOLD, null))
                .hasMessageContaining("还没轮到");

        table.act(table.currentPlayer().id(), ActionType.FOLD, null);
        assertThat(table.phase()).isEqualTo(GamePhase.SHOWDOWN);
        assertThat(carol.chips()).isEqualTo(2_000);

        table.start(alice.id());
        assertThat(carol.status()).isEqualTo(PlayerStatus.ACTIVE);
        assertThat(carol.holeCards()).hasSize(2);
        assertThat(table.playedHand(carol.id())).isTrue();
    }

    @Test
    void leavingBetweenHandsRemovesTheSeatAndReturnsChips() {
        PokerTable table = new PokerTable(UUID.randomUUID(), "测试桌", 6, 2_000, 10, 20);
        PlayerState alice = table.join("Alice");
        table.join("Bob");

        PokerTable.Departure departure = table.leave(alice.id());

        assertThat(departure.chips()).isEqualTo(2_000);
        assertThat(departure.recordHand()).isFalse();
        assertThat(table.players()).extracting(PlayerState::nickname).containsExactly("Bob");
        assertThat(table.phase()).isEqualTo(GamePhase.WAITING);
        table.join("Alice");
        assertThat(table.players()).hasSize(2);
    }

    @Test
    void leavingALiveHandFoldsAndAwardsTheRemainingPlayer() {
        PokerTable table = new PokerTable(UUID.randomUUID(), "测试桌", 6, 2_000, 10, 20);
        PlayerState alice = table.join("Alice");
        PlayerState bob = table.join("Bob");
        table.start(alice.id());
        PlayerState actor = table.currentPlayer();
        PlayerState other = actor.id().equals(alice.id()) ? bob : alice;
        int stack = actor.chips();

        PokerTable.Departure departure = table.leave(actor.id());

        assertThat(departure.chips()).isEqualTo(stack);
        assertThat(departure.recordHand()).isTrue();
        assertThat(departure.result()).isEqualTo("LOSS");
        assertThat(departure.netChips()).isNegative();
        assertThat(departure.chips() + other.chips()).isEqualTo(4_000);
        assertThat(table.phase()).isEqualTo(GamePhase.SHOWDOWN);
        assertThat(table.players()).extracting(PlayerState::id).containsExactly(other.id());
        assertThat(table.showdownWinner(other.id())).isTrue();
        assertThat(table.pot()).isZero();
        assertThat(table.message()).contains("离开了牌桌");
    }

    @Test
    void leavingOutOfTurnFoldsWithoutMovingTheAction() {
        PokerTable table = new PokerTable(UUID.randomUUID(), "测试桌", 6, 2_000, 10, 20);
        table.join("Alice");
        table.join("Bob");
        PlayerState carol = table.join("Carol");
        table.start(table.players().get(0).id());
        assertThat(table.currentPlayer().nickname()).isEqualTo("Alice");
        int turn = table.currentTurnSeat();
        int pot = table.pot();

        table.leave(carol.id());

        assertThat(table.phase()).isEqualTo(GamePhase.PRE_FLOP);
        assertThat(table.currentTurnSeat()).isEqualTo(turn);
        assertThat(table.pot()).isEqualTo(pot);
        assertThat(table.players()).extracting(PlayerState::nickname).containsExactly("Alice", "Bob");
        assertThat(table.message()).isEqualTo("Carol 离开了牌桌");
    }

    @Test
    void spectatorCanLeaveWithoutTouchingTheHand() {
        PokerTable table = new PokerTable(UUID.randomUUID(), "测试桌", 6, 2_000, 10, 20);
        PlayerState alice = table.join("Alice");
        table.join("Bob");
        table.start(alice.id());
        PlayerState carol = table.join("Carol");
        int turn = table.currentTurnSeat();
        int pot = table.pot();

        PokerTable.Departure departure = table.leave(carol.id());

        assertThat(departure.chips()).isEqualTo(2_000);
        assertThat(departure.recordHand()).isFalse();
        assertThat(table.players()).hasSize(2);
        assertThat(table.phase()).isEqualTo(GamePhase.PRE_FLOP);
        assertThat(table.currentTurnSeat()).isEqualTo(turn);
        assertThat(table.pot()).isEqualTo(pot);
    }

    @Test
    void rejectsLeavingWhileAllIn() {
        PokerTable table = new PokerTable(UUID.randomUUID(), "测试桌", 6, 2_000, 10, 20);
        table.join("Alice");
        table.join("Bob");
        table.start(table.players().get(0).id());
        table.act(table.currentPlayer().id(), ActionType.ALL_IN, null);
        PlayerState allIn = table.players().stream()
                .filter(player -> player.status() == PlayerStatus.ALL_IN).findFirst().orElseThrow();

        assertThat(table.phase()).isEqualTo(GamePhase.PRE_FLOP);
        assertThatThrownBy(() -> table.leave(allIn.id())).hasMessageContaining("全押");
        assertThat(table.players()).hasSize(2);
        assertThat(allIn.status()).isEqualTo(PlayerStatus.ALL_IN);
    }

    @Test
    void lastPlayerLeavingResetsAnEmptyTable() {
        PokerTable table = new PokerTable(UUID.randomUUID(), "测试桌", 6, 2_000, 10, 20);
        PlayerState alice = table.join("Alice");

        table.leave(alice.id());

        assertThat(table.players()).isEmpty();
        assertThat(table.phase()).isEqualTo(GamePhase.WAITING);
        assertThat(table.pot()).isZero();
        assertThat(table.currentTurnSeat()).isEqualTo(-1);
        assertThat(table.message()).contains("等待玩家加入");
    }

    @Test
    void aiPlayerCannotLeave() {
        PokerTable table = new PokerTable(UUID.randomUUID(), "私人桌", 6, 2_000, 10, 20, true);
        table.join("Alice");
        PlayerState ai = table.joinAi("AI·小河");

        assertThatThrownBy(() -> table.leave(ai.id())).hasMessageContaining("AI");
        assertThat(table.players()).hasSize(2);
    }
}
