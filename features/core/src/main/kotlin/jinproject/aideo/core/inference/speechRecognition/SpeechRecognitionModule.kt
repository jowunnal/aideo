package jinproject.aideo.core.inference.speechRecognition

import dagger.Binds
import dagger.MapKey
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import jinproject.aideo.core.inference.SpeechRecognitionAvailableModel
import jinproject.aideo.core.inference.speechRecognition.api.SpeechRecognition

@MapKey
@Retention(AnnotationRetention.BINARY)
annotation class SpeechRecognitionModelKey(val value: SpeechRecognitionAvailableModel)

@Module
@InstallIn(SingletonComponent::class)
abstract class SpeechRecognitionModule {

    @Binds
    @IntoMap
    @SpeechRecognitionModelKey(SpeechRecognitionAvailableModel.Whisper)
    abstract fun bindWhisper(impl: Whisper): SpeechRecognition

    @Binds
    @IntoMap
    @SpeechRecognitionModelKey(SpeechRecognitionAvailableModel.SenseVoice)
    abstract fun bindSenseVoice(impl: SenseVoice): SpeechRecognition
}
