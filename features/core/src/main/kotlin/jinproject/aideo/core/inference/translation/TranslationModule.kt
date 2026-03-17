package jinproject.aideo.core.inference.translation

import dagger.Binds
import dagger.MapKey
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import jinproject.aideo.core.inference.translation.api.Translation
import javax.inject.Singleton

@MapKey
@Retention(AnnotationRetention.BINARY)
annotation class TranslationModelKey(val value: TranslationAvailableModel)

@Module
@InstallIn(SingletonComponent::class)
abstract class TranslationModule {

    @Binds
    @IntoMap
    @TranslationModelKey(TranslationAvailableModel.M2M100)
    abstract fun bindsM2M100(impl: M2M100): Translation

    @Binds
    @IntoMap
    @TranslationModelKey(TranslationAvailableModel.MlKit)
    abstract fun bindsMlKit(impl: MlKitTranslation): Translation
}