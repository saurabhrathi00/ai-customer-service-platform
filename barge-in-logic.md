# Barge-In Logic — VoxHelperAI + EnableX

## What is Barge-In?
User interrupts the bot while it's speaking. Bot should:
1. Immediately stop talking
2. Listen to the user
3. Process new input
4. Respond naturally

## Architecture Overview

```
                    EnableX WebSocket (Bidirectional)
                           ↕
               ┌───────────────────────┐
               │   Call Orchestrator    │
               │                       │
               │  ┌─────────────────┐  │
               │  │  State Machine  │  │
               │  │                 │  │
               │  │  IDLE           │  │
               │  │  BOT_SPEAKING   │  │
               │  │  USER_SPEAKING  │  │
               │  │  PROCESSING     │  │
               │  └────────┬────────┘  │
               │           │           │
               │  ┌────────┴────────┐  │
               │  │  Audio Manager  │  │
               │  │                 │  │
               │  │  - TTS Queue    │  │
               │  │  - Send Buffer  │  │
               │  │  - VAD          │  │
               │  └─────────────────┘  │
               └───────────────────────┘
                    ↕           ↕
            ElevenLabs      Gemini 2.5
            (STT + TTS)      Flash
```

## State Machine

```
┌──────┐  call starts   ┌──────────┐
│ IDLE │ ──────────────→ │ GREETING │
└──────┘                 └────┬─────┘
                              │ greeting TTS done
                              ▼
                    ┌──────────────────┐
              ┌───→ │  USER_SPEAKING   │ ←──── user starts talking
              │     └────────┬─────────┘
              │              │ silence detected (user done)
              │              ▼
              │     ┌──────────────────┐
              │     │   PROCESSING     │ (LLM thinking + TTS generating)
              │     └────────┬─────────┘
              │              │ TTS audio ready
              │              ▼
              │     ┌──────────────────┐
              └──── │  BOT_SPEAKING    │ ←──── bot response playing
  (BARGE-IN!)       └──────────────────┘
```

**BARGE-IN happens at the BOT_SPEAKING → USER_SPEAKING transition.**

## Core Implementation

### 1. Connection Setup — Store IDs from start_media

```javascript
// call-orch-service/src/websocket-handler.js

class CallSession {
  constructor(ws) {
    this.ws = ws;                    // EnableX WebSocket
    this.voiceId = null;             // from start_media
    this.streamId = null;            // from start_media
    this.state = 'IDLE';
    this.isBotSpeaking = false;
    this.ttsQueue = [];              // pending TTS audio chunks
    this.currentSeq = 0;            // outbound sequence counter
    this.sttStream = null;           // ElevenLabs STT stream
    this.bargeInEnabled = true;
  }
}

// Handle incoming EnableX events
ws.on('message', (data) => {
  const event = JSON.parse(data);

  switch (event.event) {
    case 'connected':
      console.log('WebSocket connected to EnableX');
      break;

    case 'start_media':
      session.voiceId = event.start.voice_id;
      session.streamId = event.start.stream_id;
      session.callerNumber = event.start.from;
      session.calledNumber = event.start.to;
      session.state = 'GREETING';
      startGreeting(session);
      break;

    case 'media':
      handleIncomingAudio(session, event);
      break;

    case 'stop_media':
      cleanupSession(session);
      break;
  }
});
```

### 2. Incoming Audio Handler — Barge-In Detection

```javascript
// This is where barge-in detection happens

function handleIncomingAudio(session, event) {
  const audioPayload = event.media.payload; // Base64 ulaw audio

  // ALWAYS feed audio to STT regardless of state
  // (so we capture what user says even during bot speech)
  feedToSTT(session, audioPayload);

  // BARGE-IN CHECK: Is bot speaking AND user started talking?
  if (session.isBotSpeaking) {
    const hasUserSpeech = detectSpeech(audioPayload);

    if (hasUserSpeech) {
      triggerBargeIn(session);
    }
  }
}
```

### 3. Speech Detection (VAD) — Two Approaches

#### Approach A: STT-based detection (Simpler — Recommended for MVP)

