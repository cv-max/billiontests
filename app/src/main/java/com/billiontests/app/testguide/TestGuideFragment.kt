package com.billiontests.app.testguide

import android.content.Context
import androidx.fragment.app.viewModels
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.billiontests.app.R
import com.billiontests.app.databinding.FragmentTestGuideBinding
import com.billiontests.app.scan.CameraXActivity
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.CountDownTimer
import android.util.Log
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.billiontests.app.utils.PreferenceUtils
import com.billiontests.app.utils.SELECTED_VALUE

class TestGuideFragment : Fragment() {

    companion object {
        fun newInstance() = TestGuideFragment()
        private const val TAG = "TestGuideFragment"
    }

    private val viewModel: TestGuideViewModel by viewModels()

    private lateinit var binding: FragmentTestGuideBinding

    private lateinit var player: ExoPlayer

    private var timer: CountDownTimer? = null

    val items = listOf("Mission", "Roche")

    private var selectedValue: String = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentTestGuideBinding.inflate(inflater)

        binding.playAgainButton.setOnClickListener {
            if(::player.isInitialized) {
                player.seekTo(0)
                // Prepare the player.
                player.prepare()

                player.play()
            }
        }
        binding.startScanButton.setOnClickListener {

            if(::player.isInitialized) {
                player.release()
            }
//            startTimer(binding.timerTv)
            goToScan()
        }

        binding.startForceScanButton.setOnClickListener {
            showForceScanDialog()
        }

        val adapter = ArrayAdapter(
            requireContext(),
            R.layout.strip_list_item,
            items
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinner.adapter = adapter

        selectedValue = PreferenceUtils.getString(requireContext(), PreferenceUtils.SELECTED_ROACH, items[0])
        binding.spinner.setSelection(items.indexOf(selectedValue))

        binding.spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View?,
                position: Int,
                id: Long
            ) {
                selectedValue = items[position]
                Log.e("TAG", "Selected: $selectedValue")
                PreferenceUtils.putString(requireContext(), PreferenceUtils.SELECTED_ROACH,selectedValue)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        return binding.root
    }

    private fun showForceScanDialog() {
        val builder = AlertDialog.Builder(requireContext())
            .setTitle("Timer is still Running!")
            .setMessage("Please wait to get accurate test result")
            .setCancelable(false)
            .setPositiveButton("Start Now") { _, _ ->
                // Restart the scanning process
                if (timer != null) {
                    timer?.cancel()
                    timer = null
                }
                goToScan()
            }
            .setNegativeButton("Wait") { dialog, _ ->
                // Close the activity
                dialog.dismiss()
            }
        builder?.show()
    }

    override fun onResume() {
        super.onResume()
        try {
            val rawVideoUri =
                Uri.parse("android.resource://" + "com.billiontests.app" + "/" + R.raw.how_to_use)

            val mediaItem = MediaItem.fromUri(rawVideoUri)
            player = ExoPlayer.Builder(requireContext()).build()
            binding.playerView.player = player
            player.setMediaItem(mediaItem)
            player.prepare()

            player.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun goToScan() {

        binding.playerView.visibility = View.VISIBLE
        binding.progressBar.visibility = View.GONE
        binding.playAgainButton.visibility = View.VISIBLE
        binding.instructionTv.text = "How To Scan"
        binding.startScanButton.isEnabled = true
        binding.startForceScanButton.visibility = View.GONE
        binding.startScanButton.visibility = View.VISIBLE
        startActivity(Intent(requireActivity(), CameraXActivity::class.java).putExtra(SELECTED_VALUE, selectedValue))
    }

    private fun startTimer(textView: TextView) {
        binding.startScanButton.isEnabled = false
        textView.visibility = View.VISIBLE
        binding.playerView.visibility = View.INVISIBLE
        binding.progressBar.visibility = View.VISIBLE
        binding.playAgainButton.visibility = View.INVISIBLE
        binding.startForceScanButton.visibility = View.VISIBLE
        binding.startScanButton.visibility = View.GONE
        val totalDurationInSeconds = 30

        binding.progressBar.max = totalDurationInSeconds
        binding.instructionTv.text = "Scan the BT Card immediately after the timer ends"
        timer = object : CountDownTimer(totalDurationInSeconds * 1000L, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                // Update the TextView with the remaining seconds
                val secondsRemaining = millisUntilFinished / 1000
                textView.text = "$secondsRemaining"
                val progress =
                    ((totalDurationInSeconds - secondsRemaining) / totalDurationInSeconds.toFloat() * binding.progressBar.max).toInt()
                binding.progressBar.progress = progress

            }

            override fun onFinish() {
                // Handle the timer finish
                textView.text = "Scan Now"
                binding.progressBar.progress = totalDurationInSeconds.toInt()
                textView.visibility = View.GONE
                goToScan()

            }
        }
        timer?.start()
    }

    private fun isInternetAvailable(): Boolean {
        val connectivityManager = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork
        val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        return networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    private fun showNoInternetDialog() {
        val dialogBuilder = AlertDialog.Builder(requireContext())
            .setTitle("No Internet Connection")
            .setMessage("Internet is not available. Please check your connection and try again.")
            .setCancelable(false)
            .setPositiveButton("Retry") { dialog, _ ->
                dialog.dismiss()
                // Optionally, you can trigger a retry action here or observe the network state again
                if (!isInternetAvailable()) {
                    // If internet is still not connected, show the dialog again
                    showNoInternetDialog()
                }else{
                    goToScan()
                }
            }

        val alertDialog = dialogBuilder.create()
        alertDialog.show()
    }
    override fun onPause() {
        super.onPause()
        if(::player.isInitialized) {
            player.release()
        }
    }

    override fun onStop() {
        super.onStop()
        if(::player.isInitialized) {
            player.release()
        }
    }
}