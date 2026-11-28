package io.github.goldengate.desktop.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * A real terminal: commands run through Android's own `/system/bin/sh` inside the app's sandbox.
 *
 * Android runs them with the desktop's UID, so the app's private home is writable and the rest of
 * the system is read-only (root is not required and not used). Built-ins ([cd], [clear], [exit],
 * [help], [pwd], [history]) are handled here so the working directory survives between commands.
 */
class AndroidShell(val home: File, private val host: ShellHost) {
    var cwd: File = home
        private set

    val history = mutableListOf<String>()

    var lastExitCode: Int = 0
        private set

    fun prompt(): String =
        "${host.userName}@${host.hostName.replace(' ', '-')} ${displayPath(cwd).substringAfterLast('/').ifEmpty { "~" }} % "

    fun displayPath(file: File): String {
        val path = file.absolutePath
        val root = home.absolutePath
        return when {
            path == root -> "~"
            path.startsWith("$root/") -> "~/" + path.removePrefix("$root/")
            else -> path
        }
    }

    /** Resolves a typed path against the current directory. Unlike the simulated shell this can
     *  point anywhere the app's UID can read; the OS enforces permissions. */
    fun resolve(path: String): File = when {
        path.isEmpty() || path == "~" -> home
        path.startsWith("~/") -> File(home, path.removePrefix("~/"))
        path.startsWith("/") -> File(path)
        else -> File(cwd, path)
    }

    fun complete(line: String): String {
        val parts = line.split(' ')
        val last = parts.last()
        val dirPart = last.substringBeforeLast('/', "")
        val namePart = last.substringAfterLast('/')
        val dir = if (dirPart.isEmpty()) cwd else resolve(dirPart)
        val matches = dir.listFiles()
            ?.filter { it.name.startsWith(namePart) && !it.name.startsWith(".") }
            ?.sortedBy { it.name.lowercase() }
            ?: return line
        if (matches.size != 1) return line
        val completed = (if (dirPart.isNotEmpty()) "$dirPart/" else "") + matches[0].name +
            (if (matches[0].isDirectory) "/" else "")
        return (parts.dropLast(1) + completed).joinToString(" ")
    }

    suspend fun run(input: String): ShellResult = withContext(Dispatchers.IO) {
        val line = input.trim()
        if (line.isEmpty()) return@withContext ShellResult("")
        history += line
        val args = line.split(Regex("\\s+"))
        when (args.first()) {
            "clear" -> return@withContext ShellResult("", clear = true)
            "exit" -> return@withContext ShellResult("[Process completed]", exit = true)
            "help" -> return@withContext ShellResult(HELP)
            "pwd" -> return@withContext ShellResult(displayPath(cwd))
            "history" -> return@withContext ShellResult(
                history.mapIndexed { i, s -> "%5d  %s".format(i + 1, s) }.joinToString("\n")
            )
            "cd" -> {
                val typed = args.getOrElse(1) { "~" }
                val target = resolve(typed)
                return@withContext when {
                    !target.exists() -> ShellResult("cd: no such file or directory: $typed")
                    !target.isDirectory -> ShellResult("cd: not a directory: $typed")
                    !target.canRead() -> ShellResult("cd: permission denied: $typed")
                    else -> { cwd = target; host.changed(); ShellResult("") }
                }
            }
        }

        val output = StringBuilder()
        try {
            val process = ProcessBuilder("/system/bin/sh", "-c", line)
                .directory(cwd)
                .redirectErrorStream(true)
                .apply {
                    environment()["HOME"] = home.absolutePath
                    environment()["PWD"] = cwd.absolutePath
                    environment()["TERM"] = "xterm-256color"
                    environment()["PATH"] =
                        "/product/bin:/apex/com.android.runtime/bin:/apex/com.android.art/bin:" +
                        "/system_ext/bin:/system/bin:/system/xbin:/odm/bin:/vendor/bin:/vendor/xbin"
                }
                .start()
            val reader = Thread {
                runCatching {
                    process.inputStream.bufferedReader().use { stream ->
                        val buffer = CharArray(4096)
                        while (true) {
                            val n = stream.read(buffer)
                            if (n <= 0) break
                            synchronized(output) { output.append(buffer, 0, n) }
                        }
                    }
                }
            }.apply { isDaemon = true; start() }
            val finished = process.waitFor(30, TimeUnit.SECONDS)
            if (!finished) process.destroyForcibly()
            runCatching { reader.join(800) }
            lastExitCode = if (finished) runCatching { process.exitValue() }.getOrDefault(0) else 124
            val text = synchronized(output) { output.toString() }.trimEnd()
            if (finished) ShellResult(text) else ShellResult(("$text\n[timed out after 30s]").trim())
        } catch (t: Throwable) {
            ShellResult("sh: ${t.message ?: "couldn't start the shell"}")
        }
    }

    companion object {
        val HELP = """
            Real Android shell — /system/bin/sh, running as Golden Gate Desktop's user.
              ls, cat, echo, mkdir, touch, rm, mv, cp   real file commands (HOME = ~)
              getprop, settings, dumpsys, uname, id      real Android tools (read-only where Android restricts)
              df -h, ps -A, uptime, date                 real system information
              ping, curl, wget                          real network tools when the device ships them
              cd, pwd, clear, history, exit, help       handled by the terminal itself
            Commands run with this app's permissions, so ~ is writable and the rest of the
            system is read-only. Nothing here requires root.
        """.trimIndent()
    }
}
