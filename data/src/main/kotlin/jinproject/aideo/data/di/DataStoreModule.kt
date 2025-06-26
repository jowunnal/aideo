package jinproject.aideo.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import jinproject.aideo.app.PlayerPreferences
import jinproject.aideo.data.datasource.local.datastore.serializer.PlayerPreferencesSerializer
import javax.inject.Singleton


internal val Context.playerPreferencesStore: DataStore<PlayerPreferences> by dataStore(
    fileName = "simulator_prefs.pb",
    serializer = PlayerPreferencesSerializer,
)

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    @Singleton
    @Provides
    fun providesPlayerPreferencesDataStore(@ApplicationContext context: Context): DataStore<PlayerPreferences> {
        return context.playerPreferencesStore
    }
}