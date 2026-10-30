package org.stypox.dicio.unicodeCldrPlugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import com.android.build.gradle.BaseExtension

class UnicodeCldrPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val languagesTask = target.tasks.create("unicodeCldrLanguages", UnicodeCldrLanguagesTask::class.java)

        // make sure the generated kotlin files are compiled by adding them to the source sets; note
        // that this also makes sure any task depending on source sets also depends on this task
        // https://slack-chats.kotlinlang.org/t/486810
        // AGP 9 compiles Kotlin itself and no longer exposes Kotlin source sets, so the generated
        // files go through the Android "main" source set, which only takes plain files; the task
        // dependency comes from the inputs.dir() below
        target.extensions
            .getByType(BaseExtension::class.java)
            .sourceSets
            .getByName("main")
            .kotlin
            .srcDir(languagesTask.outputDir.get().asFile)
        target.tasks
            .matching { it.name.contains("Kotlin") }
            .configureEach { inputs.dir(languagesTask.outputDir) }
    }
}
