package com.example.securenotes.activities

import android.animation.ValueAnimator
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.securenotes.R
import com.example.securenotes.adapters.ConnectionCandidateAdapter
import com.example.securenotes.database.DatabaseHelper
import com.example.securenotes.databinding.ActivityAddThoughtConnectionBinding
import com.example.securenotes.models.CreateConnectionResult
import com.example.securenotes.models.Note
import com.example.securenotes.repositories.NoteRepository
import com.example.securenotes.utils.SessionManager
import java.util.concurrent.Future

class AddThoughtConnectionActivity : BaseActivity() {
    private lateinit var binding: ActivityAddThoughtConnectionBinding
    private lateinit var repository: NoteRepository
    private lateinit var session: SessionManager
    private lateinit var adapter: ConnectionCandidateAdapter
    private var sourceNoteId: Long = -1L
    private var sourceNoteTitle: String = ""
    private var allCandidates: List<Note> = emptyList()
    private var loadTask: Future<*>? = null
    private var connectInProgress = false
    private var requestId = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddThoughtConnectionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupEdgeToEdgeInsets(binding.mainAddThoughtConnection)

        session = SessionManager(applicationContext)
        repository = NoteRepository(DatabaseHelper(applicationContext))
        sourceNoteId = intent.getLongExtra(NoteEditorActivity.EXTRA_NOTE_ID, -1L)

        if (!session.isLoggedIn() || session.getCurrentUserId() <= 0 || sourceNoteId <= 0) {
            Toast.makeText(this, R.string.thought_connections_note_unavailable, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        adapter = ConnectionCandidateAdapter { candidate -> confirmConnection(candidate) }
        binding.rvConnectionCandidates.layoutManager = LinearLayoutManager(this)
        binding.rvConnectionCandidates.adapter = adapter

        binding.btnConnectionPickerBack.setOnClickListener { finish() }
        binding.etConnectionSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                applySearchFilter(s?.toString().orEmpty())
            }
        })

        loadCandidates()
    }

    private fun loadCandidates() {
        val request = ++requestId
        cancelIo(loadTask)
        binding.progressConnectionCandidates.visibility = View.VISIBLE
        binding.tvConnectionCandidatesEmpty.visibility = View.GONE
        binding.rvConnectionCandidates.visibility = View.GONE
        val userId = session.getCurrentUserId()
        loadTask = runIo({
            val source = repository.getNote(userId, sourceNoteId)
            val candidates = if (source == null) emptyList() else repository.getConnectionCandidates(userId, sourceNoteId)
            source to candidates
        }) { result ->
            if (request != requestId) return@runIo
            binding.progressConnectionCandidates.visibility = View.GONE
            result.onSuccess { (source, candidates) ->
                if (source == null) {
                    Toast.makeText(this, R.string.thought_connections_note_unavailable, Toast.LENGTH_SHORT).show()
                    finish()
                    return@onSuccess
                }
                sourceNoteTitle = source.title
                allCandidates = candidates
                applySearchFilter(binding.etConnectionSearch.text?.toString().orEmpty())
                revealCandidates()
            }.onFailure {
                binding.tvConnectionCandidatesEmpty.setText(R.string.thought_connections_load_failed)
                binding.tvConnectionCandidatesEmpty.visibility = View.VISIBLE
            }
        }
    }

    private fun applySearchFilter(query: String) {
        val filtered = if (query.isBlank()) {
            allCandidates
        } else {
            allCandidates.filter {
                it.title.contains(query, ignoreCase = true) ||
                    it.content.contains(query, ignoreCase = true)
            }
        }
        adapter.submitList(filtered)
        val empty = filtered.isEmpty()
        binding.rvConnectionCandidates.visibility = if (empty) View.GONE else View.VISIBLE
        binding.tvConnectionCandidatesEmpty.visibility = if (empty) View.VISIBLE else View.GONE
        binding.tvConnectionCandidatesEmpty.setText(
            if (query.isBlank()) R.string.thought_connections_empty_candidates
            else R.string.thought_connections_no_search_results
        )
    }

    private fun revealCandidates() {
        if (!ValueAnimator.areAnimatorsEnabled()) return
        val target = if (binding.rvConnectionCandidates.visibility == View.VISIBLE) {
            binding.rvConnectionCandidates
        } else {
            binding.tvConnectionCandidatesEmpty
        }
        target.animate().cancel()
        target.alpha = 0.4f
        target.animate().alpha(1f).setDuration(160L).start()
    }

    private fun confirmConnection(candidate: Note) {
        if (connectInProgress) return
        AlertDialog.Builder(this)
            .setTitle(R.string.thought_connections_confirm_title)
            .setMessage(
                getString(
                    R.string.thought_connections_confirm_message,
                    sourceNoteTitle,
                    candidate.title
                )
            )
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_connect) { _, _ -> createConnection(candidate) }
            .show()
    }

    private fun createConnection(candidate: Note) {
        if (connectInProgress) return
        connectInProgress = true
        adapter.setBusy(candidate.id)
        binding.progressConnectionCandidates.visibility = View.VISIBLE
        val userId = session.getCurrentUserId()
        runIo({ repository.connectNotes(userId, sourceNoteId, candidate.id) }) { result ->
            connectInProgress = false
            adapter.setBusy(null)
            binding.progressConnectionCandidates.visibility = View.GONE
            result.onSuccess { outcome ->
                when (outcome) {
                    CreateConnectionResult.CREATED -> {
                        Toast.makeText(this, R.string.thought_connections_created, Toast.LENGTH_SHORT).show()
                        setResult(RESULT_OK)
                        finish()
                        if (ValueAnimator.areAnimatorsEnabled()) {
                            overridePendingTransition(R.anim.fade_in, R.anim.slide_out_left)
                        }
                    }
                    CreateConnectionResult.ALREADY_CONNECTED -> {
                        Toast.makeText(this, R.string.thought_connections_already_connected, Toast.LENGTH_SHORT).show()
                        loadCandidates()
                    }
                    CreateConnectionResult.SAME_NOTE -> {
                        Toast.makeText(this, R.string.thought_connections_create_failed, Toast.LENGTH_SHORT).show()
                    }
                    CreateConnectionResult.NOTE_NOT_AVAILABLE -> {
                        Toast.makeText(this, R.string.thought_connections_note_unavailable, Toast.LENGTH_SHORT).show()
                        loadCandidates()
                    }
                }
            }.onFailure {
                Toast.makeText(this, R.string.thought_connections_create_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
