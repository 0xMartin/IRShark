package com.m4r71n.irshark.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

private const val PREFS_NAME = "irshark_prefs"
private const val KEY_SAVED_REMOTES = "saved_remotes"
private const val KEY_REMOTE_HISTORY = "remote_history"
private const val REMOTE_DELIMITER = "||"

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
                        iconName = categorySeedFromPath(parts[1].trim()),
                        sourceProfilePath = parts[1].trim().takeIf { it.startsWith("flipper_irdb") },
                        favorite = false
                    )
                }
                parts.size == 2 -> {
                    SavedRemote(
                        name = parts[0].trim(),
                        profilePath = parts[1].trim(),
                        commands = emptyList(),
                        buttons = emptyList(),
                        iconName = categorySeedFromPath(parts[1].trim()),
                        sourceProfilePath = parts[1].trim().takeIf { it.startsWith("flipper_irdb") },
                        favorite = false
                    )
                }
                else -> {
                    SavedRemote(name = row, profilePath = "", commands = emptyList(), buttons = emptyList(), iconName = null, favorite = false)
                }
            }
        }
}

fun saveSavedRemotes(context: Context, remotes: List<SavedRemote>) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val serialized = serializeSavedRemotesJson(remotes)
    prefs.edit().putString(KEY_SAVED_REMOTES, serialized.toString()).apply()
}

fun loadRemoteHistory(context: Context): List<RemoteHistoryEntry> {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val raw = prefs.getString(KEY_REMOTE_HISTORY, "").orEmpty().trim()
    if (raw.isBlank() || !raw.startsWith("[")) return emptyList()

    return runCatching {
        val arr = JSONArray(raw)
        buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val name = obj.optString("name").trim()
                if (name.isBlank()) continue

                val buttons = obj.optJSONArray("buttons")?.let { list ->
                    buildList {
                        for (j in 0 until list.length()) {
                            val button = list.optJSONObject(j) ?: continue
                            val label = button.optString("label").trim()
                            if (label.isBlank()) continue
                            add(
                                SavedRemoteButton(
                                    label = label,
                                    code = button.optString("code").trim()
                                )
                            )
                        }
                    }
                }.orEmpty()

                add(
                    RemoteHistoryEntry(
                        name = name,
                        profilePath = obj.optString("profilePath").trim(),
                        sourceProfilePath = obj.optString("sourceProfilePath").trim().ifBlank { null },
                        iconName = obj.optString("iconName").trim().ifBlank { null },
                        openedAtEpochMs = obj.optLong("openedAtEpochMs", 0L),
                        buttons = buttons
                    )
                )
            }
        }
    }.getOrElse { emptyList() }
}

fun saveRemoteHistory(context: Context, history: List<RemoteHistoryEntry>) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val serialized = JSONArray().apply {
        history.take(100).forEach { entry ->
            put(
                JSONObject().apply {
                    put("name", entry.name)
                    put("profilePath", entry.profilePath)
                    put("sourceProfilePath", entry.sourceProfilePath ?: "")
                    put("iconName", entry.iconName ?: "")
                    put("openedAtEpochMs", entry.openedAtEpochMs)
                    put(
                        "buttons",
                        JSONArray().apply {
                            entry.buttons.forEach { button ->
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
    prefs.edit().putString(KEY_REMOTE_HISTORY, serialized).apply()
}

fun recordRemoteHistory(
    history: List<RemoteHistoryEntry>,
    entry: RemoteHistoryEntry,
    limit: Int = 100
): List<RemoteHistoryEntry> {
    val deduped = history.filterNot { it.stableKey == entry.stableKey }
    return listOf(entry) + deduped.take((limit - 1).coerceAtLeast(0))
}

fun serializeSavedRemotesJson(remotes: List<SavedRemote>): String {
    return JSONArray().apply {
        remotes.forEach { remote ->
            put(
                JSONObject().apply {
                    put("name", remote.name)
                    put("profilePath", remote.profilePath)
                    put("iconName", remote.iconName ?: "")
                    put("sourceProfilePath", remote.sourceProfilePath ?: "")
                    put("favorite", remote.favorite)
                    put("columnCount", remote.columnCount)
                    put("groupByCategory", remote.groupByCategory)
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
                    if (profilePath.startsWith("flipper_irdb")) profilePath else ""
                }.ifBlank { null }
                val iconName = obj.optString("iconName").trim().ifBlank {
                    categorySeedFromPath(sourceProfilePath ?: profilePath)
                }

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
                        iconName = iconName,
                        sourceProfilePath = sourceProfilePath,
                        favorite = obj.optBoolean("favorite", false),
                        columnCount = obj.optInt("columnCount", 2).coerceIn(1, 3),
                        groupByCategory = obj.optBoolean("groupByCategory", true)
                    )
                )
            }
        }
    }.getOrElse { emptyList() }
}

fun exportRemotesToJson(remotes: List<SavedRemote>): String {
    val array = JSONArray()
    for (remote in remotes) {
        val obj = JSONObject()
        obj.put("name", remote.name)
        obj.put("profilePath", remote.profilePath)
        obj.put("iconName", remote.iconName ?: "")
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
    return runCatching {
        val trimmed = json.trim()
        val array = when {
            trimmed.startsWith("[") -> JSONArray(trimmed)
            trimmed.startsWith("{") -> {
                val root = JSONObject(trimmed)
                root.optJSONArray("remotes") ?: JSONArray().apply { put(root) }
            }
            else -> JSONArray()
        }

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
                iconName = obj.optString("iconName").takeIf { it.isNotBlank() }
                    ?: categorySeedFromPath(obj.optString("sourceProfilePath").takeIf { it.isNotBlank() }
                        ?: obj.optString("profilePath")),
                sourceProfilePath = obj.optString("sourceProfilePath").takeIf { it.isNotBlank() },
                favorite = obj.optBoolean("favorite", false)
            )
        }
    }.getOrElse {
        emptyList()
    }
}
