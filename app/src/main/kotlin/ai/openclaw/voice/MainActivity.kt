package ai.openclaw.voice

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import ai.openclaw.voice.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    private val requestMicPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.onMicButtonClicked()
        } else {
            Toast.makeText(this, "Microphone permission is required", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        observeViewModel()
    }

    private fun setupUI() {
        binding.micButton.setOnClickListener {
            if (hasMicPermission()) {
                viewModel.onMicButtonClicked()
            } else {
                requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.appState.collect { state ->
                updateUI(state)
            }
        }

        lifecycleScope.launch {
            viewModel.transcription.collect { text ->
                binding.transcriptionText.text = text
                binding.transcriptionText.visibility = if (text.isNotEmpty()) View.VISIBLE else View.GONE
            }
        }

        lifecycleScope.launch {
            viewModel.response.collect { text ->
                binding.responseText.text = text
                binding.responseText.visibility = if (text.isNotEmpty()) View.VISIBLE else View.GONE
            }
        }

        lifecycleScope.launch {
            viewModel.amplitude.collect { amp ->
                binding.waveformView.updateAmplitude(amp)
            }
        }

        lifecycleScope.launch {
            viewModel.errorMessage.collect { msg ->
                if (msg != null) {
                    Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
                    viewModel.clearError()
                }
            }
        }
    }

    private fun updateUI(state: MainViewModel.AppState) {
        // Reset animated state
        binding.micButton.clearAnimation()
        binding.waveformView.visibility = View.GONE
        binding.waveformView.setRecording(false)

        when (state) {
            MainViewModel.AppState.MODELS_MISSING -> {
                binding.statusText.text = getString(R.string.status_models_missing)
                binding.micButton.isEnabled = false
                binding.micButton.setImageResource(R.drawable.ic_mic_off)
                binding.modelsWarningCard.visibility = View.VISIBLE
            }

            MainViewModel.AppState.IDLE -> {
                binding.statusText.text = getString(R.string.status_idle)
                binding.micButton.isEnabled = true
                binding.micButton.setImageResource(R.drawable.ic_mic)
                binding.modelsWarningCard.visibility = View.GONE
            }

            MainViewModel.AppState.LISTENING -> {
                binding.statusText.text = getString(R.string.status_listening)
                binding.micButton.isEnabled = true
                binding.micButton.setImageResource(R.drawable.ic_mic_active)
                binding.waveformView.visibility = View.VISIBLE
                binding.waveformView.setRecording(true)
                binding.micButton.startAnimation(
                    android.view.animation.AnimationUtils.loadAnimation(this, R.anim.pulse)
                )
            }

            MainViewModel.AppState.PROCESSING -> {
                binding.statusText.text = getString(R.string.status_processing)
                binding.micButton.isEnabled = false
                binding.micButton.setImageResource(R.drawable.ic_mic)
            }

            MainViewModel.AppState.SPEAKING -> {
                binding.statusText.text = getString(R.string.status_speaking)
                binding.micButton.isEnabled = true
                binding.micButton.setImageResource(R.drawable.ic_stop)
            }
        }
    }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
}
