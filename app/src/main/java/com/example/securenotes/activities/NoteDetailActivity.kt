package com.example.securenotes.activities

import android.animation.ValueAnimator
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.securenotes.R
import com.example.securenotes.adapters.ConnectedThoughtsAdapter
import com.example.securenotes.adapters.IdeaStageHistoryAdapter
import com.example.securenotes.database.DatabaseHelper
import com.example.securenotes.databinding.ActivityNoteDetailBinding
import com.example.securenotes.models.ConnectedThought
import com.example.securenotes.models.IdeaStage
import com.example.securenotes.models.Note
import com.example.securenotes.repositories.NoteRepository
import com.example.securenotes.utils.SessionManager
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Future

class NoteDetailActivity : BaseActivity() {
    private lateinit var binding: ActivityNoteDetailBinding
    private lateinit var repository: NoteRepository
    private lateinit var session: SessionManager
    private lateinit var relatedAdapter: ConnectedThoughtsAdapter
    private lateinit var historyAdapter: IdeaStageHistoryAdapter
    private var noteId: Long = -1
    private var loadedNote: Note? = null
    private var loadTask: Future<*>? = null
    private var relatedTask: Future<*>? = null
    private var historyTask: Future<*>? = null
    private var requestId = 0
    private var relatedRequestId = 0
    private var historyRequestId = 0
    private var pinUpdateInProgress = false
    private var stageUpdateInProgress = false
    private val removeUpdatesInFlight = mutableSetOf<Long>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNoteDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupEdgeToEdgeInsets(binding.mainNoteDetail)
        repository = NoteRepository(DatabaseHelper(applicationContext))
        session = SessionManager(applicationContext)
        noteId = intent.getLongExtra(NoteEditorActivity.EXTRA_NOTE_ID, -1)

        relatedAdapter = ConnectedThoughtsAdapter(
            onOpen = { openConnectedThought(it) },
            onRemove = { thought, control -> confirmRemoveConnection(thought, control) }
        )
        binding.rvRelatedThoughts.layoutManager = LinearLayoutManager(this)
        binding.rvRelatedThoughts.adapter = relatedAdapter

        historyAdapter = IdeaStageHistoryAdapter()
        binding.rvIdeaHistory.layoutManager = LinearLayoutManager(this)
        binding.rvIdeaHistory.adapter = historyAdapter

