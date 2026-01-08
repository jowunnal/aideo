package jinproject.aideo.core.runtime

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import jinproject.aideo.core.inference.whisper.WhisperManager
import jinproject.aideo.core.inference.senseVoice.SenseVoiceManager
import jinproject.aideo.core.inference.whisper.VocabUtils
import jinproject.aideo.core.runtime.api.SpeechToText
import jinproject.aideo.core.runtime.impl.executorch.ExecutorchSTT
import jinproject.aideo.core.runtime.impl.executorch.ExecutorchSpeechToText
import jinproject.aideo.core.runtime.impl.onnx.OnnxSTT
import jinproject.aideo.core.runtime.impl.onnx.OnnxSpeechToText
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SpeechToTextModule {

    @Provides
    @Singleton
    @ExecutorchSTT
    fun providesExecutorchSpeechToText(
        @ApplicationContext context: Context,
        vocabUtils: VocabUtils,
    ): SpeechToText {
        return ExecutorchSpeechToText(
            context = context,
            vocabUtils = vocabUtils
        )
    }

    @Provides
    @Singleton
    @OnnxSTT
    fun providesOnnxSpeechToText(
        @ApplicationContext context: Context,
    ): SpeechToText {
        return OnnxSpeechToText(context = context)
    }
}