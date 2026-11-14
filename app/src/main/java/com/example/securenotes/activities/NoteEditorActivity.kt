package com.example.securenotes.activities

import android.os.Bundle
import android.widget.Toast
import com.example.securenotes.R
import com.example.securenotes.database.DatabaseHelper
import com.example.securenotes.databinding.ActivityNoteEditorBinding
import com.example.securenotes.repositories.NoteRepository
import com.example.securenotes.utils.SessionManager

class NoteEditorActivity : BaseActivity() {
    private lateinit var binding: ActivityNoteEditorBinding
    private lateinit var repository: NoteRepository
    private lateinit var session: SessionManager
    private var noteId: Long = -1L
    private var pinned = false
    private var recordLoading = false
    private var saveInProgress = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNoteEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupEdgeToEdgeInsets(binding.mainNoteEditor)
        session = SessionManager(applicationContext)
        repository = NoteRepository(DatabaseHelper(applicationContext))
        noteId = intent.getLongExtra(EXTRA_NOTE_ID, -1L)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnPinNote.setOnClickListener {
            if (!recordLoading && !saveInProgress) {
                pinned = !pinned
                binding.btnPinNote.alpha = if (pinned) 1f else .45f
            }
        }
        binding.btnSaveNote.setOnClickListener { saveNote() }
        binding.etNoteContent.addTextChangedListener(SimpleTextWatcher { text ->
            val words = text.trim().split(Regex("\\s+")).count { it.isNotBlank() }
            binding.tvWordCount.text = "$words words | ${text.length} characters"
        })

        if (noteId > 0) loadNote()
    }

    private fun loadNote() {
        recordLoading = true
        binding.btnSaveNote.isEnabled = false
        binding.btnPinNote.isEnabled = false
        findViewById<android.widget.TextView>(R.id.tvSaveStatus).setText(R.string.state_loading_note)
        val userId = session.getCurrentUserId()
        runIo({ repository.getNote(userId, noteId) }) { outcome ->
            recordLoading = false
            outcome.onSuccess { note ->
                if (note == null) {
                    Toast.makeText(this, "This note is no longer available.", Toast.LENGTH_LONG).show()
                    finish()
                    return@onSuccess
                }
                binding.etNoteTitle.setText(note.title)
                binding.etNoteContent.setText(note.content)
                pinned = note.isPinned
                binding.tvEditorTitle.setText(R.string.title_edit_note)
                binding.btnPinNote.alpha = if (pinned) 1f else .45f
                binding.btnSaveNote.isEnabled = true
                binding.btnPinNote.isEnabled = true
                findViewById<android.widget.TextView>(R.id.tvSaveStatus).setText(R.string.info_draft_saved)
            }.onFailure {
                Toast.makeText(this, "This note is unavailable because its encryption key could not be used.", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun saveNote() {
        if (recordLoading || saveInProgress) return
        val title = binding.etNoteTitle.text?.toString()?.trim().orEmpty()
        val content = binding.etNoteContent.text?.toString().orEmpty()
        if (title.isBlank() && content.isBlank()) {
            binding.etNoteTitle.error = "Enter a title or note content"
            return
        }

        saveInProgress = true
        binding.btnSaveNote.isEnabled = false
        findViewById<android.widget.TextView>(R.id.tvSaveStatus).setText(R.string.state_saving_note)
        val userId = session.getCurrentUserId()
        runIo({
            if (noteId > 0) repository.updateNote(userId, noteId, title, content, pinned)
            else repository.createNote(userId, title, content, pinned) > 0
        }) { outcome ->
            saveInProgress = false
            binding.btnSaveNote.isEnabled = true
            outcome.onSuccess { saved ->
                if (saved) {
                    findViewById<android.widget.TextView>(R.id.tvSaveStatus).setText(R.string.state_note_saved)
                    Toast.makeText(this, "Note saved securely", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    findViewById<android.widget.TextView>(R.id.tvSaveStatus).setText(R.string.error_generic)
                    Toast.makeText(this, getString(R.string.error_generic), Toast.LENGTH_SHORT).show()
                }
            }.onFailure {
                findViewById<android.widget.TextView>(R.id.tvSaveStatus).setText(R.string.error_generic)
                Toast.makeText(this, "Could not save this note securely.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private class SimpleTextWatcher(private val changed: (String) -> Unit) : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            changed(s?.toString().orEmpty())
        }
        override fun afterTextChanged(s: android.text.Editable?) = Unit
    }

    companion object { const val EXTRA_NOTE_ID = "NOTE_ID" }
}
