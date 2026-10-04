package com.example.poker.service;

import com.example.poker.domain.ActionType;
import com.example.poker.dto.TableViews;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.support.ExecutorSubscribableChannel;

import static org.assertj.core.api.Assertions.assertThat;

class TableServiceNextHandTest {
    @Test
    void startsTheNextHandAutomaticallyAfterShowdown() {
        TableService service = new TableService(new SimpMessagingTemplate(new ExecutorSubscribableChannel()));
        TableViews.SessionView alice = service.create("自动下一局", "Alice", 2, false, 0);
        TableViews.SessionView bob = service.join(alice.table().id(), "Bob");
        TableViews.TableView table = service.start(alice.table().id(), alice.playerId(), alice.reconnectToken());

        TableViews.PlayerView actor = table.players().stream().filter(TableViews.PlayerView::currentTurn)
                .findFirst().orElseThrow();
        TableViews.SessionView actorSession = actor.id().equals(alice.playerId()) ? alice : bob;
        table = service.act(table.id(), actorSession.playerId(), actorSession.reconnectToken(), ActionType.FOLD, null);

        assertThat(table.phase().name()).isEqualTo("SHOWDOWN");
        assertThat(table.nextHandDeadline()).isGreaterThan(System.currentTimeMillis());

        service.runScheduledNextHand(table.id());
        TableViews.TableView next = service.get(table.id(), alice.playerId(), alice.reconnectToken());

        assertThat(next.handNumber()).isEqualTo(table.handNumber() + 1);
        assertThat(next.phase().name()).isEqualTo("PRE_FLOP");
        assertThat(next.nextHandDeadline()).isZero();
    }
}
