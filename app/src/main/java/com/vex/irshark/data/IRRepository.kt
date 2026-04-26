package com.vex.irshark.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

private const val DB_ROOT = "flipper_irdb"
private const val PREFS_NAME = "irshark_prefs"
private const val KEY_SAVED_REMOTES = "saved_remotes"
private const val REMOTE_DELIMITER = "||"

data class FlipperDbIndex(
    val totalProfiles: Int = 0,
    val folders: Map<String, List<String>> = emptyMap(),
    val profilesByFolder: Map<String, List<FlipperProfile>> = emptyMap(),
    val profiles: List<FlipperProfile> = emptyList(),
    val lintConfig: FlipperLintConfig = FlipperLintConfig(),
    val status: String = "Loading Flipper-IRDB..."
)

data class FlipperProfile(
    val path: String,
    val parentPath: String,
    val name: String,
    val commands: List<String>
)

data class SavedRemote(
    val name: String,
    val profilePath: String,
    val commands: List<String>,
    val buttons: List<SavedRemoteButton> = emptyList(),
    val sourceProfilePath: String? = null,
    val favorite: Boolean = false
)

data class SavedRemoteButton(
    val label: String,
    val code: String
)

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

data class UniversalCommandItem(
    val displayLabel: String,
    val actualCommand: String,
    val profileCoverage: Int
)

suspend fun loadFlipperDbIndex(context: Context): FlipperDbIndex {
    return withContext(Dispatchers.IO) {
        runCatching {
            val lintConfig = parseLintConfig(context)
            val folders = mutableMapOf<String, MutableList<String>>()
            val profilesByFolder = mutableMapOf<String, MutableList<FlipperProfile>>()
            val allProfiles = mutableListOf<FlipperProfile>()

            fun walk(path: String) {
                val children = context.assets.list(path)?.sorted().orEmpty()
                folders.putIfAbsent(path, mutableListOf())
                profilesByFolder.putIfAbsent(path, mutableListOf())

                children.forEach { child ->
                    val childPath = "$path/$child"
                    if (child.endsWith(".ir", ignoreCase = true)) {
                        val commands = parseIrCommands(context, childPath)
                        val profile = FlipperProfile(
                            path = childPath,
                            parentPath = path,
                            name = child.removeSuffix(".ir").replace('_', ' ').trim(),
                            commands = commands
                        )
                        profilesByFolder[path]?.add(profile)
                        allProfiles += profile
                    } else {
                        val nested = context.assets.list(childPath).orEmpty()
                        if (nested.isNotEmpty()) {
                            folders[path]?.add(childPath)
                            walk(childPath)
                        }
                    }
                }
            }

            walk(DB_ROOT)

            FlipperDbIndex(
                totalProfiles = allProfiles.size,
                folders = folders,
                profilesByFolder = profilesByFolder,
                profiles = allProfiles,
                lintConfig = lintConfig,
                status = "Loaded ${allProfiles.size} profiles"
            )
        }.getOrElse {
            FlipperDbIndex(status = "Flipper-IRDB unavailable")
        }
    }
}

fun profilesUnderPath(dbIndex: FlipperDbIndex, folderPath: String): List<FlipperProfile> {
    val prefix = "$folderPath/"
    return dbIndex.profiles.filter { it.parentPath == folderPath || it.path.startsWith(prefix) }
}

fun commandStatsForPath(dbIndex: FlipperDbIndex, folderPath: String): Map<String, Int> {
    val stats = linkedMapOf<String, Int>()
    profilesUnderPath(dbIndex, folderPath).forEach { profile ->
        profile.commands.forEach { command ->
            stats[command] = (stats[command] ?: 0) + 1
        }
    }
    return stats
}

