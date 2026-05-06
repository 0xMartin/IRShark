package com.m4r71n.irshark.data

import android.content.Context
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

private const val DB_ROOT = "flipper_irdb"
private const val IR_PLUS_ROOT = "$DB_ROOT/_Converted_/IR_Plus"
private const val IR_PLUS_PARENT_PATH = "$DB_ROOT/Other/IR Plus"
private const val PRONTO_ROOT = "$DB_ROOT/_Converted_/Pronto"
private const val PRONTO_PARENT_PATH = "$DB_ROOT/Other/Pronto"
private const val CSV_ROOT = "$DB_ROOT/_Converted_/CSV"
private const val CSV_PARENT_PATH = "$DB_ROOT/Other/CSV"
private const val PREFS_NAME = "irshark_prefs"
private const val KEY_SAVED_REMOTES = "saved_remotes"
private const val KEY_REMOTE_HISTORY = "remote_history"
private const val REMOTE_DELIMITER = "||"
private const val DB_INDEX_CACHE_FILE_PREFIX = "db_index_cache"
private const val DB_INDEX_CACHE_VERSION = 5
private const val DOWNLOADED_DB_BASE_DIR = "flipper_irdb_downloaded"
private const val IR_CODE_BLOCK_CACHE_LIMIT = 256
private const val UNIQUE_PAYLOAD_CACHE_LIMIT = 256
private const val UNSORTED_DISPLAY_NAME = "Unsorted"

private data class ConvertedDbSource(
    val rootPath: String,
    val parentPath: String,
    val rootName: String
)

private val CONVERTED_DB_SOURCES = listOf(
    ConvertedDbSource(
        rootPath = IR_PLUS_ROOT,
        parentPath = IR_PLUS_PARENT_PATH,
        rootName = "IR Plus"
    ),
    ConvertedDbSource(
        rootPath = PRONTO_ROOT,
        parentPath = PRONTO_PARENT_PATH,
        rootName = "Pronto"
    ),
    ConvertedDbSource(
        rootPath = CSV_ROOT,
        parentPath = CSV_PARENT_PATH,
        rootName = "CSV"
    )
)

private val irCodeBlockCacheLock = Any()
private val irCodeBlockCache = object : LinkedHashMap<String, List<ParsedIrCodeBlock>>(IR_CODE_BLOCK_CACHE_LIMIT, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<ParsedIrCodeBlock>>?): Boolean {
        return size > IR_CODE_BLOCK_CACHE_LIMIT
    }
}

private val uniquePayloadCacheLock = Any()
private val uniquePayloadCache = object : LinkedHashMap<String, List<String>>(UNIQUE_PAYLOAD_CACHE_LIMIT, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<String>>?): Boolean {
        return size > UNIQUE_PAYLOAD_CACHE_LIMIT
    }
}

private const val DEDUP_COMMANDS_CACHE_LIMIT = 64
private val dedupCommandsCacheLock = Any()
private val dedupCommandsCache = object : LinkedHashMap<String, List<UniversalCommandItem>>(DEDUP_COMMANDS_CACHE_LIMIT, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<UniversalCommandItem>>?): Boolean {
        return size > DEDUP_COMMANDS_CACHE_LIMIT
    }
}

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
    val columnCount: Int = 2
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

private data class DbProfileDescriptor(
    val path: String,
    val parentPath: String,
    val displayName: String,
    val signature: String
)

private data class DbLayoutSnapshot(
    val folders: Map<String, List<String>>,
    val profileDescriptors: List<DbProfileDescriptor>
)

private data class DbIndexCacheSnapshot(
    val index: FlipperDbIndex,
    val signaturesByPath: Map<String, String>
)

private const val UNIVERSAL_OTHER_PATH = "$DB_ROOT/Other"

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

