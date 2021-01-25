package io.databoxtech.r2_viewer.epub

import android.app.ProgressDialog
import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.result.ActivityResultLauncher
import io.databoxtech.r2_viewer.R
import io.databoxtech.r2epub.utils.NavigatorContract
import io.databoxtech.r2epub.utils.extensions.blockingProgressDialog
import io.databoxtech.r2epub.utils.extensions.download
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.anko.design.longSnackbar
import org.readium.r2.shared.Injectable
import org.readium.r2.shared.extensions.extension
import org.readium.r2.shared.extensions.tryOrNull
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.asset.FileAsset
import org.readium.r2.shared.publication.asset.PublicationAsset
import org.readium.r2.shared.publication.services.isRestricted
import org.readium.r2.shared.publication.services.protectionError
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.mediatype.MediaType
import org.readium.r2.streamer.Streamer
import org.readium.r2.streamer.server.Server
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.net.ServerSocket
import java.net.URL
import java.util.*
import kotlin.coroutines.CoroutineContext

class MainActivity : AppCompatActivity(), CoroutineScope {


    private val DEBUG: Boolean = true;
    private lateinit var streamer: Streamer
    private lateinit var server: Server
    private var localPort: Int = 0
    private lateinit var preferences: SharedPreferences
    private lateinit var R2DIRECTORY: String

    private lateinit var progressDialog: ProgressDialog

    private lateinit var navigatorLauncher: ActivityResultLauncher<NavigatorContract.Input>

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        progressDialog = blockingProgressDialog(getString(R.string.progress_wait_while_preparing_book))
        progressDialog.show()

        preferences = getSharedPreferences("org.readium.r2.settings", Context.MODE_PRIVATE)

        streamer = Streamer(this,
            contentProtections = listOfNotNull(
            )
        )

        val s = ServerSocket(8181)
        s.localPort
        s.close()

        localPort = s.localPort
        server = Server(localPort, applicationContext)

//        val properties = Properties()
//        val inputStream = this.assets.open("configs/config.properties")
//        properties.load(inputStream)
        val useExternalFileDir = true //properties.getProperty("useExternalFileDir", "false")!!.toBoolean()

        R2DIRECTORY = if (useExternalFileDir) {
            this.getExternalFilesDir(null)?.path + "/"
        } else {
            this.filesDir.path + "/"
        }

        navigatorLauncher = registerForActivityResult(NavigatorContract()) { pubData: NavigatorContract.Output? ->
            if (pubData == null)
                return@registerForActivityResult

            tryOrNull { pubData.publication.close() }
            Timber.d("Publication closed")
            if (pubData.deleteOnResult)
                tryOrNull { pubData.file.delete() }
            finish()
        }
        val remoteUrl = intent.getStringExtra("epub");

        openBook(remoteUrl)
    }


     fun openBook(remoteUrl: String) {

        launch {

            val remoteAsset: FileAsset? = tryOrNull { URL(remoteUrl).copyToTempFile()?.let { FileAsset(it) } }
            val mediaType = MediaType.of(fileExtension = "epub") //book.ext.removePrefix(".")
            val asset = remoteAsset // remote file
                ?: FileAsset(File(remoteUrl), mediaType = mediaType) // local file

            streamer.open(asset, allowUserInteraction = true, sender = this@MainActivity)
                .onFailure {
                    Timber.d(it)
                    progressDialog.dismiss()
                    presentOpeningException(it)
                }
                .onSuccess { it ->
                    if (it.isRestricted) {
                        progressDialog.dismiss()
                    } else {
                        prepareToServe(it, asset)
                        navigatorLauncher.launch(
                            NavigatorContract.Input(
                                file = asset.file,
                                mediaType = mediaType,
                                publication = it,
                                bookId = 128457L,
                                deleteOnResult = remoteAsset != null,
                                baseUrl = Publication.localBaseUrlOf(asset.name, localPort)
                            )
                        )
                    }
                }
        }
    }

    private fun prepareToServe(publication: Publication, asset: PublicationAsset) {
        val key = publication.metadata.identifier ?: publication.metadata.title
        preferences.edit().putString("$key-publicationPort", localPort.toString()).apply()
        val userProperties = applicationContext.filesDir.path + "/" + Injectable.Style.rawValue + "/UserProperties.json"
        server.addEpub(publication, null, "/${asset.name}", userProperties)
    }

    private fun presentOpeningException(error: Publication.OpeningException) {
        Log.d("dryize", error.message!!)
//        catalogView.longSnackbar(error.getUserMessage(this))
    }

    private suspend fun URL.copyToTempFile(): File? = tryOrNull {
        val filename = UUID.randomUUID().toString()
        val path = "$R2DIRECTORY$filename.$extension"
        download(path)
    }

    private fun startServer() {
        if (!server.isAlive) {
            try {
                server.start()
            } catch (e: IOException) {
                // do nothing
                if (DEBUG) Timber.e(e)
            }
            if (server.isAlive) {
                isServerStarted = true
            }
        }
    }

    override fun onStart() {
        super.onStart()
        startServer()
    }

    override fun onDestroy() {
        super.onDestroy()
        //TODO not sure if this is needed
        stopServer()
    }

    private fun stopServer() {
        if (server.isAlive) {
            server.stop()
            isServerStarted = false
        }
    }

    companion object {

        var isServerStarted = false
            private set

    }
}