fun resolveUniversalCommandsForPath(
    dbIndex: FlipperDbIndex,
    folderPath: String,
    limit: Int = 18
): List<UniversalCommandItem> {
    val stats = commandStatsForPath(dbIndex, folderPath)
    if (stats.isEmpty()) return emptyList()

    val lintResolved = resolveUsingLint(
        lintConfig = dbIndex.lintConfig,
        folderPath = folderPath,
        commandStats = stats
    )

    if (lintResolved.isNotEmpty()) {
        return lintResolved.take(limit)
    }

    return stats.entries
        .sortedByDescending { it.value }
        .take(limit)
        .map {
            UniversalCommandItem(
                displayLabel = normalizeDisplayName(it.key),
                actualCommand = it.key,
                profileCoverage = it.value
            )
        }
}

fun countProfilesForCommand(dbIndex: FlipperDbIndex, folderPath: String, command: String): Int {
    return profilesUnderPath(dbIndex, folderPath).count { profile ->
        profile.commands.any { it.equals(command, ignoreCase = true) }
    }
}

fun parentPath(path: String): String? {
    if (path == DB_ROOT) return null
    val slash = path.lastIndexOf('/')
    if (slash <= 0) return DB_ROOT
    return path.substring(0, slash)
}

fun prettyName(path: String): String {
    val name = path.substringAfterLast('/').ifBlank { path }
    return name.replace('_', ' ')
}

fun prettyPath(path: String): String {
    return path
        .removePrefix("$DB_ROOT/")
        .removePrefix(DB_ROOT)
        .ifBlank { "Root" }
        .replace('/', ' ')
        .replace('_', ' ')
}

fun prettyPathWithChevron(path: String): String {
    val normalized = path
        .removePrefix("$DB_ROOT/")
        .removePrefix(DB_ROOT)

    if (normalized.isBlank()) return "Root"

    return normalized
        .split('/')
        .joinToString(" > ") { segment -> segment.replace('_', ' ') }
}

fun dbRootPath(): String = DB_ROOT

fun loadSavedRemotes(context: Context): List<SavedRemote> {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val raw = prefs.getString(KEY_SAVED_REMOTES, "").orEmpty()
    if (raw.isBlank()) {
        return emptyList()
    }

    parseSavedRemotesJson(raw)?.let { return it }

    return raw.split(REMOTE_DELIMITER)
        .mapNotNull { token ->
            val row = token.trim()
            if (row.isBlank()) return@mapNotNull null

            val parts = row.split("::")
            when {
                parts.size >= 3 -> {
                    val commands = parts[2].split(";;").map { it.trim() }.filter { it.isNotBlank() }
                    SavedRemote(
                        name = parts[0].trim(),
                        profilePath = parts[1].trim(),
                        commands = commands,
                        buttons = commands.map { SavedRemoteButton(label = it, code = "") },
                        sourceProfilePath = parts[1].trim().takeIf { it.startsWith(DB_ROOT) },
                        favorite = false
                    )
                }
                parts.size == 2 -> {
                    SavedRemote(
                        name = parts[0].trim(),
                        profilePath = parts[1].trim(),
                        commands = emptyList(),
                        buttons = emptyList(),
                        sourceProfilePath = parts[1].trim().takeIf { it.startsWith(DB_ROOT) },
                        favorite = false
                    )
                }
                else -> {
                    SavedRemote(name = row, profilePath = "", commands = emptyList(), buttons = emptyList(), favorite = false)
                }
            }
        }
}

fun saveSavedRemotes(context: Context, remotes: List<SavedRemote>) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val serialized = serializeSavedRemotesJson(remotes)
    prefs.edit().putString(KEY_SAVED_REMOTES, serialized.toString()).apply()
}

fun serializeSavedRemotesJson(remotes: List<SavedRemote>): String {
    return JSONArray().apply {
        remotes.forEach { remote ->
            put(
                JSONObject().apply {
                    put("name", remote.name)
                    put("profilePath", remote.profilePath)
                    put("sourceProfilePath", remote.sourceProfilePath ?: "")
                    put("favorite", remote.favorite)
                    put(
                        "commands",
                        JSONArray().apply {
                            remote.commands.forEach { put(it) }
                        }
                    )
                    put(
                        "buttons",
                        JSONArray().apply {
                            remote.buttons.forEach { button ->
                                put(
                                    JSONObject().apply {
                                        put("label", button.label)
                                        put("code", button.code)
                                    }
                                )
                            }
                        }
                    )
                }
            )
        }
    }.toString()
}

