package jinproject.aideo.data.repository.impl

import jinproject.aideo.data.TranslationManager.getSubtitleFileIdentifier
import jinproject.aideo.data.datasource.local.LocalFileDataSource
import jinproject.aideo.data.datasource.local.LocalSettingDataSource
import jinproject.aideo.data.repository.MediaRepository
import jinproject.aideo.data.toSubtitleFileIdentifier
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class MediaRepositoryImpl @Inject constructor(
    private val localSettingDataSource: LocalSettingDataSource,
    private val localFileDataSource: LocalFileDataSource,
) : MediaRepository {
    @OptIn(ExperimentalCoroutinesApi::class)

    /**
     * 자막 파일이 존재하는지 확인하는 함수
     *
     * @return 자막언어와 일치하는 자막 파일이 있으면 1,
     * 자막언어와 일치하는 자막 파일은 없지만 다른 언어로 번역된 자막 파일이 있으면 0,
     * 어떠한 자막 파일도 없으면 -1
     */
    override suspend fun checkSubtitleFileExist(id: Long): Int {
        val subtitleLanguage = localSettingDataSource.getSubtitleLanguage().first()

        return checkSubtitleFileExist(id = id, srcLang = subtitleLanguage)
    }

    override suspend fun checkSubtitleFileExist(id: Long, srcLang: String): Int {
        val isSubtitleExist = localFileDataSource.isFileExist(
            fileId = id,
            fileExtension = "".toSubtitleFileIdentifier()
        )

        if (isSubtitleExist) {
            val isSubtitleByLanguageExist =
                localFileDataSource.isFileExist(
                    fileIdentifier = getSubtitleFileIdentifier(
                        id = id,
                        languageCode = srcLang
                    )
                )

            return if (isSubtitleByLanguageExist)
                1
            else
                0
        }

        return -1
    }
}