        binding.btnDetailBack.setOnClickListener { finish() }
        binding.btnDetailPin.isEnabled = false
        binding.btnDetailPin.setOnClickListener { togglePin() }
        binding.btnDetailEdit.setOnClickListener {
            startActivity(
                Intent(this, NoteEditorActivity::class.java)
                    .putExtra(NoteEditorActivity.EXTRA_NOTE_ID, noteId)
            )
        }
        binding.btnDetailShare.isEnabled = false
        binding.btnDetailShare.setOnClickListener { shareSelectedNote() }
        binding.btnDetailDelete.setOnClickListener {
            AlertDialog.Builder(this).setMessage("Delete this note?")
                .setNegativeButton(getString(R.string.action_cancel), null)
                .setPositiveButton(getString(R.string.action_delete)) { _, _ -> deleteNote() }
                .show()
        }
        binding.btnChangeIdeaStage.setOnClickListener { showStagePickerDialog() }
        installPressFeedback(binding.btnAddThoughtConnection)
        binding.btnAddThoughtConnection.setOnClickListener { openConnectionPicker() }
        binding.tvRelatedThoughtsEmpty.setOnClickListener { openConnectionPicker() }
    }

    override fun onResume() {
        super.onResume()
        if (::binding.isInitialized && noteId > 0) {
            loadNote()
            loadRelatedThoughts()
            loadIdeaJourneyHistory()
        }
    }

    private fun loadNote() {
        binding.btnDetailShare.isEnabled = false
        binding.btnDetailPin.isEnabled = false
        loadedNote = null
        val request = ++requestId
        cancelIo(loadTask)
        binding.tvDetailNoteTitle.setText(R.string.state_loading_note)
        val userId = session.getCurrentUserId()
        loadTask = runIo({ repository.getNote(userId, noteId) }) { result ->
            if (request != requestId) return@runIo
            result.onSuccess { note ->
                if (note == null) {
                    Toast.makeText(this, "This note is no longer available.", Toast.LENGTH_LONG).show()
                    finish()
                    return@onSuccess
                }
                loadedNote = note
                binding.tvDetailNoteTitle.text = note.title
                binding.tvDetailNoteContent.text = note.content
                binding.tvDetailTimestamps.text = DateFormat.getDateTimeInstance().format(Date(note.updatedAt))
                binding.btnDetailShare.isEnabled = true
                renderPinControl(note.isPinned)
                renderIdeaJourney(note)
            }.onFailure {
                Toast.makeText(this, "This note could not be decrypted.", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun renderIdeaJourney(note: Note) {
        val stageName = getStageName(note.ideaStage)
        binding.tvIdeaJourneyStageBadge.text = getString(R.string.idea_journey_current_stage, stageName)
        updateStageIndicatorUI(note.ideaStage)
    }

    private fun updateStageIndicatorUI(activeStage: IdeaStage) {
        val activeColor = ContextCompat.getColor(this, R.color.colorHighlight)
        val inactiveColor = ContextCompat.getColor(this, R.color.colorNavSelected)
        val activeTextColor = ContextCompat.getColor(this, R.color.colorOnPrimary)
        val inactiveTextColor = ContextCompat.getColor(this, R.color.colorTextPrimary)

        val indicators: List<Pair<IdeaStage, TextView>> = listOf(
            IdeaStage.CAPTURED to binding.stageIndicatorCaptured,
            IdeaStage.EXPLORING to binding.stageIndicatorExploring,
            IdeaStage.DEVELOPING to binding.stageIndicatorDeveloping,
            IdeaStage.COMPLETED to binding.stageIndicatorCompleted
        )

        indicators.forEach { (stage, view) ->
            val isActive = stage == activeStage
            view.setBackgroundColor(if (isActive) activeColor else inactiveColor)
            view.setTextColor(if (isActive) activeTextColor else inactiveTextColor)
        }
    }

    private fun showStagePickerDialog() {
        val note = loadedNote ?: return
        val stages = IdeaStage.entries.toTypedArray()
        val stageNames = stages.map { getStageName(it) }.toTypedArray()
        val currentSelection = stages.indexOf(note.ideaStage)

        AlertDialog.Builder(this)
            .setTitle(R.string.idea_journey_title)
            .setSingleChoiceItems(stageNames, currentSelection) { dialog, which ->
                dialog.dismiss()
                val selectedStage = stages[which]
                if (selectedStage != note.ideaStage) {
                    changeIdeaStage(selectedStage)
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun changeIdeaStage(newStage: IdeaStage) {
        val note = loadedNote ?: return
        val ownerId = session.getCurrentUserId()
        if (ownerId <= 0 || stageUpdateInProgress) return
        stageUpdateInProgress = true
        binding.btnChangeIdeaStage.isEnabled = false
        runIo({ repository.updateIdeaStage(ownerId, note.id, newStage) }) { result ->
            stageUpdateInProgress = false
            binding.btnChangeIdeaStage.isEnabled = true
            result.onSuccess { updated ->
                if (updated) {
                    val updatedNote = note.copy(ideaStage = newStage, updatedAt = System.currentTimeMillis())
                    loadedNote = updatedNote
                    renderIdeaJourney(updatedNote)
                    loadIdeaJourneyHistory()
                    Toast.makeText(
                        this,
                        getString(R.string.idea_journey_stage_updated, getStageName(newStage)),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(this, R.string.idea_journey_update_failed, Toast.LENGTH_SHORT).show()
                }
            }.onFailure {
                Toast.makeText(this, R.string.idea_journey_update_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadIdeaJourneyHistory() {
        val request = ++historyRequestId
        cancelIo(historyTask)
        binding.progressIdeaHistory.visibility = View.VISIBLE
        binding.tvIdeaHistoryEmpty.visibility = View.GONE
        val userId = session.getCurrentUserId()
        historyTask = runIo({ repository.getIdeaStageHistory(userId, noteId) }) { result ->
            if (request != historyRequestId) return@runIo
            binding.progressIdeaHistory.visibility = View.GONE
            result.onSuccess { historyList ->
                historyAdapter.submitList(historyList)
                val empty = historyList.isEmpty()
                binding.tvIdeaHistoryEmpty.visibility = if (empty) View.VISIBLE else View.GONE
                binding.rvIdeaHistory.visibility = if (empty) View.GONE else View.VISIBLE
            }.onFailure {
                historyAdapter.submitList(emptyList())
                binding.tvIdeaHistoryEmpty.visibility = View.VISIBLE
                binding.rvIdeaHistory.visibility = View.GONE
            }
        }
    }

    private fun getStageName(stage: IdeaStage): String {
        return when (stage) {
            IdeaStage.CAPTURED -> getString(R.string.idea_stage_captured)
            IdeaStage.EXPLORING -> getString(R.string.idea_stage_exploring)
            IdeaStage.DEVELOPING -> getString(R.string.idea_stage_developing)
            IdeaStage.COMPLETED -> getString(R.string.idea_stage_completed)
        }
    }

    private fun loadRelatedThoughts() {
        val request = ++relatedRequestId
        cancelIo(relatedTask)
        binding.progressRelatedThoughts.visibility = View.VISIBLE
        binding.tvRelatedThoughtsEmpty.visibility = View.GONE
        val userId = session.getCurrentUserId()
        relatedTask = runIo({ repository.getConnectedNotes(userId, noteId) }) { result ->
            if (request != relatedRequestId) return@runIo
            binding.progressRelatedThoughts.visibility = View.GONE
            result.onSuccess { thoughts ->
                relatedAdapter.submitList(thoughts)
                val empty = thoughts.isEmpty()
                binding.tvRelatedThoughtsEmpty.visibility = if (empty) View.VISIBLE else View.GONE
                binding.rvRelatedThoughts.visibility = if (empty) View.GONE else View.VISIBLE
                revealRelatedThoughts()
            }.onFailure {
                relatedAdapter.submitList(emptyList())
                binding.tvRelatedThoughtsEmpty.visibility = View.VISIBLE
                binding.rvRelatedThoughts.visibility = View.GONE
                Toast.makeText(this, R.string.thought_connections_load_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun revealRelatedThoughts() {
        if (!ValueAnimator.areAnimatorsEnabled()) return
        val target = if (binding.rvRelatedThoughts.visibility == View.VISIBLE) {
            binding.rvRelatedThoughts
        } else {
            binding.tvRelatedThoughtsEmpty
        }
        target.animate().cancel()
        target.alpha = 0.35f
        target.animate().alpha(1f).setDuration(180L).start()
    }

    private fun openConnectionPicker() {
        if (noteId <= 0 || session.getCurrentUserId() <= 0) return
        startActivity(
            Intent(this, AddThoughtConnectionActivity::class.java)
                .putExtra(NoteEditorActivity.EXTRA_NOTE_ID, noteId)
        )
        if (ValueAnimator.areAnimatorsEnabled()) {
            overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out)
        }
    }

    private fun openConnectedThought(thought: ConnectedThought) {
        startActivity(
            Intent(this, NoteDetailActivity::class.java)
                .putExtra(NoteEditorActivity.EXTRA_NOTE_ID, thought.note.id)
        )
        if (ValueAnimator.areAnimatorsEnabled()) {
            overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out)
        }
    }

    private fun confirmRemoveConnection(thought: ConnectedThought, control: View) {
        AlertDialog.Builder(this)
            .setTitle(R.string.thought_connections_remove_title)
            .setMessage(R.string.thought_connections_remove_message)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_remove) { _, _ ->
                removeConnection(thought, control)
            }
            .show()
    }

    private fun removeConnection(thought: ConnectedThought, control: View) {
        val ownerId = session.getCurrentUserId()
        if (ownerId <= 0 || !removeUpdatesInFlight.add(thought.note.id)) return
        control.isEnabled = false
        runIo({ repository.removeConnection(ownerId, noteId, thought.note.id) }) { result ->
            removeUpdatesInFlight.remove(thought.note.id)
            control.isEnabled = true
            result.onSuccess { removed ->
                if (removed) {
                    animateRemoval(thought.note.id)
                    Toast.makeText(this, R.string.thought_connections_removed, Toast.LENGTH_SHORT).show()
                    loadRelatedThoughts()
                } else {
                    Toast.makeText(this, R.string.thought_connections_remove_failed, Toast.LENGTH_SHORT).show()
                }
            }.onFailure {
                Toast.makeText(this, R.string.thought_connections_remove_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun animateRemoval(connectedNoteId: Long) {
        if (!ValueAnimator.areAnimatorsEnabled()) return
        val holder = binding.rvRelatedThoughts.findViewHolderForAdapterPosition(
            relatedAdapter.currentList.indexOfFirst { it.note.id == connectedNoteId }
        ) ?: return
        holder.itemView.animate().cancel()
        holder.itemView.animate().alpha(0.2f).setDuration(120L).start()
    }

    private fun renderPinControl(isPinned: Boolean) {
        binding.btnDetailPin.contentDescription = getString(
            if (isPinned) R.string.action_unpin_note else R.string.action_pin_note
        )
        binding.btnDetailPin.imageTintList = ContextCompat.getColorStateList(
            this,
            if (isPinned) R.color.colorHighlight else R.color.colorIconMuted
        )
        binding.btnDetailPin.alpha = if (isPinned) 1f else 0.72f
        binding.btnDetailPin.isSelected = isPinned
        binding.btnDetailPin.isEnabled = !pinUpdateInProgress
    }

    private fun togglePin() {
        val note = loadedNote ?: return
        val ownerId = session.getCurrentUserId()
        if (note.userId != ownerId || pinUpdateInProgress) return
        pinUpdateInProgress = true
        binding.btnDetailPin.isEnabled = false
        val nextPinnedState = !note.isPinned
        runIo({ repository.setPinned(ownerId, noteId, nextPinnedState) }) { result ->
            pinUpdateInProgress = false
            result.onSuccess { updated ->
                if (updated) {
                    loadedNote = note.copy(isPinned = nextPinnedState, updatedAt = System.currentTimeMillis())
                    renderPinControl(nextPinnedState)
                    animatePinFeedback()
                    Toast.makeText(
                        this,
                        if (nextPinnedState) R.string.pin_added_feedback else R.string.pin_removed_feedback,
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    renderPinControl(note.isPinned)
                    Toast.makeText(this, R.string.pin_update_failed, Toast.LENGTH_SHORT).show()
                }
            }.onFailure {
                renderPinControl(note.isPinned)
                Toast.makeText(this, R.string.pin_update_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun animatePinFeedback() {
        if (!ValueAnimator.areAnimatorsEnabled()) return
        binding.btnDetailPin.animate().cancel()
        binding.btnDetailPin.scaleX = 1f
        binding.btnDetailPin.scaleY = 1f
        binding.btnDetailPin.animate().scaleX(1.18f).scaleY(1.18f).setDuration(90L).withEndAction {
            binding.btnDetailPin.animate().scaleX(1f).scaleY(1f).setDuration(120L).start()
        }.start()
    }

    private fun shareSelectedNote() {
        val note = loadedNote ?: return
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, note.title)
            putExtra(Intent.EXTRA_TEXT, "${note.title}\n\n${note.content}")
        }
        try {
            startActivity(Intent.createChooser(sendIntent, getString(R.string.action_share)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "No app is available to share this note.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteNote() {
        binding.btnDetailDelete.isEnabled = false
        runIo({ repository.deleteNote(session.getCurrentUserId(), noteId) }) { result ->
            binding.btnDetailDelete.isEnabled = true
            result.onSuccess { deleted ->
                if (deleted) finish()
                else Toast.makeText(this, "Could not delete note.", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(this, "Could not delete note.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