```javascript
// ElevenLabs Scribe v2 Realtime will emit transcription events.
// If we get a transcript while bot is speaking → barge-in.

function setupSTTStream(session) {
  // Connect to ElevenLabs Scribe v2 Realtime WebSocket
  const sttWs = new WebSocket('wss://api.elevenlabs.io/v1/speech-to-text/stream', {
    headers: { 'xi-api-key': process.env.ELEVENLABS_API_KEY }
  });

  sttWs.on('open', () => {
    sttWs.send(JSON.stringify({
      type: 'config',
      config: {
        model_id: 'scribe_v2',
        sample_rate: 8000,
        encoding: 'ulaw',          // EnableX sends ulaw
        language_code: 'hi',       // Hindi (or 'en' for English)
        endpointing: {
          type: 'flexible',
          min_silence_ms: 500      // 500ms silence = user stopped talking
        }
      }
    }));
  });

  sttWs.on('message', (data) => {
    const result = JSON.parse(data);

    if (result.type === 'transcript') {
      // Got partial/final transcript

      // BARGE-IN: If bot is speaking and we got a word
      if (session.isBotSpeaking && result.text.trim().length > 0) {
        triggerBargeIn(session);
      }

      // FINAL transcript: user finished speaking
      if (result.is_final && result.text.trim().length > 0) {
        session.state = 'PROCESSING';
        processUserInput(session, result.text);
      }
    }
  });

  session.sttStream = sttWs;
}

function feedToSTT(session, base64Audio) {
  if (session.sttStream && session.sttStream.readyState === WebSocket.OPEN) {
    session.sttStream.send(JSON.stringify({
      type: 'audio',
      audio: base64Audio    // Forward EnableX audio directly to ElevenLabs
    }));
  }
}
```

#### Approach B: VAD-based detection (Faster, more control)

```javascript
// Use a lightweight VAD (Voice Activity Detection) library
// to detect if incoming audio contains speech.
// Faster than waiting for STT transcript — sub-50ms detection.

const vad = require('@anthropic/vad');  // or any VAD library

function detectSpeech(base64Audio) {
  // Decode base64 → ulaw bytes
  const ulawBuffer = Buffer.from(base64Audio, 'base64');

  // Convert ulaw to PCM for VAD processing
  const pcmBuffer = ulawToPCM(ulawBuffer);

  // Run VAD
  const isSpeech = vad.detect(pcmBuffer, {
    sampleRate: 8000,
    threshold: 0.6,           // confidence threshold
    minSpeechDuration: 100    // minimum 100ms of speech to trigger
  });

  return isSpeech;
}

// Use BOTH VAD + STT together:
// VAD for instant barge-in trigger (stop bot fast)
// STT for actual transcript (process what user said)
```

### 4. Barge-In Trigger — The Critical Function

```javascript
async function triggerBargeIn(session) {
  // Prevent multiple barge-in triggers for same interruption
  if (session.bargeInProcessing) return;
  session.bargeInProcessing = true;

  console.log(`[${session.voiceId}] BARGE-IN DETECTED`);

  // ──── STEP 1: Stop TTS generation ────
  // If TTS is still generating audio, abort it
  if (session.ttsAbortController) {
    session.ttsAbortController.abort();
  }

  // ──── STEP 2: Clear local TTS queue ────
  // Discard any audio chunks waiting to be sent
  session.ttsQueue = [];
  session.isBotSpeaking = false;

  // ──── STEP 3: Send clear_media to EnableX ────
  // This flushes any audio already sent but not yet played
  const clearEvent = {
    event: 'clear_media',
    stream_id: session.streamId,
    voice_id: session.voiceId
  };
  session.ws.send(JSON.stringify(clearEvent));

  // ──── STEP 4: Update state ────
  session.state = 'USER_SPEAKING';

  // Reset barge-in flag after short delay
  setTimeout(() => {
    session.bargeInProcessing = false;
  }, 200);

  console.log(`[${session.voiceId}] Bot stopped. Listening to user.`);
}
```

### 5. Bot Speaking — Send TTS Audio with Barge-In Awareness

```javascript
async function sendBotResponse(session, text) {
  session.state = 'BOT_SPEAKING';
  session.isBotSpeaking = true;

  // Create abort controller for this TTS stream
  session.ttsAbortController = new AbortController();

  try {
    // Stream TTS from ElevenLabs
    const ttsStream = await generateTTSStream(text, session.ttsAbortController.signal);

    for await (const audioChunk of ttsStream) {
      // CHECK: Has barge-in happened while we're sending?
      if (!session.isBotSpeaking) {
        console.log(`[${session.voiceId}] Barge-in occurred. Stopping TTS send.`);
        break;  // Stop sending remaining audio
      }

      // Convert TTS output to ulaw 8kHz (EnableX required format)
      const ulawBase64 = pcmToUlawBase64(audioChunk);

      // Send to EnableX
      const mediaEvent = {
        event: 'media',
        voice_id: session.voiceId,
        stream_id: session.streamId,
        media: {
          seq: ++session.currentSeq,
          timestamp: Date.now(),
          format: {
            encoding: 'ulaw',
            sample_rate: 8000,
            channels: 1
          },
          payload: ulawBase64
        }
      };

      session.ws.send(JSON.stringify(mediaEvent));
    }

    // If we finished speaking without interruption
    if (session.isBotSpeaking) {
      session.isBotSpeaking = false;
      session.state = 'USER_SPEAKING';
      console.log(`[${session.voiceId}] Bot finished speaking. Listening.`);
    }

  } catch (err) {
    if (err.name === 'AbortError') {
      console.log(`[${session.voiceId}] TTS aborted due to barge-in.`);
    } else {
      console.error(`[${session.voiceId}] TTS error:`, err);
    }
  }
}
```