fun parseSavedRemotesJson(raw: String): List<SavedRemote>? {
    val trimmed = raw.trim()
    if (!trimmed.startsWith("[")) return null

    return runCatching {
        val arr = JSONArray(trimmed)
        buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val name = obj.optString("name").trim()
                if (name.isBlank()) continue

                val profilePath = obj.optString("profilePath").trim()
                val commands = obj.optJSONArray("commands")?.let { list ->
                    buildList {
                        for (j in 0 until list.length()) {
                            val cmd = list.optString(j).trim()
                            if (cmd.isNotBlank()) add(cmd)
                        }
                    }
                }.orEmpty()

                val buttons = obj.optJSONArray("buttons")?.let { list ->
                    buildList {
                        for (j in 0 until list.length()) {
                            val b = list.optJSONObject(j) ?: continue
                            val label = b.optString("label").trim()
                            if (label.isBlank()) continue
                            add(
                                SavedRemoteButton(
                                    label = label,
                                    code = b.optString("code").trim()
                                )
                            )
                        }
                    }
                }.orEmpty()

                val sourceProfilePath = obj.optString("sourceProfilePath").trim().ifBlank {
                    if (profilePath.startsWith(DB_ROOT)) profilePath else ""
                }.ifBlank { null }

                val resolvedButtons = if (buttons.isNotEmpty()) {
                    buttons
                } else {
                    commands.map { SavedRemoteButton(label = it, code = "") }
                }

                add(
                    SavedRemote(
                        name = name,
                        profilePath = profilePath,
                        commands = if (commands.isNotEmpty()) commands else resolvedButtons.map { it.label },
                        buttons = resolvedButtons,
                        sourceProfilePath = sourceProfilePath,
                        favorite = obj.optBoolean("favorite", false)
                    )
                )
            }
        }
    }.getOrElse { emptyList() }
}

suspend fun loadDbIrCodeOptions(context: Context, assetPath: String): List<DbIrCodeOption> {
    return withContext(Dispatchers.IO) {
        parseIrCodeBlocks(context, assetPath).map { block ->
            val data = block.fields["data"].orEmpty()
            val protocol = block.fields["protocol"].orEmpty()
            val address = block.fields["address"].orEmpty()
            val command = block.fields["command"].orEmpty()

            val codeValue = when {
                data.isNotBlank() -> data
                protocol.isNotBlank() || address.isNotBlank() || command.isNotBlank() -> {
                    listOf(
                        "protocol=$protocol",
                        "address=$address",
                        "command=$command"
                    ).joinToString("; ")
                }
                else -> block.fields.entries.joinToString("; ") { "${it.key}=${it.value}" }
            }

            val details = block.fields.entries.joinToString("\n") { "${it.key}: ${it.value}" }

            DbIrCodeOption(
                label = block.displayName,
                code = codeValue,
                details = details
            )
        }
    }
}

private fun parseIrCommands(context: Context, assetPath: String): List<String> {
    return parseIrCodeBlocks(context, assetPath).map { it.displayName }
}

private data class ParsedIrCodeBlock(
    val displayName: String,
    val fields: Map<String, String>
)

