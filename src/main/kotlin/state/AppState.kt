package org.ossreviewtoolkit.workbench.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

import java.nio.file.Path

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import org.ossreviewtoolkit.model.OrtResult
import org.ossreviewtoolkit.model.readValue
import org.ossreviewtoolkit.workbench.ui.MenuItem

@Composable
fun rememberAppState() = remember { AppState() }

class AppState {
    var currentScreen by mutableStateOf(MenuItem.SUMMARY)
        private set

    val dependencies = DependenciesState()
    val issues = IssuesState()
    val violations = ViolationsState()
    val vulnerabilities = VulnerabilitiesState()
    val result = ResultState()
    val settings = SettingsState()

    val openResultDialog = DialogState<Path?>()

    fun switchScreen(menuItem: MenuItem) {
        currentScreen = menuItem
    }

    suspend fun openOrtResult() {
        val path = openResultDialog.awaitResult()
        if (path != null) {
            loadOrtResult(path)
        }
    }

    private suspend fun loadOrtResult(path: Path) {
        result.error = null
        result.path = path
        result.status = ResultStatus.LOADING
        withContext(Dispatchers.IO) {
            runCatching {
                val ortResult = path.toFile().readValue<OrtResult>()
                result.status = ResultStatus.PROCESSING
                result.setOrtResult(ortResult)
                result.status = ResultStatus.FINISHED
            }.onFailure { e ->
                e.printStackTrace()
                result.error = "Cannot read ORT result from $path:\n${e.message}"
                result.status = ResultStatus.ERROR
            }
        }
    }
}
