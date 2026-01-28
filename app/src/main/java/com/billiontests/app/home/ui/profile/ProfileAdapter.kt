package com.billiontests.app.home.ui.profile

import com.billiontests.app.R
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ProfileAdapter(private val profileList: List<String>, private val clickListener: OnProfileItemClickListener) :
    RecyclerView.Adapter<ProfileAdapter.ProfileViewHolder>() {

    interface OnProfileItemClickListener {
        fun onProfileItemClick(item: String, position: Int)
    }

    class ProfileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val profileName: TextView = itemView.findViewById(R.id.tvProfileName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProfileViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.profile_list_item, parent, false)
        return ProfileViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ProfileViewHolder, position: Int) {
        holder.profileName.text = profileList[position]
        holder.profileName.setOnClickListener {
            clickListener.onProfileItemClick(profileList[position], position)
        }
    }

    override fun getItemCount(): Int {
        return profileList.size
    }
}