private fun parseIrCodeBlocks(context: Context, assetPath: String): List<ParsedIrCodeBlock> {
    return runCatching {
        val blocks = mutableListOf<ParsedIrCodeBlock>()
        var currentName: String? = null
        val currentFields = linkedMapOf<String, String>()

        fun flushCurrent() {
            val name = currentName ?: return
            blocks += ParsedIrCodeBlock(
                displayName = normalizeDisplayName(name),
                fields = currentFields.toMap()
            )
            currentName = null
            currentFields.clear()
        }

        context.assets.open(assetPath).bufferedReader().useLines { lines ->
            lines.forEach { rawLine ->
                val line = rawLine.trim()
                if (line.startsWith("name:", ignoreCase = true)) {
                    flushCurrent()
                    currentName = line.substringAfter(':').trim()
                } else if (currentName != null && ':' in line) {
                    val key = line.substringBefore(':').trim().lowercase()
                    val value = line.substringAfter(':').trim()
                    if (key.isNotBlank() && value.isNotBlank()) {
                        currentFields[key] = value
                    }
                }
            }
        }

        flushCurrent()
        blocks
    }.getOrElse { emptyList() }
}

private fun parseLintConfig(context: Context): FlipperLintConfig {
    return runCatching {
        val raw = context.assets.open("$DB_ROOT/.fff-ir-lint.json").bufferedReader().use { it.readText() }
        val root = JSONObject(raw)
        val nameCheck = root.optJSONObject("name-check") ?: return@runCatching FlipperLintConfig()

        val groupsObj = nameCheck.optJSONObject("\$groups")
        val groups = mutableMapOf<String, List<LintMatcher>>()
        if (groupsObj != null) {
            groupsObj.keys().forEach { groupName ->
                groups[groupName] = parseMatcherArray(groupsObj.optJSONArray(groupName))
            }
        }

        val pathRules = mutableListOf<PathLintRule>()
        nameCheck.keys().forEach { key ->
            if (key.startsWith("$")) return@forEach
            val pathRuleObj = nameCheck.optJSONObject(key) ?: return@forEach
            val canonicalRules = mutableListOf<CanonicalCommandRule>()

            pathRuleObj.keys().forEach { canonicalName ->
                val matcherArray = pathRuleObj.optJSONArray(canonicalName) ?: JSONArray()
                canonicalRules += CanonicalCommandRule(
                    canonicalName = canonicalName,
                    matchers = parseMatcherArray(matcherArray)
                )
            }

            val patterns = key.split(',').map { it.trim() }.filter { it.isNotBlank() }
            if (patterns.isNotEmpty() && canonicalRules.isNotEmpty()) {
                pathRules += PathLintRule(patterns = patterns, canonicalRules = canonicalRules)
            }
        }

        FlipperLintConfig(groups = groups, pathRules = pathRules)
    }.getOrElse { FlipperLintConfig() }
}

private fun parseMatcherArray(array: JSONArray?): List<LintMatcher> {
    if (array == null) return emptyList()
    val result = mutableListOf<LintMatcher>()
    for (i in 0 until array.length()) {
        val raw = array.optString(i).trim()
        if (raw.isBlank()) continue
        result += when {
            raw.startsWith("\$group:") -> {
                LintMatcher.GroupReference(raw.removePrefix("\$group:").trim())
            }
            raw.startsWith("/") && raw.endsWith("/") && raw.length > 2 -> {
                val pattern = raw.substring(1, raw.length - 1)
                runCatching { Regex(pattern, RegexOption.IGNORE_CASE) }
                    .getOrElse { Regex(Regex.escape(pattern), RegexOption.IGNORE_CASE) }
                    .let { LintMatcher.RegexPattern(it) }
            }
            else -> {
                LintMatcher.Literal(raw.lowercase())
            }
        }
    }
    return result
}

