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
        probe: MediaProbeResult?
    ): CompatibilityCheckResult {
        if (probe == null) {
            return CompatibilityCheckResult(isCompatible = true)
        }

        val isLegacyChromecast = isChromecastGen2OrOlder(deviceModel, deviceName)

        if (isLegacyChromecast) {
            val reasons = mutableListOf<String>()

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
                val msg = "Este vídeo possui $reasonText. O Chromecast (2ª geração) não possui decodificador de hardware para este formato."
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
