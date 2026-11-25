package com.algermusic.player.data.local

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.algermusic.player.model.AlbumItem
import com.algermusic.player.model.ArtistItem
import com.algermusic.player.model.AudioFormatSupport
import com.algermusic.player.model.LocalFolderNode
import com.algermusic.player.model.MusicQualityLevel
import com.algermusic.player.model.SongItem
import com.kyant.taglib.Picture
import com.kyant.taglib.PropertyMap
import com.kyant.taglib.TagLib
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.Charset
import java.security.MessageDigest

/**
 * 1:1 复刻 WinUI LocalMusicScannerService.cs：
 * - MediaStore + SAF 目录树扫描本地音频
 * - TagLib 读取精确元数据、内嵌歌词与封面
 * - GB18030 / Latin-1 中文乱码自动修复
 * - 最长前缀匹配构建多根目录文件夹树与展开扁平化
 */
class LocalMusicScannerService(
    private val context: Context,
    private val settingsRepo: SettingsRepository
) {
    companion object {
        val SUPPORTED_EXTENSIONS: Set<String> = AudioFormatSupport.SUPPORTED_EXTENSIONS

        private val LATIN1_SUPPLEMENT_REGEX = Regex("""[\u0080-\u00FF]""")
        private val CJK_UNIFIED_REGEX = Regex("""[\u3400-\u9FFF]""")
        private val GB18030_CHARSET: Charset by lazy {
            runCatching { Charset.forName("GB18030") }.getOrElse { Charset.forName("GBK") }
        }

        fun repairLegacyChineseText(value: String?): String {
            if (value.isNullOrBlank()) return ""
            val normalized = value.replace("\u0000", "").trim()
            if (normalized.isBlank()) return ""
            if (CJK_UNIFIED_REGEX.containsMatchIn(normalized)) {
                return normalized
            }
            if (LATIN1_SUPPLEMENT_REGEX.containsMatchIn(normalized)) {
                runCatching {
                    val rawBytes = normalized.toByteArray(Charsets.ISO_8859_1)
                    val decoded = String(rawBytes, GB18030_CHARSET).replace("\u0000", "").trim()
                    if (!decoded.contains('\uFFFD') && CJK_UNIFIED_REGEX.containsMatchIn(decoded)) {
                        return decoded
                    }
                }
            }
            return normalized
        }

        fun normalizeFolderPath(rawPath: String?): String {
            if (rawPath.isNullOrBlank()) return ""
            var trimmed = rawPath.trim().replace('\\', '/')
            if (trimmed.length > 1) {
                trimmed = trimmed.trimEnd('/')
            }
            return trimmed
        }

        fun isPathBlocked(folderPath: String, blockedFolders: Collection<String>): Boolean {
            if (blockedFolders.isEmpty()) return false
            val normalized = normalizeFolderPath(folderPath).lowercase()
            if (normalized.isBlank()) return false
            return blockedFolders.any { rawBlocked ->
                val blocked = normalizeFolderPath(rawBlocked).lowercase()
                blocked.isNotBlank() && (normalized == blocked || normalized.startsWith("$blocked/"))
            }
        }

        fun filterBlockedSongs(songs: List<SongItem>, blockedFolders: List<String>): List<SongItem> {
            if (blockedFolders.isEmpty()) return songs
            val normalizedBlocked = blockedFolders
                .map { normalizeFolderPath(it).lowercase() }
                .filter { it.isNotBlank() }
            if (normalizedBlocked.isEmpty()) return songs
            return songs.filterNot { song ->
                val filePath = normalizeFolderPath(song.localFilePath)
                val dirPath = filePath.substringBeforeLast('/', "").ifBlank { "/" }
                isPathBlocked(dirPath, normalizedBlocked)
            }
        }

        fun inferAutoRootPath(dirPath: String): String {
            val normalized = normalizeFolderPath(dirPath)
            if (normalized.isBlank() || normalized == "/") return "/"
            val lower = normalized.lowercase()
            return when {
                lower == "/storage/emulated/0" || lower.startsWith("/storage/emulated/0/") -> "/storage/emulated/0"
                lower.startsWith("/storage/") -> {
                    val segs = normalized.trimStart('/').split('/')
                    if (segs.size >= 2) "/${segs[0]}/${segs[1]}" else normalized
                }
                lower == "/sdcard" || lower.startsWith("/sdcard/") -> "/sdcard"
                else -> {
                    val firstSeg = normalized.trimStart('/').substringBefore('/')
                    if (firstSeg.isNotBlank()) "/$firstSeg" else "/"
                }
            }
        }

        private fun rootDisplayName(rootPath: String): String {
            val lower = rootPath.lowercase()
            return when (lower) {
                "/storage/emulated/0", "/sdcard" -> "内部存储"
                else -> rootPath.substringAfterLast('/').ifBlank { rootPath }
            }
        }

        fun buildLocalMusicFolderTree(
            songs: List<SongItem>,
            customRoots: List<String>? = null
        ): List<LocalFolderNode> {
            val explicitRoots = (customRoots ?: emptyList())
                .map(::normalizeFolderPath)
                .filter { it.isNotBlank() }
                .distinctBy { it.lowercase() }

            val rootNodes = mutableListOf<LocalFolderNode>()
            val nodeMap = linkedMapOf<String, LocalFolderNode>()

            for (rootPath in explicitRoots) {
                val node = LocalFolderNode(
                    key = rootPath.lowercase(),
                    path = rootPath,
                    name = rootDisplayName(rootPath),
                    level = 0,
                    isExpanded = true
                )
                rootNodes.add(node)
                nodeMap[node.key] = node
            }

            for (song in songs) {
                val fullPath = normalizeFolderPath(song.localFilePath)
                if (fullPath.isBlank()) continue
                val dirPath = fullPath.substringBeforeLast('/', "").ifBlank { "/" }

                var matchedRoot = explicitRoots
                    .filter { isSameOrSubPath(dirPath, it) }
                    .maxByOrNull { it.length }

                if (matchedRoot.isNullOrBlank()) {
                    // 自动按存储根 / SAF 顶层目录聚合，保留完整多层子文件夹结构
                    matchedRoot = inferAutoRootPath(dirPath)
                    val fallbackKey = matchedRoot.lowercase()
                    if (!nodeMap.containsKey(fallbackKey)) {
                        val fallbackNode = LocalFolderNode(
                            key = fallbackKey,
                            path = matchedRoot,
                            name = rootDisplayName(matchedRoot),
                            level = 0,
                            isExpanded = true
                        )
                        rootNodes.add(fallbackNode)
                        nodeMap[fallbackKey] = fallbackNode
                    }
                }

                val targetNode = ensureFolderChain(matchedRoot, dirPath, nodeMap)
                targetNode.songs.add(song)
            }

            for (root in rootNodes) {
                sortFolderNodeRecursively(root)
            }
            rootNodes.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            return rootNodes
        }

        private fun inferNasAutoRootPath(dirPath: String): Pair<String, String> {
            val normalized = normalizeFolderPath(dirPath)
            if (normalized.isBlank() || normalized == "/") return "/" to "NAS 根目录"
            val segs = normalized.trimStart('/').split('/').filter { it.isNotBlank() }
            if (segs.isEmpty()) return "/" to "NAS 根目录"
            val first = segs[0].lowercase()
            // 识别飞牛 fnOS 存储卷路径：/vol3/1000/<目录> 或 /vol1/@appshare/<目录>
            if (first.startsWith("vol") && segs.size >= 3 &&
                (segs[1].all { it.isDigit() } || segs[1].startsWith("@"))
            ) {
                val rootPath = "/${segs[0]}/${segs[1]}/${segs[2]}"
                return rootPath to segs[2]
            }
            if (first.startsWith("vol") && segs.size == 2) {
                val volNum = first.removePrefix("vol")
                val label = if (segs[1] == "1000") "存储空间 $volNum" else segs[1]
                return "/${segs[0]}/${segs[1]}" to label
            }
            return "/${segs[0]}" to segs[0]
        }

        fun buildNasMusicFolderTree(
            songs: List<SongItem>,
            libraryRoots: List<Pair<String, String>> = emptyList()
        ): List<LocalFolderNode> {
            val explicitRoots = libraryRoots
                .map { (p, name) -> normalizeFolderPath(p) to name.trim() }
                .filter { it.first.isNotBlank() }
                .distinctBy { it.first.lowercase() }

            val rootNodes = mutableListOf<LocalFolderNode>()
            val nodeMap = linkedMapOf<String, LocalFolderNode>()

            for ((rootPath, label) in explicitRoots) {
                val display = label.ifBlank { rootPath.substringAfterLast('/').ifBlank { "音乐库" } }
                val node = LocalFolderNode(
                    key = rootPath.lowercase(),
                    path = rootPath,
                    name = display,
                    level = 0,
                    isExpanded = true
                )
                rootNodes.add(node)
                nodeMap[node.key] = node
            }

            for (song in songs) {
                val fullPath = normalizeFolderPath(song.nasVirtualPath)
                if (fullPath.isBlank()) continue
                val dirPath = fullPath.substringBeforeLast('/', "").ifBlank { "/" }

                var matchedRoot = explicitRoots
                    .map { it.first }
                    .filter { isSameOrSubPath(dirPath, it) }
                    .maxByOrNull { it.length }

                if (matchedRoot.isNullOrBlank()) {
                    val (autoPath, autoName) = inferNasAutoRootPath(dirPath)
                    matchedRoot = autoPath
                    val fallbackKey = matchedRoot.lowercase()
                    if (!nodeMap.containsKey(fallbackKey)) {
                        val fallbackNode = LocalFolderNode(
                            key = fallbackKey,
                            path = matchedRoot,
                            name = autoName,
                            level = 0,
                            isExpanded = true
                        )
                        rootNodes.add(fallbackNode)
                        nodeMap[fallbackKey] = fallbackNode
                    }
                }

                val targetNode = ensureFolderChain(matchedRoot, dirPath, nodeMap)
                targetNode.songs.add(song)
            }

            // 移除没有歌曲的空根目录
            rootNodes.removeAll { it.totalSongCount == 0 }
            for (root in rootNodes) {
                sortFolderNodeRecursively(root)
            }
            rootNodes.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

            // 若只有一个顶层音乐库容器且根下无散落单曲，直接展开其子文件夹作为顶层，省去多余一层点击
            if (rootNodes.size == 1 && rootNodes[0].songs.isEmpty() && rootNodes[0].children.isNotEmpty()) {
                return rootNodes[0].children.toList()
            }
            return rootNodes
        }

        fun findNodeByKey(roots: List<LocalFolderNode>, targetKey: String?): LocalFolderNode? {
            if (targetKey.isNullOrBlank()) return null
            val keyLower = targetKey.lowercase()
            fun search(node: LocalFolderNode): LocalFolderNode? {
                if (node.key == keyLower) return node
                for (child in node.children) {
                    val found = search(child)
                    if (found != null) return found
                }
                return null
            }
            for (root in roots) {
                val found = search(root)
                if (found != null) return found
            }
            return null
        }

        fun findFolderBreadcrumbPath(
            roots: List<LocalFolderNode>,
            targetKey: String?
        ): List<LocalFolderNode> {
            if (targetKey.isNullOrBlank()) return emptyList()
            val keyLower = targetKey.lowercase()
            val trail = mutableListOf<LocalFolderNode>()
            fun dfs(node: LocalFolderNode): Boolean {
                trail.add(node)
                if (node.key == keyLower) return true
                for (child in node.children) {
                    if (dfs(child)) return true
                }
                trail.removeAt(trail.lastIndex)
                return false
            }
            for (root in roots) {
                if (dfs(root)) return trail.toList()
            }
            return emptyList()
        }

        private fun ensureFolderChain(
            rootPath: String,
            targetDirPath: String,
            nodeMap: MutableMap<String, LocalFolderNode>
        ): LocalFolderNode {
            var currentNode = nodeMap.getValue(rootPath.lowercase())
            if (rootPath.equals(targetDirPath, ignoreCase = true)) {
                return currentNode
            }

            val relative = if (rootPath == "/") {
                targetDirPath.trimStart('/')
            } else {
                targetDirPath.substring(rootPath.length).trimStart('/')
            }
            val segments = relative.split('/').filter { it.isNotBlank() }
            var currentPath = rootPath

            for (segment in segments) {
                currentPath = if (currentPath == "/") "/$segment" else "$currentPath/$segment"
                val key = currentPath.lowercase()
                var childNode = nodeMap[key]
                if (childNode == null) {
                    childNode = LocalFolderNode(
                        key = key,
                        path = currentPath,
                        name = segment,
                        level = currentNode.level + 1,
                        isExpanded = false
                    )
                    currentNode.children.add(childNode)
                    nodeMap[key] = childNode
                }
                currentNode = childNode
            }
            return currentNode
        }

        private fun sortFolderNodeRecursively(node: LocalFolderNode) {
            node.songs.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            node.children.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            for (child in node.children) {
                sortFolderNodeRecursively(child)
            }
        }

        fun flattenVisibleFolderTree(roots: List<LocalFolderNode>): List<LocalFolderNode> {
            val result = mutableListOf<LocalFolderNode>()
            fun traverse(node: LocalFolderNode) {
                result.add(node)
                if (node.isExpanded) {
                    for (child in node.children) {
                        traverse(child)
                    }
                }
            }
            roots.forEach(::traverse)
            return result
        }

        fun expandAndFindNodeByPath(
            roots: List<LocalFolderNode>,
            folderPath: String?
        ): LocalFolderNode? {
            if (folderPath.isNullOrBlank()) return null
            val targetKey = normalizeFolderPath(folderPath).lowercase()
            fun search(node: LocalFolderNode): LocalFolderNode? {
                if (node.key == targetKey) return node
                for (child in node.children) {
                    val found = search(child)
                    if (found != null) {
                        node.isExpanded = true
                        return found
                    }
                }
                return null
            }
            for (root in roots) {
                val found = search(root)
                if (found != null) return found
            }
            return null
        }

        private fun isSameOrSubPath(candidatePath: String, rootPath: String): Boolean {
            if (candidatePath.equals(rootPath, ignoreCase = true)) return true
            val prefix = if (rootPath.endsWith('/')) rootPath else "$rootPath/"
            return candidatePath.startsWith(prefix, ignoreCase = true)
        }
    }

    fun resolveTreeUriDisplayInfo(treeUriStr: String): Pair<String, String> {
        return runCatching {
            val uri = Uri.parse(treeUriStr)
            val doc = DocumentFile.fromTreeUri(context, uri)
            val decoded = Uri.decode(treeUriStr)
            val afterTree = decoded.substringAfter("/tree/", "")
            val readablePath = when {
                afterTree.startsWith("primary:", ignoreCase = true) ->
                    "内部存储/" + afterTree.substringAfter("primary:").trimStart('/')
                afterTree.contains(':') ->
                    afterTree.replace(':', '/')
                else -> doc?.name ?: treeUriStr
            }.trimEnd('/')
            val name = doc?.name?.takeIf { it.isNotBlank() }
                ?: readablePath.substringAfterLast('/').ifBlank { "本地文件夹" }
            name to readablePath.ifBlank { name }
        }.getOrElse {
            val decoded = Uri.decode(treeUriStr).substringAfterLast('/').ifBlank { "本地文件夹" }
            decoded to decoded
        }
    }

    suspend fun scanAllLocalMusic(onProgress: (Int) -> Unit = {}): List<SongItem> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<SongItem>()
        val seenKeys = HashSet<String>(256)
        val settings = settingsRepo.currentSettings

        // 1. 若开启系统媒体库扫描，扫描 MediaStore 音频库
        if (settings.scanMediaStoreEnabled) {
            scanMediaStore(songs, seenKeys, onProgress)
        }

        // 2. 扫描用户额外授权的 SAF 文件夹（递归多层子文件夹）
        val treeUris = settings.localTreeUris
        for (treeUriStr in treeUris) {
            runCatching {
                val treeUri = Uri.parse(treeUriStr)
                val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return@runCatching
                val rootName = rootDoc.name?.takeIf { it.isNotBlank() } ?: "本地目录"
                scanDocumentTree(rootDoc, "/$rootName", songs, seenKeys, onProgress)
            }
        }

        onProgress(songs.size)
        songs.sortedWith(
            Comparator<SongItem> { a, b -> String.CASE_INSENSITIVE_ORDER.compare(a.name, b.name) }
                .thenBy { it.localFilePath.orEmpty() }
        )
    }

    private fun scanMediaStore(
        output: MutableList<SongItem>,
        seenKeys: MutableSet<String>,
        onProgress: (Int) -> Unit
    ) {
        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.DISPLAY_NAME)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.SIZE)
            add(MediaStore.Audio.Media.DATE_MODIFIED)
            @Suppress("DEPRECATION")
            add(MediaStore.Audio.Media.DATA)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Audio.Media.RELATIVE_PATH)
            }
        }.toTypedArray()

        runCatching {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Audio.Media.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val titleCol = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)
                val durCol = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
                val sizeCol = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)
                val modCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_MODIFIED)
                @Suppress("DEPRECATION")
                val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                val relCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
                } else -1

                while (cursor.moveToNext()) {
                    val mediaId = cursor.getLong(idCol)
                    val displayName = cursor.getString(nameCol).orEmpty()
                    val ext = displayName.substringAfterLast('.', "").lowercase()
                    if (ext !in SUPPORTED_EXTENSIONS) continue

                    val size = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L
                    if (size <= 1024L) continue

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        mediaId
                    ).toString()

                    val rawDataPath = if (dataCol >= 0) cursor.getString(dataCol).orEmpty() else ""
                    val relDir = if (relCol >= 0) cursor.getString(relCol).orEmpty() else ""
                    val displayPath = when {
                        rawDataPath.isNotBlank() -> normalizeFolderPath(rawDataPath)
                        relDir.isNotBlank() -> normalizeFolderPath("/$relDir$displayName")
                        else -> "/Music/$displayName"
                    }

                    val dedupKey = "${displayPath.lowercase()}|$size"
                    if (!seenKeys.add(dedupKey)) continue

                    val fallbackTitle = if (titleCol >= 0) cursor.getString(titleCol).orEmpty() else ""
                    val fallbackArtist = if (artistCol >= 0) cursor.getString(artistCol).orEmpty() else ""
                    val fallbackAlbum = if (albumCol >= 0) cursor.getString(albumCol).orEmpty() else ""
                    val fallbackDur = if (durCol >= 0) cursor.getLong(durCol) else 0L
                    val modified = if (modCol >= 0) cursor.getLong(modCol) * 1000L else 0L

                    val song = parseAudioFromUri(
                        contentUri = contentUri,
                        displayPath = displayPath,
                        fileName = displayName,
                        fileSize = size,
                        modifiedTimeMs = modified,
                        fallbackTitle = fallbackTitle,
                        fallbackArtist = fallbackArtist,
                        fallbackAlbum = fallbackAlbum,
                        fallbackDurationMs = fallbackDur
                    )
                    output.add(song)
                    if (output.size % 10 == 0) {
                        onProgress(output.size)
                    }
                }
            }
        }
    }

    private fun scanDocumentTree(
        dir: DocumentFile,
        virtualDirPath: String,
        output: MutableList<SongItem>,
        seenKeys: MutableSet<String>,
        onProgress: (Int) -> Unit
    ) {
        val children = dir.listFiles()
        val cueDocs = children.filter { it.isFile && it.name?.endsWith(".cue", ignoreCase = true) == true }
        val audioDocsByName = HashMap<String, DocumentFile>()
        val audioDocsByStem = HashMap<String, DocumentFile>()
        for (doc in children) {
            if (!doc.isFile) continue
            val name = doc.name ?: continue
            val ext = name.substringAfterLast('.', "").lowercase()
            if (ext in SUPPORTED_EXTENSIONS) {
                audioDocsByName[name.lowercase()] = doc
                audioDocsByStem[name.substringBeforeLast('.').lowercase()] = doc
            }
        }

        // 先解析当前目录中的 .cue 分轨表，自动拆分整轨无损专辑
        val cueConsumedAudioNames = HashSet<String>()
        for (cueDoc in cueDocs) {
            val cueName = cueDoc.name ?: continue
            val cueText = runCatching {
                context.contentResolver.openInputStream(cueDoc.uri)?.use { stream ->
                    CueSheetParser.decodeCueBytes(stream.readBytes())
                }
            }.getOrNull() ?: continue
            val parsed = CueSheetParser.parse(cueText)
            if (parsed.tracks.isEmpty()) continue

            val cueStemLower = cueName.substringBeforeLast('.').lowercase()
            val tracksByAudio = parsed.tracks.groupBy { it.audioFileName.lowercase() }
            for ((refFileNameLower, cueTracks) in tracksByAudio) {
                val targetAudio = audioDocsByName[refFileNameLower]
                    ?: audioDocsByStem[refFileNameLower.substringBeforeLast('.')]
                    ?: audioDocsByStem[cueStemLower]
                    ?: continue
                val audioName = targetAudio.name ?: continue
                val size = targetAudio.length()
                if (size <= 1024L) continue
                cueConsumedAudioNames.add(audioName.lowercase())

                val fullPath = normalizeFolderPath("$virtualDirPath/$audioName")
                val cuePath = normalizeFolderPath("$virtualDirPath/$cueName")
                val baseSong = parseAudioFromUri(
                    contentUri = targetAudio.uri.toString(),
                    displayPath = fullPath,
                    fileName = audioName,
                    fileSize = size,
                    modifiedTimeMs = targetAudio.lastModified(),
                    fallbackTitle = parsed.albumTitle,
                    fallbackArtist = parsed.albumPerformer,
                    fallbackAlbum = parsed.albumTitle,
                    fallbackDurationMs = 0L
                )
                val reParsed = if (baseSong.durationMs > 0L) {
                    CueSheetParser.parse(cueText, totalAudioDurationMs = baseSong.durationMs)
                        .tracks.filter { it.audioFileName.equals(cueTracks.first().audioFileName, ignoreCase = true) }
                } else {
                    cueTracks
                }

                for (ct in reParsed) {
                    val trackKey = "${audioName.lowercase()}|$size|cue_${ct.trackNumber}_${ct.startMs}"
                    if (!seenKeys.add(trackKey)) continue
                    val trackDur = when {
                        ct.endMs != null && ct.endMs > ct.startMs -> ct.endMs - ct.startMs
                        baseSong.durationMs > ct.startMs -> baseSong.durationMs - ct.startMs
                        else -> 0L
                    }
                    val idHash = sha256HexPrefix("$fullPath#cue_${ct.trackNumber}_${ct.startMs}".lowercase().toByteArray(Charsets.UTF_8), 16)
                    output.add(
                        baseSong.copy(
                            id = "local_cue_$idHash",
                            name = ct.title,
                            artists = listOf(ArtistItem(name = ct.performer)),
                            album = baseSong.album.copy(name = ct.album.ifBlank { baseSong.album.name }),
                            durationMs = trackDur,
                            clipStartMs = ct.startMs,
                            clipEndMs = ct.endMs,
                            cueSheetPath = cuePath
                        )
                    )
                    if (output.size % 10 == 0) onProgress(output.size)
                }
            }
        }

        for (doc in children) {
            val name = doc.name ?: continue
            if (doc.isDirectory) {
                scanDocumentTree(doc, "$virtualDirPath/$name", output, seenKeys, onProgress)
            } else if (doc.isFile) {
                if (name.lowercase() in cueConsumedAudioNames) continue
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext !in SUPPORTED_EXTENSIONS) continue
                val size = doc.length()
                if (size <= 1024L) continue
                val fullPath = normalizeFolderPath("$virtualDirPath/$name")
                val dedupKey = "${name.lowercase()}|$size"
                if (!seenKeys.add(dedupKey)) continue

                val song = parseAudioFromUri(
                    contentUri = doc.uri.toString(),
                    displayPath = fullPath,
                    fileName = name,
                    fileSize = size,
                    modifiedTimeMs = doc.lastModified(),
                    fallbackTitle = "",
                    fallbackArtist = "",
                    fallbackAlbum = "",
                    fallbackDurationMs = 0L
                )
                output.add(song)
                if (output.size % 10 == 0) {
                    onProgress(output.size)
                }
            }
        }
    }

    private fun parseAudioFromUri(
        contentUri: String,
        displayPath: String,
        fileName: String,
        fileSize: Long,
        modifiedTimeMs: Long,
        fallbackTitle: String,
        fallbackArtist: String,
        fallbackAlbum: String,
        fallbackDurationMs: Long
    ): SongItem {
        val nameNoExt = fileName.substringBeforeLast('.')
        var guessedTitle = nameNoExt
        var guessedArtist = "未知歌手"
        if (nameNoExt.contains(" - ")) {
            val parts = nameNoExt.split(" - ", limit = 2)
            guessedArtist = parts[0].trim().ifBlank { "未知歌手" }
            guessedTitle = parts[1].trim().ifBlank { nameNoExt }
        }

        var title = repairLegacyChineseText(fallbackTitle).ifBlank { guessedTitle }
        var artist = repairLegacyChineseText(fallbackArtist)
            .takeIf { it.isNotBlank() && !it.equals("<unknown>", ignoreCase = true) }
            ?: guessedArtist
        var album = repairLegacyChineseText(fallbackAlbum)
            .takeIf { it.isNotBlank() && !it.equals("<unknown>", ignoreCase = true) }
            ?: "未知专辑"
        var durationMs = fallbackDurationMs
        var channelCount: Int? = null
        var embeddedLyric: String? = null
        var coverPath = ""
        var bitrateKbps = 0
        var replayGainDb: Float? = null

        runCatching {
            val uri = Uri.parse(contentUri)
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                val meta = TagLib.getMetadata(pfd.dup().detachFd(), readPictures = true)
                val props = TagLib.getAudioProperties(pfd.dup().detachFd())

                if (props != null) {
                    if (props.length > 0) durationMs = props.length.toLong()
                    if (props.channels > 0) channelCount = props.channels
                    if (props.bitrate > 0) bitrateKbps = props.bitrate
                }

                if (meta != null) {
                    val map = meta.propertyMap
                    repairLegacyChineseText(map["TITLE"]?.firstOrNull())
                        .takeIf { it.isNotBlank() }?.let { title = it }

                    val artistsFromTag = map["ARTIST"]
                        ?.map { repairLegacyChineseText(it) }
                        ?.filter { it.isNotBlank() }
                        .orEmpty()
                    if (artistsFromTag.isNotEmpty()) {
                        artist = artistsFromTag.joinToString(" / ")
                    }

                    repairLegacyChineseText(map["ALBUM"]?.firstOrNull())
                        .takeIf { it.isNotBlank() }?.let { album = it }

                    val lrcRaw = map["LYRICS"]?.firstOrNull() ?: map["UNSYNCEDLYRICS"]?.firstOrNull()
                    repairLegacyChineseText(lrcRaw).takeIf { it.isNotBlank() }?.let { embeddedLyric = it }

                    replayGainDb = parseReplayGainFromTagMap(map)

                    val frontPic = meta.pictures.firstOrNull { it.pictureType == "Front Cover" }
                        ?: meta.pictures.firstOrNull()
                    if (frontPic != null && frontPic.data.isNotEmpty()) {
                        val hash = sha256HexPrefix(frontPic.data, 16)
                        val coverFile = File(settingsRepo.coverCacheDir, "$hash.jpg")
                        if (!coverFile.exists()) {
                            coverFile.writeBytes(frontPic.data)
                        }
                        coverPath = Uri.fromFile(coverFile).toString()
                    }
                }
            }
        }

        val idHash = sha256HexPrefix(displayPath.lowercase().toByteArray(Charsets.UTF_8), 16)
        val extLower = AudioFormatSupport.extractExtension(fileName)
        val extBadge = extLower.uppercase().ifBlank { "LOCAL" }
        val qualityBadge = if (bitrateKbps > 0) "$extBadge ${bitrateKbps}k" else extBadge
        val qualityLevel = when {
            AudioFormatSupport.isHiResExtension(extLower) -> MusicQualityLevel.HIRES
            AudioFormatSupport.isLosslessExtension(extLower) || bitrateKbps > 500 -> MusicQualityLevel.LOSSLESS
            bitrateKbps >= 256 || AudioFormatSupport.isHighQualityLossyExtension(extLower) -> MusicQualityLevel.EXHIGH
            else -> MusicQualityLevel.STANDARD
        }

        return SongItem(
            id = "local_$idHash",
            name = title,
            picUrl = coverPath,
            artists = listOf(ArtistItem(name = artist)),
            album = AlbumItem(name = album, picUrl = coverPath),
            durationMs = durationMs,
            channelCount = channelCount,
            fileSize = fileSize,
            modifiedTime = modifiedTimeMs,
            embeddedLyric = embeddedLyric,
            source = "local",
            localFilePath = displayPath,
            localContentUri = contentUri,
            playMusicUrl = contentUri,
            resolvedPlatform = "local",
            playingQualityBadge = qualityBadge,
            playingQualityLevel = qualityLevel,
            playingBitrateKbps = bitrateKbps,
            replayGainDb = replayGainDb
        )
    }

    private fun parseReplayGainFromTagMap(map: Map<String, Array<String>>): Float? {
        val trackRaw = map["REPLAYGAIN_TRACK_GAIN"]?.firstOrNull()
            ?: map["REPLAYGAIN_ALBUM_GAIN"]?.firstOrNull()
        if (!trackRaw.isNullOrBlank()) {
            val num = trackRaw.trim()
                .replace("db", "", ignoreCase = true)
                .trim()
                .toFloatOrNull()
            if (num != null && num.isFinite()) return num.coerceIn(-24f, 18f)
        }
        val r128Raw = map["R128_TRACK_GAIN"]?.firstOrNull()
            ?: map["R128_ALBUM_GAIN"]?.firstOrNull()
        if (!r128Raw.isNullOrBlank()) {
            val q78 = r128Raw.trim().toIntOrNull()
            if (q78 != null) {
                // Opus R128 参考电平为 -23 LUFS，加 +5 dB 对齐 ReplayGain -18 LUFS
                return (q78 / 256f + 5f).coerceIn(-24f, 18f)
            }
        }
        return null
    }

    /**
     * 使用 TagLib 原地编辑本地音频元数据（歌名、歌手、专辑、内嵌歌词与封面）。
     */
    suspend fun updateLocalSongTags(
        song: SongItem,
        newTitle: String,
        newArtist: String,
        newAlbum: String,
        newLyric: String? = null,
        newCoverBytes: ByteArray? = null
    ): Result<SongItem> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanTitle = newTitle.trim().ifBlank { song.name }
            val cleanArtist = newArtist.trim().ifBlank { "未知歌手" }
            val cleanAlbum = newAlbum.trim().ifBlank { "未知专辑" }
            val uriStr = song.localContentUri?.takeIf { it.isNotBlank() }
                ?: song.localFilePath?.takeIf { File(it).exists() }?.let { Uri.fromFile(File(it)).toString() }
                ?: throw IllegalStateException("未找到可写入的本地音频路径")

            val uri = Uri.parse(uriStr)
            val pfd = if (uri.scheme.equals("file", ignoreCase = true)) {
                android.os.ParcelFileDescriptor.open(
                    File(uri.path!!),
                    android.os.ParcelFileDescriptor.MODE_READ_WRITE
                )
            } else {
                context.contentResolver.openFileDescriptor(uri, "rw")
            } ?: throw IllegalStateException("系统未授予该文件的写入权限（可通过 SAF 重新授权所在目录）")

            pfd.use { fd ->
                val existingMeta = runCatching { TagLib.getMetadata(fd.dup().detachFd(), readPictures = false) }.getOrNull()
                val propMap = PropertyMap()
                existingMeta?.propertyMap?.forEach { (k, v) -> propMap[k] = v }
                propMap["TITLE"] = arrayOf(cleanTitle)
                propMap["ARTIST"] = cleanArtist.split("/", "、").map { it.trim() }.filter { it.isNotBlank() }
                    .ifEmpty { listOf(cleanArtist) }.toTypedArray()
                propMap["ALBUM"] = arrayOf(cleanAlbum)
                if (newLyric != null) {
                    if (newLyric.isBlank()) {
                        propMap.remove("LYRICS")
                    } else {
                        propMap["LYRICS"] = arrayOf(newLyric)
                    }
                }
                val savedOk = TagLib.savePropertyMap(fd.dup().detachFd(), propMap)
                if (!savedOk) {
                    throw IllegalStateException("该音频格式暂不支持写入标签")
                }

                if (newCoverBytes != null && newCoverBytes.isNotEmpty()) {
                    val pic = Picture(
                        data = newCoverBytes,
                        description = "Cover",
                        pictureType = "Front Cover",
                        mimeType = "image/jpeg"
                    )
                    TagLib.savePictures(fd.dup().detachFd(), arrayOf(pic))
                }
            }

            var updatedCover = song.picUrl
            if (newCoverBytes != null && newCoverBytes.isNotEmpty()) {
                val hash = sha256HexPrefix(newCoverBytes, 16)
                val coverFile = File(settingsRepo.coverCacheDir, "$hash.jpg")
                coverFile.writeBytes(newCoverBytes)
                updatedCover = Uri.fromFile(coverFile).toString()
            }

            song.copy(
                name = cleanTitle,
                artists = cleanArtist.split("/", "、").map { it.trim() }.filter { it.isNotBlank() }
                    .ifEmpty { listOf(cleanArtist) }.map { ArtistItem(name = it) },
                album = song.album.copy(name = cleanAlbum, picUrl = updatedCover),
                picUrl = updatedCover,
                embeddedLyric = newLyric?.takeIf { it.isNotBlank() } ?: song.embeddedLyric
            )
        }
    }

    /**
     * 删除本地音频文件（供重复歌曲清理使用）。
     */
    suspend fun deleteLocalSongFile(song: SongItem): Boolean = withContext(Dispatchers.IO) {
        if (song.cueSheetPath != null) return@withContext false
        val uriStr = song.localContentUri
        if (!uriStr.isNullOrBlank() && uriStr.startsWith("content://", ignoreCase = true)) {
            val uri = Uri.parse(uriStr)
            val deletedDoc = runCatching {
                DocumentFile.fromSingleUri(context, uri)?.delete() == true
            }.getOrDefault(false)
            if (deletedDoc) return@withContext true
            val deletedResolver = runCatching {
                context.contentResolver.delete(uri, null, null) > 0
            }.getOrDefault(false)
            if (deletedResolver) return@withContext true
        }
        val path = song.localFilePath
        if (!path.isNullOrBlank()) {
            val f = File(path)
            if (f.exists() && f.delete()) return@withContext true
        }
        false
    }

    /**
     * 解析从外部文件管理器通过 ACTION_VIEW 传入的音频 URI，提取 TagLib 元数据与封面以便直接播放。
     */
    suspend fun parseExternalAudioUri(uri: Uri): SongItem? = withContext(Dispatchers.IO) {
        var displayName = ""
        var fileSize = 0L
        if (uri.scheme.equals("content", ignoreCase = true)) {
            runCatching {
                context.contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (nameIdx >= 0) displayName = cursor.getString(nameIdx).orEmpty()
                        if (sizeIdx >= 0) fileSize = cursor.getLong(sizeIdx)
                    }
                }
            }
        } else if (uri.scheme.equals("file", ignoreCase = true)) {
            val file = uri.path?.let { File(it) }
            if (file != null) {
                displayName = file.name
                fileSize = file.length()
            }
        }
        if (displayName.isBlank()) {
            displayName = Uri.decode(uri.lastPathSegment.orEmpty())
                .substringAfterLast('/')
                .ifBlank { "外部音频.mp3" }
        }
        val displayPath = uri.path?.takeIf { uri.scheme.equals("file", ignoreCase = true) && it.isNotBlank() }
            ?: "/外部音频/$displayName"

        runCatching {
            parseAudioFromUri(
                contentUri = uri.toString(),
                displayPath = normalizeFolderPath(displayPath),
                fileName = displayName,
                fileSize = fileSize,
                modifiedTimeMs = System.currentTimeMillis(),
                fallbackTitle = "",
                fallbackArtist = "",
                fallbackAlbum = "",
                fallbackDurationMs = 0L
            )
        }.getOrNull()
    }

    private fun sha256HexPrefix(bytes: ByteArray, chars: Int): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) {
            val v = b.toInt() and 0xFF
            sb.append(HEX[v ushr 4]).append(HEX[v and 0x0F])
        }
        return sb.substring(0, minOf(chars, sb.length))
    }

    private val HEX = "0123456789abcdef".toCharArray()
}
