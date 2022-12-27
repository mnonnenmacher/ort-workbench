package org.ossreviewtoolkit.workbench.ui.packages

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.Package
import org.ossreviewtoolkit.model.PackageCurationResult
import org.ossreviewtoolkit.model.Provenance
import org.ossreviewtoolkit.model.ScanResult
import org.ossreviewtoolkit.model.ScannerDetails
import org.ossreviewtoolkit.model.licenses.ResolvedLicenseInfo
import org.ossreviewtoolkit.utils.spdx.SpdxSingleLicenseExpression
import org.ossreviewtoolkit.workbench.model.DecoratedVulnerability
import org.ossreviewtoolkit.workbench.model.DependencyReference
import org.ossreviewtoolkit.workbench.model.FilterData
import org.ossreviewtoolkit.workbench.model.Issue
import org.ossreviewtoolkit.workbench.model.OrtModel
import org.ossreviewtoolkit.workbench.model.Violation
import org.ossreviewtoolkit.workbench.util.SpdxExpressionStringComparator
import org.ossreviewtoolkit.workbench.util.matchAnyValue
import org.ossreviewtoolkit.workbench.util.matchExclusionStatus
import org.ossreviewtoolkit.workbench.util.matchIssueStatus
import org.ossreviewtoolkit.workbench.util.matchString
import org.ossreviewtoolkit.workbench.util.matchStringContains
import org.ossreviewtoolkit.workbench.util.matchViolationStatus
import org.ossreviewtoolkit.workbench.util.matchVulnerabilityStatus

class PackagesViewModel(private val ortModel: OrtModel = OrtModel.INSTANCE) {
    private val scope = CoroutineScope(Dispatchers.Default)

    private val packages = MutableStateFlow(emptyList<PackageInfo>())
    private val filter = MutableStateFlow(PackagesFilter())

    private val _state = MutableStateFlow(PackagesState.INITIAL)
    val state: StateFlow<PackagesState> get() = _state

    init {
        scope.launch {
            ortModel.api.collect { api ->
                val projectsAndPackages =
                    (api.result.getProjects().map { it.toPackage().toCuratedPackage() } + api.result.getPackages())
                        .sorted()

                packages.value = projectsAndPackages.map { pkg ->
                    val references = api.getReferences(pkg.metadata.id)
                    val issues = api.getIssues().filter { it.id == pkg.metadata.id }
                    val violations = api.getViolations().filter { it.pkg == pkg.metadata.id }
                    val vulnerabilities = api.getVulnerabilities().filter { it.pkg == pkg.metadata.id }
                    val scanResultInfos = api.getScanResults(pkg.metadata.id).map { it.toInfo() }

                    PackageInfo(
                        metadata = pkg.metadata,
                        curations = pkg.curations,
                        resolvedLicenseInfo = api.licenseInfoResolver.resolveLicenseInfo(pkg.metadata.id),
                        references = references,
                        issues = issues,
                        violations = violations,
                        vulnerabilities = vulnerabilities,
                        scanResultInfos = scanResultInfos
                    )
                }
            }
        }

        scope.launch { packages.collect { initState(it) } }

        scope.launch {
            filter.collect { newFilter ->
                val oldState = state.value
                _state.value = oldState.copy(
                    packages = packages.value.filter(newFilter::check),
                    textFilter = newFilter.text,
                    exclusionStatusFilter = oldState.exclusionStatusFilter.copy(
                        selectedItem = newFilter.exclusionStatus
                    ),
                    issueStatusFilter = oldState.issueStatusFilter.copy(selectedItem = newFilter.issueStatus),
                    licenseFilter = oldState.licenseFilter.copy(selectedItem = newFilter.license),
                    namespaceFilter = oldState.namespaceFilter.copy(selectedItem = newFilter.namespace),
                    projectFilter = oldState.projectFilter.copy(selectedItem = newFilter.project),
                    scopeFilter = oldState.scopeFilter.copy(selectedItem = newFilter.scope),
                    typeFilter = oldState.typeFilter.copy(selectedItem = newFilter.type),
                    violationStatusFilter = oldState.violationStatusFilter.copy(
                        selectedItem = newFilter.violationStatus
                    ),
                    vulnerabilityStatusFilter = oldState.vulnerabilityStatusFilter.copy(
                        selectedItem = newFilter.vulnerabilityStatus
                    )
                )
            }
        }
    }

