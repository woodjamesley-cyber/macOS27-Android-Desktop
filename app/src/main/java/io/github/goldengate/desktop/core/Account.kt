package io.github.goldengate.desktop.core

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * A local, device-only account created in the Setup Assistant.
 *
 * There is no server: the password never leaves the device and is stored as a salted SHA-256
 * hash (not in clear) in the desktop's private SharedPreferences.
 */
object LocalAccount {
    const val MIN_PASSWORD_LENGTH = 6

    private val emailPattern = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$")

    fun isValidEmail(value: String): Boolean = emailPattern.matches(value.trim())

    fun newSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun hash(password: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt.toByteArray(Charsets.UTF_8))
        return digest.digest(password.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    fun verify(password: String, salt: String, hash: String): Boolean =
        hash.isNotBlank() &&
            MessageDigest.isEqual(hash(password, salt).toByteArray(Charsets.UTF_8), hash.toByteArray(Charsets.UTF_8))

    /** Two-letter monogram used by the account avatar and the lock screen. */
    fun initials(name: String, email: String = ""): String {
        val source = name.trim().ifBlank { email.substringBefore('@') }
        val parts = source.split(Regex("\\s+")).filter { it.isNotBlank() }
        val letters = when {
            parts.size >= 2 -> "${parts.first().first()}${parts.last().first()}"
            source.isNotEmpty() -> source.take(2)
            else -> "?"
        }
        return letters.uppercase()
    }

    /** Avatar gradient pairs (start, end) in ARGB. */
    val avatarGradients: List<Pair<Long, Long>> = listOf(
        0xFF7C8CE0 to 0xFFB8C6FF,
        0xFFE0605C to 0xFFF7A97F,
        0xFF34A853 to 0xFF9BE38F,
        0xFF9B59B6 to 0xFFD8A7F0,
        0xFFF2A65A to 0xFFE05A7A,
        0xFF3A9AD9 to 0xFF8FD3F4,
    )

    fun gradient(index: Int): Pair<Long, Long> = avatarGradients[index.mod(avatarGradients.size)]
}
