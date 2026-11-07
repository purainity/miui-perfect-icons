package me.purainity.miuiperfecticons.ui

/** 以项目为单位展示直接依赖、传递依赖和随应用分发的资源 */
data class OpenSourceProject(
    val name: String,
    val developer: String,
    val license: String,
    val url: String,
    val dependencyPrefixes: List<String> = emptyList(),
    val excludedPrefixes: List<String> = emptyList(),
)

object OpenSourceProjects {
    val all = listOf(
        OpenSourceProject("Perfect-Icons-APP", "Perfect Icons Project Team", "GPL-3.0", "https://github.com/pzcn/MIUI-Icons-APP"),
        OpenSourceProject("Magisk Module Installer", "topjohnwu", "GPL-3.0", "https://github.com/topjohnwu/Magisk/blob/master/scripts/module_installer.sh"),
        OpenSourceProject("Material Symbols", "Google", "Apache License 2.0", "https://github.com/google/material-design-icons"),
        OpenSourceProject("Remix Icon", "Remix Design", "Apache License 2.0", "https://github.com/Remix-Design/RemixIcon/tree/v4.6.0"),
        OpenSourceProject("AndroidX", "Android Open Source Project", "Apache License 2.0", "https://github.com/androidx/androidx", listOf("androidx."), listOf("androidx.databinding:")),
        OpenSourceProject("View Binding", "Android Open Source Project", "Apache License 2.0", "https://android.googlesource.com/platform/frameworks/data-binding/", listOf("androidx.databinding:")),
        OpenSourceProject("Material Components", "Google", "Apache License 2.0", "https://github.com/material-components/material-components-android", listOf("com.google.android.material:material:")),
        OpenSourceProject("Kotlin", "JetBrains", "Apache License 2.0", "https://github.com/JetBrains/kotlin", listOf("org.jetbrains.kotlin:kotlin-stdlib:")),
        OpenSourceProject("kotlinx.coroutines", "JetBrains", "Apache License 2.0", "https://github.com/Kotlin/kotlinx.coroutines", listOf("org.jetbrains.kotlinx:kotlinx-coroutines-")),
        OpenSourceProject("kotlinx.serialization", "JetBrains", "Apache License 2.0", "https://github.com/Kotlin/kotlinx.serialization", listOf("org.jetbrains.kotlinx:kotlinx-serialization-")),
        OpenSourceProject("JetBrains Annotations", "JetBrains", "Apache License 2.0", "https://github.com/JetBrains/java-annotations", listOf("org.jetbrains:annotations:")),
        OpenSourceProject("Guava", "Google", "Apache License 2.0", "https://github.com/google/guava", listOf("com.google.guava:listenablefuture:")),
        OpenSourceProject("Error Prone", "Google", "Apache License 2.0", "https://github.com/google/error-prone", listOf("com.google.errorprone:error_prone_annotations:")),
        OpenSourceProject("JSpecify", "JSpecify Authors", "Apache License 2.0", "https://github.com/jspecify/jspecify", listOf("org.jspecify:jspecify:")),
    )
}
