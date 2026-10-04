package com.example.cinelocal.cast

data class CompatibilityCheckResult(
    val isCompatible: Boolean,
    val warningMessage: String? = null,
    val isGen2OrOlder: Boolean = false,
    val reason: String? = null
)

object CastCompatibility {

    fun isChromecastGen2OrOlder(deviceModel: String?, deviceName: String?): Boolean {
        val model = (deviceModel ?: "").lowercase()
        val name = (deviceName ?: "").lowercase()
        val combined = "$model $name"

        if (combined.contains("ultra") ||
            combined.contains("4k") ||
            combined.contains("google tv") ||
            combined.contains("android tv") ||
            combined.contains("shield") ||
            combined.contains("samsung") ||
            combined.contains("lg") ||
            combined.contains("sony") ||
            combined.contains("tcl") ||
            combined.contains("fire")
        ) {
            return false
        }

        if (combined.contains("chromecast") || model.startsWith("nc2-6a5") || model.startsWith("h2g2-42")) {
            return true
        }

        return false
    }

    fun checkCompatibility(
        deviceModel: String?,
        deviceName: String?,
        probe: MediaProbeResult?,
        uri: android.net.Uri? = null
    ): CompatibilityCheckResult {
        if (probe == null) {
            return CompatibilityCheckResult(isCompatible = true)
        }

        val isLegacyChromecast = isChromecastGen2OrOlder(deviceModel, deviceName)

        if (isLegacyChromecast) {
            val reasons = mutableListOf<String>()

            val uriStr = uri?.toString() ?: ""
            val isMkv = uriStr.substringBefore("?").endsWith(".mkv", ignoreCase = true) ||
                    uriStr.contains(".mkv", ignoreCase = true) ||
                    probe.videoMime?.contains("matroska", ignoreCase = true) == true ||
                    probe.containerMime?.contains("matroska", ignoreCase = true) == true

            if (isMkv) {
                reasons.add("Formato de contêiner MKV (Matroska)")
            }
            if (probe.isHevc) {
                reasons.add("Vídeo codificado em HEVC (H.265)")
            }
            if (probe.is10Bit) {
                reasons.add("Cores de 10-bit / HDR")
            }
            if (probe.height > 1080 || probe.width > 1920) {
                reasons.add("Resolução 4K (${probe.width}x${probe.height})")
            }

            if (reasons.isNotEmpty()) {
                val reasonText = reasons.joinToString(", ")
                val msg = if (isMkv && !probe.isHevc && !probe.is10Bit && probe.height <= 1080) {
                    "Este vídeo está no formato de contêiner MKV. Embora os codecs internos (vídeo e áudio) sejam compatíveis, o seu Chromecast (2ª geração ou antigo) não aceita o contêiner MKV nativamente. É por isso que ele roda diretamente na sua TV Samsung DU7700 (que é moderna e possui decodificadores atualizados), mas falha no Chromecast 2 antigo. Para resolver de forma rápida (em segundos), você pode apenas reempacotar (remux) para MP4 sem recodificar o vídeo!"
                } else {
                    "Este vídeo possui $reasonText. O seu Chromecast de 2ª geração (ou antigo) não possui decodificador por hardware para este formato e falhará ao reproduzir, embora a sua TV Samsung DU7700 (que é moderna) consiga reproduzir nativamente. Recomendamos converter o arquivo ou assistir diretamente no celular."
                }
                return CompatibilityCheckResult(
                    isCompatible = false,
                    warningMessage = msg,
                    isGen2OrOlder = true,
                    reason = reasonText
                )
            }
        }

        return CompatibilityCheckResult(isCompatible = true)
    }
}
