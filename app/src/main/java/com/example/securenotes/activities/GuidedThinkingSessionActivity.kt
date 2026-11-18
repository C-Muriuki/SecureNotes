package com.example.securenotes.activities

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import com.example.securenotes.R
import com.example.securenotes.database.DatabaseHelper
import com.example.securenotes.databinding.ActivityGuidedThinkingSessionBinding
import com.example.securenotes.models.ThinkingFramework
import com.example.securenotes.models.ThinkingSession
import com.example.securenotes.repositories.GuidedThinkingRepository
import com.example.securenotes.repositories.ThinkingTemplateRepository
import com.example.securenotes.utils.SessionManager

class GuidedThinkingSessionActivity : BaseActivity() {
    private lateinit var binding: ActivityGuidedThinkingSessionBinding
    private lateinit var sessionManager: SessionManager
    private lateinit var repository: GuidedThinkingRepository

    private var sessionId: Long = -1
    private var currentSession: ThinkingSession? = null
    private var currentFramework: ThinkingFramework? = null
    private var currentStepIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGuidedThinkingSessionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupEdgeToEdgeInsets(binding.mainThinkingSession)

        sessionManager = SessionManager(applicationContext)
        val helper = DatabaseHelper(applicationContext)
        repository = GuidedThinkingRepository(helper)

        sessionId = intent.getLongExtra(GuidedThinkingActivity.EXTRA_SESSION_ID, -1)
        if (sessionId <= 0 || !sessionManager.isLoggedIn()) {
            finish()
            return
        }

        binding.btnSessionBack.setOnClickListener { saveAndFinish() }
        binding.btnStepPrevious.setOnClickListener { previousStep() }
        binding.btnStepNext.setOnClickListener { nextStep() }
        binding.btnSessionCreateNote.setOnClickListener { createNoteFromSession() }

        loadSession()
    }

    private fun loadSession() {
        val userId = sessionManager.getCurrentUserId()
        runIo({ repository.getSession(userId, sessionId) }) { result ->
            result.onSuccess { session ->
                if (session == null) {
                    Toast.makeText(this, "Session not found.", Toast.LENGTH_SHORT).show()
                    finish()
                    return@onSuccess
                }
                currentSession = session
                val framework = ThinkingTemplateRepository.getFramework(session.frameworkId)
                if (framework == null) {
                    Toast.makeText(this, "Framework definition not found.", Toast.LENGTH_SHORT).show()
                    finish()
                    return@onSuccess
                }
                currentFramework = framework
                binding.tvSessionFrameworkName.text = framework.title
                renderStep(0)
            }.onFailure {
                Toast.makeText(this, "Could not load reflection session.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun renderStep(index: Int) {
        val framework = currentFramework ?: return
        val steps = framework.steps
        if (index !in steps.indices) return

        currentStepIndex = index
        val step = steps[index]
        val totalSteps = steps.size

        binding.tvSessionProgressCounter.text = getString(
            R.string.guided_thinking_step_counter, index + 1, totalSteps
        )
        binding.progressStepIndicator.max = totalSteps * 100
        binding.progressStepIndicator.progress = (index + 1) * 100

        binding.tvStepPromptTitle.text = step.promptTitle
        binding.tvStepPromptDescription.text = step.promptDescription
        binding.inputLayoutStepResponse.hint = step.hintText.ifBlank { getString(R.string.guided_thinking_hint_response) }

        // Find existing response for this step
        val existingResponse = currentSession?.responses?.find { it.stepId == step.id }?.response ?: ""
        binding.etStepResponse.setText(existingResponse)
        binding.etStepResponse.setSelection(existingResponse.length)

        binding.btnStepPrevious.isEnabled = index > 0
        if (index == totalSteps - 1) {
            binding.btnStepNext.text = getString(R.string.guided_thinking_action_complete)
        } else {
            binding.btnStepNext.text = getString(R.string.guided_thinking_action_next)
        }
    }

    private fun saveCurrentResponse(onComplete: () -> Unit = {}) {
        val framework = currentFramework ?: run { onComplete(); return }
        val steps = framework.steps
        if (currentStepIndex !in steps.indices) { onComplete(); return }

        val step = steps[currentStepIndex]
        val responseText = binding.etStepResponse.text?.toString() ?: ""
        if (responseText.isBlank()) {
            onComplete()
            return
        }

        val userId = sessionManager.getCurrentUserId()
        runIo({ repository.saveResponse(userId, sessionId, step.id, responseText) }) { result ->
            result.onSuccess {
                // Update local session responses cache
                currentSession = repository.getSession(userId, sessionId)
                onComplete()
            }.onFailure { onComplete() }
        }
    }

    private fun nextStep() {
        val framework = currentFramework ?: return
        saveCurrentResponse {
            if (currentStepIndex < framework.steps.size - 1) {
                renderStep(currentStepIndex + 1)
            } else {
                completeSession()
            }
        }
    }

    private fun previousStep() {
        saveCurrentResponse {
            if (currentStepIndex > 0) {
                renderStep(currentStepIndex - 1)
            }
        }
    }

    private fun completeSession() {
        val userId = sessionManager.getCurrentUserId()
        runIo({ repository.completeSession(userId, sessionId) }) { result ->
            result.onSuccess {
                Toast.makeText(this, R.string.guided_thinking_session_completed_toast, Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun createNoteFromSession() {
        saveCurrentResponse {
            val userId = sessionManager.getCurrentUserId()
            runIo({
                repository.completeSession(userId, sessionId)
                repository.createNoteFromSession(userId, sessionId)
            }) { result ->
                result.onSuccess { newNoteId ->
                    if (newNoteId > 0) {
                        Toast.makeText(this, R.string.guided_thinking_note_created_toast, Toast.LENGTH_SHORT).show()
                        val intent = Intent(this, NoteDetailActivity::class.java).apply {
                            putExtra(NoteEditorActivity.EXTRA_NOTE_ID, newNoteId)
                        }
                        startActivity(intent)
                        finish()
                    } else {
                        Toast.makeText(this, "Please enter at least one response before saving as note.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun saveAndFinish() {
        saveCurrentResponse { finish() }
    }
}
