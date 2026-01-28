package com.billiontests.app.scan

// UrineTestAdapter.kt
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.billiontests.app.R
import com.billiontests.app.model.UrineTestResult

class UrineTestAdapter(
    private val items: List<UrineTestResult>,
    private val onDetailsButtonClickListener: OnDetailsButtonClickListener
) : RecyclerView.Adapter<UrineTestAdapter.UrineViewHolder>() {

    interface OnDetailsButtonClickListener{
        fun onDetailsButtonClick(urineTestResult: UrineTestResult)
    }

    class UrineViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvParameter: TextView = itemView.findViewById(R.id.tvParameter)
        val tvResult: TextView = itemView.findViewById(R.id.tvResult)
        val tvNormalRange: TextView = itemView.findViewById(R.id.tvNormalRange)
//        val btnDetails: TextView = itemView.findViewById(R.id.details_button)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UrineViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_urin_test, parent, false)
        return UrineViewHolder(view)
    }

    override fun onBindViewHolder(holder: UrineViewHolder, position: Int) {
        val item = items[position]
        holder.tvParameter.text = item.parameter
        if(item.parameter.equals("Erythrocytes")){
            holder.tvParameter.text = "Erythrocytes/Hb"
        }
        holder.tvResult.text = "Result: " + item.result
        holder.tvNormalRange.text = "Expected: " + item.normalRange
        if(item.normalRange.contains("Negative")) {
            if (item.result.equals(item.normalRange)) {
                holder.tvResult.setTextColor(holder.tvResult.context.resources.getColor(R.color.green))
            } else {
                holder.tvResult.setTextColor(holder.tvResult.context.resources.getColor(R.color.red))
            }
        }
        holder.itemView.setOnClickListener {
            onDetailsButtonClickListener.onDetailsButtonClick(item)
        }
    }

    override fun getItemCount() = items.size
}
