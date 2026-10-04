package com.example.poker.service;

import com.example.poker.domain.ActionType;
import com.example.poker.dto.TableViews;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.support.ExecutorSubscribableChannel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TableServiceLeaveTest {
    @Test
    void leavingRemovesTheSeatSoThePlayerCanJoinAgain() {
        TableService service = new TableService(new SimpMessagingTemplate(new ExecutorSubscribableChannel()));
        TableViews.SessionView alice = service.create("离开桌", "Alice", 6, false, 0);
        TableViews.SessionView bob = service.join(alice.table().id(), "Bob");

        service.leave(alice.table().id(), alice.playerId(), alice.reconnectToken());

        assertThatThrownBy(() -> service.get(alice.table().id(), alice.playerId(), alice.reconnectToken()))
                .hasMessageContaining("玩家不存在");
        TableViews.TableView remaining = service.get(alice.table().id(), bob.playerId(), bob.reconnectToken());
        assertThat(remaining.players()).extracting(TableViews.PlayerView::nickname).containsExactly("Bob");
        assertThat(service.list().get(0).playerCount()).isEqualTo(1);

        TableViews.SessionView rejoined = service.join(alice.table().id(), "Alice");
        assertThat(rejoined.playerId()).isNotEqualTo(alice.playerId());
        assertThat(service.list().get(0).playerCount()).isEqualTo(2);
    }

    @Test
    void leavingDuringAHandFoldsAndKeepsTheTableOpen() {
        TableService service = new TableService(new SimpMessagingTemplate(new ExecutorSubscribableChannel()));
        TableViews.SessionView alice = service.create("离开桌", "Alice", 6, false, 0);
        TableViews.SessionView bob = service.join(alice.table().id(), "Bob");
        TableViews.TableView table = service.start(alice.table().id(), alice.playerId(), alice.reconnectToken());
        TableViews.PlayerView actor = table.players().stream().filter(TableViews.PlayerView::currentTurn)
                .findFirst().orElseThrow();
        TableViews.SessionView actorSession = actor.id().equals(alice.playerId()) ? alice : bob;
        TableViews.SessionView other = actorSession == alice ? bob : alice;

        service.leave(table.id(), actorSession.playerId(), actorSession.reconnectToken());

        TableViews.TableView remaining = service.get(table.id(), other.playerId(), other.reconnectToken());
        assertThat(remaining.phase().name()).isEqualTo("SHOWDOWN");
        assertThat(remaining.players()).hasSize(1);
        assertThat(remaining.message()).contains("离开了牌桌");
        assertThat(remaining.nextHandDeadline()).isZero();
        assertThat(service.list().get(0).playerCount()).isEqualTo(1);
    }

    @Test
    void rejectsLeavingWhileAllIn() {
        TableService service = new TableService(new SimpMessagingTemplate(new ExecutorSubscribableChannel()));
        TableViews.SessionView alice = service.create("离开桌", "Alice", 6, false, 0);
        TableViews.SessionView bob = service.join(alice.table().id(), "Bob");
        TableViews.TableView table = service.start(alice.table().id(), alice.playerId(), alice.reconnectToken());
        TableViews.PlayerView actor = table.players().stream().filter(TableViews.PlayerView::currentTurn)
                .findFirst().orElseThrow();
        TableViews.SessionView actorSession = actor.id().equals(alice.playerId()) ? alice : bob;
        TableViews.SessionView other = actorSession == alice ? bob : alice;
        service.act(table.id(), actorSession.playerId(), actorSession.reconnectToken(), ActionType.ALL_IN, null);

        assertThatThrownBy(() -> service.leave(table.id(), actorSession.playerId(), actorSession.reconnectToken()))
                .hasMessageContaining("全押");
        assertThat(service.get(table.id(), other.playerId(), other.reconnectToken()).players()).hasSize(2);
    }
}
