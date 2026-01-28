package com.billiontests.app.home.ui.home

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import com.billiontests.app.R
import androidx.fragment.app.viewModels
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity.MODE_PRIVATE
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.billiontests.app.databinding.FragmentHomeBinding
import com.billiontests.app.home.HomeViewModel
import androidx.navigation.fragment.findNavController
import com.billiontests.app.utils.PreferenceUtils

class HomeFragment : Fragment() {

    companion object {
        fun newInstance() = HomeFragment()
    }

    private val viewModel: HomeViewModel by viewModels()
    private lateinit var sharedPreferences: SharedPreferences

    private lateinit var player: ExoPlayer

    private lateinit var binding:FragmentHomeBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // TODO: Use the ViewModel
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentHomeBinding.inflate(inflater)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sharedPreferences = requireContext().getSharedPreferences(PreferenceUtils.PREFERENCE_NAME, MODE_PRIVATE)

        binding.takeTestButton.setOnClickListener {
            findNavController().navigate(R.id.action_navigation_home_to_testGuideFragment2)
        }


        binding.supportLayout.setOnClickListener {
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
            player.repeatMode = ExoPlayer.REPEAT_MODE_ONE
            player.play()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onPause() {
        super.onPause()
        player.release()
    }

    override fun onStop() {
        super.onStop()
        player.release()
    }
}