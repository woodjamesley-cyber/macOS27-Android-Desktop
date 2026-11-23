package io.github.goldengate.desktop

import io.github.goldengate.desktop.core.Shell
import io.github.goldengate.desktop.core.ShellHost
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ShellTest {
    private lateinit var home: File
    private lateinit var sh: Shell
    private val notes = mutableListOf<String>()
    private val opened = mutableListOf<String>()

    @Before fun setUp() {
        home = Files.createTempDirectory("ggshell").toFile()
        sh = Shell(home, object : ShellHost {
            override val userName = "Alex"
            override val hostName = "Golden Gate"
            override fun notify(title: String, body: String) { notes += "$title|$body" }
            override fun launchApp(name: String) = (name == "Notes").also { if (it) opened += name }
            override fun open(target: File): Boolean { opened += target.name; return true }
        })
    }

    @After fun tearDown() { home.deleteRecursively() }

    @Test fun filesystemCommands() {
        sh.run("mkdir -p a/b")
        assertTrue(File(home, "a/b").isDirectory)
        sh.run("cd a")
        assertEquals("~/a", sh.run("pwd").output)
        sh.run("echo hello world > note.txt")
        assertEquals("hello world", sh.run("cat note.txt").output)
        sh.run("echo again >> note.txt")
        assertEquals("hello world\nagain", sh.run("cat note.txt").output)
        assertEquals("b/  note.txt", sh.run("ls").output)
        sh.run("cp note.txt b")
        assertTrue(File(home, "a/b/note.txt").exists())
        sh.run("mv note.txt renamed.txt")
        assertTrue(File(home, "a/renamed.txt").exists())
        sh.run("rm renamed.txt")
        assertFalse(File(home, "a/renamed.txt").exists())
        assertTrue(sh.run("rm b").output.contains("is a directory"))
        sh.run("rm -r b")
        assertFalse(File(home, "a/b").exists())
        sh.run("touch x.md")
        assertTrue(File(home, "a/x.md").exists())
    }

    @Test fun sandboxPreventsEscapingHome() {
        sh.run("cd ..")
        assertEquals("~", sh.run("pwd").output)
        assertTrue(sh.run("cd ../../..").output.contains("permission denied"))
        assertTrue(sh.run("cat ../../etc/passwd").output.contains("Permission denied"))
        assertTrue(sh.run("rm -r ~").output.contains("not permitted"))
        sh.run("cd /")
        assertEquals("~", sh.run("pwd").output)
    }

    @Test fun quotingCalcHistoryAndUnknown() {
        assertEquals("a  b", sh.run("echo \"a  b\"").output)
        assertEquals("7", sh.run("calc 1+2*3").output)
        assertTrue(sh.run("frobnicate").output.contains("command not found"))
        assertTrue(sh.run("history").output.contains("calc 1+2*3"))
        assertTrue(sh.run("clear").clear)
        assertTrue(sh.run("exit").exit)
    }

    @Test fun hostIntegration() {
        sh.run("notify Hello \"from the shell\"")
        assertEquals(listOf("Hello|from the shell"), notes)
        sh.run("open -a Notes")
        sh.run("touch f.txt")
        sh.run("open f.txt")
        assertEquals(listOf("Notes", "f.txt"), opened)
        assertTrue(sh.run("open -a Nope").output.contains("Unable to find"))
    }

    @Test fun tabCompletion() {
        sh.run("mkdir Documents")
        assertEquals("cd Documents/", sh.complete("cd Doc"))
        assertEquals("history ", sh.complete("hist"))
        assertEquals("whoami", sh.run("whoami").output.let { "whoami" })
        assertEquals("alex", sh.run("whoami").output)
    }

    @Test fun headAndWc() {
        File(home, "l.txt").writeText((1..20).joinToString("\n") + "\n")
        assertEquals("1\n2\n3", sh.run("head -n 3 l.txt").output)
        assertTrue(sh.run("wc l.txt").output.trim().startsWith("20"))
    }
}
