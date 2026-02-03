package jinproject.aideo.core.inference.speechRecognition

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import jinproject.aideo.core.inference.speechRecognition.api.SpeechRecognition
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SpeechRecognitionModule {

    @Provides
    @Singleton
    @WhisperModel
    fun providesWhisper(
        @ApplicationContext context: Context,
    ): SpeechRecognition {
        return Whisper(
            context = context,
        )
    }

    @Provides
    @Singleton
    @SenseVoiceModel
    fun providesSenseVoice(
        @ApplicationContext context: Context,
    ): SpeechRecognition {
        return SenseVoice(context = context)
    }
}