suspend fun loadFlipperDbIndex(
    context: Context,
    onProgress: ((DbLoadProgress) -> Unit)? = null
): FlipperDbIndex {
    return withContext(Dispatchers.IO) {
        runCatching {
            val sourceInfo = resolveDbStorage(context)
            val cacheKey = sourceInfo.cacheKey
            val lintConfig = parseLintConfig(context)
            val cachedSnapshot = loadDbIndexCache(context, cacheKey)

            if (cachedSnapshot != null) {
                val missingConvertedSources = CONVERTED_DB_SOURCES.filter { source ->
                    isDbDirectory(context, source.rootPath) &&
                        cachedSnapshot.index.profiles.none { it.path.startsWith("${source.rootPath}/") }
                }
                if (missingConvertedSources.isEmpty()) {
                    val cached = cachedSnapshot.index.copy(
                        lintConfig = lintConfig,
                        status = "Loaded ${cachedSnapshot.index.totalProfiles} profiles (cached)"
                    )
                    if (onProgress != null) {
                        withContext(Dispatchers.Main) {
                            onProgress(
                                DbLoadProgress(
                                    loadedFiles = cached.totalProfiles,
                                    totalFiles = cached.totalProfiles
                                )
                            )
                        }
                    }
                    return@runCatching cached
                }
            }

            val layout = collectDbLayout(context)

            val totalFiles = layout.profileDescriptors.size
            var loadedFiles = 0
            var reusedProfiles = 0
            var reparsedProfiles = 0

            if (onProgress != null) {
                withContext(Dispatchers.Main) {
                    onProgress(DbLoadProgress(loadedFiles = 0, totalFiles = totalFiles))
                }
            }

            val cachedProfilesByPath = cachedSnapshot?.index?.profiles?.associateBy { it.path }.orEmpty()
            val cachedSignaturesByPath = cachedSnapshot?.signaturesByPath.orEmpty()
            val currentSignaturesByPath = linkedMapOf<String, String>()
            val allProfiles = ArrayList<FlipperProfile>(totalFiles)

            layout.profileDescriptors.forEach { descriptor ->
                currentSignaturesByPath[descriptor.path] = descriptor.signature

                val cached = cachedProfilesByPath[descriptor.path]
                val canReuse = cached != null && cachedSignaturesByPath[descriptor.path] == descriptor.signature
                val profile = if (canReuse) {
                    reusedProfiles += 1
                    cached.copy(parentPath = descriptor.parentPath, name = descriptor.displayName)
                } else {
                    reparsedProfiles += 1
                    FlipperProfile(
                        path = descriptor.path,
                        parentPath = descriptor.parentPath,
                        name = descriptor.displayName,
                        commands = parseIrCommands(context, descriptor.path)
                    )
                }

                allProfiles += profile
                loadedFiles += 1
                if (onProgress != null && (loadedFiles == totalFiles || loadedFiles % 20 == 0)) {
                    withContext(Dispatchers.Main) {
                        onProgress(DbLoadProgress(loadedFiles = loadedFiles, totalFiles = totalFiles))
                    }
                }
            }

            val profilesByFolder = allProfiles.groupByTo(linkedMapOf()) { it.parentPath }
            val folderMap = layout.folders

            val freshIndex = FlipperDbIndex(
                totalProfiles = allProfiles.size,
                folders = folderMap,
                profilesByFolder = profilesByFolder,
                profiles = allProfiles,
                lintConfig = lintConfig,
                status = if (cachedSnapshot != null) {
                    "Loaded ${allProfiles.size} profiles (${sourceInfo.label}, reused $reusedProfiles, re-parsed $reparsedProfiles)"
                } else {
                    "Loaded ${allProfiles.size} profiles (${sourceInfo.label})"
                }
            )

            saveDbIndexCache(
                context = context,
                index = freshIndex,
                cacheKey = cacheKey,
                signaturesByPath = currentSignaturesByPath
            )
            freshIndex
        }.getOrElse {
            FlipperDbIndex(status = "Flipper-IRDB unavailable")
        }
    }
}

fun isDownloadedDbAvailable(context: Context): Boolean {
    val dbRootDir = File(downloadedDbBaseDir(context), DB_ROOT)
    if (!dbRootDir.exists() || !dbRootDir.isDirectory) return false
    return dbRootDir.walkTopDown().any { it.isFile && it.name.endsWith(".ir", ignoreCase = true) }
}

fun resolveEffectiveDbSource(context: Context): DbSourceType {
    val settings = loadAppSettings(context)
    return if (settings.preferDownloadedDb && isDownloadedDbAvailable(context)) {
        DbSourceType.DOWNLOADED
    } else {
        DbSourceType.DEFAULT
    }
}

fun bundledDbVersionLabel(): String = "Bundled (assets)"

