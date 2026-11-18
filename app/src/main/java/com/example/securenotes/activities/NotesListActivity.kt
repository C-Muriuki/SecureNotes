package com.example.securenotes.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.securenotes.R
import com.example.securenotes.adapters.NotesAdapter
import com.example.securenotes.database.DatabaseHelper
import com.example.securenotes.databinding.ActivityNotesListBinding
import com.example.securenotes.models.IdeaStage
import com.example.securenotes.models.Note
import com.example.securenotes.repositories.NoteRepository
import com.example.securenotes.utils.SessionManager
import com.google.android.material.snackbar.Snackbar
import java.util.concurrent.Future

class NotesListActivity : BaseActivity() {
    private lateinit var binding: ActivityNotesListBinding
    private lateinit var adapter: NotesAdapter
    private lateinit var repository: NoteRepository
    private lateinit var session: SessionManager
    private var filterPinned: Boolean? = null
    private var filterStage: IdeaStage? = null
    private var recentFilter = false
    private var loadTask: Future<*>? = null
    private var requestId = 0
    private val pinUpdatesInFlight = mutableSetOf<Long>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotesListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupEdgeToEdgeInsets(binding.mainNotesList)
        session = SessionManager(applicationContext)
        repository = NoteRepository(DatabaseHelper(applicationContext))

        val stageVal = intent.getIntExtra(DashboardActivity.EXTRA_STAGE_FILTER, -1)
        if (stageVal >= 0) {
            filterStage = IdeaStage.fromInt(stageVal)
        }

        setupUI()
        setupSwipeDelete()
    }

    override fun onResume() {
        super.onResume()
        if (::adapter.isInitialized) refresh()
    }

    private fun setupUI() {
        binding.rvAllNotes.layoutManager = LinearLayoutManager(this)
        adapter = NotesAdapter(
            onItemClick = { note -> openNote(note) },
            onPinToggle = { note, control -> togglePin(note, control) }
        )
        binding.rvAllNotes.adapter = adapter
        binding.btnSearchNotes.setOnClickListener { navigateToTab(SearchActivity::class.java) }
        installPressFeedback(binding.fabNotesListCreate)
        binding.fabNotesListCreate.setOnClickListener { startActivity(Intent(this, NoteEditorActivity::class.java)) }
        binding.chipAll.setOnClickListener { filterPinned = null; filterStage = null; recentFilter = false; refresh() }
        binding.chipPinned.setOnClickListener { filterPinned = true; filterStage = null; recentFilter = false; refresh() }
        binding.chipRecent.setOnClickListener { filterPinned = null; filterStage = null; recentFilter = true; refresh() }
        binding.bottomNavNotes.selectedItemId = R.id.nav_notes
        binding.bottomNavNotes.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_notes -> true
                R.id.nav_account -> { navigateToTab(AccountActivity::class.java); true }
                else -> true
            }
        }
    }

    private fun refresh() {
        val currentRequest = ++requestId
        cancelIo(loadTask)
        val emptyState = findViewById<View>(R.id.layoutEmptyStateNotes)
        binding.rvAllNotes.isEnabled = false
        emptyState.visibility = if (adapter.currentList.isEmpty()) View.VISIBLE else View.GONE
        if (adapter.currentList.isEmpty()) {
            findViewById<android.widget.TextView>(R.id.tvEmptyTitle).setText(R.string.state_loading_notes)
            findViewById<android.widget.TextView>(R.id.tvEmptyMessage).text = ""
        }
        val userId = session.getCurrentUserId()
        val pinnedFilter = filterPinned
        val stageFilter = filterStage
        val onlyRecent = recentFilter
        loadTask = runIo({
            if (onlyRecent) repository.getRecentNotes(userId, 10)
            else repository.getNotes(userId, pinned = pinnedFilter, stage = stageFilter)
        }) { result ->
            if (currentRequest != requestId) return@runIo
            binding.rvAllNotes.isEnabled = true
            result.onSuccess { notes ->
                adapter.submitList(notes) {
                    emptyState.visibility = if (notes.isEmpty()) View.VISIBLE else View.GONE
                    if (notes.isEmpty()) {
                        findViewById<android.widget.TextView>(R.id.tvEmptyTitle).setText(R.string.state_empty_notes_title)
                        findViewById<android.widget.TextView>(R.id.tvEmptyMessage).setText(R.string.state_empty_notes)
                    }
                }
            }.onFailure {
                if (adapter.currentList.isEmpty()) {
                    findViewById<android.widget.TextView>(R.id.tvEmptyTitle).setText(R.string.error_generic)
                    findViewById<android.widget.TextView>(R.id.tvEmptyMessage).text = ""
                    emptyState.visibility = View.VISIBLE
                }
                Toast.makeText(this, "Could not load notes. Please sign in again.", Toast.LENGTH_LONG).show()
            }
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
                    if (android.animation.ValueAnimator.areAnimatorsEnabled()) {
                        control.animate().cancel()
                        control.scaleX = 1f
                        control.scaleY = 1f
                        control.animate().scaleX(1.16f).scaleY(1.16f).setDuration(90L).withEndAction {
                            control.animate().scaleX(1f).scaleY(1f).setDuration(120L).start()
                        }.start()
                    }
                    Toast.makeText(
                        this,
                        if (nextPinnedState) R.string.pin_added_feedback else R.string.pin_removed_feedback,
                        Toast.LENGTH_SHORT
                    ).show()
                    refresh()
                } else {
                    Toast.makeText(this, R.string.pin_update_failed, Toast.LENGTH_SHORT).show()
                }
            }.onFailure {
                Toast.makeText(this, R.string.pin_update_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openNote(note: Note) {
        startActivity(Intent(this, NoteDetailActivity::class.java).putExtra(NoteEditorActivity.EXTRA_NOTE_ID, note.id))
    }

    private fun setupSwipeDelete() {
        ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) = false

            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {
                val position = vh.bindingAdapterPosition
                if (position == RecyclerView.NO_POSITION) {
                    refresh()
                    return
                }
                val note = adapter.noteAt(position)
                binding.rvAllNotes.isEnabled = false
                runIo({ repository.deleteNote(session.getCurrentUserId(), note.id) }) { result ->
                    binding.rvAllNotes.isEnabled = true
                    result.onSuccess { deleted ->
                        if (deleted) {
                            Snackbar.make(binding.root, "Note deleted", Snackbar.LENGTH_LONG)
                                .setAction("Undo") { restoreNote(note) }
                                .show()
                        } else {
                            Toast.makeText(this@NotesListActivity, "Could not delete note.", Toast.LENGTH_SHORT).show()
                        }
                        refresh()
                    }.onFailure {
                        Toast.makeText(this@NotesListActivity, "Could not delete note.", Toast.LENGTH_SHORT).show()
                        refresh()
                    }
                }
            }
        }).attachToRecyclerView(binding.rvAllNotes)
    }

    private fun restoreNote(note: Note) {
        runIo({ repository.restoreNote(note) }) { result ->
            result.onSuccess { restored ->
                if (!restored) Toast.makeText(this, "Could not restore note.", Toast.LENGTH_SHORT).show()
                refresh()
            }.onFailure {
                Toast.makeText(this, "Could not restore note.", Toast.LENGTH_SHORT).show()
                refresh()
            }
        }
    }
}
