package io.github.goldengate.desktop.core

import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Host services the shell can call. Kept as an interface so the shell is unit-testable on the JVM. */
interface ShellHost {
    val userName: String
    val hostName: String
    fun open(target: File): Boolean = false
    fun launchApp(name: String): Boolean = false
    fun notify(title: String, body: String) {}
    fun processes(): List<String> = emptyList()
    fun changed() {}
}

data class ShellResult(val output: String, val clear: Boolean = false, val exit: Boolean = false)

/**
 * A small zsh-flavoured command interpreter sandboxed to the desktop's private home folder.
 * It never touches the Android filesystem outside [home]; `cd ..` stops at the home root.
 */
class Shell(val home: File, private val host: ShellHost) {
    var cwd: File = home
        private set
    val history = mutableListOf<String>()

    val commands = listOf(
        "cat", "calc", "cd", "clear", "cp", "date", "echo", "exit", "find", "head", "help", "history",
        "ls", "mkdir", "mv", "notify", "open", "ps", "pwd", "rm", "touch", "uname", "wc", "whoami",
    )

    fun prompt(): String = "${host.userName}@${host.hostName.replace(' ', '-')} ${displayPath(cwd).substringAfterLast('/').ifEmpty { "~" }} % "

    fun displayPath(f: File): String {
        val p = f.canonicalPath
        val h = home.canonicalPath
        return if (p == h) "~" else if (p.startsWith("$h/")) "~/" + p.removePrefix("$h/") else p
    }

    /** Resolves a user path; returns null when it would escape the sandbox. */
    fun resolve(path: String): File? {
        val f = when {
            path == "~" || path.isEmpty() -> home
            path.startsWith("~/") -> File(home, path.removePrefix("~/"))
            path.startsWith("/") -> File(home, path.removePrefix("/"))
            else -> File(cwd, path)
        }
        val canon = f.canonicalFile
        val h = home.canonicalFile
        return if (canon == h || canon.path.startsWith(h.path + File.separator)) canon else null
    }

    fun complete(line: String): String {
        val parts = line.split(' ')
        val last = parts.last()
        if (parts.size == 1) {
            val m = commands.filter { it.startsWith(last) }
            return if (m.size == 1) m[0] + " " else line
        }
        val dirPart = last.substringBeforeLast('/', "")
        val namePart = last.substringAfterLast('/')
        val dir = resolve(dirPart.ifEmpty { "." }) ?: return line
        val m = dir.listFiles()?.filter { it.name.startsWith(namePart) && !it.name.startsWith(".") }?.sortedBy { it.name } ?: return line
        if (m.size != 1) return line
        val completed = (if (dirPart.isNotEmpty()) "$dirPart/" else "") + m[0].name + if (m[0].isDirectory) "/" else ""
        return (parts.dropLast(1) + completed).joinToString(" ")
    }

    fun run(input: String): ShellResult {
        val line = input.trim()
        if (line.isEmpty()) return ShellResult("")
        history.add(line)
        // Output redirection: cmd > file  /  cmd >> file
        var redirect: Pair<String, Boolean>? = null
        var body = line
        val idx = line.indexOf('>')
        if (idx > 0 && !line.startsWith("calc")) {
            val append = line.getOrNull(idx + 1) == '>'
            redirect = line.substring(idx + if (append) 2 else 1).trim() to append
            body = line.substring(0, idx).trim()
        }
        val args = tokenize(body)
        if (args.isEmpty()) return ShellResult("")
        val result = try { exec(args.first(), args.drop(1)) } catch (t: Throwable) { ShellResult("${args.first()}: ${t.message ?: "error"}") }
        if (redirect != null) {
            val target = resolve(redirect.first) ?: return ShellResult("zsh: permission denied: ${redirect.first}")
            if (target.isDirectory) return ShellResult("zsh: is a directory: ${redirect.first}")
            val text = if (result.output.isEmpty()) "" else result.output + "\n"
            if (redirect.second) target.appendText(text) else target.writeText(text)
            host.changed()
            return ShellResult("")
        }
        return result
    }

    private fun tokenize(s: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var quote: Char? = null
        var any = false
        for (ch in s) {
            when {
                quote != null && ch == quote -> quote = null
                quote != null -> cur.append(ch)
                ch == '"' || ch == '\'' -> { quote = ch; any = true }
                ch == ' ' -> { if (cur.isNotEmpty() || any) { out += cur.toString(); cur.clear(); any = false } }
                else -> cur.append(ch)
            }
        }
        if (cur.isNotEmpty() || any) out += cur.toString()
        return out
    }

