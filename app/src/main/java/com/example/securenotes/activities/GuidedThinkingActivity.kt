package com.example.securenotes.activities

import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.securenotes.R
import com.example.securenotes.adapters.ThinkingFrameworkAdapter
import com.example.securenotes.adapters.ThinkingSessionAdapter
import com.example.securenotes.database.DatabaseHelper
import com.example.securenotes.databinding.ActivityGuidedThinkingBinding
import com.example.securenotes.models.ThinkingFramework
import com.example.securenotes.models.ThinkingSession
import com.example.securenotes.repositories.GuidedThinkingRepository
import com.example.securenotes.repositories.ThinkingTemplateRepository
import com.example.securenotes.utils.SessionManager
import java.util.concurrent.Future

class GuidedThinkingActivity : BaseActivity() {
    private lateinit var binding: ActivityGuidedThinkingBinding
    private lateinit var session: SessionManager
    private lateinit var repository: GuidedThinkingRepository
    private lateinit var sessionAdapter: ThinkingSessionAdapter

    private var sourceNoteId: Long? = null
    private var loadTask: Future<*>? = null
    private var requestId = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGuidedThinkingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupEdgeToEdgeInsets(binding.mainGuidedThinking)

        session = SessionManager(applicationContext)
        val helper = DatabaseHelper(applicationContext)
        repository = GuidedThinkingRepository(helper)

        if (!session.isLoggedIn() || session.getCurrentUserId() <= 0) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        val noteIdExtra = intent.getLongExtra(NoteEditorActivity.EXTRA_NOTE_ID, -1)
        if (noteIdExtra > 0) {
            sourceNoteId = noteIdExtra
        }

        binding.btnGuidedThinkingBack.setOnClickListener { finish() }

        setupFrameworksList()
        setupSessionsList()
    }

    override fun onResume() {
        super.onResume()
        if (::binding.isInitialized && session.isLoggedIn()) {
            loadSessions()
        }
    }

    private fun setupFrameworksList() {
        val frameworks = ThinkingTemplateRepository.getFrameworks()
        binding.rvFrameworks.layoutManager = LinearLayoutManager(this)
        binding.rvFrameworks.adapter = ThinkingFrameworkAdapter(frameworks) { framework ->
            startSessionWithFramework(framework)
        }
    }

    private fun setupSessionsList() {
        sessionAdapter = ThinkingSessionAdapter { session ->
            openSession(session)
        }
        binding.rvSessions.layoutManager = LinearLayoutManager(this)
        binding.rvSessions.adapter = sessionAdapter
    }

    private fun startSessionWithFramework(framework: ThinkingFramework) {
        val userId = session.getCurrentUserId()
        runIo({ repository.createSession(userId, framework.id, sourceNoteId) }) { result ->
            result.onSuccess { sessionId ->
                if (sessionId > 0) {
                    val intent = Intent(this, GuidedThinkingSessionActivity::class.java).apply {
                        putExtra(EXTRA_SESSION_ID, sessionId)
                    }
                    startActivity(intent)
                    if (ValueAnimator.areAnimatorsEnabled()) {
                        overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out)
                    }
                }
            }
        }
    }

    private fun openSession(sessionItem: ThinkingSession) {
        val intent = Intent(this, GuidedThinkingSessionActivity::class.java).apply {
            putExtra(EXTRA_SESSION_ID, sessionItem.id)
        }
        startActivity(intent)
        if (ValueAnimator.areAnimatorsEnabled()) {
            overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out)
        }
    }

    private fun loadSessions() {
        val currentRequest = ++requestId
        cancelIo(loadTask)
        binding.progressGuidedThinking.visibility = View.VISIBLE
        binding.tvSessionsEmpty.visibility = View.GONE
        binding.rvSessions.visibility = View.GONE

        val userId = session.getCurrentUserId()
        loadTask = runIo({ repository.getSessionHistory(userId) }) { result ->
            if (currentRequest != requestId) return@runIo
            binding.progressGuidedThinking.visibility = View.GONE
            result.onSuccess { sessions ->
                sessionAdapter.submitList(sessions)
                val empty = sessions.isEmpty()
                binding.tvSessionsEmpty.visibility = if (empty) View.VISIBLE else View.GONE
                binding.rvSessions.visibility = if (empty) View.GONE else View.VISIBLE
            }.onFailure {
                sessionAdapter.submitList(emptyList())
                binding.tvSessionsEmpty.visibility = View.VISIBLE
                binding.rvSessions.visibility = View.GONE
            }
        }
    }

    companion object {
        const val EXTRA_SESSION_ID = "extra_session_id"
    }
}
