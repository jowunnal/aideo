package jinproject.aideo.core.inference

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ServiceComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ServiceScoped
import jinproject.aideo.core.inference.senseVoice.SenseVoiceManager
import jinproject.aideo.core.inference.whisper.WhisperManager
import jinproject.aideo.core.media.MediaFileManager
import jinproject.aideo.core.runtime.api.SpeechToText
import jinproject.aideo.core.runtime.impl.onnx.OnnxSTT
import jinproject.aideo.core.runtime.impl.onnx.Punctuation
import jinproject.aideo.core.runtime.impl.onnx.SileroVad
import jinproject.aideo.core.runtime.impl.onnx.SpeakerDiarization
import jinproject.aideo.data.datasource.local.LocalFileDataSource
import jinproject.aideo.data.datasource.local.LocalPlayerDataSource
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

@Module
@InstallIn(ServiceComponent::class)
object SpeechRecognitionModule {

    @Provides
    @ServiceScoped
    fun providesSpeechRecognitionManager(
        localPlayerDataSource: LocalPlayerDataSource,
        @ApplicationContext context: Context,
        mediaFileManager: MediaFileManager,
        @OnnxSTT speechToText: SpeechToText,
        vad: SileroVad,
        speakerDiarization: SpeakerDiarization,
        localFileDataSource: LocalFileDataSource,
        punctuation: Punctuation,
    ): SpeechRecognitionManager {
        val selectedModel = runBlocking {
            localPlayerDataSource.getSelectedModel().first()
        }

        return when (AvailableModel.findByName(selectedModel)) {
            AvailableModel.SenseVoice -> {
                SenseVoiceManager(
                    mediaFileManager = mediaFileManager,
                    speechToText = speechToText,
                    vad = vad,
                    speakerDiarization = speakerDiarization,
                    localFileDataSource = localFileDataSource,
                    punctuation = punctuation
                )
            }

            AvailableModel.Whisper -> {
                WhisperManager(
                    context = context,
                    localFileDataSource = localFileDataSource,
                    mediaFileManager = mediaFileManager,
                    speechToText = speechToText,
                    vad = vad,
                    speakerDiarization = speakerDiarization
                )
            }
        }
    }

    /*
    @Provides
    @SenseVoice
    @ServiceScoped
    fun providesSenseVoiceManager(
        mediaFileManager: MediaFileManager,
        @OnnxSTT speechToText: SpeechToText,
        vad: SileroVad,
        speakerDiarization: SpeakerDiarization,
        localFileDataSource: LocalFileDataSource,
    ): SpeechRecognitionManager {
        return SenseVoiceManager(
            mediaFileManager = mediaFileManager,
            speechToText = speechToText,
            vad = vad,
            speakerDiarization = speakerDiarization,
            localFileDataSource = localFileDataSource
        )
    }

    @Provides
    @Whisper
    @ServiceScoped
    fun providesWhisperManager(
        @ApplicationContext context: Context,
        localFileDataSource: LocalFileDataSource,
        mediaFileManager: MediaFileManager,
        @OnnxSTT speechToText: SpeechToText,
        vad: SileroVad,
        speakerDiarization: SpeakerDiarization,
    ): WhisperManager {
        return WhisperManager(
            context = context,
            localFileDataSource = localFileDataSource,
            mediaFileManager = mediaFileManager,
            speechToText = speechToText,
            vad = vad,
            speakerDiarization = speakerDiarization
        )
    }
     */
}