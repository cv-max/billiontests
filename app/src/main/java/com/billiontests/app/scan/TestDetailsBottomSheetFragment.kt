package com.billiontests.app.scan

import android.content.Context
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import com.billiontests.app.R
import com.billiontests.app.databinding.FragmentTestDetailsBottomSheetBinding
import com.billiontests.app.model.UrineParamDictionary
import com.billiontests.app.model.UrineTestResult
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment


class TestDetailsBottomSheetFragment : BottomSheetDialogFragment() {


    private lateinit var binding: FragmentTestDetailsBottomSheetBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        binding = FragmentTestDetailsBottomSheetBinding.inflate(LayoutInflater.from(requireContext()), container, false)
        return binding.root
    }

    companion object {
        private const val ARG_RESULT = "arg_result"

        fun newInstance(result: UrineTestResult): TestDetailsBottomSheetFragment =
            TestDetailsBottomSheetFragment().apply {
                arguments = Bundle().apply { putParcelable(ARG_RESULT, result) }
            }
    }

    override fun onStart() {
        super.onStart()
        // Expand to content, optionally full height
        (dialog as? BottomSheetDialog)?.behavior?.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
            isDraggable = true
        }
        // Make sure the sheet can go full height if content is tall
        view?.let { v ->
            v.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
            v.requestLayout()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val result = requireArguments().getParcelable<UrineTestResult>(ARG_RESULT)!!

        val tvName = binding.tvName

        val tvResultBig = binding.tvResultBig
        val tvRefRange = binding.tvRefRange
        val tvMeaning = binding.tvMeaning
        val tvWhenToAct = binding.tvWhenToAct
        val tvConfounders = binding.tvConfounders

        tvName.text = result.parameter
        tvResultBig.text = result.result
        tvRefRange.text = "Ref. range: ${result.normalRange}"

        // Meta text (simple mapping by prefix)
        val meta = UrineParamDictionary.findMetaFor(result.parameter)
        tvMeaning.text = meta?.meaning ?: "—"
        tvWhenToAct.text = meta?.whenToAct ?: "—"
        tvConfounders.text = meta?.confounders ?: "—"

        // Flag
        val flag = computeFlag(result.result, result.normalRange, result.parameter)

        val color = when (flag) {
            Flag.NORMAL -> requireContext().getColor(R.color.green)
            Flag.HIGH, Flag.ABNORMAL -> requireContext().getColor(R.color.red)
            Flag.LOW -> requireContext().getColor( R.color.orange)
            else -> requireContext().getColor(R.color.grey)
        }
        tvResultBig.setTextColor(color)
    }

    enum class Flag { NORMAL, HIGH, LOW, ABNORMAL }

    private fun computeFlag(result: String, refRange: String, parameterLabel: String): Flag? {
        val res = result.trim().lowercase()
        val ref = refRange.trim().lowercase()

        // qualitative
        if (ref.contains("negative")) {
            return when {
                res.startsWith("neg") -> Flag.NORMAL
                res.startsWith("trace") -> Flag.ABNORMAL
                res.contains("pos") || res.contains("moderate") || res.contains("high") -> Flag.ABNORMAL
                else -> null
            }
        }
        // quantitative examples
        if (parameterLabel.startsWith("Specific Gravity", true)) {
            val v = res.toDoubleOrNull() ?: return null
            return when {
                v < 1.005 -> Flag.LOW
                v > 1.030 -> Flag.HIGH
                else -> Flag.NORMAL
            }
        }
        if (parameterLabel.equals("pH", true) || parameterLabel.startsWith("Urine pH", true)) {
            val v = res.toDoubleOrNull() ?: return null
            return when {
                v < 5.0 -> Flag.LOW
                v > 8.0 -> Flag.HIGH
                else -> Flag.NORMAL
            }
        }
        return null
    }

    private fun Context.m3Color(resId: Int): Int =
        androidx.core.content.ContextCompat.getColor(this, resId)
}