package jinproject.aideo.core.inference

import android.content.Context
import com.k2fsa.sherpa.onnx.OfflineSpeakerDiarization
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ServiceComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ServiceScoped
import dagger.hilt.components.SingletonComponent
import jinproject.aideo.core.inference.senseVoice.SenseVoice
import jinproject.aideo.core.inference.senseVoice.SenseVoiceManager
import jinproject.aideo.core.inference.whisper.Whisper
import jinproject.aideo.core.inference.whisper.WhisperManager
import jinproject.aideo.core.media.MediaFileManager
import jinproject.aideo.core.runtime.api.SpeechToText
import jinproject.aideo.core.runtime.impl.executorch.ExecutorchSTT
import jinproject.aideo.core.runtime.impl.onnx.OnnxSTT
import jinproject.aideo.core.runtime.impl.onnx.SileroVad
import jinproject.aideo.core.runtime.impl.onnx.SpeakerDiarization
import jinproject.aideo.data.datasource.local.LocalFileDataSource

@Module
@InstallIn(ServiceComponent::class)
object SpeechRecognitionModule {

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
        @ExecutorchSTT speechToText: SpeechToText,
        vad: SileroVad,
    ): WhisperManager {
        return WhisperManager(
            context = context,
            localFileDataSource = localFileDataSource,
            mediaFileManager = mediaFileManager,
            speechToText = speechToText,
            vad = vad,
        )
    }
}