suspend fun importFlipperDatabaseFromZip(
    context: Context,
    inputStream: InputStream
): FlipperDbUpdateResult {
    return withContext(Dispatchers.IO) {
        runCatching {
            val tempExtractDir = File(context.cacheDir, "db_import_extract_${System.currentTimeMillis()}")
            val stagingBaseDir = File(context.cacheDir, "db_import_stage_${System.currentTimeMillis()}")
            tempExtractDir.deleteRecursively()
            stagingBaseDir.deleteRecursively()
            tempExtractDir.mkdirs()
            stagingBaseDir.mkdirs()

            extractZipFromStream(inputStream, tempExtractDir)

            val extractedRoot = tempExtractDir.listFiles()?.firstOrNull { it.isDirectory } ?: tempExtractDir
            val sourceDir = File(extractedRoot, DB_ROOT).takeIf { it.exists() && it.isDirectory } ?: extractedRoot

            val stagedDbRoot = File(stagingBaseDir, DB_ROOT)
            stagedDbRoot.mkdirs()
            sourceDir.listFiles().orEmpty()
                .filterNot { it.name.equals(".git", ignoreCase = true) }
                .forEach { child ->
                    copyRecursively(child, File(stagedDbRoot, child.name))
                }

            if (!stagedDbRoot.walkTopDown().any { it.isFile && it.name.endsWith(".ir", ignoreCase = true) }) {
                tempExtractDir.deleteRecursively()
                stagingBaseDir.deleteRecursively()
                return@runCatching FlipperDbUpdateResult(
                    success = false,
                    updated = false,
                    latestTag = null,
                    message = "ZIP does not contain IR files – check that you selected a valid Flipper IRDB archive"
                )
            }

            val targetBaseDir = downloadedDbBaseDir(context)
            targetBaseDir.deleteRecursively()
            stagingBaseDir.copyRecursively(targetBaseDir, overwrite = true)
            stagingBaseDir.deleteRecursively()
            tempExtractDir.deleteRecursively()
            clearDbIndexCaches(context)

            val importLabel = "imported-${System.currentTimeMillis() / 1000}"
            FlipperDbUpdateResult(
                success = true,
                updated = true,
                latestTag = importLabel,
                message = "Database imported successfully"
            )
        }.getOrElse { err ->
            FlipperDbUpdateResult(
                success = false,
                updated = false,
                latestTag = null,
                message = err.message ?: "Import failed"
            )
        }
    }
}

fun profilesUnderPath(dbIndex: FlipperDbIndex, folderPath: String): List<FlipperProfile> {
    val prefix = "$folderPath/"
    return dbIndex.profiles.filter { it.parentPath == folderPath || it.path.startsWith(prefix) }
}

fun convertedManufacturersForUniversal(dbIndex: FlipperDbIndex): List<String> {
    return dbIndex.profiles
        .asSequence()
        .filter { isConvertedProfilePath(it.path) }
        .mapNotNull { universalManufacturerFromProfileName(it.name) }
        .distinct()
        .sortedBy { it.lowercase() }
        .toList()
}

private fun profilesForUniversalPath(
    dbIndex: FlipperDbIndex,
    folderPath: String,
    includeConverted: Boolean
): List<FlipperProfile> {
    if (folderPath == UNIVERSAL_OTHER_PATH) {
        if (!includeConverted) return emptyList()
        return dbIndex.profiles.filter { isConvertedProfilePath(it.path) }
    }

    if (folderPath.startsWith("$UNIVERSAL_OTHER_PATH/")) {
        if (!includeConverted) return emptyList()
        val manufacturer = folderPath.substringAfter("$UNIVERSAL_OTHER_PATH/").trim()
        if (manufacturer.isBlank()) return emptyList()
        return dbIndex.profiles.filter { profile ->
            isConvertedProfilePath(profile.path) &&
                universalManufacturerFromProfileName(profile.name)?.equals(manufacturer, ignoreCase = true) == true
        }
    }

    val scoped = profilesUnderPath(dbIndex, folderPath)
    return if (includeConverted) scoped else scoped.filterNot { isConvertedProfilePath(it.path) }
}

private fun isConvertedProfilePath(path: String): Boolean {
    return path.startsWith("$DB_ROOT/_Converted_/")
}

private fun universalManufacturerFromProfileName(profileName: String): String? {
    val normalized = profileName.trim().replace(Regex("\\s+"), " ")
    if (normalized.isBlank()) return null
    return normalized.substringBefore(' ').ifBlank { null }
}

fun commandStatsForPath(dbIndex: FlipperDbIndex, folderPath: String): Map<String, Int> {
    return commandStatsForPath(dbIndex, folderPath, includeConverted = true)
}

