package com.example.securenotes.activities

import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.securenotes.R
import com.example.securenotes.adapters.ThoughtConnectionAdapter
import com.example.securenotes.database.DatabaseHelper
import com.example.securenotes.databinding.ActivityThoughtConnectionsBinding
import com.example.securenotes.models.Note
import com.example.securenotes.repositories.NoteRepository
import com.example.securenotes.utils.SessionManager
import java.util.concurrent.Future

class ThoughtConnectionsActivity : BaseActivity() {
    private lateinit var binding: ActivityThoughtConnectionsBinding
    private lateinit var repository: NoteRepository
    private lateinit var session: SessionManager
    private lateinit var adapter: ThoughtConnectionAdapter
    private var loadTask: Future<*>? = null
    private var requestId = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityThoughtConnectionsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupEdgeToEdgeInsets(binding.mainThoughtConnections)

        session = SessionManager(applicationContext)
        repository = NoteRepository(DatabaseHelper(applicationContext))

        if (!session.isLoggedIn() || session.getCurrentUserId() <= 0) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        adapter = ThoughtConnectionAdapter(
            onOpenFirst = { openNote(it.first) },
            onOpenSecond = { openNote(it.second) }
        )
        binding.rvThoughtConnections.layoutManager = LinearLayoutManager(this)
        binding.rvThoughtConnections.adapter = adapter
        binding.btnThoughtConnectionsBack.setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        if (::binding.isInitialized && session.isLoggedIn()) loadConnections()
    }

    private fun loadConnections() {
        val request = ++requestId
        cancelIo(loadTask)
        binding.progressThoughtConnections.visibility = View.VISIBLE
        binding.tvThoughtConnectionsEmpty.visibility = View.GONE
        val userId = session.getCurrentUserId()
        loadTask = runIo({ repository.getRecentConnections(userId, limit = null) }) { result ->
            if (request != requestId) return@runIo
            binding.progressThoughtConnections.visibility = View.GONE
            result.onSuccess { connections ->
                adapter.submitList(connections)
                val empty = connections.isEmpty()
                binding.tvThoughtConnectionsEmpty.visibility = if (empty) View.VISIBLE else View.GONE
                binding.rvThoughtConnections.visibility = if (empty) View.GONE else View.VISIBLE
                revealList()
            }.onFailure {
                adapter.submitList(emptyList())
                binding.tvThoughtConnectionsEmpty.visibility = View.VISIBLE
                binding.rvThoughtConnections.visibility = View.GONE
                Toast.makeText(this, R.string.thought_connections_load_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun revealList() {
        if (!ValueAnimator.areAnimatorsEnabled()) return
        val target = if (binding.rvThoughtConnections.visibility == View.VISIBLE) {
            binding.rvThoughtConnections
        } else {
            binding.tvThoughtConnectionsEmpty
        }
        target.animate().cancel()
        target.alpha = 0.4f
        target.animate().alpha(1f).setDuration(180L).start()
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
}
