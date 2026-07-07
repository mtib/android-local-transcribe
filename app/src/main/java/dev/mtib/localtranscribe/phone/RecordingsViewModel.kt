package dev.mtib.localtranscribe.phone

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.mtib.localtranscribe.core.session.RecordingRepository
import dev.mtib.localtranscribe.core.session.RecordingSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RecordingsViewModel(app: Application) : AndroidViewModel(app) {
    val repository = RecordingRepository(app)

    private val _sessions = MutableStateFlow<List<RecordingSession>>(emptyList())
    val sessions = _sessions.asStateFlow()

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) { _sessions.value = repository.list() }
    }

    fun delete(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.delete(id)
            _sessions.value = repository.list()
        }
    }
}
