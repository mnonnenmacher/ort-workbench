package org.ossreviewtoolkit.workbench.ui.violations

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

import org.ossreviewtoolkit.model.Identifier
import org.ossreviewtoolkit.model.LicenseSource
import org.ossreviewtoolkit.model.Severity
import org.ossreviewtoolkit.utils.spdx.SpdxSingleLicenseExpression
import org.ossreviewtoolkit.workbench.model.FilterData
import org.ossreviewtoolkit.workbench.model.OrtModel
import org.ossreviewtoolkit.workbench.model.ResolutionStatus
import org.ossreviewtoolkit.workbench.model.ResolvedRuleViolation
import org.ossreviewtoolkit.workbench.utils.SpdxExpressionStringComparator
import org.ossreviewtoolkit.workbench.utils.matchResolutionStatus
import org.ossreviewtoolkit.workbench.utils.matchString
import org.ossreviewtoolkit.workbench.utils.matchStringContains
import org.ossreviewtoolkit.workbench.utils.matchValue

class ViolationsViewModel(private val ortModel: OrtModel) {
    private val scope = CoroutineScope(Dispatchers.Default)

    private val violations = MutableStateFlow(emptyList<ResolvedRuleViolation>())
    private val filter = MutableStateFlow(ViolationsFilter())

    private val _state = MutableStateFlow(ViolationsState.INITIAL)
    val state: StateFlow<ViolationsState> get() = _state

    init {
        scope.launch { ortModel.api.collect { violations.value = it.getViolations() } }

        scope.launch { violations.collect { initState(it) } }

        scope.launch {
            filter.collect { newFilter ->
                val oldState = state.value
                _state.value = oldState.copy(
                    violations = violations.value.filter(newFilter::check),
                    textFilter = newFilter.text,
                    identifierFilter = oldState.identifierFilter.copy(selectedItem = newFilter.identifier),
                    licenseFilter = oldState.licenseFilter.copy(selectedItem = newFilter.license),
                    licenseSourceFilter = oldState.licenseSourceFilter.copy(selectedItem = newFilter.licenseSource),
                    resolutionStatusFilter = oldState.resolutionStatusFilter.copy(
                        selectedItem = newFilter.resolutionStatus
                    ),
                    ruleFilter = oldState.ruleFilter.copy(selectedItem = newFilter.rule),
                    severityFilter = oldState.severityFilter.copy(selectedItem = newFilter.severity)
                )
            }
        }
    }

    private fun initState(violations: List<ResolvedRuleViolation>) {
        _state.value = ViolationsState(
            violations = violations,
            textFilter = "",
            identifierFilter = FilterData(violations.mapNotNullTo(sortedSetOf()) { it.pkg }.toList()),
            licenseFilter = FilterData(
                violations.mapNotNullTo(sortedSetOf(SpdxExpressionStringComparator())) {
                    it.license
                }.toList()
            ),
            licenseSourceFilter = FilterData(LicenseSource.values().toList()),
            resolutionStatusFilter = FilterData(ResolutionStatus.values().toList()),
            ruleFilter = FilterData(violations.mapTo(sortedSetOf()) { it.rule }.toList()),
            severityFilter = FilterData(Severity.values().toList())
        )
    }

    fun updateTextFilter(text: String) {
        filter.value = filter.value.copy(text = text)
    }

    fun updateIdentifierFilter(identifier: Identifier?) {
        filter.value = filter.value.copy(identifier = identifier)
    }

    fun updateLicenseFilter(license: SpdxSingleLicenseExpression?) {
        filter.value = filter.value.copy(license = license)
    }

    fun updateLicenseSourceFilter(licenseSource: LicenseSource?) {
        filter.value = filter.value.copy(licenseSource = licenseSource)
    }

    fun updateResolutionStatusFilter(resolutionStatus: ResolutionStatus?) {
        filter.value = filter.value.copy(resolutionStatus = resolutionStatus)
    }

    fun updateRuleFilter(rule: String?) {
        filter.value = filter.value.copy(rule = rule)
    }

    fun updateSeverityFilter(severity: Severity?) {
        filter.value = filter.value.copy(severity = severity)
    }
}

data class ViolationsFilter(
    val identifier: Identifier? = null,
    val license: SpdxSingleLicenseExpression? = null,
    val licenseSource: LicenseSource? = null,
    val resolutionStatus: ResolutionStatus? = null,
    val rule: String? = null,
    val severity: Severity? = null,
    val text: String = ""
) {
    fun check(violation: ResolvedRuleViolation) =
        matchValue(identifier, violation.pkg)
                && matchValue(license, violation.license)
                && matchValue(licenseSource, violation.licenseSource)
                && matchResolutionStatus(resolutionStatus, violation.resolutions)
                && matchString(rule, violation.rule)
                && matchValue(severity, violation.severity)
                && matchStringContains(
            text,
            listOfNotNull(
                violation.pkg?.toCoordinates(),
                violation.rule,
                violation.license?.toString(),
                violation.licenseSource?.name,
                violation.message,
                violation.howToFix
            )
        )
}
