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
        val session = if (config.isAnonymous || config.username.isBlank()) {
            // Tenta estratégias progressivas de autenticação não protegida no Windows
            try {
                connection.authenticate(AuthenticationContext("Guest", CharArray(0), ""))
            } catch (e1: Exception) {
                try {
                    connection.authenticate(AuthenticationContext.anonymous())
                } catch (e2: Exception) {
                    connection.authenticate(AuthenticationContext("", CharArray(0), ""))
                }
            }
        } else {
            connection.authenticate(
                AuthenticationContext(
                    config.username.trim(),
                    config.password.toCharArray(),
                    config.domain.trim()
                )
            )
        }

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
            val cleanMsg = when {
                e.message?.contains("STATUS_LOGON_FAILURE", ignoreCase = true) == true ->
                    "Falha no logon do Windows. Se a conta exigir senha, informe seu usuário e senha do Windows."
                e.message?.contains("STATUS_ACCESS_DENIED", ignoreCase = true) == true ->
                    "Acesso negado pelo Windows. Adicione 'Todos' na aba Compartilhamento e Segurança da pasta."
                e.message?.contains("Connection refused", ignoreCase = true) == true ->
                    "Conexão recusada na porta 445. Verifique se o compartilhamento de arquivos está ativo no Windows."
                else -> e.localizedMessage ?: "Erro ao conectar no computador."
            }
            Result.failure(Exception(cleanMsg))
        }
    }

    suspend fun testAndConnectShare(
        config: SmbConnectionConfig,
        shareName: String
    ): Result<SmbShareItem> = withContext(Dispatchers.IO) {
        val cleanShare = shareName.trim().trim('/', '\\').replace('\\', '/')
        if (cleanShare.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Nome do compartilhamento não pode ser vazio."))
        }
        try {
            val session = getOrCreateSession(config)
            val share = session.connectShare(cleanShare) as? DiskShare
                ?: return@withContext Result.failure(Exception("O compartilhamento '$cleanShare' não é um compartilhamento de arquivos."))
            try {
                share.list("")
            } catch (_: Exception) {
                // Algumas pastas podem não listar raiz mas estarem ativas
            } finally {
                try { share.close() } catch (_: Exception) {}
            }
            Result.success(SmbShareItem(name = cleanShare, comment = "Compartilhamento ativo"))
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao conectar no compartilhamento '$cleanShare' em ${config.host}", e)
            val msg = when {
                e.message?.contains("STATUS_ACCESS_DENIED", ignoreCase = true) == true ||
                e.message?.contains("Access is denied", ignoreCase = true) == true ->
                    "Acesso negado no Windows para '$cleanShare'. Certifique-se de liberar 'Todos' na aba Segurança (NTFS) e Compartilhamento no PC."
                e.message?.contains("STATUS_BAD_NETWORK_NAME", ignoreCase = true) == true ||
                e.message?.contains("not found", ignoreCase = true) == true ->
                    "Compartilhamento '$cleanShare' não encontrado. Verifique se o nome digitado confere com o nome compartilhado no Windows."
                else -> "Erro ao acessar '$cleanShare': ${e.localizedMessage ?: e.message}"
            }
            Result.failure(Exception(msg))
        }
    }

    suspend fun listShares(
        config: SmbConnectionConfig,
        customShares: List<String> = emptyList()
    ): List<SmbShareItem> = withContext(Dispatchers.IO) {
        try {
            val commonShares = (listOf(
                "Filmes", "Series", "Séries", "Videos", "Vídeos", "Downloads", "Users", "Public", "Media",
                "Compartilhado", "Musicas", "Músicas", "Animes", "Cinema", "Novelas", "Shared", "Movies",
                "TV", "CineLocal", "CinemaLocal", "C", "D", "E", "F", "C$", "D$", "E$"
            ) + customShares).distinct()

            val accessibleShares = mutableListOf<SmbShareItem>()
            val session = getOrCreateSession(config)

            for (shareName in commonShares) {
                try {
                    val share = session.connectShare(shareName) as? DiskShare
                    if (share != null) {
                        accessibleShares.add(SmbShareItem(name = shareName, comment = "Pasta compartilhada"))
                        share.close()
                    }
                } catch (_: Exception) {
                    // Compartilhamento não existe ou sem permissão
                }
            }

            if (accessibleShares.isEmpty()) {
                // Sugestões amigáveis com indicação para adicionar nome manual
                listOf("Filmes", "Series", "Videos", "Users", "Public", "Compartilhado").forEach { fallback ->
                    accessibleShares.add(SmbShareItem(name = fallback, comment = "Toque para tentar acessar ou adicione o nome exato"))
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
            throw e
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
            getOrCreateSessionSync(config)
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