    private fun err(cmd: String, msg: String) = ShellResult("$cmd: $msg")

    private fun exec(cmd: String, a: List<String>): ShellResult = when (cmd) {
        "help" -> ShellResult(
            "Golden Gate Desktop shell — sandboxed to your home folder.\n" +
                "  ls [-la] [dir]      list files         cd [dir]        change directory\n" +
                "  pwd                 print directory    cat file        print a file\n" +
                "  echo text [> file]  print / write      mkdir [-p] dir  make folder\n" +
                "  touch file          create file        rm [-r] path    delete permanently\n" +
                "  mv src dst          move / rename      cp [-r] src dst copy\n" +
                "  head [-n N] file    first lines        wc file         count lines/words\n" +
                "  find [name]         search names       open path|app   open in desktop\n" +
                "  notify title [msg]  post notification  calc expr       calculator\n" +
                "  date  whoami  uname [-a]  ps  history  clear  exit"
        )
        "pwd" -> ShellResult(displayPath(cwd))
        "whoami" -> ShellResult(host.userName.lowercase().replace(' ', '.'))
        "uname" -> ShellResult(if (a.contains("-a")) "GoldenGate ${host.hostName} 27.0 Darwin-like-shell (Android JVM) arm64" else "GoldenGate")
        "date" -> ShellResult(SimpleDateFormat("EEE MMM d HH:mm:ss zzz yyyy", Locale.US).format(Date()))
        "clear" -> ShellResult("", clear = true)
        "exit" -> ShellResult("[Process completed]", exit = true)
        "history" -> ShellResult(history.mapIndexed { i, s -> "%5d  %s".format(i + 1, s) }.joinToString("\n"))
        "echo" -> ShellResult(a.joinToString(" "))
        "ps" -> ShellResult((listOf("  PID TTY      CMD") + host.processes().mapIndexed { i, p -> "%5d ttys000  %s".format(100 + i * 7, p) }).joinToString("\n"))
        "calc" -> {
            val expr = a.joinToString(" ")
            if (expr.isBlank()) err(cmd, "usage: calc expression") else ShellResult(CalcEngine.format(CalcEngine.evaluate(expr)))
        }
        "cd" -> {
            val target = resolve(a.firstOrNull() ?: "~")
            when {
                target == null -> err("cd", "permission denied: ${a.first()}")
                !target.exists() -> err("cd", "no such file or directory: ${a.first()}")
                !target.isDirectory -> err("cd", "not a directory: ${a.first()}")
                else -> { cwd = target; ShellResult("") }
            }
        }
        "ls" -> {
            val flags = a.filter { it.startsWith("-") }.joinToString("")
            val paths = a.filterNot { it.startsWith("-") }
            val dir = resolve(paths.firstOrNull() ?: ".")
            when {
                dir == null -> err("ls", "permission denied")
                !dir.exists() -> err("ls", "${paths.first()}: No such file or directory")
                dir.isFile -> ShellResult(dir.name)
                else -> {
                    val all = flags.contains('a')
                    val items = (dir.listFiles() ?: emptyArray()).filter { all || !it.name.startsWith(".") }.sortedBy { it.name.lowercase() }
                    if (flags.contains('l')) {
                        val fmt = SimpleDateFormat("MMM d HH:mm", Locale.US)
                        ShellResult(items.joinToString("\n") { f ->
                            "%s  %8d  %s  %s".format(if (f.isDirectory) "drwxr-xr-x" else "-rw-r--r--", if (f.isDirectory) 0 else f.length(), fmt.format(Date(f.lastModified())), f.name + if (f.isDirectory) "/" else "")
                        })
                    } else ShellResult(items.joinToString("  ") { it.name + if (it.isDirectory) "/" else "" })
                }
            }
        }
        "cat" -> {
            if (a.isEmpty()) err("cat", "usage: cat file") else ShellResult(a.joinToString("\n") { p ->
                val f = resolve(p)
                when {
                    f == null -> "cat: $p: Permission denied"
                    !f.exists() -> "cat: $p: No such file or directory"
                    f.isDirectory -> "cat: $p: Is a directory"
                    f.length() > 1_000_000 -> "cat: $p: File too large to display"
                    else -> f.readText().trimEnd('\n')
                }
            })
        }
        "head" -> {
            var n = 10
            val rest = a.toMutableList()
            val i = rest.indexOf("-n")
            if (i >= 0 && i + 1 < rest.size) { n = rest[i + 1].toIntOrNull() ?: 10; rest.removeAt(i + 1); rest.removeAt(i) }
            val f = rest.firstOrNull()?.let { resolve(it) }
            if (f == null || !f.isFile) err("head", "${rest.firstOrNull() ?: ""}: No such file") else ShellResult(f.useLines { it.take(n).joinToString("\n") })
        }
        "wc" -> {
            val f = a.firstOrNull()?.let { resolve(it) }
            if (f == null || !f.isFile) err("wc", "${a.firstOrNull() ?: ""}: No such file") else {
                val t = f.readText()
                ShellResult("%8d %8d %8d %s".format(t.count { it == '\n' }, t.split(Regex("\\s+")).count { it.isNotEmpty() }, t.toByteArray().size, a.first()))
            }
        }
        "mkdir" -> {
            val p = a.contains("-p")
            val names = a.filterNot { it.startsWith("-") }
            if (names.isEmpty()) err("mkdir", "usage: mkdir [-p] directory") else {
                val out = names.mapNotNull { n ->
                    val f = resolve(n)
                    when {
                        f == null -> "mkdir: $n: Permission denied"
                        f.exists() -> "mkdir: $n: File exists"
                        (if (p) f.mkdirs() else f.mkdir()) -> null
                        else -> "mkdir: $n: No such file or directory"
                    }
                }
                host.changed(); ShellResult(out.joinToString("\n"))
            }
        }
        "touch" -> {
            if (a.isEmpty()) err("touch", "usage: touch file") else {
                val out = a.mapNotNull { n ->
                    val f = resolve(n)
                    when {
                        f == null -> "touch: $n: Permission denied"
                        f.exists() -> { f.setLastModified(System.currentTimeMillis()); null }
                        f.parentFile?.isDirectory != true -> "touch: $n: No such file or directory"
                        else -> { f.createNewFile(); null }
                    }
                }
                host.changed(); ShellResult(out.joinToString("\n"))
            }
        }
        "rm" -> {
            val rec = a.any { it.startsWith("-") && it.contains('r') }
            val names = a.filterNot { it.startsWith("-") }
            if (names.isEmpty()) err("rm", "usage: rm [-r] file ...") else {
                val out = names.mapNotNull { n ->
                    val f = resolve(n)
                    when {
                        f == null || f == home.canonicalFile -> "rm: $n: Operation not permitted"
                        !f.exists() -> "rm: $n: No such file or directory"
                        f.isDirectory && !rec -> "rm: $n: is a directory"
                        else -> { if (f.isDirectory) f.deleteRecursively() else f.delete(); null }
                    }
                }
                host.changed(); ShellResult(out.joinToString("\n"))
            }
        }
        "mv", "cp" -> {
            val names = a.filterNot { it.startsWith("-") }
            if (names.size != 2) err(cmd, "usage: $cmd source target") else {
                val src = resolve(names[0]); var dst = resolve(names[1])
                when {
                    src == null || dst == null -> err(cmd, "Permission denied")
                    !src.exists() -> err(cmd, "${names[0]}: No such file or directory")
                    else -> {
                        if (dst.isDirectory) dst = File(dst, src.name)
                        if (dst.canonicalPath.startsWith(src.canonicalPath + File.separator)) err(cmd, "cannot move a folder into itself")
                        else {
                            if (cmd == "mv") { if (!src.renameTo(dst)) { src.copyRecursively(dst, true); src.deleteRecursively() } }
                            else src.copyRecursively(dst, true)
                            host.changed(); ShellResult("")
                        }
                    }
                }
            }
        }
        "find" -> {
            val q = a.lastOrNull { !it.startsWith("-") }?.trim('*') ?: ""
            ShellResult(cwd.walkTopDown().filter { it != cwd && !it.name.startsWith(".") && it.name.contains(q, true) }.take(200)
                .joinToString("\n") { "./" + it.relativeTo(cwd).path })
        }
        "open" -> {
            val t = a.filterNot { it.startsWith("-") }
            if (t.isEmpty()) err("open", "usage: open path | open -a App")
            else if (a.contains("-a")) { if (host.launchApp(t.joinToString(" "))) ShellResult("") else err("open", "Unable to find application named '${t.joinToString(" ")}'") }
            else {
                val f = resolve(t.first())
                when {
                    f != null && f.exists() -> { host.open(f); ShellResult("") }
                    host.launchApp(t.joinToString(" ")) -> ShellResult("")
                    else -> err("open", "The file ${t.first()} does not exist.")
                }
            }
        }
        "notify" -> {
            if (a.isEmpty()) err("notify", "usage: notify title [message]") else { host.notify(a.first(), a.drop(1).joinToString(" ")); ShellResult("") }
        }
        else -> ShellResult("zsh: command not found: $cmd")
    }
}
