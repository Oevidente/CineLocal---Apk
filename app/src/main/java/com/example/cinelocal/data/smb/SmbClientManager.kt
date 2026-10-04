package com.example.cinelocal.data.smb

import android.util.Log
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.smbj.share.File as SmbFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.EnumSet
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object SmbClientManager {

    private const val TAG = "SmbClientManager"

    private val VIDEO_EXTENSIONS = setOf(
        "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm",
        "ts", "m2ts", "mpg", "mpeg", "3gp", "rmvb", "vob", "m4v"
    )

    private val smbConfig: SmbConfig = SmbConfig.builder()
        .withTimeout(15, TimeUnit.SECONDS)
        .withSoTimeout(25, TimeUnit.SECONDS)
        .withMultiProtocolNegotiate(true)
        .build()

    private val smbClient = SMBClient(smbConfig)

    // Cache de sessões ativas por host + username
    private val activeSessions = ConcurrentHashMap<String, Pair<Connection, Session>>()

    private fun getSessionKey(config: SmbConnectionConfig): String {
        val authPart = if (config.isAnonymous) {
            "anon"
        } else {
            val pwdHash = config.password.hashCode()
            "${config.domain.trim()}\\${config.username.trim()}:$pwdHash"
        }
        return "${config.host}:${config.port}:$authPart"
    }

    fun invalidateSession(config: SmbConnectionConfig) {
        val key = getSessionKey(config)
        val cached = activeSessions.remove(key)
        try {
            cached?.second?.close()
            cached?.first?.close()
        } catch (_: Exception) {}
    }

    fun clearAllSessions() {
        val keys = activeSessions.keys().toList()
        for (k in keys) {
            val cached = activeSessions.remove(k)
            try {
                cached?.second?.close()
                cached?.first?.close()
            } catch (_: Exception) {}
        }
    }

    fun getOrCreateSessionSync(config: SmbConnectionConfig): Session {
        val key = getSessionKey(config)
        val cached = activeSessions[key]
        if (cached != null && cached.first.isConnected && cached.second.connection.isConnected) {
            return cached.second
        }

        try {
            cached?.second?.close()
            cached?.first?.close()
        } catch (_: Exception) {}

        val connection = smbClient.connect(config.host, config.port)
        val auth = if (config.isAnonymous || config.username.isBlank()) {
            AuthenticationContext.anonymous()
        } else {
            AuthenticationContext(
                config.username.trim(),
                config.password.toCharArray(),
                config.domain.trim()
            )
        }

        val session = connection.authenticate(auth)
        activeSessions[key] = Pair(connection, session)
        return session
    }

    suspend fun getOrCreateSession(config: SmbConnectionConfig): Session = withContext(Dispatchers.IO) {
        getOrCreateSessionSync(config)
    }

    suspend fun testConnection(config: SmbConnectionConfig): Result<String> = withContext(Dispatchers.IO) {
        try {
            val session = getOrCreateSession(config)
            if (session.connection.isConnected) {
                Result.success("Conexão com ${config.host} estabelecida com sucesso!")
            } else {
                Result.failure(Exception("Não foi possível validar a conexão SMB."))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro testando conexão com ${config.host}", e)
            Result.failure(e)
        }
    }

    suspend fun listShares(config: SmbConnectionConfig): List<SmbShareItem> = withContext(Dispatchers.IO) {
        try {
            // Em SMB, alguns compartilhamentos administrativos padrão terminam em $
            // e outros são compartilhamentos de mídia/arquivos
            val commonShares = listOf(
                "Filmes", "Series", "Videos", "Downloads", "Users", "Public", "Media",
                "Compartilhado", "Musicas", "C", "D", "E", "C$", "D$"
            )

            val accessibleShares = mutableListOf<SmbShareItem>()
            val session = getOrCreateSession(config)

            for (shareName in commonShares) {
                try {
                    val share = session.connectShare(shareName) as? DiskShare
                    if (share != null) {
                        accessibleShares.add(SmbShareItem(name = shareName))
                        share.close()
                    }
                } catch (_: Exception) {
                    // Compartilhamento não existe ou sem permissão
                }
            }

            if (accessibleShares.isEmpty()) {
                // Tenta compartilhamentos genéricos comuns
                listOf("Users", "Public", "Compartilhado").forEach { fallback ->
                    accessibleShares.add(SmbShareItem(name = fallback, comment = "Tente conectar ou informe o nome exato"))
                }
            }

            accessibleShares
        } catch (e: Exception) {
            Log.e(TAG, "Erro listando compartilhamentos em ${config.host}", e)
            emptyList()
        }
    }

    suspend fun listDirectory(
        config: SmbConnectionConfig,
        shareName: String,
        directoryPath: String = ""
    ): List<SmbFileItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<SmbFileItem>()
        var share: DiskShare? = null
        try {
            val session = getOrCreateSession(config)
            share = session.connectShare(shareName) as? DiskShare ?: return@withContext emptyList()

            val cleanPath = directoryPath.trim().trim('/').replace('/', '\\')
            val fileList = if (cleanPath.isBlank()) share.list("") else share.list(cleanPath)

            for (item in fileList) {
                val name = item.fileName
                if (name == "." || name == ".." || name.startsWith("desktop.ini", ignoreCase = true) || name.startsWith("Thumbs.db", ignoreCase = true)) {
                    continue
                }

                val isDir = (item.fileAttributes and 0x10L) != 0L // FILE_ATTRIBUTE_DIRECTORY
                val subPath = if (directoryPath.isBlank()) name else "${directoryPath.trim('/')}/$name"
                val ext = name.substringAfterLast('.', "").lowercase()
                val isVideo = !isDir && ext in VIDEO_EXTENSIONS

                results.add(
                    SmbFileItem(
                        name = name,
                        isDirectory = isDir,
                        path = subPath,
                        shareName = shareName,
                        sizeBytes = item.endOfFile,
                        lastModified = item.changeTime.toEpochMillis(),
                        isVideo = isVideo
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro lendo diretório '$directoryPath' no share '$shareName'", e)
        } finally {
            try {
                share?.close()
            } catch (_: Exception) {}
        }

        // Ordena: pastas primeiro, depois vídeos alfabeticamente
        results.sortedWith(
            compareByDescending<SmbFileItem> { it.isDirectory }
                .thenByDescending { it.isVideo }
                .thenBy { it.name.lowercase() }
        )
    }

    fun openSmbFile(
        config: SmbConnectionConfig,
        shareName: String,
        filePath: String
    ): SmbFile {
        val key = getSessionKey(config)
        val cached = activeSessions[key]
        val session = if (cached != null && cached.first.isConnected) {
            cached.second
        } else {
            val conn = smbClient.connect(config.host, config.port)
            val auth = if (config.isAnonymous || config.username.isBlank()) {
                AuthenticationContext.anonymous()
            } else {
                AuthenticationContext(config.username, config.password.toCharArray(), config.domain)
            }
            val sess = conn.authenticate(auth)
            activeSessions[key] = Pair(conn, sess)
            sess
        }

        val share = session.connectShare(shareName) as DiskShare
        val cleanPath = filePath.trim().trim('/').replace('/', '\\')
        return share.openFile(
            cleanPath,
            EnumSet.of(AccessMask.GENERIC_READ),
            null,
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OPEN,
            null
        )
    }

    fun isVideoFile(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext in VIDEO_EXTENSIONS
    }
}
