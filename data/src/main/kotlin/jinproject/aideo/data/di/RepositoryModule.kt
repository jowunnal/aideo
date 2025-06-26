package jinproject.aideo.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jinproject.aideo.data.repository.GalleryRepository
import jinproject.aideo.data.repository.PlayerRepository
import jinproject.aideo.data.repository.impl.GalleryRepositoryImpl
import jinproject.aideo.data.repository.impl.PlayerRepositoryImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindGalleryRepository(
        galleryRepositoryImpl: GalleryRepositoryImpl
    ): GalleryRepository

    @Binds
    @Singleton
    abstract fun bindPlayerRepository(
        playerRepositoryImpl: PlayerRepositoryImpl
    ): PlayerRepository
} 