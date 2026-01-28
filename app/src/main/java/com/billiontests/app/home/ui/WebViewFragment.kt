package com.billiontests.app.home.ui

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.billiontests.app.databinding.FragmentWebViewBinding
import android.webkit.WebSettings
import android.webkit.WebViewClient
import androidx.appcompat.app.AlertDialog

class WebViewFragment : Fragment() {

    private lateinit var binding: FragmentWebViewBinding

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment

        binding = FragmentWebViewBinding.inflate(inflater)

        binding.webViewProgressBar.visibility = View.VISIBLE
        val url = requireArguments().getString("url") ?: ""
        val webView = binding.webView

        webView.webViewClient = WebViewClient() // Ensures WebView opens links within the app

        val webSettings: WebSettings = webView.settings
        webSettings.javaScriptEnabled = true // Enable JavaScript if needed

        if (url.isNotEmpty()) {
            webView.loadUrl(url)
            binding.webViewProgressBar.visibility = View.GONE
        }else{
            binding.webViewProgressBar.visibility = View.GONE
        }
        return binding.root
    }

}