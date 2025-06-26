package jinproject.aideo.data.di

import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jinproject.aideo.data.BuildConfig
import jinproject.aideo.data.datasource.remote.RemoteGCPDataSource
import jinproject.aideo.data.datasource.remote.service.GCPService
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.create
import java.io.IOException
import java.time.Duration
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object GoogleCloudRetrofitModule {

    @Singleton
    @Provides
    fun provideTranslationOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .addInterceptor(HeaderInterceptor("Bearer ${BuildConfig.GOOGLE_TRANSLATION_ID}"))
            .readTimeout(Duration.ofSeconds(60))
            .writeTimeout(Duration.ofSeconds(30))
            .build()
    }

    @Singleton
    @Provides
    fun provideTranslationRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create(GsonBuilder().setLenient().create()))
            .baseUrl("https://translation.googleapis.com/")
            .client(okHttpClient)
            .build()
    }

    @Singleton
    @Provides
    fun createSTTService(retrofit: Retrofit): GCPService =
        retrofit.create()
}

internal class HeaderInterceptor @Inject constructor(private val accessToken: String) :
    Interceptor {
    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val newRequest = chain.request().newBuilder()
            .addHeader("Authorization", accessToken)
            .build()

        return chain.proceed(newRequest)
    }
}