package jinproject.aideo.data

import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object TranslationManager {
    suspend fun detectLanguage(text: String): String = suspendCancellableCoroutine { cont ->
        LanguageIdentification.getClient(
            LanguageIdentificationOptions.Builder()
                .setConfidenceThreshold(0.2f)
                .build()
        ).identifyLanguage(extractTextFromSrt(text))
            .addOnSuccessListener { languageCode ->
                if (languageCode == "und") {
                    cont.resumeWithException(IllegalStateException("Language couldn't be identified"))
                } else {
                    cont.resume(languageCode)
                }
            }
            .addOnFailureListener { e ->
                cont.resumeWithException(e)
            }
    }

    private fun extractTextFromSrt(srtContent: String): String {
        val timeRegex = Regex("""^\d{2}:\d{2}:\d{2}[,\.]\d{3} --> """)
        val indexRegex = Regex("""^\d+$""")

        return srtContent
            .lines()
            .filter { line ->
                line.isNotBlank() &&
                        !timeRegex.containsMatchIn(line) &&
                        !indexRegex.matches(line)
            }
            .joinToString("\n") { it.trim() }
    }
}