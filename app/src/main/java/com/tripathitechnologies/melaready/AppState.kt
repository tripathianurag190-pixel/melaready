package com.tripathitechnologies.melaready

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.tripathitechnologies.melaready.data.LocalStore
import com.tripathitechnologies.melaready.domain.*

class AppState(application: Application) : AndroidViewModel(application) {
    private val store = LocalStore(application)
    var workspace by mutableStateOf(Workspace()); private set
    var storageError by mutableStateOf(false); private set
    private var loadFailed = false
    init { try { workspace = store.load() } catch (_: Exception) { storageError = true; loadFailed = true } }
    private fun commit(next: Workspace): Boolean {
        if (loadFailed) return false
        return try { store.save(next); workspace = next; storageError = false; true }
        catch (_: Exception) { storageError = true; false }
    }
    fun language() = commit(workspace.copy(hindi = !workspace.hindi))
    fun completeGateway() = commit(workspace.copy(gatewayCompleted = true))
    fun setServerUrl(url: String) = commit(workspace.copy(serverUrl = url.trim()))
    fun sample(): Boolean = if (workspace.event == null) commit(Workflow.sample(workspace.hindi)) else false
    fun createEvent(name: String, venue: String, count: String): Boolean {
        if (workspace.event != null || !Workflow.validEvent(name, venue, count)) return false
        return commit(workspace.copy(event = Event(name.trim(), venue.trim(), count.toInt())))
    }
    fun member(name: String, role: String, zone: String): Boolean {
        if (workspace.event == null || !Workflow.validMember(name, role, zone)) return false
        return commit(workspace.copy(members = workspace.members + Member(name = name.trim(), role = role.trim(), zone = zone.trim())))
    }
    fun removeMember(id: String) = commit(workspace.copy(members = workspace.members.filterNot { it.id == id }))
    fun answer(choice: Int): Boolean {
        val index = workspace.answers.size
        if (workspace.event == null || index !in Training.correct.indices || Training.correct[index] != choice) return false
        return commit(workspace.copy(answers = workspace.answers + choice))
    }
    fun restartTraining() = commit(workspace.copy(answers = emptyList()))
    fun saveLearning(topic: String, score: Int) = commit(workspace.copy(learning = workspace.learning + (topic to maxOf(score, workspace.learning[topic] ?: 0))))
    fun check(index: Int) { if (index in 0..3) commit(workspace.copy(checks = if (index in workspace.checks) workspace.checks - index else workspace.checks + index)) }
    fun incident(note: String, zone: String): Boolean {
        if (workspace.event == null || note.trim().length !in 5..240 || zone.trim().length !in 1..60) return false
        return commit(workspace.copy(incidents = workspace.incidents + Incident(note = note.trim(), zone = zone.trim())))
    }
    fun advanceIncident(id: String) = commit(workspace.copy(incidents = workspace.incidents.map { if (it.id == id) it.copy(status = Workflow.next(it.status)) else it }))
    fun clear(): Boolean = try { store.clear(); workspace = Workspace(); storageError = false; loadFailed = false; true } catch (_: Exception) { storageError = true; false }
}
