package com.billiontests.app.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

// UrineTestResult.kt
@Parcelize
data class UrineTestResult(
    val parameter: String,
    val result: String,
    val normalRange: String,
    val unit: String = ""
):Parcelable {
    override fun toString(): String {
        return "UrineTestResult(parameter='$parameter', result='$result', normalRange='$normalRange')"
    }
}

data class ParamMeta(
    val displayName: String,
    val shortRef: String,
    val meaning: String,
    val whenToAct: String,
    val confounders: String,
    val notes: String? = null
)

object UrineParamDictionary {
    // keys should match the start of your parameter label (e.g., "Leukocyte", "Protein", etc.)
    val meta: Map<String, ParamMeta> = mapOf(
        "Leukocyte" to ParamMeta(
            displayName = "Leukocyte Esterase (LEU)",
            shortRef = "Negative",
            meaning = "Detects white blood cells. Negative generally suggests no UTI/inflammation.",
            whenToAct = "If burning urine, fever, urgency, flank pain, or persistent positive results—consult a clinician.",
            confounders = "Contamination, vaginal discharge, improper collection, storage delay."
        ),
        "Protein" to ParamMeta(
            displayName = "Protein (Albumin) (PRO)",
            shortRef = "Negative (≤15 mg/dL)",
            meaning = "Protein leakage in urine; persistent positives can indicate kidney involvement.",
            whenToAct = "If proteinuria persists or is high—needs clinical correlation and follow-up.",
            confounders = "Strenuous exercise, fever, dehydration, concentrated urine."
        ),
        "Glucose" to ParamMeta(
            displayName = "Glucose (GLU)",
            shortRef = "Negative",
            meaning = "Sugar in urine; positives may correlate with high blood glucose.",
            whenToAct = "If positive repeatedly—check blood glucose/HbA1c and consult clinician.",
            confounders = "Drugs/dyes; very dilute urine may mask low positives."
        ),
        "Specific Gravity" to ParamMeta(
            displayName = "Specific Gravity (SG)",
            shortRef = "1.005–1.030",
            meaning = "Urine concentration; reflects hydration and renal concentrating ability.",
            whenToAct = "Very low or very high values—evaluate hydration, renal function, or osmolality.",
            confounders = "High glucose/protein can artifactually raise SG; temperature; reagent limitations."
        ),
        "Ketone" to ParamMeta(
            displayName = "Ketone (KET)",
            shortRef = "Negative / Trace",
            meaning = "By-products of fat metabolism; elevated in fasting, DKA, low-carb diets.",
            whenToAct = "If moderate/high with symptoms (nausea, vomiting, breathlessness)—urgent evaluation.",
            confounders = "Dehydration, prolonged fasting, some medications."
        ),
        "Nitrite" to ParamMeta(
            displayName = "Nitrite (NIT)",
            shortRef = "Negative",
            meaning = "Detects nitrate-reducing bacteria; positive supports UTI (with symptoms).",
            whenToAct = "Positive with symptoms—seek medical care; negative does not exclude UTI.",
            confounders = "Non-nitrate diet, frequent voiding, non-reducing organisms."
        ),
        "pH" to ParamMeta(
            displayName = "Urine pH",
            shortRef = "5–8",
            meaning = "Acidity/alkalinity; relates to stones, infections, diet.",
            whenToAct = "Very acidic/alkaline persistently—evaluate diet, stones, or infection.",
            confounders = "Stale samples (CO₂ loss), diet extremes."
        ),
        "Blood" to ParamMeta(
            displayName = "Blood (BLO)",
            shortRef = "Negative",
            meaning = "RBCs/hemoglobin; positives can suggest stones, infection, trauma, menses.",
            whenToAct = "If positive and not menstrual—evaluate with clinician, repeat test.",
            confounders = "Menstrual contamination, strenuous exercise."
        ),
        "Erythrocytes" to ParamMeta(
            displayName = "Erythrocytes (ERY)",
            shortRef = "Negative",
            meaning = "Red blood cells in urine; positives indicate possible bleeding in urinary tract.",
            whenToAct = "If positive, consult clinician to check for infection, stones, or trauma.",
            confounders = "Menstrual contamination, vigorous exercise, or recent catheterization."
        ),

        "Bilirubin" to ParamMeta(
            displayName = "Bilirubin (BIL)",
            shortRef = "Negative",
            meaning = "Bile pigment; positives can suggest hepatobiliary disease.",
            whenToAct = "If positive—liver function evaluation recommended.",
            confounders = "Exposure to light degrades bilirubin; drugs/dyes."
        ),
        "Urobilinogen" to ParamMeta(
            displayName = "Urobilinogen (UBG)",
            shortRef = "0.2–1.0 mg/dL",
            meaning = "Heme breakdown product; elevated in hemolysis or liver disease.",
            whenToAct = "High or absent results—correlate with LFTs, clinical picture.",
            confounders = "Sample delay, light exposure."
        ),
        "Color" to ParamMeta(
            displayName = "Urine Color",
            shortRef = "Pale Yellow",
            meaning = "Indicates hydration status and possible presence of pigments or substances.",
            whenToAct = "If urine is dark yellow, red, brown, or cloudy—check hydration and consult clinician if persistent.",
            confounders = "Dehydration, certain foods (beetroot, berries), vitamins, or medications."
        ),

        )

    fun findMetaFor(parameterLabel: String): ParamMeta? {
        // match by prefix
        return meta.entries.firstOrNull { (k, _) ->
            parameterLabel.startsWith(k, ignoreCase = true)
        }?.value
    }
}