private fun resolveUsingLint(
    lintConfig: FlipperLintConfig,
    folderPath: String,
    commandStats: Map<String, Int>
): List<UniversalCommandItem> {
    val relativePath = folderPath.removePrefix("$DB_ROOT/").removePrefix(DB_ROOT)
    if (relativePath.isBlank()) return emptyList()

    val matchingRules = lintConfig.pathRules.filter { rule ->
        rule.patterns.any { pattern -> wildcardPathMatches(pattern, relativePath) }
    }
    if (matchingRules.isEmpty()) return emptyList()

    val resolved = mutableListOf<UniversalCommandItem>()
    val alreadyUsed = mutableSetOf<String>()

    matchingRules.forEach { rule ->
        rule.canonicalRules.forEach { canonical ->
            val best = commandStats.entries
                .asSequence()
                .filterNot { alreadyUsed.contains(it.key) }
                .filter { entry -> matcherSetMatches(entry.key, canonical.matchers, lintConfig.groups) }
                .maxByOrNull { it.value }

            if (best != null) {
                alreadyUsed += best.key
                resolved += UniversalCommandItem(
                    displayLabel = canonical.canonicalName.replace('_', ' ').uppercase(),
                    actualCommand = best.key,
                    profileCoverage = best.value
                )
            }
        }
    }

    return resolved
}

private fun matcherSetMatches(
    command: String,
    matchers: List<LintMatcher>,
    groups: Map<String, List<LintMatcher>>
): Boolean {
    if (matchers.isEmpty()) return false
    return matchers.any { matcherMatches(command, it, groups) }
}

private fun matcherMatches(
    command: String,
    matcher: LintMatcher,
    groups: Map<String, List<LintMatcher>>
): Boolean {
    val lower = command.lowercase()
    return when (matcher) {
        is LintMatcher.Literal -> lower == matcher.value || lower.contains(matcher.value)
        is LintMatcher.RegexPattern -> matcher.pattern.containsMatchIn(command)
        is LintMatcher.GroupReference -> {
            val groupMatchers = groups[matcher.group].orEmpty()
            groupMatchers.any { matcherMatches(command, it, groups) }
        }
    }
}

private fun wildcardPathMatches(pattern: String, path: String): Boolean {
    val regexPattern = buildString {
        append('^')
        pattern.forEach { ch ->
            when (ch) {
                '*' -> append(".*")
                '?' -> append('.')
                '.', '(', ')', '[', ']', '{', '}', '^', '$', '+', '|', '\\' -> {
                    append('\\')
                    append(ch)
                }
                else -> append(ch)
            }
        }
        append('$')
    }
    return Regex(regexPattern, RegexOption.IGNORE_CASE).matches(path)
}

private fun normalizeDisplayName(raw: String): String {
    return raw
        .replace('_', ' ')
        .replace('-', ' ')
        .uppercase()
}

// ── Import / Export ───────────────────────────────────────────────────────────

fun exportRemotesToJson(remotes: List<SavedRemote>): String {
    val array = JSONArray()
    for (remote in remotes) {
        val obj = JSONObject()
        obj.put("name", remote.name)
        obj.put("profilePath", remote.profilePath)
        obj.put("sourceProfilePath", remote.sourceProfilePath ?: "")
        obj.put("favorite", remote.favorite)
        val buttonsArray = JSONArray()
        for (button in remote.buttons) {
            val b = JSONObject()
            b.put("label", button.label)
            b.put("code", button.code)
            buttonsArray.put(b)
        }
        obj.put("buttons", buttonsArray)
        array.put(obj)
    }
    return array.toString(2)
}

fun importRemotesFromJson(json: String): List<SavedRemote> {
    return try {
        val array = JSONArray(json)
        (0 until array.length()).mapNotNull { i ->
            val obj = array.optJSONObject(i) ?: return@mapNotNull null
            val name = obj.optString("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val buttonsArray = obj.optJSONArray("buttons") ?: JSONArray()
            val buttons = (0 until buttonsArray.length()).mapNotNull { j ->
                val b = buttonsArray.optJSONObject(j) ?: return@mapNotNull null
                val label = b.optString("label").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                SavedRemoteButton(label = label, code = b.optString("code"))
            }
            SavedRemote(
                name = name,
                profilePath = obj.optString("profilePath"),
                commands = buttons.map { it.label },
                buttons = buttons,
                sourceProfilePath = obj.optString("sourceProfilePath").takeIf { it.isNotBlank() },
                favorite = obj.optBoolean("favorite", false)
            )
        }
    } catch (_: Exception) {
        emptyList()
    }
}
