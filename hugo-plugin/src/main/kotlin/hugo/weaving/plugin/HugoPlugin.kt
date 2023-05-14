package hugo.weaving.plugin

import com.android.build.gradle.AbstractAppExtension
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.api.AndroidBasePlugin
import org.aspectj.bridge.IMessage
import org.aspectj.bridge.MessageHandler
import org.aspectj.tools.ajc.Main
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile
import java.io.File

class HugoPlugin : Plugin<Project> {

    override fun apply(project: Project) {

        val hasApp = project.plugins.hasPlugin(AndroidBasePlugin::class.java)
        if (!hasApp) {
            throw IllegalStateException("'android' or 'android-library' plugin required.")
        }

        val baseExtension = project.extensions.getByName("android") as? BaseExtension ?: return

        val libraryExtension = baseExtension as? LibraryExtension

        val appExtension = baseExtension as? AbstractAppExtension

        val variants =
            libraryExtension?.libraryVariants ?: appExtension?.applicationVariants ?: return


        project.dependencies.apply {
            add("debugImplementation", "com.jakewharton.hugo:hugo-runtime:1.2.2-SNAPSHOT")
        }

        project.extensions.create("hugo", HugoExtension::class.java)


        val log = project.logger


        variants.all { variant ->
            if (!variant.buildType.isDebuggable) {
                log.debug("Skipping non-debuggable build type '${variant.buildType.name}'.")
                return@all
            } else if (!(project.extensions.getByName("hugo") as HugoExtension).enabled) {
                log.debug("Hugo is not disabled.")
                return@all
            }

            val javaCompile: JavaCompile = variant.javaCompileProvider.get()

            javaCompile.doLast {
                val args = arrayOf(
                    "-showWeaveInfo",
                    "-1.8",
                    "-inpath",
                    javaCompile.destinationDirectory.asFile.orNull.toString(),
                    "-aspectpath",
                    javaCompile.classpath.asPath,
                    "-d",
                    javaCompile.destinationDirectory.asFile.orNull.toString(),
                    "-classpath",
                    javaCompile.classpath.asPath,
                    "-bootclasspath",
                    baseExtension.bootClasspath.joinToString(File.pathSeparator)
                )

                log.debug("ajc args: " + args.contentToString())

                val handler = MessageHandler(true)
                Main().run(args, handler)
                for (message: IMessage in handler.getMessages(null, true)) {
                    when (message.kind) {
                        IMessage.ABORT, IMessage.ERROR, IMessage.FAIL -> {
                            log.error(message.message, message.thrown)
                        }

                        IMessage.WARNING -> {
                            log.warn(message.message, message.thrown)
                        }

                        IMessage.INFO -> {
                            log.info(message.message, message.thrown)
                        }

                        IMessage.DEBUG -> {
                            log.debug(message.message, message.thrown)
                        }

                        else -> {

                        }
                    }
                }
            }
        }
    }
}
