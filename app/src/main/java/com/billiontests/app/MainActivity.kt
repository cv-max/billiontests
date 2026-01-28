package com.billiontests.app

import com.billiontests.app.authentication.AuthenticationActivity
import com.billiontests.app.intro.IntroFragment
import com.billiontests.app.utils.PreferenceUtils
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.button.MaterialButton
import com.google.firebase.appcheck.ktx.appCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.firebase.ktx.Firebase
import com.google.firebase.ktx.initialize

private const val NUM_PAGES = 2

class MainActivity : FragmentActivity() {

    private lateinit var viewPager: ViewPager2

        companion object {
            private val REQUIRED_PERMISSIONS =
                mutableListOf(
                    android.Manifest.permission.CAMERA,
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION
                ).toTypedArray()
        }

    private val activityResultLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        )
        { permissions ->
            // Handle Permission granted/rejected
            var permissionGranted = true
            permissions.entries.forEach {
                if (it.key in REQUIRED_PERMISSIONS && !it.value)
                    permissionGranted = false
            }
            if (!permissionGranted) {
                Toast.makeText(
                    baseContext,
                    "Permission request denied",
                    Toast.LENGTH_SHORT
                ).show()
            }else{
                startActivity(Intent(this, AuthenticationActivity::class.java))
                finish()
            }
        }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.pager)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        Firebase.initialize(context = this)
        Firebase.appCheck.installAppCheckProviderFactory(
            PlayIntegrityAppCheckProviderFactory.getInstance(),
        )
    }

    override fun onResume() {
        super.onResume()


        val sharedPreferences = getSharedPreferences(PreferenceUtils.PREFERENCE_NAME, MODE_PRIVATE)

        val isFirstTimeUser = sharedPreferences.getBoolean(PreferenceUtils.IS_FIRST_TIME_USER,true)
        Log.e("MainActivity","is first time user: $isFirstTimeUser")
        if(isFirstTimeUser.not()){
            startActivity(Intent(this,AuthenticationActivity::class.java))
            finish()
            return
        }

        viewPager = findViewById(R.id.pager)
        val nextButton = findViewById<MaterialButton>(R.id.next_button)
        val skipButton = findViewById<MaterialButton>(R.id.skip_button)

        // The pager adapter, which provides the pages to the view pager widget.
        val pagerAdapter = ScreenSlidePagerAdapter(this)
        viewPager.adapter = pagerAdapter

        nextButton.setOnClickListener {
            if (viewPager.currentItem == 1) {
                if(allPermissionsGranted().not()){
                    activityResultLauncher.launch(REQUIRED_PERMISSIONS)
                }else {
                    sharedPreferences.edit().putBoolean(PreferenceUtils.IS_FIRST_TIME_USER, false).apply()
                    startActivity(Intent(this, AuthenticationActivity::class.java))
                    finish()
                }
            } else {
                viewPager.currentItem += 1
            }
        }

        skipButton.setOnClickListener {
            if(allPermissionsGranted().not()){
                activityResultLauncher.launch(REQUIRED_PERMISSIONS)
            }else {
                sharedPreferences.edit().putBoolean(PreferenceUtils.IS_FIRST_TIME_USER, false).apply()
                startActivity(Intent(this, AuthenticationActivity::class.java))
                finish()
            }
        }
    }

    override fun onBackPressed() {
        if (viewPager.currentItem == 0) {
            // If the user is currently looking at the first step, allow the system to handle
            // the Back button. This calls finish() on this activity and pops the back stack.
            super.onBackPressed()
        } else {
            // Otherwise, select the previous step.
            viewPager.currentItem -= 1
        }
    }

    private inner class ScreenSlidePagerAdapter(fa: FragmentActivity) : FragmentStateAdapter(fa),
        IntroFragment.OnSkipButtonClickListener, IntroFragment.OnNextButtonClickListener {
        override fun getItemCount(): Int = NUM_PAGES

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 ->
                    IntroFragment.newInstance(
                        ContextCompat.getString(
                            this@MainActivity,
                            R.string.recommended_by_doctors
                        ),
                        ContextCompat.getString(this@MainActivity, R.string.intro_content_one),
                        ContextCompat.getString(this@MainActivity, R.string.intro_last_one),
                        position, this, this
                    )

//                1 -> IntroFragment.newInstance(
//                    ContextCompat.getString(
//                        this@MainActivity,
//                        R.string.dummy_title_one
//                    ),
//                    ContextCompat.getString(this@MainActivity, R.string.dummy_description_one),
//                    position, this, this
//                )
//
//                2 ->
//                    IntroFragment.newInstance(
//                        ContextCompat.getString(
//                            this@MainActivity,
//                            R.string.dummy_title_two
//                        ),
//                        ContextCompat.getString(this@MainActivity, R.string.dummy_description_two),
//                        position, this, this
//                    )

                else -> IntroFragment.newInstance(
                    ContextCompat.getString(
                        this@MainActivity,
                        R.string.dummy_title_three
                    ),
                    ContextCompat.getString(this@MainActivity, R.string.intro_content_two),
                    ContextCompat.getString(this@MainActivity, R.string.intro_last_two),
                    position, this, this
                )
            }
        }

        override fun onSkipButtonClicked() {
            TODO("Not yet implemented")
        }

        override fun onNextButtonClick(finish: Boolean) {
            TODO("Not yet implemented")
        }
    }
}