fun commandStatsForPath(dbIndex: FlipperDbIndex, folderPath: String, includeConverted: Boolean): Map<String, Int> {
    val stats = linkedMapOf<String, Int>()
    profilesForUniversalPath(dbIndex, folderPath, includeConverted).forEach { profile ->
        profile.commands.forEach { command ->
            stats[command] = (stats[command] ?: 0) + 1
        }
    }
    return stats
}

fun resolveUniversalCommandsForPath(
    dbIndex: FlipperDbIndex,
    folderPath: String,
    includeConverted: Boolean = true,
    limit: Int = 18
): List<UniversalCommandItem> {
    val stats = commandStatsForPath(dbIndex, folderPath, includeConverted)
    if (stats.isEmpty()) return emptyList()

    val lintResolved = resolveUsingLint(
        lintConfig = dbIndex.lintConfig,
        folderPath = folderPath,
        commandStats = stats
    )

    if (lintResolved.isNotEmpty()) {
        return lintResolved.take(limit)
    }

    val otherDefaults = resolveOtherDefaults(folderPath, stats, limit)
    if (otherDefaults.isNotEmpty()) {
        return otherDefaults
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

private fun resolveOtherDefaults(
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

fun countProfilesForCommand(dbIndex: FlipperDbIndex, folderPath: String, command: String): Int {
    return countProfilesForCommand(dbIndex, folderPath, command, includeConverted = true)
}

fun countProfilesForCommand(dbIndex: FlipperDbIndex, folderPath: String, command: String, includeConverted: Boolean): Int {
    return profilesForUniversalPath(dbIndex, folderPath, includeConverted).count { profile ->
        profile.commands.any { it.equals(command, ignoreCase = true) }
    }
}

/**
 * Returns the ordered list of profiles under [folderPath] that contain [command].
 * The order matches the iteration order used by [countProfilesForCommand].
 */
fun profilesForCommand(dbIndex: FlipperDbIndex, folderPath: String, command: String): List<FlipperProfile> {
    return profilesForCommand(dbIndex, folderPath, command, includeConverted = true)
}

fun profilesForCommand(
    dbIndex: FlipperDbIndex,
    folderPath: String,
    command: String,
    includeConverted: Boolean
): List<FlipperProfile> {
    return profilesForUniversalPath(dbIndex, folderPath, includeConverted).filter { profile ->
        profile.commands.any { it.equals(command, ignoreCase = true) }
    }
}

/**
 * Returns a deduplicated list of IR payload strings for [command] across all matching
 * profiles under [folderPath]. Profiles whose payload is byte-for-byte identical to a
 * previously seen one are skipped, so toggling TVs ON then OFF is avoided.
 */
suspend fun getUniquePayloadsForCommand(
    context: android.content.Context,
    dbIndex: FlipperDbIndex,
    folderPath: String,
    command: String,
    includeConverted: Boolean = true
): List<String> {
    return withContext(Dispatchers.Default) {
        val cacheKey = buildString {
            append(resolveDbStorage(context).cacheKey)
            append('|')
            append(folderPath)
            append('|')
            append(includeConverted)
            append('|')
            append(command.lowercase())
        }

        synchronized(uniquePayloadCacheLock) {
            uniquePayloadCache[cacheKey]?.let { return@withContext it }
        }

        val payloads = profilesForCommand(dbIndex, folderPath, command, includeConverted)
            .mapNotNull { getIrCodePayload(context, it.path, command) }
            .distinctBy { it.trim() }

        synchronized(uniquePayloadCacheLock) {
            uniquePayloadCache[cacheKey] = payloads
        }
        payloads
    }
}

/**
 * Like [resolveUniversalCommandsForPath] but replaces each item's [UniversalCommandItem.profileCoverage]
 * with the number of *unique* IR payloads for that command (requires IO to read assets).
 * Results are cached per (folderPath, includeConverted) pair (LRU, up to 64 entries).
 * Call this off the main thread.
 */
suspend fun resolveUniversalCommandsWithDedup(
    context: android.content.Context,
    dbIndex: FlipperDbIndex,
    folderPath: String,
    includeConverted: Boolean = true,
    limit: Int = 18
): List<UniversalCommandItem> {
    return withContext(Dispatchers.Default) {
        // Build cache key from folderPath and includeConverted flag
        val cacheKey = "$folderPath|$includeConverted"

        // Check cache first (thread-safe)
        synchronized(dedupCommandsCacheLock) {
            dedupCommandsCache[cacheKey]?.let { return@withContext it }
        }

        val base = resolveUniversalCommandsForPath(dbIndex, folderPath, includeConverted, limit)
        if (base.isEmpty()) return@withContext emptyList()

        val targetAliases = linkedMapOf<String, String>()
        val targetNormalized = linkedMapOf<String, String>()
        val uniquePayloads = linkedMapOf<String, LinkedHashSet<String>>()

        base.forEach { item ->
            val actual = item.actualCommand
            val normalized = normalizeDisplayName(actual).lowercase()
            targetAliases.putIfAbsent(actual.lowercase(), actual)
            targetAliases.putIfAbsent(normalized, actual)
            targetNormalized[actual] = normalized
            uniquePayloads[actual] = linkedSetOf()
        }

        profilesForUniversalPath(dbIndex, folderPath, includeConverted).forEach { profile ->
            val wantedTargets = profile.commands
                .mapNotNull { cmd ->
                    val byRaw = targetAliases[cmd.lowercase()]
                    byRaw ?: targetAliases[normalizeDisplayName(cmd).lowercase()]
                }
                .distinct()
            if (wantedTargets.isEmpty()) return@forEach

            val payloadByName = parseIrCodeBlocks(context, profile.path)
                .associate { block ->
                    normalizeDisplayName(block.displayName).lowercase() to serializeIrPayload(block.fields).trim()
                }

            wantedTargets.forEach { target ->
                val key = targetNormalized[target] ?: return@forEach
                val payload = payloadByName[key] ?: return@forEach
                uniquePayloads[target]?.add(payload)
            }
        }

        val result = base.map { item ->
            item.copy(profileCoverage = uniquePayloads[item.actualCommand]?.size ?: 0)
        }

        // Store in cache (thread-safe)
        synchronized(dedupCommandsCacheLock) {
            dedupCommandsCache[cacheKey] = result
        }

        result
    }
}

/**
 * Reads a specific profile asset and returns the raw key=value payload string
 * for the given [commandName], ready to pass to [transmitIrCode].
 */
private fun getIrCodePayload(context: android.content.Context, profilePath: String, commandName: String): String? {
    val normalized = normalizeDisplayName(commandName)
    val block = parseIrCodeBlocks(context, profilePath).firstOrNull {
        it.displayName.equals(normalized, ignoreCase = true) ||
        it.displayName.equals(commandName, ignoreCase = true)
    } ?: return null
    return serializeIrPayload(block.fields)
}

private fun serializeIrPayload(fields: Map<String, String>): String {
    return fields.entries.joinToString("; ") { (k, v) -> "$k=$v" }
}

fun parentPath(path: String): String? {
    if (path == DB_ROOT) return null
    val slash = path.lastIndexOf('/')
    if (slash <= 0) return DB_ROOT
    return path.substring(0, slash)
}

fun prettyName(path: String): String {
    val name = path.substringAfterLast('/').ifBlank { path }
    return prettifyDisplaySegment(name)
}

fun prettyPath(path: String): String {
    return path
        .removePrefix("$DB_ROOT/")
        .removePrefix(DB_ROOT)
        .ifBlank { "Root" }
        .split('/')
        .joinToString(" ") { segment -> prettifyDisplaySegment(segment) }
}

fun prettyPathWithChevron(path: String): String {
    val normalized = path
        .removePrefix("$DB_ROOT/")
        .removePrefix(DB_ROOT)

    if (normalized.isBlank()) return "Root"

    return normalized
        .split('/')
        .joinToString(" > ") { segment -> prettifyDisplaySegment(segment) }
}

private fun prettifyDisplaySegment(segment: String): String {
    val display = segment.replace('_', ' ')
    return if (display.equals("Other", ignoreCase = true)) UNSORTED_DISPLAY_NAME else display
}

fun dbRootPath(): String = DB_ROOT

fun categorySeedFromPath(path: String?): String? {
    val raw = path?.trim().orEmpty()
    if (raw.isBlank()) return null
    return raw
        .substringAfter("$DB_ROOT/", "")
        .substringBefore('/')
        .ifBlank { null }
}

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
                        iconName = categorySeedFromPath(parts[1].trim()),
                        sourceProfilePath = parts[1].trim().takeIf { it.startsWith(DB_ROOT) },
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
                        columnCount = obj.optInt("columnCount", 2).coerceIn(1, 3)
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
            val frequency = block.fields["frequency"].orEmpty()
            val protocol = block.fields["protocol"].orEmpty()
            val address = block.fields["address"].orEmpty()
            val command = block.fields["command"].orEmpty()

            val codeValue = when {
                data.isNotBlank() -> {
                    listOf(
                        "type=raw",
                        "frequency=$frequency",
                        "data=$data"
                    ).joinToString("; ")
                }
                protocol.isNotBlank() || address.isNotBlank() || command.isNotBlank() -> {
                    listOf(
                        "type=parsed",
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
    val cacheKey = "${resolveDbStorage(context).cacheKey}:$assetPath"
    synchronized(irCodeBlockCacheLock) {
        irCodeBlockCache[cacheKey]?.let { return it }
    }

    val parsedBlocks = runCatching {
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

        openDbInputStream(context, assetPath).bufferedReader().useLines { lines ->
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

    synchronized(irCodeBlockCacheLock) {
        irCodeBlockCache[cacheKey] = parsedBlocks
    }
    return parsedBlocks
}

private fun convertedDisplayName(assetPath: String, rootName: String): String {
    val fileName = assetPath.substringAfterLast('/').removeSuffix(".ir").replace('_', ' ').trim()
    val folderName = assetPath.substringBeforeLast('/', "")
        .substringAfterLast('/')
        .replace('_', ' ')
        .trim()

    val base = when {
        folderName.isBlank() -> fileName
        folderName.equals(rootName, ignoreCase = true) -> fileName
        else -> "$folderName $fileName"
    }
    return base.replace(Regex("\\s+"), " ").trim()
}

private fun collectDbLayout(context: Context): DbLayoutSnapshot {
    val folders = linkedMapOf<String, MutableList<String>>()
    val descriptors = mutableListOf<DbProfileDescriptor>()

    fun walkRegular(path: String) {
        val children = listDbChildren(context, path)
        folders.putIfAbsent(path, mutableListOf())

        for (child in children) {
            val childPath = "$path/$child"
            if (childPath == "$DB_ROOT/_Converted_") continue

            if (child.endsWith(".ir", ignoreCase = true)) {
                descriptors += DbProfileDescriptor(
                    path = childPath,
                    parentPath = path,
                    displayName = child.removeSuffix(".ir").replace('_', ' ').trim(),
                    signature = fileSignature(context, childPath)
                )
            } else if (isDbDirectory(context, childPath)) {
                folders[path]?.add(childPath)
                walkRegular(childPath)
            }
        }
    }

    fun walkConverted(path: String, source: ConvertedDbSource) {
        val children = listDbChildren(context, path)
        for (child in children) {
            val childPath = "$path/$child"
            if (child.endsWith(".ir", ignoreCase = true)) {
                descriptors += DbProfileDescriptor(
                    path = childPath,
                    parentPath = source.parentPath,
                    displayName = convertedDisplayName(childPath, source.rootName),
                    signature = fileSignature(context, childPath)
                )
            } else if (isDbDirectory(context, childPath)) {
                walkConverted(childPath, source)
            }
        }
    }

    walkRegular(DB_ROOT)
    CONVERTED_DB_SOURCES.forEach { source ->
        if (isDbDirectory(context, source.rootPath)) {
            walkConverted(source.rootPath, source)
        }
    }

    return DbLayoutSnapshot(
        folders = folders.mapValues { (_, value) -> value.toList() },
        profileDescriptors = descriptors
    )
}

private fun fileSignature(context: Context, path: String): String {
    return when (resolveDbStorage(context).type) {
        DbSourceType.DOWNLOADED -> {
            val file = logicalPathToDownloadedFile(context, path)
            if (!file.exists() || !file.isFile) {
                "missing"
            } else {
                "${file.length()}:${file.lastModified()}"
            }
        }
        DbSourceType.DEFAULT -> {
            // Asset timestamps are unavailable; cache key already includes app install revision.
            "asset"
        }
    }
}

private fun saveDbIndexCache(
    context: Context,
    index: FlipperDbIndex,
    cacheKey: String,
    signaturesByPath: Map<String, String>
) {
    runCatching {
        val file = File(context.filesDir, dbIndexCacheFileName(cacheKey))
        val root = JSONObject().apply {
            put("version", DB_INDEX_CACHE_VERSION)
            put("totalProfiles", index.totalProfiles)

            val foldersObj = JSONObject()
            index.folders.forEach { (path, children) ->
                val arr = JSONArray()
                children.forEach { arr.put(it) }
                foldersObj.put(path, arr)
            }
            put("folders", foldersObj)

            val profilesArr = JSONArray()
            index.profiles.forEach { profile ->
                val cmdArr = JSONArray()
                profile.commands.forEach { cmdArr.put(it) }
                profilesArr.put(
                    JSONObject().apply {
                        put("path", profile.path)
                        put("parentPath", profile.parentPath)
                        put("name", profile.name)
                        put("commands", cmdArr)
                    }
                )
            }
            put("profiles", profilesArr)

            val signaturesObj = JSONObject()
            signaturesByPath.forEach { (path, signature) ->
                signaturesObj.put(path, signature)
            }
            put("signatures", signaturesObj)
        }
        file.writeText(root.toString(), Charsets.UTF_8)
    }
}

private fun loadDbIndexCache(context: Context, cacheKey: String): DbIndexCacheSnapshot? {
    return runCatching {
        val file = File(context.filesDir, dbIndexCacheFileName(cacheKey))
        if (!file.exists()) return null
        val raw = file.readText(Charsets.UTF_8)
        val root = JSONObject(raw)
        if (root.optInt("version", 0) != DB_INDEX_CACHE_VERSION) return null

        val folders = mutableMapOf<String, List<String>>()
        val foldersObj = root.optJSONObject("folders") ?: JSONObject()
        foldersObj.keys().forEach { key ->
            val arr = foldersObj.optJSONArray(key) ?: JSONArray()
            val children = buildList {
                for (i in 0 until arr.length()) {
                    val item = arr.optString(i).trim()
                    if (item.isNotBlank()) add(item)
                }
            }
            folders[key] = children
        }

        val profiles = mutableListOf<FlipperProfile>()
        val profilesArr = root.optJSONArray("profiles") ?: JSONArray()
        for (i in 0 until profilesArr.length()) {
            val obj = profilesArr.optJSONObject(i) ?: continue
            val path = obj.optString("path").trim()
            val parentPath = obj.optString("parentPath").trim()
            val name = obj.optString("name").trim()
            if (path.isBlank() || parentPath.isBlank() || name.isBlank()) continue
            val cmdArr = obj.optJSONArray("commands") ?: JSONArray()
            val commands = buildList {
                for (j in 0 until cmdArr.length()) {
                    val cmd = cmdArr.optString(j).trim()
                    if (cmd.isNotBlank()) add(cmd)
                }
            }
            profiles += FlipperProfile(
                path = path,
                parentPath = parentPath,
                name = name,
                commands = commands
            )
        }

        if (profiles.isEmpty()) return null

        val signaturesByPath = mutableMapOf<String, String>()
        val signaturesObj = root.optJSONObject("signatures") ?: JSONObject()
        signaturesObj.keys().forEach { path ->
            val signature = signaturesObj.optString(path).trim()
            if (path.isNotBlank() && signature.isNotBlank()) {
                signaturesByPath[path] = signature
            }
        }

        val profilesByFolder = profiles.groupBy { it.parentPath }
        val index = FlipperDbIndex(
            totalProfiles = root.optInt("totalProfiles", profiles.size),
            folders = folders,
            profilesByFolder = profilesByFolder,
            profiles = profiles,
            status = "Loaded ${profiles.size} profiles (cached)"
        )
        DbIndexCacheSnapshot(index = index, signaturesByPath = signaturesByPath)
    }.getOrNull()
}

private fun parseLintConfig(context: Context): FlipperLintConfig {
    return runCatching {
        val raw = openDbInputStream(context, "$DB_ROOT/.fff-ir-lint.json").bufferedReader().use { it.readText() }
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

private data class DbStorageInfo(
    val type: DbSourceType,
    val label: String,
    val cacheKey: String
)

private fun resolveDbStorage(context: Context): DbStorageInfo {
    val settings = loadAppSettings(context)
    val hasDownloaded = isDownloadedDbAvailable(context)
    val appRevision = appInstallRevision(context)
    return if (settings.preferDownloadedDb && hasDownloaded) {
        val tagSuffix = settings.downloadedDbTag?.ifBlank { "unknown" } ?: "unknown"
        DbStorageInfo(
            type = DbSourceType.DOWNLOADED,
            label = "downloaded",
            cacheKey = "downloaded_${tagSuffix}_$appRevision"
        )
    } else {
        DbStorageInfo(
            type = DbSourceType.DEFAULT,
            label = "assets",
            cacheKey = "assets_$appRevision"
        )
    }
}

private fun appInstallRevision(context: Context): Long {
    return runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
    }.getOrDefault(0L)
}

private fun dbIndexCacheFileName(cacheKey: String): String {
    val clean = cacheKey.replace(Regex("[^A-Za-z0-9._-]"), "_")
    return "${DB_INDEX_CACHE_FILE_PREFIX}_${clean}_v${DB_INDEX_CACHE_VERSION}.json"
}

private fun clearDbIndexCaches(context: Context) {
    context.filesDir.listFiles().orEmpty()
        .filter { it.name.startsWith(DB_INDEX_CACHE_FILE_PREFIX) }
        .forEach { it.delete() }

    synchronized(irCodeBlockCacheLock) {
        irCodeBlockCache.clear()
    }

    synchronized(uniquePayloadCacheLock) {
        uniquePayloadCache.clear()
    }
}

private fun downloadedDbBaseDir(context: Context): File {
    return File(context.filesDir, DOWNLOADED_DB_BASE_DIR)
}

private fun logicalPathToDownloadedFile(context: Context, path: String): File {
    val relative = path.removePrefix("$DB_ROOT/").removePrefix(DB_ROOT)
    val base = File(downloadedDbBaseDir(context), DB_ROOT)
    return if (relative.isBlank()) base else File(base, relative)
}

private fun listDbChildren(context: Context, path: String): List<String> {
    return when (resolveDbStorage(context).type) {
        DbSourceType.DEFAULT -> context.assets.list(path)?.sorted().orEmpty()
        DbSourceType.DOWNLOADED -> {
            val dir = logicalPathToDownloadedFile(context, path)
            if (dir.exists() && dir.isDirectory) {
                dir.list()?.sorted().orEmpty()
            } else if (isBundledConvertedPath(path)) {
                // Keep bundled converted profiles discoverable even when downloaded DB omits _Converted_.
                context.assets.list(path)?.sorted().orEmpty()
            } else {
                emptyList()
            }
        }
    }
}

private fun isDbDirectory(context: Context, path: String): Boolean {
    return when (resolveDbStorage(context).type) {
        DbSourceType.DEFAULT -> context.assets.list(path).orEmpty().isNotEmpty()
        DbSourceType.DOWNLOADED -> {
            val file = logicalPathToDownloadedFile(context, path)
            if (file.exists() && file.isDirectory) {
                true
            } else {
                isBundledConvertedPath(path) && context.assets.list(path).orEmpty().isNotEmpty()
            }
        }
    }
}

private fun openDbInputStream(context: Context, path: String): InputStream {
    return when (resolveDbStorage(context).type) {
        DbSourceType.DEFAULT -> context.assets.open(path)
        DbSourceType.DOWNLOADED -> {
            val file = logicalPathToDownloadedFile(context, path)
            if (file.exists() && file.isFile) {
                file.inputStream()
            } else if (isBundledConvertedPath(path)) {
                context.assets.open(path)
            } else {
                file.inputStream()
            }
        }
    }
}

private fun isBundledConvertedPath(path: String): Boolean {
    return CONVERTED_DB_SOURCES.any { source ->
        path == source.rootPath || path.startsWith("${source.rootPath}/")
    }
}

private fun extractZipFromStream(inputStream: InputStream, outputDir: File) {
    ZipInputStream(inputStream.buffered()).use { zipStream ->
        while (true) {
            val entry = zipStream.nextEntry ?: break
            val relative = entry.name.substringAfter('/', entry.name).trim()
            if (relative.isBlank()) {
                zipStream.closeEntry()
                continue
            }
            val outFile = File(outputDir, relative)
            if (entry.isDirectory) {
                outFile.mkdirs()
            } else {
                outFile.parentFile?.mkdirs()
                outFile.outputStream().use { out ->
                    zipStream.copyTo(out)
                }
            }
            zipStream.closeEntry()
        }
    }
}

private fun copyRecursively(source: File, target: File) {
    if (source.isDirectory) {
        target.mkdirs()
        source.listFiles().orEmpty().forEach { child ->
            copyRecursively(child, File(target, child.name))
        }
    } else {
        target.parentFile?.mkdirs()
        source.copyTo(target, overwrite = true)
    }
}

// ── Import / Export ───────────────────────────────────────────────────────────

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
