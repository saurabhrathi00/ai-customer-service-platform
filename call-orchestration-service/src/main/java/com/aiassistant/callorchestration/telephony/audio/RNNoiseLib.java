package com.aiassistant.callorchestration.telephony.audio;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

public interface RNNoiseLib extends Library {

    RNNoiseLib INSTANCE = Native.load("rnnoise", RNNoiseLib.class);

    Pointer rnnoise_create(Pointer model);

    void rnnoise_destroy(Pointer state);

    /**
     * Process one frame (480 float samples at 48 kHz = 10 ms).
     * Returns VAD probability [0..1].
     */
    float rnnoise_process_frame(Pointer state, float[] out, float[] in);

    int rnnoise_get_frame_size();
}
