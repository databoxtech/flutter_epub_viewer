package io.databoxtech.r2epub.utils

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import io.databoxtech.r2_viewer.epub.EpubActivity
import java.io.File
import org.readium.r2.shared.util.mediatype.MediaType
import org.readium.r2.shared.extensions.destroyPublication
import org.readium.r2.shared.extensions.getPublication
import org.readium.r2.shared.extensions.putPublication
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.Locator


class NavigatorContract : ActivityResultContract<NavigatorContract.Input, NavigatorContract.Output>() {

    data class Input(
        val file: File,
        val mediaType: MediaType?,
        val publication: Publication,
        val bookId: Long?,
        val initialLocator: Locator? = null,
        val deleteOnResult: Boolean = false,
        val baseUrl: String? = null
    )

    data class Output(
        val file: File,
        val publication: Publication,
        val deleteOnResult: Boolean
    )

    override fun createIntent(context: Context, input: Input): Intent {
        val intent = Intent(context, when (input.mediaType) {
            MediaType.EPUB -> EpubActivity::class.java
//            MediaType.PDF, MediaType.LCP_PROTECTED_PDF -> PdfActivity::class.java
//            MediaType.READIUM_AUDIOBOOK, MediaType.READIUM_AUDIOBOOK_MANIFEST, MediaType.LCP_PROTECTED_AUDIOBOOK -> AudiobookActivity::class.java
//            MediaType.CBZ -> ComicActivity::class.java
//            MediaType.PDF -> DiViNaActivity::class.java
            else -> throw IllegalArgumentException("Unknown [mediaType]")
        })

        intent.apply {
            putPublication(input.publication)
            putExtra("bookId", input.bookId)
            putExtra("publicationPath", input.file.path)
            putExtra("publicationFileName", input.file.name)
            putExtra("deleteOnResult", input.deleteOnResult)
            putExtra("baseUrl", input.baseUrl)
//            putExtra("locator", input.initialLocator)
        }
        return intent
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Output? {
        if (intent == null)
            return null

        val path = intent.getStringExtra("publicationPath")
            ?: throw Exception("publicationPath required")

        intent.destroyPublication(null)

        return Output(
            file = File(path),
            publication = intent.getPublication(null),
            deleteOnResult = intent.getBooleanExtra("deleteOnResult", false)
        )
    }
}