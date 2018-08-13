package si.hehe.iscp

import android.content.Context
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView

class ImageAdapter(private val context: Context, private val images: List<Uri>) : BaseAdapter() {
    override fun getView(pos: Int, view: View?, p2: ViewGroup?): View {
        val imageView : ImageView
        if (view == null) {
            imageView = ImageView(context)
            imageView.layoutParams = ViewGroup.LayoutParams(85, 85)
            imageView.scaleType = ImageView.ScaleType.CENTER_CROP
            imageView.setPadding(8, 8, 8, 8)
        } else {
            imageView = view as ImageView
        }
        imageView.setImageURI(images[pos])
        return imageView
    }

    override fun getItem(p0: Int): Any {
        return images[p0]
    }

    override fun getItemId(p0: Int): Long {
        return p0.toLong()
    }

    override fun getCount(): Int {
        return images.size
    }

}