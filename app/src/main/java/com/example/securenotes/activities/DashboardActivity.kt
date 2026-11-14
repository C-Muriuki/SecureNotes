package com.example.securenotes.activities

import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.securenotes.R
import com.example.securenotes.adapters.NotesAdapter
import com.example.securenotes.adapters.PinnedNotesAdapter
import com.example.securenotes.adapters.ThoughtConnectionAdapter
import com.example.securenotes.database.DatabaseHelper
import com.example.securenotes.databinding.ActivityDashboardBinding
import com.example.securenotes.models.IdeaStage
import com.example.securenotes.models.Note
import com.example.securenotes.models.ThoughtConnection
import com.example.securenotes.networking.WritingPromptRepository
import com.example.securenotes.repositories.NoteRepository
import com.example.securenotes.utils.SessionManager
import java.util.concurrent.Future

class DashboardActivity : BaseActivity() {
    private lateinit var binding: ActivityDashboardBinding
    private lateinit var session: SessionManager
    private lateinit var repository: NoteRepository
    private lateinit var connectionsAdapter: ThoughtConnectionAdapter
    private var notesTask: Future<*>? = null
    private var requestId = 0
    private val pinUpdatesInFlight = mutableSetOf<Long>()

    private data class DashboardNotes(
        val recent: List<Note>,
        val pinned: List<Note>,
        val totalCount: Int,
        val connections: List<ThoughtConnection>,
        val stageCounts: Map<IdeaStage, Int>
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupEdgeToEdgeInsets(binding.mainDashboard)
        session = SessionManager(applicationContext)
        repository = NoteRepository(DatabaseHelper(applicationContext))
        if (!session.isLoggedIn() || session.getCurrentUserId() <= 0) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }
        binding.tvUserGreeting.text = getString(R.string.dashboard_greeting, session.getCurrentUserFullName() ?: "User")
        binding.rvPinnedNotes.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.rvRecentNotes.layoutManager = LinearLayoutManager(this)
        connectionsAdapter = ThoughtConnectionAdapter(
            onOpenFirst = { openNote(it.first) },
            onOpenSecond = { openNote(it.second) }
        )
        binding.rvDashboardConnections.layoutManager = LinearLayoutManager(this)
        binding.rvDashboardConnections.adapter = connectionsAdapter
        installPressFeedback(binding.btnExploreConnections)
        val openConnectionsHub = {
            startActivity(Intent(this, ThoughtConnectionsActivity::class.java))
            if (ValueAnimator.areAnimatorsEnabled()) {
                overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out)
            }
        }
        binding.btnExploreConnections.setOnClickListener { openConnectionsHub() }
        binding.tvDashboardConnectionsEmpty.setOnClickListener { openConnectionsHub() }

        binding.cardStageCaptured.setOnClickListener { openStageNotes(IdeaStage.CAPTURED) }
        binding.cardStageExploring.setOnClickListener { openStageNotes(IdeaStage.EXPLORING) }
        binding.cardStageDeveloping.setOnClickListener { openStageNotes(IdeaStage.DEVELOPING) }
        binding.cardStageCompleted.setOnClickListener { openStageNotes(IdeaStage.COMPLETED) }

        setupNavigation()
        loadWritingPrompt()
    }

    override fun onResume() {
        super.onResume()
        if (::binding.isInitialized && ::session.isInitialized && session.isLoggedIn()) refreshNotes()
    }

    private fun openStageNotes(stage: IdeaStage) {
        val intent = Intent(this, NotesListActivity::class.java).apply {
            putExtra(EXTRA_STAGE_FILTER, stage.value)
        }
        startActivity(intent)
    }

    private fun loadWritingPrompt() {
        binding.tvDailyPrompt.text = getString(R.string.dashboard_prompt_loading)
        binding.tvDailyPromptStatus.text = getString(R.string.dashboard_prompt_loading)
        WritingPromptRepository.load(applicationContext) { prompt ->
            if (isFinishing || isDestroyed) return@load
            binding.tvDailyPrompt.text = if (prompt.author.isNullOrBlank()) prompt.text else prompt.text + "\n— " + prompt.author
            binding.tvDailyPromptStatus.setText(
                if (prompt.isOffline) R.string.dashboard_prompt_offline else R.string.dashboard_prompt_online
            )
        }
    }

    private fun refreshNotes() {
        val currentRequest = ++requestId
        cancelIo(notesTask)
        val userId = session.getCurrentUserId()
        binding.tvTotalNotesCount.text = getString(R.string.state_loading_notes)
        notesTask = runIo({
            DashboardNotes(
                recent = repository.getRecentNotes(userId, 5),
                pinned = repository.getNotes(userId, pinned = true),
                totalCount = repository.countNotes(userId),
                connections = repository.getRecentConnections(userId, limit = 3),
                stageCounts = repository.getIdeaStageCounts(userId)
            )
        }) { result ->
            if (currentRequest != requestId) return@runIo
            result.onSuccess { snapshot ->
                binding.tvTotalNotesCount.text = snapshot.totalCount.toString()
                binding.tvCountCaptured.text = (snapshot.stageCounts[IdeaStage.CAPTURED] ?: 0).toString()
                binding.tvCountExploring.text = (snapshot.stageCounts[IdeaStage.EXPLORING] ?: 0).toString()
                binding.tvCountDeveloping.text = (snapshot.stageCounts[IdeaStage.DEVELOPING] ?: 0).toString()
                binding.tvCountCompleted.text = (snapshot.stageCounts[IdeaStage.COMPLETED] ?: 0).toString()

                binding.rvPinnedNotes.adapter = PinnedNotesAdapter(snapshot.pinned) { openNote(it) }
                val recent = NotesAdapter(
                    onItemClick = { openNote(it) },
                    onPinToggle = { note, control -> togglePin(note, control) }
                )
                recent.submitList(snapshot.recent)
                binding.rvRecentNotes.adapter = recent
                renderConnections(snapshot.connections)
            }.onFailure {
                binding.tvTotalNotesCount.text = "—"
                Toast.makeText(this, "Could not load your notes. Your account key may be unavailable.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun renderConnections(connections: List<ThoughtConnection>) {
        connectionsAdapter.submitList(connections)
        val empty = connections.isEmpty()
        binding.tvDashboardConnectionsEmpty.visibility = if (empty) View.VISIBLE else View.GONE
        binding.rvDashboardConnections.visibility = if (empty) View.GONE else View.VISIBLE
        if (ValueAnimator.areAnimatorsEnabled()) {
            val target = if (empty) binding.tvDashboardConnectionsEmpty else binding.rvDashboardConnections
            target.animate().cancel()
            target.alpha = 0.45f
            target.animate().alpha(1f).setDuration(160L).start()
        }
    }

    private fun togglePin(note: Note, control: View) {
        val ownerId = session.getCurrentUserId()
        if (note.userId != ownerId || !pinUpdatesInFlight.add(note.id)) return
        control.isEnabled = false
        val nextPinnedState = !note.isPinned
        runIo({ repository.setPinned(ownerId, note.id, nextPinnedState) }) { result ->
            pinUpdatesInFlight.remove(note.id)
            control.isEnabled = true
            result.onSuccess { updated ->
                if (updated) {
                    animatePinFeedback(control)
                    Toast.makeText(
                        this,
                        if (nextPinnedState) R.string.pin_added_feedback else R.string.pin_removed_feedback,
                        Toast.LENGTH_SHORT
                    ).show()
                    refreshNotes()
                } else {
                    Toast.makeText(this, R.string.pin_update_failed, Toast.LENGTH_SHORT).show()
                }
            }.onFailure {
                Toast.makeText(this, R.string.pin_update_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun animatePinFeedback(control: View) {
        if (!ValueAnimator.areAnimatorsEnabled()) return
        control.animate().cancel()
        control.scaleX = 1f
        control.scaleY = 1f
        control.animate().scaleX(1.16f).scaleY(1.16f).setDuration(90L).withEndAction {
            control.animate().scaleX(1f).scaleY(1f).setDuration(120L).start()
        }.start()
    }

    private fun openNote(note: Note) {
        startActivity(
            Intent(this, NoteDetailActivity::class.java)
                .putExtra(NoteEditorActivity.EXTRA_NOTE_ID, note.id)
        )
        if (ValueAnimator.areAnimatorsEnabled()) {
            overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out)
        }
    }

    private fun setupNavigation() {
        binding.btnProfile.setOnClickListener { startActivity(Intent(this, AccountActivity::class.java)) }
        binding.cardSearchEntry.setOnClickListener { navigateToTab(SearchActivity::class.java) }
        installPressFeedback(binding.fabCreateNote)
        binding.fabCreateNote.setOnClickListener { startActivity(Intent(this, NoteEditorActivity::class.java)) }
        binding.bottomNav.selectedItemId = R.id.nav_home
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_notes -> { navigateToTab(NotesListActivity::class.java); true }
                R.id.nav_search -> { navigateToTab(SearchActivity::class.java); true }
                R.id.nav_settings -> { navigateToTab(SettingsActivity::class.java); true }
                else -> true
            }
        }
    }

    companion object {
        const val EXTRA_STAGE_FILTER = "extra_stage_filter"
    }
}
