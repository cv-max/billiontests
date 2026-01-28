package com.billiontests.app.scan

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import com.billiontests.app.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class LumaBottomSheetDialogFragment(
    private val previousLower: String? = null,
    private val previousUpper: String? = null,
    private val onSave: (lower: String, upper: String) -> Unit
) : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_luma_bottom_sheet_dialog, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val etLower = view.findViewById<EditText>(R.id.etLower)
        val etUpper = view.findViewById<EditText>(R.id.etUpper)
        val btnSave = view.findViewById<Button>(R.id.btnSave)

        // Set previously saved values, if available
        previousLower?.let { etLower.setText(it) }
        previousUpper?.let { etUpper.setText(it) }

        btnSave.setOnClickListener {
            val lower = etLower.text.toString().trim()
            val upper = etUpper.text.toString().trim()
            onSave(lower, upper)
            dismiss()
        }
    }
}
