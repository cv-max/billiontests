package com.billiontests.app.home.ui.notifications

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.billiontests.app.R
import com.billiontests.app.databinding.ItemScanResultBinding
import com.billiontests.app.model.ScanResult
import com.billiontests.app.utils.formatTimestamp
import com.bumptech.glide.Glide

class ScanResultAdapter(private val scanResults: List<ScanResult>, private val clickListener: OnScannedItemClickListener) :
    RecyclerView.Adapter<ScanResultAdapter.ScanResultViewHolder>() {

    interface OnScannedItemClickListener {
        fun onScannedItemClick(path: String, scanResult: ScanResult)
    }

    inner class ScanResultViewHolder(private val binding: ItemScanResultBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(scanResult: ScanResult, position: Int) {
            binding.scanIdTextView.text =  scanResult.scanId
            binding.dateTextView.text = formatTimestamp(scanResult.timeStamp, "dd/MM/yyyy hh:mm a")
            binding.statusTextView.text = scanResult.status.capitalize()
            binding.statusTextView.setTextColor(
                if (scanResult.status == "success") ContextCompat.getColor(
                    binding.root.context,
                    R.color.green
                ) else ContextCompat.getColor(binding.root.context, R.color.red)
            )

            Glide.with(binding.itemImageView.context).load(scanResult.scannedImage)
                .into(binding.itemImageView)

            binding.ivSyncStatus.setImageResource(if(scanResult.isUploaded) R.drawable.baseline_cloud_done_24 else R.drawable.baseline_sync_24)

            binding.scannedItemLayout.setOnClickListener {
                clickListener.onScannedItemClick(scanResult.scannedImage, scanResult)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ScanResultViewHolder {
        val binding =
            ItemScanResultBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ScanResultViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ScanResultViewHolder, position: Int) {
        holder.bind(scanResults[position], position)
    }

    override fun getItemCount(): Int = scanResults.size
}
