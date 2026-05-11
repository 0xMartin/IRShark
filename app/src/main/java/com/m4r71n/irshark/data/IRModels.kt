package com.m4r71n.irshark.data

import androidx.compose.runtime.Immutable

enum class DbSourceType {
    DEFAULT,
    DOWNLOADED
}

@Immutable
data class FlipperDbUpdateResult(
    val success: Boolean,
    val updated: Boolean,
    val latestTag: String?,
    val message: String
)

@Immutable
data class FlipperDbIndex(
    val totalProfiles: Int = 0,
    val folders: Map<String, List<String>> = emptyMap(),
    val profilesByFolder: Map<String, List<FlipperProfile>> = emptyMap(),
    val profiles: List<FlipperProfile> = emptyList(),
    val lintConfig: FlipperLintConfig = FlipperLintConfig(),
    val status: String = "Loading Flipper-IRDB..."
)

@Immutable
data class FlipperProfile(
    val path: String,
    val parentPath: String,
    val name: String,
    val commands: List<String>
)

@Immutable
data class SavedRemote(
    val name: String,
    val profilePath: String,
    val commands: List<String>,
    val buttons: List<SavedRemoteButton> = emptyList(),
    val iconName: String? = null,
    val sourceProfilePath: String? = null,
    val favorite: Boolean = false,
    val columnCount: Int = 2,
    val groupByCategory: Boolean = true
)

@Immutable
data class SavedRemoteButton(
    val label: String,
    val code: String
)

@Immutable
data class RemoteHistoryEntry(
    val name: String,
    val profilePath: String,
    val sourceProfilePath: String? = null,
    val iconName: String? = null,
    val openedAtEpochMs: Long,
    val buttons: List<SavedRemoteButton> = emptyList()
) {
    val stableKey: String
        get() = when {
            !sourceProfilePath.isNullOrBlank() -> "db:$sourceProfilePath"
            profilePath.isNotBlank() -> "path:$profilePath:${name.lowercase()}"
            buttons.isNotEmpty() -> "custom:$name:${buttons.joinToString("|") { "${it.label}:${it.code}" }.hashCode()}"
            else -> "name:${name.lowercase()}"
        }
}

@Immutable
data class DbIrCodeOption(
    val label: String,
    val code: String,
    val details: String
)

data class FlipperLintConfig(
    val groups: Map<String, List<LintMatcher>> = emptyMap(),
    val pathRules: List<PathLintRule> = emptyList()
)

data class PathLintRule(
    val patterns: List<String>,
    val canonicalRules: List<CanonicalCommandRule>
)

data class CanonicalCommandRule(
    val canonicalName: String,
    val matchers: List<LintMatcher>
)

sealed class LintMatcher {
    data class Literal(val value: String) : LintMatcher()
    data class RegexPattern(val pattern: Regex) : LintMatcher()
    data class GroupReference(val group: String) : LintMatcher()
}

@Immutable
data class UniversalCommandItem(
    val displayLabel: String,
    val actualCommand: String,
    val profileCoverage: Int
)

@Immutable
data class DbLoadProgress(
    val loadedFiles: Int = 0,
    val totalFiles: Int = 0
)
