package org.ossreviewtoolkit.workbench.ui.dependencies

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

import org.ossreviewtoolkit.model.PackageLinkage
import org.ossreviewtoolkit.model.PackageReference
import org.ossreviewtoolkit.model.Project
import org.ossreviewtoolkit.model.Scope
import org.ossreviewtoolkit.workbench.composables.tree.TreeNode
import org.ossreviewtoolkit.workbench.model.OrtApi
import org.ossreviewtoolkit.workbench.model.OrtModel

class DependenciesViewModel(private val ortModel: OrtModel = OrtModel.INSTANCE) {
    private val scope = CoroutineScope(Dispatchers.Default)

    private val _state = MutableStateFlow(DependenciesState(emptyList()))
    val state: StateFlow<DependenciesState> get() = _state

    init {
        scope.launch {
            ortModel.api.collect { api ->
                _state.value = DependenciesState(createDependencyNodes(api))
            }
        }
    }

    private fun createDependencyNodes(api: OrtApi): List<TreeNode<DependencyTreeItem>> {
        val resolvedLicenses = api.result.collectProjectsAndPackages().associateWith {
            api.licenseInfoResolver.resolveLicenseInfo(it).filterExcluded()
        }

        fun PackageReference.toTreeNode(): TreeNode<DependencyTreeItem> {
            val children = dependencies.map { it.toTreeNode() }

            return api.result.getProject(id)?.let { project ->
                TreeNode(
                    value = DependencyTreeProject(
                        project = project,
                        linkage = linkage,
                        issues = issues,
                        resolvedLicense = resolvedLicenses.getValue(id)
                    ),
                    children = children
                )
            } ?: api.result.getPackage(id)?.let { pkg ->
                TreeNode(
                    value = DependencyTreePackage(
                        id = id,
                        pkg = pkg,
                        linkage = linkage,
                        issues = issues,
                        resolvedLicense = resolvedLicenses.getValue(id)
                    ),
                    children = children
                )
            } ?: TreeNode(
                value = DependencyTreeError(
                    id = id,
                    message = "Could not find package or project for id '${id.toCoordinates()}'."
                )
            )
        }

        fun Scope.toTreeNode(project: Project): TreeNode<DependencyTreeItem> {
            val children = dependencies.map { it.toTreeNode() }

            return TreeNode(
                value = DependencyTreeScope(
                    project = project,
                    scope = this
                ),
                children = children
            )
        }

        fun Project.toTreeNode(): TreeNode<DependencyTreeItem> {
            val children = scopes.map { it.toTreeNode(this) }

            return TreeNode(
                value = DependencyTreeProject(
                    project = this,
                    linkage = PackageLinkage.PROJECT_STATIC,
                    issues = emptyList(),
                    resolvedLicense = resolvedLicenses.getValue(id)
                ),
                children = children
            )
        }

        return api.result.getProjects().map { it.toTreeNode() }
    }
}
