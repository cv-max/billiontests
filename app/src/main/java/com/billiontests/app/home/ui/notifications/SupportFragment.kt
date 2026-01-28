package com.billiontests.app.home.ui.notifications

import com.billiontests.app.databinding.FragmentSupportBinding
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.billiontests.app.database.ScanDatabase
import com.billiontests.app.model.ScanResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SupportFragment : Fragment(), ScanResultAdapter.OnScannedItemClickListener {

    private var _binding: FragmentSupportBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!
    private lateinit var scanResultAdapter: ScanResultAdapter
    private lateinit var scanDatabase: ScanDatabase

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val supportViewModel =
            ViewModelProvider(this).get(SupportViewModel::class.java)

        _binding = FragmentSupportBinding.inflate(inflater, container, false)
        val root: View = binding.root

        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        scanDatabase = ScanDatabase.getDatabase(requireContext())

        fetchScanResults()
    }

    private fun fetchScanResults() {
        lifecycleScope.launch(Dispatchers.IO) {
            val scanResults = scanDatabase.scanResultDao().getAllScanResults()
            launch(Dispatchers.Main) {
                setupRecyclerView(scanResults)
            }
        }
    }

    private fun setupRecyclerView(scanResults: List<ScanResult>) {
        binding.historyTv.isVisible = scanResults.isNullOrEmpty()

        scanResultAdapter = ScanResultAdapter(scanResults, this)
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = scanResultAdapter
        }
    }
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onScannedItemClick(path: String,scanResult: ScanResult) {
        TODO("Not yet implemented")
    }
}