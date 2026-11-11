package com.billiontests.app.home

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Insets
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Observer
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupWithNavController
import com.billiontests.app.R
import com.billiontests.app.databinding.ActivityBottomHomeBinding
import com.billiontests.app.network.NetworkConnectionLiveData
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.ktx.Firebase
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfig
import com.google.firebase.remoteconfig.ktx.remoteConfigSettings


class BottomHomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBottomHomeBinding
    private lateinit var networkConnectionLiveData: NetworkConnectionLiveData
    private val remoteConfig: FirebaseRemoteConfig = FirebaseRemoteConfig.getInstance()
    private val configSettings = remoteConfigSettings {
        minimumFetchIntervalInSeconds = 0
    }

    private var isInternetAvailable = false

    companion object {
        private const val TAG = "BottomHomeActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        enableEdgeToEdge()
        binding = ActivityBottomHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupEdgeToEdge()
//        ViewCompat.setOnApplyWindowInsetsListener(binding.container) { v, insets ->
//            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
//
//            // Apply top padding to the container to avoid status bar overlap
//            v.updatePadding(top = systemBars.top)
//
//            insets
//        }
        remoteConfig.setConfigSettingsAsync(configSettings)
        remoteConfig.setDefaultsAsync(R.xml.remote_config_defaults)
        val navView: BottomNavigationView = binding.navView
        val wrapperView = binding.fragWrapper

        val params = wrapperView.layoutParams as CoordinatorLayout.LayoutParams

        val navController = findNavController(R.id.nav_host_fragment_activity_bottom_home)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_home, R.id.navigation_dashboard, R.id.navigation_notifications
            )
        )
//        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)
        // Set up OnDestinationChangedListener to hide/show BottomNavigationView
        navController.addOnDestinationChangedListener { _, destination, _ ->
            when (destination.id) {
                R.id.navigation_home,
                R.id.navigation_dashboard,
//                R.id.navigation_notifications,
                R.id.navigation_profile -> {
                    // Show the BottomNavigationView for these fragments
                    navView.visibility = View.VISIBLE
                    binding.bottomAppBar.visibility = View.VISIBLE
                    // Reserve exactly the bar's real height (includes its nav-bar inset padding)
                    // instead of a hardcoded 90dp, which left a grey band when they didn't match.
                    binding.bottomAppBar.post {
                        params.setMargins(0, 0, 0, binding.bottomAppBar.height)
                        wrapperView.layoutParams = params
                    }
                }

                else -> {
                    // Hide the BottomNavigationView for other fragments
                    navView.visibility = View.GONE
                    binding.bottomAppBar.visibility = View.GONE
                    params.setMargins(0, 0, 0, 0)
                    wrapperView.layoutParams = params

                }
            }
        }
        val appVersion = getAppVersion(this)

        remoteConfig.fetchAndActivate()
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val updated = task.result
                    Log.d(TAG, "Config params updated: $updated")

                    val remoteConfigValues = remoteConfig.all
                    val url = remoteConfig.getString("url") as String
                    val version = remoteConfig.getString("version") as String

                    Log.e(TAG, "values from config: version = $version, url = $url")
                    if (url.isNotEmpty() && isInternetAvailable && appVersion != version) {
                        showAutoUpdateDialog(url, version)
                    }
                }
            }
        networkConnectionLiveData = NetworkConnectionLiveData(this)
    }

    private fun Int.dp(): Int =
        (this * resources.displayMetrics.density).toInt()

    private fun getAppVersion(context: Context): String {
        var result = ""
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            result = packageInfo.versionName ?: ""
            result = if (result.isNotEmpty()) result.replace("[a-zA-Z]|-".toRegex(), "") else ""
        } catch (e: PackageManager.NameNotFoundException) {
            // Handle the exception
        }
        return result
    }

    override fun onResume() {
        super.onResume()
//        networkConnectionLiveData.observe(this, Observer { isConnected ->
//            isInternetAvailable = isConnected
//            Log.e("Internet", "is internet available: $isConnected")
//            if (isConnected.not()) {
//                showNoInternetDialog()
//            }
//        })
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
            }.setNegativeButton("Close App") { dialog, _ ->
                dialog.dismiss()
                finish()
            }

        val alertDialog = dialogBuilder.create()
        alertDialog.show()
    }

    private fun showAutoUpdateDialog(url: String, version: String) {
        val dialogBuilder = AlertDialog.Builder(this)
            .setTitle("Update Available")
            .setMessage("Please download the latest version ($version) of the app.")
            .setCancelable(false)
            .setPositiveButton("Update") { dialog, _ ->
                dialog.dismiss()
                openUrl(url)
                // Optionally, you can trigger a retry action here or observe the network state again

            }.setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }

        val alertDialog = dialogBuilder.create()
        alertDialog.show()
    }

    private fun isInternetAvailable(): Boolean {
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork
        val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        return networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse(url)
        // Check if there's an app that can handle this intent
//        if (intent.resolveActivity(packageManager) != null) {
        startActivity(intent)
//        } else {
//            // Handle the case where no app can handle the URL
//            Toast.makeText(this, "No application available to open the URL", Toast.LENGTH_SHORT).show()
//        }
    }

    private fun setupEdgeToEdge() {
        val containerInitialPadding = InitialPadding(binding.container)
        val bottomBarInitialPadding = InitialPadding(binding.bottomAppBar)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val displayCutout = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout())

            val safeInsets = Insets.of(
                maxOf(systemBars.left, displayCutout.left),
                maxOf(systemBars.top, displayCutout.top),
                maxOf(systemBars.right, displayCutout.right),
                maxOf(systemBars.bottom, displayCutout.bottom)
            )

            // Keep screen content below the status bar and display cutout.
            binding.container.updatePadding(
                left = containerInitialPadding.left + safeInsets.left,
                top = containerInitialPadding.top + safeInsets.top,
                right = containerInitialPadding.right + safeInsets.right,
                bottom = containerInitialPadding.bottom
            )

            // Keep bottom navigation content above the gesture/navigation area
            // while allowing its background to draw behind that area.
            // NOTE: Material BottomAppBar already applies the bottom system-bar inset as
            // padding itself, so we must NOT add safeInsets.bottom here — doing so double-
            // counts the nav-bar height and leaves a large empty band under the bar.
            binding.bottomAppBar.updatePadding(
                left = bottomBarInitialPadding.left + safeInsets.left,
                top = bottomBarInitialPadding.top,
                right = bottomBarInitialPadding.right + safeInsets.right,
//                bottom = bottomBarInitialPadding.bottom
            )

            windowInsets
        }

        // The inner BottomNavigationView (a Material NavigationBarView) also applies the
        // bottom system-bar inset as padding by default. Combined with the BottomAppBar
        // applying it too, this double-counts the nav-bar height and leaves an empty band
        // under the nav icons (more visible on devices with a larger gesture inset).
        // Replace its inset listener with a no-op so only the BottomAppBar handles it.
        ViewCompat.setOnApplyWindowInsetsListener(binding.navView) { _, insets -> insets }

        ViewCompat.requestApplyInsets(binding.root)
    }

    private data class InitialPadding(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int
    ) {
        constructor(view: View) : this(
            left = view.paddingLeft,
            top = view.paddingTop,
            right = view.paddingRight,
            bottom = view.paddingBottom
        )
    }
}