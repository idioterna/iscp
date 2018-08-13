package si.hehe.iscp

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.view.View
import kotlinx.android.synthetic.main.activity_upload.*
import net.schmizz.sshj.xfer.InMemorySourceFile
import org.jetbrains.anko.defaultSharedPreferences
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.toast
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.URI

class UploadActivity : AppCompatActivity() {

    private val ssh = SSH()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_upload)
        val prefs = defaultSharedPreferences
        if (prefs.getString("auth_ssh_key", "") == "") {

            val d = AlertDialog.Builder(this).create()
            d.setTitle(getText(R.string.title_alert_not_configured))
            d.setMessage(getText(R.string.message_alert_not_configured))
            d.setButton(0, "OK") { _, _ -> finishActivity(0)}
            d.show()
        }
        upload_button.setOnClickListener { _ ->
            upload_progress_bar.visibility = View.VISIBLE
            handleUpload(intent)
            upload_button.text = getText(R.string.cancel_upload)
            upload_button.setOnClickListener {
                finish()
            }
        }
        val images = getImages(intent)
        image_view.adapter = ImageAdapter(this, images)
        upload_progress_bar.visibility = View.INVISIBLE
    }

    private fun processImage(uri: Uri, index: Int) : String {
        val prefs = defaultSharedPreferences
        val image = BitmapFactory.decodeStream(contentResolver.openInputStream(uri))
        val imageSize = Integer.valueOf(prefs.getString("image_size", "1000")!!)
        val imageScaling = prefs.getString("image_crop", "longer")!!
        val imageQuality = Integer.valueOf(prefs.getString("image_quality", "75")!!)
        val urlPrefix = prefs.getString("url_prefix", "")!!
        val path = prefs.getString("path", "")!!
        val prefix = upload_prefix.text.toString()
        var width = 0
        var height = 0
        when {
            imageScaling.startsWith("longer") -> {
                if (image.width >= image.height) { // landscape
                    width = imageSize
                    height = Math.round(imageSize * (image.height.toFloat() / image.width))
                } else { // portrait
                    width = Math.round(imageSize * (image.width.toFloat() / image.height))
                    height = imageSize
                }
            }
            imageScaling.startsWith("horiz") -> {
                val factor = (imageSize.toFloat() / image.width)
                width = imageSize
                height = Math.round(image.height * factor)
            }
            imageScaling.startsWith("vert") -> {
                val factor = (imageSize.toFloat() / image.height)
                width = Math.round(image.width * factor)
                height = imageSize
            }
        }
        val filename : String
        filename = if (index > 0) {
            prefix + "-" + index.toString() + ".jpg"
        } else {
            "$prefix.jpg"
        }
        doAsync {
            val ni = Bitmap.createScaledBitmap(image, width, height, false)
            image.recycle()
            val baos = ByteArrayOutputStream()
            ni.compress(Bitmap.CompressFormat.JPEG, imageQuality, baos)
            ni.recycle()

            val mf = object : InMemorySourceFile() {
                override fun getName(): String {
                    return filename
                }

                override fun getLength(): Long {
                    return baos.size().toLong()
                }

                override fun getInputStream(): InputStream {
                    return ByteArrayInputStream(baos.toByteArray())
                }
            }
            ssh.upload(prefs, mf, File(path, filename).path)
            baos.close()
        }
        return URI.create(urlPrefix).resolve(filename).toString() + "\n"
    }

    private fun getImages(intent: Intent) : List<Uri> {
        when {
            intent.action == Intent.ACTION_SEND
                    && intent.type?.startsWith("image/") == true -> {
                return listOf(intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as Uri)
            }
            intent.action == Intent.ACTION_SEND_MULTIPLE
                    && intent.type?.startsWith("image/") == true -> {
                return intent.getParcelableArrayListExtra<Parcelable>(Intent.EXTRA_STREAM)
                        .map {it as Uri}.toList()
            }
        }
        return listOf()
    }

    private fun handleUpload(intent: Intent) {
        val uris = getImages(intent)
        var urls = ""
        var cnt = 0
        upload_progress_bar.isIndeterminate = false
        upload_progress_bar.max = uris.size
        upload_progress_bar.progress = cnt
        if (uris.size == 1) {
            cnt = -1
        }
        for (uri in uris) {
            upload_status_text.text = uri.toString()
            cnt++
            urls += processImage(uri, cnt)
            upload_progress_bar.progress = cnt
        }
        val clipboard: ClipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.primaryClip = ClipData.newPlainText("urls", urls)
        toast("urls copied to clipboard")
        finish()

    }

}