### 6. TTS Generation — ElevenLabs Streaming

```javascript
async function generateTTSStream(text, abortSignal) {
  const response = await fetch(
    `https://api.elevenlabs.io/v1/text-to-speech/${VOICE_ID}/stream`,
    {
      method: 'POST',
      headers: {
        'xi-api-key': process.env.ELEVENLABS_API_KEY,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        text: text,
        model_id: 'eleven_flash_v2_5',
        output_format: 'ulaw_8000',   // Direct ulaw output if supported
        // OR 'pcm_16000' and downsample + transcode to ulaw yourself
        stream: true
      }),
      signal: abortSignal  // Allows aborting mid-stream on barge-in
    }
  );

  return response.body;  // ReadableStream of audio chunks
}
```

### 7. Audio Format Conversion

```javascript
// EnableX requires: ulaw, 8000 Hz, mono, base64

// If ElevenLabs outputs PCM (e.g., pcm_16000):
// 1. Downsample 16kHz → 8kHz
// 2. Convert linear PCM → ulaw
// 3. Base64 encode

const { MuLawEncoder } = require('audio-codec');

function pcmToUlawBase64(pcmBuffer, inputSampleRate = 16000) {
  let samples = new Int16Array(pcmBuffer.buffer);

  // Step 1: Downsample if needed (16kHz → 8kHz = take every 2nd sample)
  if (inputSampleRate === 16000) {
    const downsampled = new Int16Array(samples.length / 2);
    for (let i = 0; i < downsampled.length; i++) {
      downsampled[i] = samples[i * 2];
    }
    samples = downsampled;
  }

  // Step 2: PCM → ulaw
  const ulawBuffer = Buffer.alloc(samples.length);
  for (let i = 0; i < samples.length; i++) {
    ulawBuffer[i] = MuLawEncoder.encode(samples[i]);
  }

  // Step 3: Base64 encode
  return ulawBuffer.toString('base64');
}

// Reverse: EnableX ulaw → PCM for STT
function ulawToPCM(ulawBuffer) {
  const { MuLawDecoder } = require('audio-codec');
  const pcmBuffer = Buffer.alloc(ulawBuffer.length * 2);
  for (let i = 0; i < ulawBuffer.length; i++) {
    const sample = MuLawDecoder.decode(ulawBuffer[i]);
    pcmBuffer.writeInt16LE(sample, i * 2);
  }
  return pcmBuffer;
}
```

### 8. Full Conversation Loop

```javascript
async function processUserInput(session, transcript) {
  console.log(`[${session.voiceId}] User said: "${transcript}"`);

  session.state = 'PROCESSING';

  // Add to conversation history
  session.conversationHistory.push({
    role: 'user',
    content: transcript
  });

  // Get LLM response
  const llmResponse = await getGeminiResponse(
    session.systemPrompt,
    session.conversationHistory
  );

  // Add to history
  session.conversationHistory.push({
    role: 'assistant',
    content: llmResponse
  });

  // Speak the response (with barge-in awareness)
  await sendBotResponse(session, llmResponse);
}

