package com.billiontests.app.home.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.billiontests.app.databinding.FragmentTestResultBinding
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity.MODE_PRIVATE
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.billiontests.app.R
import com.billiontests.app.database.ScanDatabase
import com.billiontests.app.home.ui.notifications.ScanResultAdapter
import com.billiontests.app.model.ScanResult
import com.billiontests.app.model.toImageAnalysisResponse
import com.billiontests.app.model.toMissionAnalysisResponse
import com.billiontests.app.scan.ResultActivity
import com.billiontests.app.utils.MISSION_RESPONSE
import com.billiontests.app.utils.PreferenceUtils
import com.billiontests.app.utils.SELECTED_VALUE
import com.bumptech.glide.Glide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DashboardFragment : Fragment(), ScanResultAdapter.OnScannedItemClickListener {

    private var _binding: FragmentTestResultBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!
    private lateinit var scanResultAdapter: ScanResultAdapter
    private lateinit var scanDatabase: ScanDatabase
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val dashboardViewModel =
            ViewModelProvider(this)[DashboardViewModel::class.java]

        _binding = FragmentTestResultBinding.inflate(inflater, container, false)
        val root: View = binding.root
        sharedPreferences =
            requireContext().getSharedPreferences(PreferenceUtils.PREFERENCE_NAME, MODE_PRIVATE)

        scanDatabase = ScanDatabase.getDatabase(requireContext())

        fetchScanResults()

        binding.takeTestResultButton.setOnClickListener {
            findNavController().navigate(DashboardFragmentDirections.actionNavigationDashboardToTestGuideFragment2())
        }

        binding.whatsappButton.setOnClickListener {
            val phoneNumber =
                "+917982764035" // Replace with the actual phone number in international format
            val url = "https://api.whatsapp.com/send?phone=$phoneNumber"

            try {
                val intent = Intent(Intent.ACTION_VIEW)
                intent.setPackage("com.whatsapp") // Ensure the intent will only open WhatsApp
                intent.data = Uri.parse(url)
                startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
                // Handle error if WhatsApp is not installed or the number is incorrect
                Toast.makeText(
                    requireContext(),
                    "WhatsApp is not installed on your device",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        return root
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
        binding.noResultTv.isVisible = scanResults.isNullOrEmpty()

        scanResultAdapter = ScanResultAdapter(scanResults, this)
        binding.resultRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = scanResultAdapter
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onScannedItemClick(path: String, scanResult: ScanResult) {
        startActivity(
            Intent(requireContext(), ResultActivity::class.java)
                .putExtra("path", path)
                .putExtra(SELECTED_VALUE, if(scanResult.isMission) "Mission" else "Roche")
                .putExtra(
                    if (scanResult.isMission) MISSION_RESPONSE else "response",
                    if (scanResult.isMission) scanResult.toMissionAnalysisResponse() else scanResult.toImageAnalysisResponse()
                )
                .putExtra("addToDb", false)
                .putExtra("result", scanResult)
        )
    }
}