package ai.openclaw.voice.pipeline

import ai.openclaw.voice.audio.AudioRecorder
import ai.openclaw.voice.stt.WhisperTranscriber
import ai.openclaw.voice.tts.KokoroTTS
import android.content.Context
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

class VoicePipelineTest {

    private lateinit var context: Context
    private lateinit var audioRecorder: AudioRecorder
    private lateinit var whisper: WhisperTranscriber
    private lateinit var kokoro: KokoroTTS
    private lateinit var listener: VoicePipeline.Listener
    private lateinit var pipeline: VoicePipeline
    private lateinit var tempCacheDir: File

    @Before
    fun setUp() {
        tempCacheDir = createTempDir("test_cache")
        context = mockk(relaxed = true)
        audioRecorder = mockk(relaxed = true)
        whisper = mockk(relaxed = true)
        kokoro = mockk(relaxed = true)
        listener = mockk(relaxed = true)

        every { context.cacheDir } returns tempCacheDir

        pipeline = VoicePipeline(context, audioRecorder, whisper, kokoro)
        pipeline.listener = listener
    }

    @After
    fun tearDown() {
        tempCacheDir.deleteRecursively()
    }

    // --- LLM stub: echo logic ---

    @Test
    fun `givenNormalTranscription_whenPipelineProcesses_thenResponseEchoesInput`() = runTest {
        every { whisper.transcribe(any()) } returns "hello world"

        pipeline.stopRecordingAndProcess()

        verify { listener.onTranscription("hello world") }
        verify { listener.onResponse("You said: hello world") }
    }

    @Test
    fun `givenBlankTranscription_whenPipelineProcesses_thenRespondsWithDidNotCatchThat`() = runTest {
        every { whisper.transcribe(any()) } returns ""

        pipeline.stopRecordingAndProcess()

        verify { listener.onTranscription("") }
        verify { listener.onResponse("I didn't catch that. Could you say it again?") }
    }

    @Test
    fun `givenWhitespaceOnlyTranscription_whenPipelineProcesses_thenRespondsWithDidNotCatchThat`() = runTest {
        every { whisper.transcribe(any()) } returns "   "

        pipeline.stopRecordingAndProcess()

        verify { listener.onResponse("I didn't catch that. Could you say it again?") }
    }

    @Test
    fun `givenMultiWordTranscription_whenPipelineProcesses_thenResponsePreservesFullText`() = runTest {
        val input = "the quick brown fox jumps over the lazy dog"
        every { whisper.transcribe(any()) } returns input

        pipeline.stopRecordingAndProcess()

        verify { listener.onResponse("You said: $input") }
    }

    // --- Pipeline ordering ---

    @Test
    fun `givenSuccessfulPipeline_whenProcessed_thenCallsAreInOrder`() = runTest {
        every { whisper.transcribe(any()) } returns "test"
        val callOrder = mutableListOf<String>()
        every { listener.onTranscription(any()) } answers { callOrder.add("transcription") }
        every { listener.onResponse(any()) } answers { callOrder.add("response") }
        every { kokoro.speak(any(), any()) } answers { callOrder.add("speak") }

        pipeline.stopRecordingAndProcess()

        assertEquals(listOf("transcription", "response", "speak"), callOrder)
    }

    @Test
    fun `givenPipeline_whenProcessed_thenAudioRecorderIsStoppedFirst`() = runTest {
        every { whisper.transcribe(any()) } returns "test"
        val callOrder = mutableListOf<String>()
        every { audioRecorder.stop() } answers { callOrder.add("stop") }
        every { whisper.transcribe(any()) } answers {
            callOrder.add("transcribe")
            "test"
        }

        pipeline.stopRecordingAndProcess()

        assertTrue("stop() must be called before transcribe()", callOrder.indexOf("stop") < callOrder.indexOf("transcribe"))
    }

    // --- Error propagation ---

    @Test
    fun `givenTranscriberThrows_whenPipelineProcesses_thenOnErrorIsCalled`() = runTest {
        every { whisper.transcribe(any()) } throws RuntimeException("STT failed")

        pipeline.stopRecordingAndProcess()

        verify { listener.onError(match { it.contains("STT failed") }) }
        verify(exactly = 0) { listener.onResponse(any()) }
        verify(exactly = 0) { kokoro.speak(any(), any()) }
    }

    @Test
    fun `givenTtsThrows_whenPipelineProcesses_thenOnErrorIsCalled`() = runTest {
        every { whisper.transcribe(any()) } returns "hello"
        every { kokoro.speak(any(), any()) } throws RuntimeException("TTS failed")

        pipeline.stopRecordingAndProcess()

        verify { listener.onError(match { it.contains("TTS failed") }) }
    }

    // --- Recording control ---

    @Test
    fun `givenPipelineStarted_whenStartRecordingCalled_thenAudioRecorderIsStarted`() {
        pipeline.startRecording()
        // startRecording delegates to a Thread; give it a moment
        Thread.sleep(50)
        verify { audioRecorder.startRecording(any(), any()) }
    }

    @Test
    fun `givenPipelineStarted_whenStopPlaybackCalled_thenKokoroStopPlaybackIsCalled`() {
        pipeline.stopPlayback()
        verify { kokoro.stopPlayback() }
    }

    // --- State machine (IDLE → LISTENING → PROCESSING → SPEAKING → IDLE) ---

    @Test
    fun `givenIdleState_whenStartRecordingCalled_thenAmplitudeCallbackForwarded`() {
        val amplitudeSlot = slot<(Float) -> Unit>()
        every { audioRecorder.startRecording(any(), capture(amplitudeSlot)) } answers {
            amplitudeSlot.captured(0.42f)
        }

        pipeline.startRecording()
        Thread.sleep(100) // let the recording thread run

        verify { listener.onAmplitude(0.42f) }
    }

    @Test
    fun `givenRecordingError_whenStartRecordingFails_thenOnErrorIsCalled`() {
        every { audioRecorder.startRecording(any(), any()) } throws RuntimeException("mic denied")

        pipeline.startRecording()
        Thread.sleep(100) // let the recording thread run

        verify { listener.onError(match { it.contains("mic denied") }) }
    }

    // --- Listener null safety ---

    @Test
    fun `givenNullListener_whenPipelineProcesses_thenDoesNotThrow`() = runTest {
        pipeline.listener = null
        every { whisper.transcribe(any()) } returns "test"

        // Should complete without NullPointerException
        pipeline.stopRecordingAndProcess()
    }
}