    private fun initState(packages: List<PackageInfo>) {
        _state.value = PackagesState(
            packages = packages,
            textFilter = "",
            exclusionStatusFilter = FilterData(
                selectedItem = ExclusionStatus.ALL,
                options = ExclusionStatus.values().toList()
            ),
            issueStatusFilter = FilterData(
                selectedItem = IssueStatus.ALL,
                options = IssueStatus.values().toList()
            ),
            licenseFilter = FilterData(
                selectedItem = null,
                options = listOf(null) + packages.flatMapTo(sortedSetOf(SpdxExpressionStringComparator())) {
                    it.resolvedLicenseInfo.licenses.map { it.license }
                }.toList()
            ),
            namespaceFilter = FilterData(
                selectedItem = null,
                options = listOf(null) + packages.mapTo(sortedSetOf()) { it.metadata.id.namespace }.toList()
            ),
            projectFilter = FilterData(
                selectedItem = null,
                options = listOf(null) + packages.flatMapTo(sortedSetOf()) { it.references.map { it.project } }.toList()
            ),
            scopeFilter = FilterData(
                selectedItem = null,
                options = listOf(null) + packages.flatMapTo(sortedSetOf()) {
                    it.references.flatMap { it.scopes.map { it.scope } }
                }.toList()
            ),
            typeFilter = FilterData(
                selectedItem = null,
                options = listOf(null) + packages.mapTo(sortedSetOf()) { it.metadata.id.type }.toList()
            ),
            violationStatusFilter = FilterData(
                selectedItem = ViolationStatus.ALL,
                options = ViolationStatus.values().toList()
            ),
            vulnerabilityStatusFilter = FilterData(
                selectedItem = VulnerabilityStatus.ALL,
                options = VulnerabilityStatus.values().toList()
            )
        )
    }

    fun updateTextFilter(text: String) {
        filter.value = filter.value.copy(text = text)
    }

    fun updateExclusionStatusFilter(exclusionStatus: ExclusionStatus) {
        filter.value = filter.value.copy(exclusionStatus = exclusionStatus)
    }

    fun updateIssueStatusFilter(issueStatus: IssueStatus) {
        filter.value = filter.value.copy(issueStatus = issueStatus)
    }

    fun updateLicenseFilter(license: SpdxSingleLicenseExpression?) {
        filter.value = filter.value.copy(license = license)
    }

    fun updateNamespaceFilter(namespace: String?) {
        filter.value = filter.value.copy(namespace = namespace)
    }

    fun updateProjectFilter(project: Identifier?) {
        filter.value = filter.value.copy(project = project)
    }

    fun updateScopeFilter(scope: String?) {
        filter.value = filter.value.copy(scope = scope)
    }

    fun updateTypeFilter(type: String?) {
        filter.value = filter.value.copy(type = type)
    }

    fun updateViolationStatusFilter(violationStatus: ViolationStatus) {
        filter.value = filter.value.copy(violationStatus = violationStatus)
    }

    fun updateVulnerabilityStatusFilter(vulnerabilityStatus: VulnerabilityStatus) {
        filter.value = filter.value.copy(vulnerabilityStatus = vulnerabilityStatus)
    }
}

data class PackageInfo(
    val metadata: Package,
    val curations: List<PackageCurationResult>,
    val resolvedLicenseInfo: ResolvedLicenseInfo,
    val references: List<DependencyReference>,
    val issues: List<Issue>,
    val violations: List<Violation>,
    val vulnerabilities: List<DecoratedVulnerability>,
    val scanResultInfos: List<ScanResultInfo>
) {
    fun isExcluded() = references.all { it.isExcluded || it.scopes.all { it.isExcluded } }
}

data class ScanResultInfo(
    val scanner: ScannerDetails,
    val provenance: Provenance
)

data class PackagesFilter(
    val text: String = "",
    val type: String? = null,
    val namespace: String? = null,
    val project: Identifier? = null,
    val scope: String? = null,
    val license: SpdxSingleLicenseExpression? = null,
    val issueStatus: IssueStatus = IssueStatus.ALL,
    val violationStatus: ViolationStatus = ViolationStatus.ALL,
    val vulnerabilityStatus: VulnerabilityStatus = VulnerabilityStatus.ALL,
    val exclusionStatus: ExclusionStatus = ExclusionStatus.ALL
) {
    fun check(pkg: PackageInfo) =
        matchStringContains(text, pkg.metadata.id.toCoordinates())
                && matchString(type, pkg.metadata.id.type)
                && matchString(namespace, pkg.metadata.id.namespace)
                && matchAnyValue(project, pkg.references.map { it.project })
                && matchString(scope, pkg.references.flatMap { it.scopes.map { it.scope } })
                && matchAnyValue(license, pkg.resolvedLicenseInfo.licenses.map { it.license })
                && matchIssueStatus(issueStatus, pkg.issues)
                && matchViolationStatus(violationStatus, pkg.violations)
                && matchVulnerabilityStatus(vulnerabilityStatus, pkg.vulnerabilities)
                && matchExclusionStatus(exclusionStatus, pkg.isExcluded())
}

enum class IssueStatus {
    ALL,
    HAS_ISSUES,
    NO_ISSUES
}

enum class ViolationStatus {
    ALL,
    HAS_VIOLATIONS,
    NO_VIOLATIONS
}

enum class VulnerabilityStatus {
    ALL,
    HAS_VULNERABILITY,
    NO_VULNERABILITY
}

enum class ExclusionStatus {
    ALL,
    EXCLUDED,
    INCLUDED
}

private fun ScanResult.toInfo() = ScanResultInfo(scanner, provenance)
