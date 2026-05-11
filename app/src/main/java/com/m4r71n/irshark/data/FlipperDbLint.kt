package com.m4r71n.irshark.data

import org.json.JSONArray
import org.json.JSONObject

private data class OtherCanonicalRule(
    val displayLabel: String,
    val matchers: List<Regex>
)

private val OTHER_CANONICAL_RULES = listOf(
    OtherCanonicalRule("POWER", listOf(Regex("^(power|pwr|on_off|power_toggle)$", RegexOption.IGNORE_CASE))),
    OtherCanonicalRule("POWER ON", listOf(Regex("^(power_on|on|turn_on|poweron)$", RegexOption.IGNORE_CASE))),
    OtherCanonicalRule("POWER OFF", listOf(Regex("^(power_off|off|turn_off|poweroff)$", RegexOption.IGNORE_CASE))),
    OtherCanonicalRule("MUTE", listOf(Regex("^mute(_toggle|_on|_off)?$", RegexOption.IGNORE_CASE))),
    OtherCanonicalRule("VOL+", listOf(Regex("""^(vol(ume)?(\+|_up|_\^)|volume_\^|vol_\+|volume_up)$""", RegexOption.IGNORE_CASE))),
    OtherCanonicalRule("VOL-", listOf(Regex("^(vol(ume)?(-|_down|_v)|volume_v|vol_-|volume_down)$", RegexOption.IGNORE_CASE))),
    OtherCanonicalRule("CH+", listOf(Regex("""^(ch(annel)?(\+|_up|up|_next)|channelup)$""", RegexOption.IGNORE_CASE))),
    OtherCanonicalRule("CH-", listOf(Regex("^(ch(annel)?(-|_down|down|_prev)|ch_prev|channeldown)$", RegexOption.IGNORE_CASE))),
    OtherCanonicalRule("MENU", listOf(Regex("^(menu|setup|set_up|menu_setup)$", RegexOption.IGNORE_CASE))),
    OtherCanonicalRule("ENTER", listOf(Regex("^(enter|ok|select|cursor_enter|select_enter)$", RegexOption.IGNORE_CASE))),
    OtherCanonicalRule("UP", listOf(Regex("^(up|up_arrow|arrow_up|cursor_up)$", RegexOption.IGNORE_CASE))),
    OtherCanonicalRule("DOWN", listOf(Regex("^(down|dn_arrow|down_arrow|arrow_down|cursor_down)$", RegexOption.IGNORE_CASE))),
    OtherCanonicalRule("LEFT", listOf(Regex("^(left|left_arrow|arrow_left|cursor_left)$", RegexOption.IGNORE_CASE))),
    OtherCanonicalRule("RIGHT", listOf(Regex("^(right|right_arrow|arrow_right|cursor_right)$", RegexOption.IGNORE_CASE))),
    OtherCanonicalRule("INFO", listOf(Regex("^(info|display|status|osd)$", RegexOption.IGNORE_CASE))),
    OtherCanonicalRule("BACK", listOf(Regex("^(back|return|recall|last|prev_ch)$", RegexOption.IGNORE_CASE))),
    OtherCanonicalRule("INPUT", listOf(Regex("^(input|source|tv_video|tv_av|input_select)$", RegexOption.IGNORE_CASE))),
    OtherCanonicalRule("GUIDE", listOf(Regex("^(guide|epg|tv_guide)$", RegexOption.IGNORE_CASE))),
    OtherCanonicalRule("PLAY", listOf(Regex("^(play|play_pause|play/pause)$", RegexOption.IGNORE_CASE))),
    OtherCanonicalRule("STOP", listOf(Regex("""^(stop|stop_\[\]|stop1|stop2)$""", RegexOption.IGNORE_CASE))),
    OtherCanonicalRule("PAUSE", listOf(Regex("^(pause|pause_|pause/still)$", RegexOption.IGNORE_CASE)))
)

internal fun parseLintConfig(raw: String): FlipperLintConfig {
    return runCatching {
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

internal fun resolveUsingLint(
    lintConfig: FlipperLintConfig,
    folderPath: String,
    commandStats: Map<String, Int>
): List<UniversalCommandItem> {
    val relativePath = folderPath.removePrefix("flipper_irdb/").removePrefix("flipper_irdb")
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

internal fun resolveOtherDefaults(
    folderPath: String,
    commandStats: Map<String, Int>,
    limit: Int
): List<UniversalCommandItem> {
    if (!(folderPath == UNIVERSAL_OTHER_PATH || folderPath.startsWith("$UNIVERSAL_OTHER_PATH/"))) {
        return emptyList()
    }

    val normalizedStats = commandStats.entries.map { entry ->
        entry.key to normalizeCommandToken(entry.key)
    }

    val resolved = mutableListOf<UniversalCommandItem>()
    OTHER_CANONICAL_RULES.forEach { rule ->
        val matched = normalizedStats
            .filter { (_, normalized) -> rule.matchers.any { it.matches(normalized) } }
            .mapNotNull { (rawKey, _) -> commandStats[rawKey]?.let { rawKey to it } }

        if (matched.isNotEmpty()) {
            val best = matched.maxByOrNull { it.second } ?: return@forEach
            resolved += UniversalCommandItem(
                displayLabel = rule.displayLabel,
                actualCommand = best.first,
                profileCoverage = matched.sumOf { it.second }
            )
        }
    }

    return resolved
        .sortedByDescending { it.profileCoverage }
        .take(limit)
}

private fun normalizeCommandToken(raw: String): String {
    return raw.trim()
        .lowercase()
        .replace(' ', '_')
        .replace('-', '_')
        .replace('/', '_')
        .replace(Regex("_+"), "_")
        .trim('_')
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
