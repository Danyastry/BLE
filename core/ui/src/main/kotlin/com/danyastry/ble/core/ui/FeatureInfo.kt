package com.danyastry.ble.core.ui

data class FeatureInfo(
    val id: String,
    val name: String,
    val category: FeatureCategory,
    val summary: String,
    val minSdk: Int = AndroidVersion.BASELINE_SDK,
) {
    init {
        require(ID_PATTERN.matches(id)) { "Feature id must be lowercase kebab-case, was '$id'" }
        require(name.isNotBlank()) { "Feature name must not be blank" }
        require(minSdk >= AndroidVersion.BASELINE_SDK) { "minSdk must be at least ${AndroidVersion.BASELINE_SDK}, was $minSdk" }
    }

    fun isSupportedOn(deviceSdk: Int): Boolean = deviceSdk >= minSdk

    private companion object {
        val ID_PATTERN = Regex("[a-z0-9]+(-[a-z0-9]+)*")
    }
}
