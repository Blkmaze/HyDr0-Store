package tv.hydr0.store

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView

// Feeds app cards to the grid.
class AppAdapter(private val context: Context) : BaseAdapter() {

    private var items: List<StoreApp> = ArrayList()
    private val inflater = LayoutInflater.from(context)

    fun setItems(newItems: List<StoreApp>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun getCount(): Int {
        return items.size
    }

    override fun getItem(position: Int): StoreApp {
        return items[position]
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var view = convertView
        if (view == null) {
            view = inflater.inflate(R.layout.item_app, parent, false)
        }
        val app = items[position]

        val icon = view!!.findViewById<ImageView>(R.id.icon)
        val name = view.findViewById<TextView>(R.id.name)
        val version = view.findViewById<TextView>(R.id.version)
        val state = view.findViewById<TextView>(R.id.state)

        name.text = app.name
        if (app.version.isNotEmpty()) {
            version.text = "v" + app.version
        } else {
            version.text = sourceLabel(app)
        }
        Icons.show(app, icon)

        val status = Packages.state(context, app)
        if (status == Packages.UPDATE_AVAILABLE) {
            state.text = "Update available"
            state.setTextColor(Color.parseColor("#FFB020"))
        } else if (status == Packages.INSTALLED) {
            state.text = "Installed"
            state.setTextColor(Color.parseColor("#3DDC84"))
        } else {
            state.text = ""
        }
        return view
    }

    private fun sourceLabel(app: StoreApp): String {
        if (app.source.type == "store") {
            return "App store"
        }
        if (app.source.type == "web") {
            return "Website"
        }
        if (app.source.type == "github") {
            return "Latest release"
        }
        return ""
    }
}
