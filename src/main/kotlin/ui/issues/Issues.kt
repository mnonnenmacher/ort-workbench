package org.ossreviewtoolkit.workbench.ui.issues

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

import java.time.Instant

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.model.config.IssueResolution
import org.ossreviewtoolkit.model.config.IssueResolutionReason
import org.ossreviewtoolkit.utils.common.titlecase
import org.ossreviewtoolkit.workbench.composables.ExpandableText
import org.ossreviewtoolkit.workbench.composables.FilterButton
import org.ossreviewtoolkit.workbench.composables.FilterPanel
import org.ossreviewtoolkit.workbench.composables.ListScreenContent
import org.ossreviewtoolkit.workbench.composables.Preview
import org.ossreviewtoolkit.workbench.composables.SeverityIcon
import org.ossreviewtoolkit.workbench.model.Issue
import org.ossreviewtoolkit.workbench.model.ResolutionStatus
import org.ossreviewtoolkit.workbench.model.Tool

@Composable
fun Issues(viewModel: IssuesViewModel) {
    val state by viewModel.state.collectAsState()

    ListScreenContent(
        filterText = state.textFilter,
        onUpdateFilterText = viewModel::updateTextFilter,
        list = { IssuesList(state.issues) },
        filterPanel = { showFilterPanel ->
            IssuesFilterPanel(
                visible = showFilterPanel,
                state = state,
                onUpdateIdentifierFilter = viewModel::updateIdentifierFilter,
                onUpdateResolutionStatusFilter = viewModel::updateResolutionStatusFilter,
                onUpdateSeverityFilter = viewModel::updateSeverityFilter,
                onUpdateSourceFilter = viewModel::updateSourceFilter,
                onUpdateToolFilter = viewModel::updateToolFilter
            )
        }
    )
}

@Composable
fun IssuesList(issues: List<Issue>) {
    if (issues.isEmpty()) {
        Text("No issues found.", modifier = Modifier.padding(15.dp))
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            val listState = rememberLazyListState()

            LazyColumn(
                contentPadding = PaddingValues(15.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                state = listState
            ) {
                items(issues.size, key = { it }) { index ->
                    IssueCard(issues[index])
                }
            }

            VerticalScrollbar(
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                adapter = rememberScrollbarAdapter(scrollState = listState)
            )
        }
    }
}

@Composable
fun IssueCard(issue: Issue) {
    Card(modifier = Modifier.fillMaxWidth(), elevation = 8.dp) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                SeverityIcon(issue.severity, resolved = issue.resolutions.isNotEmpty())
                Text(issue.id.toCoordinates())
                Box(modifier = Modifier.weight(1f))
                Text("Source: ${issue.source}")
            }

            if (issue.resolutions.isNotEmpty()) Divider()

            issue.resolutions.forEach { resolution ->
                Text("Resolved: ${resolution.reason}", fontWeight = FontWeight.Bold)
                ExpandableText(resolution.comment)
                Divider()
            }

            ExpandableText(issue.message, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
@Preview
private fun IssueCardPreview() {
    val issue = Issue(
        id = Identifier("Maven:org.example:package:1.0.0-beta"),
        tool = Tool.ANALYZER,
        resolutions = listOf(
            IssueResolution("", IssueResolutionReason.BUILD_TOOL_ISSUE, "Some long explanation. ".repeat(20)),
            IssueResolution("", IssueResolutionReason.BUILD_TOOL_ISSUE, "Some long explanation. ".repeat(20))
        ),
        timestamp = Instant.now(),
        source = "Maven",
        message = "Some long error message. ".repeat(20),
        severity = Severity.WARNING
    )

    Preview {
        IssueCard(issue)
    }
}

@Composable
fun IssuesFilterPanel(
    visible: Boolean,
    state: IssuesState,
    onUpdateIdentifierFilter: (identifier: Identifier?) -> Unit,
    onUpdateResolutionStatusFilter: (status: ResolutionStatus?) -> Unit,
    onUpdateSeverityFilter: (severity: Severity?) -> Unit,
    onUpdateSourceFilter: (source: String?) -> Unit,
    onUpdateToolFilter: (tool: Tool?) -> Unit
) {
    FilterPanel(visible = visible) {
        FilterButton(
            data = state.severityFilter,
            label = "Severity",
            onFilterChange = onUpdateSeverityFilter,
            convert = { it.name.titlecase() }
        )

        FilterButton(data = state.sourceFilter, label = "Source", onFilterChange = onUpdateSourceFilter)

        FilterButton(
            data = state.toolFilter,
            label = "Tool",
            onFilterChange = onUpdateToolFilter,
            convert = { it.name.titlecase() }
        )

        FilterButton(
            data = state.identifierFilter,
            label = "Package",
            onFilterChange = onUpdateIdentifierFilter,
            convert = { it.toCoordinates() }
        )

        FilterButton(
            data = state.resolutionStatusFilter,
            label = "Resolution",
            onFilterChange = onUpdateResolutionStatusFilter,
            convert = { it.name.titlecase() }
        )
    }
}
