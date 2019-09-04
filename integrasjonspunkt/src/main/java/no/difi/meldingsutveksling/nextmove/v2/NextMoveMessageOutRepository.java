package no.difi.meldingsutveksling.nextmove.v2;

import no.difi.meldingsutveksling.nextmove.NextMoveOutMessage;
import no.difi.meldingsutveksling.nextmove.QNextMoveOutMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.data.querydsl.binding.QuerydslBinderCustomizer;
import org.springframework.data.querydsl.binding.QuerydslBindings;
import org.springframework.data.repository.PagingAndSortingRepository;

import java.util.Optional;

public interface NextMoveMessageOutRepository extends PagingAndSortingRepository<NextMoveOutMessage, Long>,
        QuerydslPredicateExecutor<NextMoveOutMessage>,
        QuerydslBinderCustomizer<QNextMoveOutMessage>,
        JpaRepository<NextMoveOutMessage, Long> {

    Optional<NextMoveOutMessage> findByConversationId(String conversationId);

    Optional<NextMoveOutMessage> findByMessageId(String messageId);

    void deleteByMessageId(String messageId);

    @Override
    default void customize(QuerydslBindings bindings, QNextMoveOutMessage root) {
        bindings.excluding(root.sbd);
    }
}