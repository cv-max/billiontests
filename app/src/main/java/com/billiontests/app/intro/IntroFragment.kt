package com.billiontests.app.intro

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.billiontests.app.databinding.FragmentIntroBinding


private const val ARG_title = "param1"
private const val ARG_description = "param2"
private const val ARG_last = "param3"
private const val ARG_POSITION = "position"

class IntroFragment : Fragment() {
    private var title: String? = null
    private var description: String? = null
    private var last:String? = ""
    private var position = -1

    private lateinit var binding: FragmentIntroBinding
    private lateinit var nextClickListener: OnNextButtonClickListener
    private lateinit var skipClickListener: OnSkipButtonClickListener

    interface OnNextButtonClickListener{
        fun onNextButtonClick(finish:Boolean)
    }

    interface OnSkipButtonClickListener{
        fun onSkipButtonClicked()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            title = it.getString(ARG_title)
            description = it.getString(ARG_description)
            last = it.getString(ARG_last)
            position = it.getInt(ARG_POSITION)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // Inflate the layout for this fragment
        binding = FragmentIntroBinding.inflate(inflater)

        title?.let {
            binding.introTitleTv.text = title
        }
        description?.let {
            binding.introDesTv.text = description
        }
        last?.let {
            binding.introLastTv.text = last
        }
//        binding.skipButton.isVisible = position<3

        binding.nextButton.setOnClickListener {
            nextClickListener.onNextButtonClick(position>=3)
        }
        binding.skipButton.setOnClickListener {
            skipClickListener.onSkipButtonClicked()
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

    }

    companion object {
        private const val TAG = "IntroFragment"

        @JvmStatic
        fun newInstance(param1: String, param2: String,param3:String, position: Int, nextButtonClickListener: OnNextButtonClickListener, skipButtonClickListener: OnSkipButtonClickListener) =
            IntroFragment().apply {
                nextClickListener = nextButtonClickListener
                skipClickListener = skipButtonClickListener
                arguments = Bundle().apply {
                    putString(ARG_title, param1)
                    putString(ARG_description, param2)
                    putString(ARG_last, param3)
                    putInt(ARG_POSITION, position)
                }
            }
    }
}