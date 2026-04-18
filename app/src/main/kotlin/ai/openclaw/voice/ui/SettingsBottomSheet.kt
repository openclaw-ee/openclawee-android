package ai.openclaw.voice.ui

import ai.openclaw.voice.R
import ai.openclaw.voice.settings.Settings
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.slider.Slider
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textview.MaterialTextView

/**
 * Settings bottom sheet allowing the user to configure:
 *   - API endpoint URL
 *   - Silence detection threshold (500–3000 ms, step 100 ms)
 *   - STT model selection (base / tiny)
 *   - TTS voice selection
 *
 * Settings are persisted to SharedPreferences when the sheet is stopped/dismissed.
 * The caller is responsible for calling [MainViewModel.applySettings] after dismissal.
 */
class SettingsBottomSheet : BottomSheetDialogFragment() {

    companion object {
        const val TAG = "SettingsBottomSheet"
        const val PREFS_NAME = "chloe_settings"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.bottom_sheet_settings, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = Settings.load(prefs)

        val endpointInput = view.findViewById<TextInputEditText>(R.id.endpointInput)
        val silenceSlider = view.findViewById<Slider>(R.id.silenceThresholdSlider)
        val silenceLabel = view.findViewById<MaterialTextView>(R.id.silenceThresholdLabel)
        val whisperModelDropdown = view.findViewById<AutoCompleteTextView>(R.id.whisperModelDropdown)
        val voiceDropdown = view.findViewById<AutoCompleteTextView>(R.id.voiceDropdown)

        // Populate fields from stored settings
        endpointInput.setText(current.apiEndpoint)
        silenceSlider.value = current.silenceThresholdMs.toFloat().coerceIn(500f, 3000f)
        silenceLabel.text = getString(
            R.string.settings_silence_threshold_label,
            current.silenceThresholdMs
        )

        silenceSlider.addOnChangeListener { _, value, _ ->
            silenceLabel.text = getString(
                R.string.settings_silence_threshold_label,
                value.toLong()
            )
        }

        val whisperAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            Settings.WHISPER_MODELS
        )
        whisperModelDropdown.setAdapter(whisperAdapter)
        whisperModelDropdown.setText(current.whisperModel, false)

        val voiceAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            Settings.TTS_VOICES
        )
        voiceDropdown.setAdapter(voiceAdapter)
        voiceDropdown.setText(current.ttsVoice, false)
    }

    override fun onStop() {
        super.onStop()
        saveSettings()
    }

    private fun saveSettings() {
        val v = view ?: return
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val endpoint = v.findViewById<TextInputEditText>(R.id.endpointInput)
            ?.text?.toString()?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: Settings.DEFAULT_API_ENDPOINT

        val thresholdMs = v.findViewById<Slider>(R.id.silenceThresholdSlider)
            ?.value?.toLong()
            ?: Settings.DEFAULT_SILENCE_THRESHOLD_MS

        val rawModel = v.findViewById<AutoCompleteTextView>(R.id.whisperModelDropdown)
            ?.text?.toString() ?: ""
        val whisperModel = if (rawModel in Settings.WHISPER_MODELS) rawModel else Settings.DEFAULT_WHISPER_MODEL

        val rawVoice = v.findViewById<AutoCompleteTextView>(R.id.voiceDropdown)
            ?.text?.toString() ?: ""
        val voice = if (rawVoice in Settings.TTS_VOICES) rawVoice else Settings.DEFAULT_TTS_VOICE

        Settings.save(
            prefs,
            Settings.AppSettings(
                apiEndpoint = endpoint,
                silenceThresholdMs = thresholdMs,
                ttsVoice = voice,
                whisperModel = whisperModel
            )
        )
    }
}