async function getGeminiResponse(systemPrompt, history) {
  const response = await fetch(
    `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent`,
    {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'x-goog-api-key': process.env.GEMINI_API_KEY
      },
      body: JSON.stringify({
        system_instruction: { parts: [{ text: systemPrompt }] },
        contents: history.map(m => ({
          role: m.role === 'assistant' ? 'model' : 'user',
          parts: [{ text: m.content }]
        }))
      })
    }
  );

  const data = await response.json();
  return data.candidates[0].content.parts[0].text;
}
```

## Barge-In Tuning Parameters

```javascript
const BARGE_IN_CONFIG = {
  // Minimum speech duration before triggering barge-in (ms)
  // Too low = false triggers from background noise
  // Too high = slow response to interruption
  minSpeechDuration: 150,       // recommended: 100-200ms

  // VAD confidence threshold (0-1)
  // Higher = fewer false positives, but might miss soft speech
  vadThreshold: 0.6,            // recommended: 0.5-0.7

  // Debounce: ignore repeated barge-in triggers within this window
  debounceDuration: 200,        // recommended: 150-300ms

  // Should barge-in work during greeting?
  // Usually NO — let greeting play fully
  enableDuringGreeting: false,

  // Minimum words in STT before triggering (STT-based approach)
  // Helps avoid triggering on "hmm", "uh"
  minWordsToTrigger: 1,         // 1 = trigger on any word

  // Energy threshold for VAD (dB)
  // Helps filter out background noise
  energyThreshold: -35,         // recommended: -40 to -30 dB
};
```

## Edge Cases to Handle

### 1. False Barge-In (Background Noise)
```javascript
// Problem: AC noise, traffic, TV triggers barge-in
// Solution: Use energy + duration threshold
function isRealSpeech(audioBuffer) {
  const energy = calculateEnergy(audioBuffer);
  const duration = getConsecutiveSpeechDuration();

  return energy > BARGE_IN_CONFIG.energyThreshold
    && duration > BARGE_IN_CONFIG.minSpeechDuration;
}
```

### 2. User Says "Hmm" / "Haan" While Listening
```javascript
// Problem: Backchannel sounds shouldn't stop the bot
// Solution: Use STT-based approach and check word count
function shouldTriggerBargeIn(transcript) {
  const backchannelWords = ['hmm', 'haan', 'ok', 'achha', 'mm', 'uh'];
  const words = transcript.trim().toLowerCase().split(/\s+/);

  // If only backchannel words, don't interrupt
  if (words.length <= 2 && words.every(w => backchannelWords.includes(w))) {
    return false;
  }
  return true;
}
```

### 3. Bot Almost Done Speaking
```javascript
// Problem: User interrupts when bot has only 1 second left
// Solution: If less than X ms of audio remaining, let it finish
function shouldAllowBargeIn(session) {
  const remainingAudioMs = session.ttsQueue.length * CHUNK_DURATION_MS;

  if (remainingAudioMs < 500) {  // Less than 500ms remaining
    return false;  // Let bot finish
  }
  return true;
}
```

### 4. DTMF During Bot Speech
```javascript
// If EnableX sends DTMF event while bot speaking,
// treat it as intentional input — trigger barge-in
function handleDTMF(session, dtmfEvent) {
  if (session.isBotSpeaking) {
    triggerBargeIn(session);
  }
  // Process DTMF digit
  processDTMFInput(session, dtmfEvent.digit);
}
```

### 5. Rapid Successive Barge-Ins
```javascript
// Problem: User keeps interrupting every response
// Solution: Track barge-in count, adjust behavior
function trackBargeInFrequency(session) {
  session.bargeInCount = (session.bargeInCount || 0) + 1;

  // If user interrupted 3+ times in a row, they might be frustrated
  if (session.bargeInCount >= 3) {
    // Generate shorter responses from LLM
    session.systemPrompt += '\nUser seems impatient. Keep responses very brief (1-2 sentences max).';
    session.bargeInCount = 0;
  }
}
```

## Timeline of a Barge-In Event (in milliseconds)

```
T+0ms    : User starts speaking
T+20ms   : EnableX sends media event with user audio
T+50ms   : VAD detects speech energy
T+100ms  : Speech confirmed (passed minSpeechDuration check)
T+100ms  : triggerBargeIn() called
T+101ms  : TTS abort signal sent
T+102ms  : Local TTS queue cleared
T+103ms  : clear_media sent to EnableX
T+120ms  : EnableX receives clear_media, flushes audio buffer
T+150ms  : Bot audio stops playing for caller
T+150ms  : STT processing user's speech in parallel
T+500ms  : User finishes speaking (silence detected)
T+600ms  : Final transcript received
T+700ms  : Gemini processes input
T+1200ms : TTS starts streaming new response
T+1300ms : Caller hears new bot response

Total barge-in latency: ~150ms (user speaks to bot stops)
Total response latency: ~1300ms (user speaks to new response)
```

## Testing Checklist

- [ ] Bot stops within 200ms of user speaking
- [ ] clear_media actually stops audio on caller's end
- [ ] No audio glitches after barge-in (clean transition)
- [ ] STT captures what user said during/after barge-in
- [ ] Background noise doesn't trigger false barge-in
- [ ] "Hmm", "haan" don't trigger barge-in
- [ ] Multiple rapid barge-ins don't crash the session
- [ ] Barge-in during greeting can be disabled
- [ ] Conversation history stays intact after barge-in
- [ ] Barge-in works with both Hindi and English speech
- [ ] Audio format (ulaw 8kHz) maintained correctly after barge-in
