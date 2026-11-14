package com.example.securenotes.activities

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.securenotes.R
import com.example.securenotes.adapters.NotesAdapter
import com.example.securenotes.database.DatabaseHelper
import com.example.securenotes.databinding.ActivitySearchBinding
import com.example.securenotes.models.Note
import com.example.securenotes.repositories.NoteRepository
import com.example.securenotes.utils.SessionManager
import java.util.concurrent.Future

class SearchActivity : BaseActivity() {
    private lateinit var binding: ActivitySearchBinding
    private lateinit var adapter: NotesAdapter
    private lateinit var repository: NoteRepository
    private lateinit var session: SessionManager
    private val searchHandler = Handler(Looper.getMainLooper())
    private var notesCache: List<Note>? = null
    private var latestQuery = ""
    private var loadTask: Future<*>? = null
    private var filterTask: Future<*>? = null
    private var loadRequestId = 0
    private var filterRequestId = 0
    private var hasStartedLoad = false
    private var refreshOnResume = false
    private var screenActive = false
    private val pinUpdatesInFlight = mutableSetOf<Long>()
    private val debouncedSearch = Runnable { filterCachedNotes() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupEdgeToEdgeInsets(binding.mainSearch)
        session = SessionManager(applicationContext)
        repository = NoteRepository(DatabaseHelper(applicationContext))
        adapter = NotesAdapter(
            onItemClick = { openNote(it) },
            onPinToggle = { note, control -> togglePin(note, control) }
        )
        binding.rvSearchResults.layoutManager = LinearLayoutManager(this)
        binding.rvSearchResults.adapter = adapter
        binding.btnSearchBack.setOnClickListener { finish() }
        binding.btnClearSearch.setOnClickListener { binding.etSearchQuery.text?.clear() }
        binding.etSearchQuery.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                latestQuery = s?.toString().orEmpty()
                binding.btnClearSearch.visibility = if (latestQuery.isEmpty()) View.GONE else View.VISIBLE
                scheduleSearch()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        binding.bottomNavSearch.selectedItemId = R.id.nav_search
        binding.bottomNavSearch.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> { navigateToTab(DashboardActivity::class.java); true }
                R.id.nav_notes -> { navigateToTab(NotesListActivity::class.java); true }
                R.id.nav_settings -> { navigateToTab(SettingsActivity::class.java); true }
                else -> true
            }
        }
    }

    override fun onResume() {
        super.onResume()
        screenActive = true
        if (binding.rvSearchResults.adapter == null) binding.rvSearchResults.adapter = adapter
        if (!hasStartedLoad || refreshOnResume) {
            refreshOnResume = false
            loadNotesForSearch()
        }
    }

    override fun onPause() {
        screenActive = false
        refreshOnResume = true
        loadRequestId++
        filterRequestId++
        searchHandler.removeCallbacks(debouncedSearch)
        cancelIo(loadTask)
        cancelIo(filterTask)
        notesCache = null
        // Remove decrypted note text from the paused screen's adapter and recycled views.
        adapter.submitList(null)
        binding.rvSearchResults.adapter = null
        super.onPause()
    }

    override fun onDestroy() {
        searchHandler.removeCallbacks(debouncedSearch)
        super.onDestroy()
    }

    private fun loadNotesForSearch() {
        hasStartedLoad = true
        val request = ++loadRequestId
        cancelIo(loadTask)
        cancelIo(filterTask)
        notesCache = null
        showSearchState(getString(R.string.state_search_loading), "")
        val userId = session.getCurrentUserId()
        loadTask = runIo({ repository.getNotes(userId) }) { result ->
            if (!screenActive || request != loadRequestId) return@runIo
            result.onSuccess { notes ->
                notesCache = notes
                scheduleSearch(delayMillis = 0L)
            }.onFailure {
                showSearchState(getString(R.string.error_generic), getString(R.string.state_no_search_results))
                Toast.makeText(this, "Search could not load your notes.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun scheduleSearch(delayMillis: Long = SEARCH_DEBOUNCE_MS) {
        searchHandler.removeCallbacks(debouncedSearch)
        searchHandler.postDelayed(debouncedSearch, delayMillis)
    }

    private fun filterCachedNotes() {
        if (!screenActive) return
        val source = notesCache ?: return
        val query = latestQuery.trim()
        val request = ++filterRequestId
        cancelIo(filterTask)
        filterTask = runIo({
            source.filterIndexed { index, note ->
                if (index % 64 == 0 && Thread.currentThread().isInterrupted) {
                    throw java.util.concurrent.CancellationException("Search was superseded.")
                }
                query.isEmpty() || note.title.contains(query, ignoreCase = true) ||
                    note.content.contains(query, ignoreCase = true)
            }
        }) { result ->
            if (!screenActive || request != filterRequestId) return@runIo
            result.onSuccess { matches ->
                adapter.submitList(matches)
                val empty = matches.isEmpty()
                findViewById<View>(R.id.layoutEmptySearch).visibility = if (empty) View.VISIBLE else View.GONE
                if (empty) {
                    showSearchState(
                        getString(R.string.state_no_search_results_title),
                        getString(R.string.state_no_search_results)
                    )
                }
            }.onFailure {
                Toast.makeText(this, "Search could not be completed.", Toast.LENGTH_SHORT).show()
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
                    val changedAt = System.currentTimeMillis()
                    notesCache = notesCache.orEmpty()
                        .map { if (it.id == note.id) it.copy(isPinned = nextPinnedState, updatedAt = changedAt) else it }
                        .sortedWith(compareByDescending<Note> { it.isPinned }.thenByDescending { it.updatedAt })
                    scheduleSearch(delayMillis = 0L)
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
                } else {
                    Toast.makeText(this, R.string.pin_update_failed, Toast.LENGTH_SHORT).show()
                }
            }.onFailure {
                Toast.makeText(this, R.string.pin_update_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showSearchState(title: String, message: String) {
        findViewById<View>(R.id.layoutEmptySearch).visibility = View.VISIBLE
        findViewById<TextView>(R.id.tvEmptyTitle).text = title
        findViewById<TextView>(R.id.tvEmptyMessage).text = message
    }

    private fun openNote(note: Note) =
        startActivity(Intent(this, NoteDetailActivity::class.java).putExtra(NoteEditorActivity.EXTRA_NOTE_ID, note.id))

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 300L
    }
}
