package com.billiontests.app.authentication

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.billiontests.app.R
import com.billiontests.app.home.BottomHomeActivity
import com.billiontests.app.utils.PreferenceUtils
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Observer
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.billiontests.app.network.NetworkConnectionLiveData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class AuthenticationActivity : AppCompatActivity() {

    private lateinit var navController: NavController
    private lateinit var auth: FirebaseAuth
    private lateinit var networkConnectionLiveData: NetworkConnectionLiveData

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_authentication)

        val sharedPreferences = getSharedPreferences(PreferenceUtils.PREFERENCE_NAME, MODE_PRIVATE)
        sharedPreferences.edit().putBoolean(PreferenceUtils.IS_FIRST_TIME_USER, false).apply()

        auth = Firebase.auth
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        // Get the NavController
        navController = navHostFragment.navController
        // Set up the ActionBar with the Navigation UI
        networkConnectionLiveData = NetworkConnectionLiveData(this)

    }

    public override fun onStart() {
        super.onStart()
        // Check if user is signed in (non-null) and update UI accordingly.
        val sharedPreferences = getSharedPreferences(PreferenceUtils.PREFERENCE_NAME, MODE_PRIVATE)

        val isSignedUp = sharedPreferences.getBoolean(PreferenceUtils.IS_SIGNED_UP, false)
        val currentUser = auth.currentUser
        if (currentUser != null && isSignedUp) {
            startActivity(Intent(this, BottomHomeActivity::class.java))
            finish()
            return
        }
        networkConnectionLiveData.observe(this, Observer { isConnected ->
            Log.e("Internet","is internet available: $isConnected")
            if(isConnected.not()){
                showNoInternetDialog()
            }
        })

    }

    private fun showNoInternetDialog() {
        val dialogBuilder = AlertDialog.Builder(this)
            .setTitle("No Internet Connection")
            .setMessage("Internet is not available. Please check your connection and try again.")
            .setCancelable(false)
            .setPositiveButton("Retry") { dialog, _ ->
                dialog.dismiss()
                // Optionally, you can trigger a retry action here or observe the network state again
                if (!isInternetAvailable()) {
                    // If internet is still not connected, show the dialog again
                    showNoInternetDialog()
                }
            }.setNegativeButton("Close App"){dialog,_->
                dialog.dismiss()
                finish()
            }

        val alertDialog = dialogBuilder.create()
        alertDialog.show()
    }

    private fun isInternetAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork
        val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        return networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }
}