package com.substring.helpdesk.service;

import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.openai.OpenAiAudioTranscriptionOptions;
import org.springframework.ai.openai.api.OpenAiAudioApi;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
public class AudioToTextService {

    private final TranscriptionModel transcriptionModel;

    public AudioToTextService(TranscriptionModel transcriptionModel) {
        this.transcriptionModel = transcriptionModel;
    }


    public String transcribe(Resource file) {
        String textResult = transcriptionModel.transcribe(file, OpenAiAudioTranscriptionOptions.builder()
                        .temperature(0.7F)
                        .language("en")
                        .responseFormat(OpenAiAudioApi.TranscriptResponseFormat.TEXT)
                .build());
        return textResult;
    }
}
