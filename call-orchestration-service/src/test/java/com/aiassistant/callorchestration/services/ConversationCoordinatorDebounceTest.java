package com.aiassistant.callorchestration.services;

import com.aiassistant.callorchestration.clients.KnowledgeServiceClient;
import com.aiassistant.callorchestration.clients.ws.AiConversationWsClient;
import com.aiassistant.callorchestration.telephony.CallSession;
import com.aiassistant.callorchestration.telephony.CallSessionRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * End-to-end behavioural tests for the STT-debounce path in
 * {@link ConversationCoordinator}. Validates the contract callers care
 * about: multiple STT FINALs arriving inside one user thought get merged
 * into a single MESSAGE; HANGUP / endingCall drop late finals; unclear
 * finals bypass debounce.
 */
class ConversationCoordinatorDebounceTest {

    private AiConversationWsClient wsClient;
    private CallSessionRegistry registry;
    private KnowledgeServiceClient knowledgeClient;
    private PostCallOrchestrator postCall;
    private ScheduledExecutorService scheduler;
    private ConversationCoordinator coordinator;

    private CallSession session;

    @BeforeEach
    void setUp() {
        wsClient = Mockito.mock(AiConversationWsClient.class);
        registry = Mockito.mock(CallSessionRegistry.class);
        knowledgeClient = Mockito.mock(KnowledgeServiceClient.class);
        postCall = Mockito.mock(PostCallOrchestrator.class);
        scheduler = Executors.newScheduledThreadPool(2);

        coordinator = new ConversationCoordinator(
                wsClient, knowledgeClient, registry, postCall, scheduler);

        session = CallSession.builder()
                .callId("CA-test")
                .conversationId("conv-1")
                .businessId("biz-1")
                .startedAt(Instant.now())
                .build();
        when(registry.get("CA-test")).thenReturn(Optional.of(session));
        // AI WS is treated as "always open" in unit tests — production code
        // polls isOpen() before sending, so we satisfy that handshake here.
        when(wsClient.isOpen("conv-1")).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        scheduler.shutdownNow();
    }

    @Test
    void singleFinal_emitsOneMessage_afterDebounceWindow() {
        coordinator.onCustomerUtterance("CA-test", "What are your timings?");

        // Nothing fires synchronously.
        verify(wsClient, never()).sendUserMessage(Mockito.anyString(), Mockito.anyString(), Mockito.anyString());

        // After the debounce window (~700ms + small overhead), exactly one MESSAGE.
        await().atMost(4, TimeUnit.SECONDS).untilAsserted(() ->
                verify(wsClient).sendUserMessage(
                        Mockito.eq("conv-1"), Mockito.anyString(), Mockito.eq("What are your timings?")));
    }

    @Test
    void twoBackToBackFinals_mergeIntoSingleMessage() {
        coordinator.onCustomerUtterance("CA-test", "I'm considering a residential project");
        coordinator.onCustomerUtterance("CA-test", "could you tell me if your systems can be customized");

        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        await().atMost(4, TimeUnit.SECONDS).untilAsserted(() ->
                verify(wsClient, times(1)).sendUserMessage(
                        Mockito.eq("conv-1"), Mockito.anyString(), textCaptor.capture()));
        String merged = textCaptor.getValue();
        assertTrue(merged.contains("residential project"), "must contain first fragment");
        assertTrue(merged.contains("customized"), "must contain second fragment");
    }

    @Test
    void unclearFinal_bypassesDebounce_andFiresUnclearImmediately() {
        coordinator.onCustomerUtterance("CA-test", "garbled audio", /*clear=*/ false);

        // UNCLEAR fires synchronously (no debounce).
        verify(wsClient).sendUnclearMessage(Mockito.eq("conv-1"), Mockito.anyString());
        // And does NOT enqueue a debounced MESSAGE.
        verify(wsClient, never()).sendUserMessage(Mockito.anyString(), Mockito.anyString(), Mockito.anyString());
    }

    @Test
    void emptyOrBlankText_isDropped() {
        coordinator.onCustomerUtterance("CA-test", "");
        coordinator.onCustomerUtterance("CA-test", "   ");
        coordinator.onCustomerUtterance("CA-test", null);
        verifyNoMoreInteractions(wsClient);
    }

    @Test
    void noSessionForCallId_isDropped() {
        when(registry.get("missing-call")).thenReturn(Optional.empty());
        coordinator.onCustomerUtterance("missing-call", "Hello?");
        verifyNoMoreInteractions(wsClient);
    }

    @Test
    void endingCall_dropsAllStt_evenBeforeFlush() throws InterruptedException {
        session.getEndingCall().set(true);
        coordinator.onCustomerUtterance("CA-test", "I have another question");
        // Wait past the debounce window — still no message.
        Thread.sleep(2000);
        verify(wsClient, never()).sendUserMessage(Mockito.anyString(), Mockito.anyString(), Mockito.anyString());
    }

    @Test
    void transcriptEntry_isAddedOnceOnFlush_notPerFinal() {
        coordinator.onCustomerUtterance("CA-test", "Tell me");
        coordinator.onCustomerUtterance("CA-test", "about your services");
        await().atMost(4, TimeUnit.SECONDS).untilAsserted(() ->
                verify(wsClient).sendUserMessage(Mockito.anyString(), Mockito.anyString(), Mockito.anyString()));

        // One merged transcript entry, lowercase "customer" speaker (the
        // STT-consumer side no longer adds the per-final "CUSTOMER" entry).
        assertEquals(1, session.getTranscript().size(),
                "exactly one transcript entry per merged user turn");
        CallSession.TranscriptEntry entry = session.getTranscript().get(0);
        assertEquals("customer", entry.getSpeaker());
        assertTrue(entry.getText().contains("Tell me"));
        assertTrue(entry.getText().contains("about your services"));
    }

    @Test
    void activeMessageId_isSetOnFlush() {
        assertEquals(null, session.getActiveMessageId());
        coordinator.onCustomerUtterance("CA-test", "What is the price?");
        await().atMost(4, TimeUnit.SECONDS).until(() -> session.getActiveMessageId() != null);
        assertFalse(session.getActiveMessageId().isBlank(),
                "activeMessageId must be the ULID we sent so stale RESPONSE_DELTA frames can be dropped");
    }
}
