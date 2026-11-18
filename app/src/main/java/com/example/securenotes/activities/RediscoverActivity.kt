package com.example.securenotes.activities

import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.securenotes.R
import com.example.securenotes.adapters.RediscoveryAdapter
import com.example.securenotes.database.DatabaseHelper
import com.example.securenotes.databinding.ActivityRediscoverBinding
import com.example.securenotes.models.RediscoveryCandidate
import com.example.securenotes.repositories.RediscoveryRepository
import com.example.securenotes.utils.SessionManager
import java.util.concurrent.Future

class RediscoverActivity : BaseActivity() {
    private lateinit var binding: ActivityRediscoverBinding
    private lateinit var session: SessionManager
    private lateinit var repository: RediscoveryRepository
    private lateinit var adapter: RediscoveryAdapter

    private var activeCategory = "TODAY"
    private var loadTask: Future<*>? = null
    private var requestId = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRediscoverBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupEdgeToEdgeInsets(binding.mainRediscover)

        session = SessionManager(applicationContext)
        val helper = DatabaseHelper(applicationContext)
        repository = RediscoveryRepository(helper)

        if (!session.isLoggedIn() || session.getCurrentUserId() <= 0) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        binding.btnRediscoverBack.setOnClickListener { finish() }

        adapter = RediscoveryAdapter(
            onOpenItem = { openCandidate(it) },
            onRevisitItem = { candidate, control -> revisitCandidate(candidate, control) },
            onSnoozeItem = { candidate, _ -> showSnoozeDialog(candidate) },
            onDismissItem = { candidate, control -> dismissCandidate(candidate, control) },
            onRestoreItem = { candidate, control -> restoreCandidate(candidate, control) }
        )

        binding.rvRediscoverCandidates.layoutManager = LinearLayoutManager(this)
        binding.rvRediscoverCandidates.adapter = adapter

        setupCategoryChips()
    }

    override fun onResume() {
        super.onResume()
        if (::binding.isInitialized && session.isLoggedIn()) {
            loadCandidates()
        }
    }

    private fun setupCategoryChips() {
        binding.chipCategoryToday.setOnClickListener {
            activeCategory = "TODAY"
            loadCandidates()
        }
        binding.chipCategoryForgotten.setOnClickListener {
            activeCategory = "FORGOTTEN"
            loadCandidates()
        }
        binding.chipCategoryUnfinished.setOnClickListener {
            activeCategory = "UNFINISHED"
            loadCandidates()
        }
        binding.chipCategoryConnected.setOnClickListener {
            activeCategory = "CONNECTED"
            loadCandidates()
        }
        binding.chipCategoryQueue.setOnClickListener {
            activeCategory = "QUEUE"
            loadCandidates()
        }
    }

    private fun loadCandidates() {
        val currentRequest = ++requestId
        cancelIo(loadTask)
        binding.progressRediscover.visibility = View.VISIBLE
        binding.layoutEmptyRediscover.visibility = View.GONE
        binding.rvRediscoverCandidates.visibility = View.GONE

        val userId = session.getCurrentUserId()
        val category = activeCategory

        loadTask = runIo({
            repository.getCandidatesByCategory(userId, category)
        }) { result ->
            if (currentRequest != requestId) return@runIo
            binding.progressRediscover.visibility = View.GONE
            result.onSuccess { candidates ->
                adapter.submitList(candidates)
                val empty = candidates.isEmpty()
                binding.layoutEmptyRediscover.visibility = if (empty) View.VISIBLE else View.GONE
                binding.rvRediscoverCandidates.visibility = if (empty) View.GONE else View.VISIBLE
            }.onFailure {
                adapter.submitList(emptyList())
                binding.layoutEmptyRediscover.visibility = View.VISIBLE
                binding.rvRediscoverCandidates.visibility = View.GONE
            }
        }
    }

    private fun openCandidate(candidate: RediscoveryCandidate) {
        val intent = Intent(this, NoteDetailActivity::class.java).apply {
            putExtra(NoteEditorActivity.EXTRA_NOTE_ID, candidate.note.id)
        }
        startActivity(intent)
        if (ValueAnimator.areAnimatorsEnabled()) {
            overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out)
        }
    }

    private fun revisitCandidate(candidate: RediscoveryCandidate, control: View) {
        control.isEnabled = false
        val userId = session.getCurrentUserId()
        runIo({ repository.markAsRevisited(userId, candidate.note.id) }) { result ->
            control.isEnabled = true
            result.onSuccess { success ->
                if (success) {
                    Toast.makeText(this, R.string.rediscover_revisited_toast, Toast.LENGTH_SHORT).show()
                    loadCandidates()
                } else {
                    Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show()
                }
            }.onFailure {
                Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showSnoozeDialog(candidate: RediscoveryCandidate) {
        val options = arrayOf(
            getString(R.string.rediscover_snooze_1_day),
            getString(R.string.rediscover_snooze_7_days),
            getString(R.string.rediscover_snooze_30_days)
        )
        val days = arrayOf(1, 7, 30)

        AlertDialog.Builder(this)
            .setTitle(R.string.rediscover_snooze_title)
            .setItems(options) { dialog, which ->
                dialog.dismiss()
                snoozeCandidate(candidate, days[which])
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun snoozeCandidate(candidate: RediscoveryCandidate, snoozeDays: Int) {
        val userId = session.getCurrentUserId()
        runIo({ repository.snoozeRediscovery(userId, candidate.note.id, snoozeDays) }) { result ->
            result.onSuccess { success ->
                if (success) {
                    Toast.makeText(
                        this,
                        getString(R.string.rediscover_snoozed_toast, snoozeDays),
                        Toast.LENGTH_SHORT
                    ).show()
                    loadCandidates()
                } else {
                    Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show()
                }
            }.onFailure {
                Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun dismissCandidate(candidate: RediscoveryCandidate, control: View) {
        control.isEnabled = false
        val userId = session.getCurrentUserId()
        runIo({ repository.dismissRediscovery(userId, candidate.note.id) }) { result ->
            control.isEnabled = true
            result.onSuccess { success ->
                if (success) {
                    Toast.makeText(this, R.string.rediscover_dismissed_toast, Toast.LENGTH_SHORT).show()
                    loadCandidates()
                } else {
                    Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show()
                }
            }.onFailure {
                Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun restoreCandidate(candidate: RediscoveryCandidate, control: View) {
        control.isEnabled = false
        val userId = session.getCurrentUserId()
        runIo({ repository.restoreRediscovery(userId, candidate.note.id) }) { result ->
            control.isEnabled = true
            result.onSuccess { success ->
                if (success) {
                    Toast.makeText(this, R.string.rediscover_restored_toast, Toast.LENGTH_SHORT).show()
                    loadCandidates()
                } else {
                    Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show()
                }
            }.onFailure {
                Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
