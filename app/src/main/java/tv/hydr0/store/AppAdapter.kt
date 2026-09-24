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
        val category = view.findViewById<TextView>(R.id.category)
        val version = view.findViewById<TextView>(R.id.version)
        val state = view.findViewById<TextView>(R.id.state)
        val codePanel = view.findViewById<View>(R.id.codePanel)
        val code = view.findViewById<TextView>(R.id.code)
        val action = view.findViewById<TextView>(R.id.action)
        val delete = view.findViewById<TextView>(R.id.delete)

        name.text = app.name
        category.text = app.category
        if (app.version.isNotEmpty()) {
            version.text = "v" + app.version
        } else {
            version.text = sourceLabel(app)
        }
        icon.clipToOutline = true   // round the icon to its tile's corners
        Icons.show(app, icon)

        // Install code panel, like the codes you type into "Enter code".
        // Hidden (but still taking up room) when an app has no code, so cards line up.
        if (app.code.isNotEmpty()) {
            code.text = app.code
            codePanel.visibility = View.VISIBLE
        } else {
            codePanel.visibility = View.INVISIBLE
        }

        val status = Packages.state(context, app)
        if (status == Packages.UPDATE_AVAILABLE) {
            state.text = "Update"
            state.setTextColor(Color.parseColor("#FFB020"))
            state.setBackgroundResource(R.drawable.pill_warn)
            state.visibility = View.VISIBLE
            action.text = "Update"
            delete.visibility = View.VISIBLE
        } else if (status == Packages.INSTALLED) {
            state.text = "Installed"
            state.setTextColor(Color.parseColor("#3DDC84"))
            state.setBackgroundResource(R.drawable.pill_ok)
            state.visibility = View.VISIBLE
            action.text = "Open"
            delete.visibility = View.VISIBLE
        } else {
            state.visibility = View.GONE
            delete.visibility = View.GONE
            if (app.source.type == "store" || app.source.type == "web") {
                action.text = "Get"
            } else {
                action.text = "Install"
            }
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
