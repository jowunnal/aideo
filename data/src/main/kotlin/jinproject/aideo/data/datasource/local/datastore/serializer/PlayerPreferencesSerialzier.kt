package jinproject.aideo.data.datasource.local.datastore.serializer

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.google.protobuf.InvalidProtocolBufferException
import jinproject.aideo.app.PlayerPreferences
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale

internal object PlayerPreferencesSerializer : Serializer<PlayerPreferences> {
    override val defaultValue: PlayerPreferences =
        PlayerPreferences.newBuilder()
            .setInferenceLanguage("auto")
            .setSubtitleLanguage(Locale.getDefault().language)
            .setSelectedSpeechRecognitionModel("SenseVoice")
            .setSelectedTranslationModel("MlKit")
            .build()

    override suspend fun readFrom(input: InputStream): PlayerPreferences {
        try {
            return PlayerPreferences.parseFrom(input)
        } catch (exception: InvalidProtocolBufferException) {
            throw CorruptionException("Cannot read proto.", exception)
        }
    }

    override suspend fun writeTo(t: PlayerPreferences, output: OutputStream) = t.writeTo(output)
}