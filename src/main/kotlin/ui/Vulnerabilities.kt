package org.ossreviewtoolkit.workbench.ui

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
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

import java.net.URI

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.VulnerabilityReference
import org.ossreviewtoolkit.model.config.VulnerabilityResolution
import org.ossreviewtoolkit.model.config.VulnerabilityResolutionReason
import org.ossreviewtoolkit.utils.common.titlecase
import org.ossreviewtoolkit.workbench.state.AppState
import org.ossreviewtoolkit.workbench.state.DecoratedVulnerability
import org.ossreviewtoolkit.workbench.state.VulnerabilitiesState
import org.ossreviewtoolkit.workbench.util.ExpandableText
import org.ossreviewtoolkit.workbench.util.FilterButton
import org.ossreviewtoolkit.workbench.util.FilterTextField
import org.ossreviewtoolkit.workbench.util.Preview
import org.ossreviewtoolkit.workbench.util.ResolutionStatus
import org.ossreviewtoolkit.workbench.util.WebLink

@Composable
@Preview
fun Vulnerabilities(appState: AppState) {
    val state = appState.vulnerabilities

    if (!state.initialized) {
        LaunchedEffect(Unit) {
            state.initialize(appState.result.resultApi)
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(25.dp, alignment = Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator()
            Text("Processing...")
        }
    } else {
        Column(
            modifier = Modifier.padding(15.dp).fillMaxSize()
        ) {
            TitleRow(state)

            VulnerabilitiesList(state.filteredVulnerabilities)
        }
    }
}

@Composable
private fun TitleRow(state: VulnerabilitiesState) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        FilterTextField(state.filterText, state::updateFilterText)
        FilterAdvisor(state.filterAdvisor, state.advisors, state::updateFilterAdvisor)
        FilterScoringSystem(state.filterScoringSystem, state.scoringSystems, state::updateFilterScoringSystem)
        FilterSeverity(state.filterSeverity, state.severities, state::updateFilterSeverity)
        FilterIdentifier(state.filterIdentifier, state.identifiers, state::updateFilterIdentifier)
        FilterResolutionStatus(state.filterResolutionStatus, state::updateFilterResolutionStatus)
    }
}

@Composable
private fun FilterAdvisor(advisor: String, advisors: List<String>, onAdvisorChange: (String) -> Unit) {
    FilterButton(
        selectedItem = advisor,
        items = listOf("") + advisors,
        onFilterChange = onAdvisorChange,
        buttonContent = { if (it.isEmpty()) Text("Advisor") else Text(it) }
    ) { selectedItem ->
        if (selectedItem.isEmpty()) Text("All") else Text(selectedItem)
    }
}

@Composable
private fun FilterScoringSystem(
    scoringSystem: String,
    scoringSystems: List<String>,
    onScoringSystemChange: (String) -> Unit
) {
    FilterButton(
        selectedItem = scoringSystem,
        items = listOf("") + scoringSystems,
        onFilterChange = onScoringSystemChange,
        buttonContent = { if (it.isEmpty()) Text("Scoring System") else Text(it) },
        buttonWidth = 150.dp,
        dropdownWidth = 150.dp
    ) { selectedItem ->
        if (selectedItem.isEmpty()) Text("All") else Text(selectedItem)
    }
}

@Composable
private fun FilterSeverity(severity: String, severities: List<String>, onSeverityChange: (String) -> Unit) {
    FilterButton(
        selectedItem = severity,
        items = listOf("") + severities,
        onFilterChange = onSeverityChange,
        buttonContent = { if (it.isEmpty()) Text("Severity") else Text(it) }
    ) { selectedItem ->
        if (selectedItem.isEmpty()) Text("All") else Text(selectedItem)
    }
}

@Composable
private fun IdentifierText(identifier: Identifier) {
    Text(identifier.toCoordinates(), maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun FilterIdentifier(
    identifier: Identifier?,
    identifiers: List<Identifier>,
    onIdentifierChange: (Identifier?) -> Unit
) {
    FilterButton(
        selectedItem = identifier,
        items = listOf(null) + identifiers,
        onFilterChange = onIdentifierChange,
        buttonContent = { if (it == null) Text("Package") else IdentifierText(it) },
        buttonWidth = 200.dp,
        dropdownWidth = 500.dp
    ) {
        if (it == null) Text("All") else IdentifierText(it)
    }
}

@Composable
private fun FilterResolutionStatus(
    resolutionStatus: ResolutionStatus,
    onResolutionStatusChange: (ResolutionStatus) -> Unit
) {
    FilterButton(
        selectedItem = resolutionStatus,
        items = listOf(ResolutionStatus.ALL, ResolutionStatus.RESOLVED, ResolutionStatus.UNRESOLVED),
        onFilterChange = onResolutionStatusChange,
        buttonContent = { if (it == ResolutionStatus.ALL) Text("Resolution") else Text(it.name.titlecase()) }
    ) {
        Text(it.name.titlecase())
    }
}

@Composable
fun VulnerabilitiesList(vulnerabilities: List<DecoratedVulnerability>) {
    if (vulnerabilities.isEmpty()) {
        Text("No vulnerabilities found.", modifier = Modifier.padding(top = 10.dp))
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            val listState = rememberLazyListState()

            LazyColumn(
                contentPadding = PaddingValues(vertical = 15.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(vulnerabilities.size, key = { it }) { index ->
                    VulnerabilityCard(vulnerabilities[index])
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
fun VulnerabilityCard(vulnerability: DecoratedVulnerability) {
    Card(modifier = Modifier.fillMaxWidth(), elevation = 8.dp) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(vulnerability.id, fontWeight = FontWeight.Bold)
                Text(vulnerability.pkg.toCoordinates())
                Box(modifier = Modifier.weight(1f))
                Text("Source: ${vulnerability.advisor}")
            }

            if (vulnerability.resolutions.isNotEmpty()) Divider()

            vulnerability.resolutions.forEach { resolution ->
                Text("Resolved: ${resolution.reason}", fontWeight = FontWeight.Bold)
                ExpandableText(resolution.comment)
                Divider()
            }

            vulnerability.references.forEach { reference ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    reference.scoringSystem?.let { Text("$it:") }
                    reference.severity?.let { Text(it) }
                    Box(modifier = Modifier.weight(1f))
                    Text(reference.url.host.orEmpty())
                    WebLink("Link", reference.url.toString())
                }
            }
        }
    }
}

@Composable
@Preview
private fun VulnerabilityCardPreview() {
    val vulnerability = DecoratedVulnerability(
        pkg = Identifier("Maven:com.example:package:1.0.0-beta"),
        resolutions = listOf(
            VulnerabilityResolution(
                "v-id",
                VulnerabilityResolutionReason.CANT_FIX_VULNERABILITY,
                "Some long comment. ".repeat(20)
            )
        ),
        advisor = "Advisor",
        id = "v-id",
        references = listOf(
            VulnerabilityReference(
                URI("http://example.com"),
                scoringSystem = "scosy",
                severity = "severe"
            )
        )
    )

    Preview {
        VulnerabilityCard(vulnerability)
    }
}
