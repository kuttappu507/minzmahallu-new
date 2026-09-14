package com.mms.minzmahallu.data.repository

import com.mms.minzmahallu.data.db.DatabaseManager
import com.mms.minzmahallu.data.model.AuthUser
import com.mms.minzmahallu.util.Crypto
import com.mms.minzmahallu.util.Format

/** Port of electron/services/auth.service.ts — PBKDF2-SHA256, lockout, initial setup. */
class AuthService(private val db: DatabaseManager) {
    var currentUser: AuthUser? = null
        private set

    private val seededHashes = setOf(
        """pbkdf2_sha256${'$'}200000${'$'}c2FsdC1mb3ItbW1zLWFkbWluLXVzZXI=${'$'}dJvtGdhlhx7H/9KuwAZs4U/j/DjiiDA88txKk9SnqTU=""",
        """pbkdf2_sha256${'$'}200000${'$'}zRLKI0xyc2sYKBzQaWXl6w==${'$'}qHO4yvos81/Oah+ECzVbh1ZHPz3rEhRHOJT2criWCPg="""
    )

    fun needsInitialSetup(): Boolean {
        return try {
            val count = (db.scalar("SELECT COUNT(*) FROM users") as? Number)?.toLong() ?: 0L
            if (count == 0L) return true
            if (count == 1L && seededAdmin() != null) return true
            false
        } catch (e: Exception) {
            android.util.Log.w("Auth", "needsInitialSetup failed, assuming setup needed: ${e.message}")
            true
        }
    }

    private fun seededAdmin(): Map<String, Any?>? {
        val placeholders = seededHashes.joinToString(",") { "?" }
        return db.one(
            "SELECT id,username,full_name,password_hash FROM users WHERE id=1 AND username='admin' AND password_hash IN ($placeholders)",
            seededHashes.toTypedArray()
        )
    }

    fun createInitialAdministrator(username: String, fullName: String, password: String): AuthUser {
        check(needsInitialSetup()) { "Initial setup has already been completed" }
        val u = username.trim()
        val n = fullName.trim()
        require(Regex("^[A-Za-z0-9._-]{3,32}$").matches(u)) {
            "Username must be 3-32 characters and contain only letters, numbers, dot, underscore or hyphen"
        }
        require(n.isNotEmpty()) { "Full name is required" }
        val (stored, salt) = Crypto.hashPassword(password)
        val placeholder = seededAdmin()
        val id = if (placeholder != null) {
            val pid = Format.long(placeholder, "id")
            db.run(
                "UPDATE users SET username=?,full_name=?,password_hash=?,password_salt=?,role='Administrator',is_active=1,is_locked=0,failed_attempts=0,locked_until=NULL,must_change_pwd=0,updated_at=datetime('now') WHERE id=?",
                arrayOf(u, n, stored, salt, pid)
            )
            pid
        } else {
            db.run(
                "INSERT INTO users (username,full_name,password_hash,password_salt,role,is_active,is_locked,failed_attempts,must_change_pwd) VALUES (?,?,?,?, 'Administrator',1,0,0,0)",
                arrayOf(u, n, stored, salt)
            )
        }
        val user = AuthUser(id, u, n, "Administrator", true, false, Crypto.initials(n))
        currentUser = user
        audit("LOGIN", "auth", id, "Initial administrator created and signed in")
        return user
    }

    fun login(username: String, password: String): AuthUser {
        require(username.isNotBlank() && password.isNotBlank()) { "Username and password are required" }
        check(!needsInitialSetup()) { "Initial account setup is required" }
        val user = db.one(
            "SELECT id,username,full_name,password_hash,password_salt,role,is_active,is_locked,failed_attempts,locked_until,must_change_pwd FROM users WHERE username = ? COLLATE NOCASE",
            arrayOf(username.trim())
        ) ?: error("Invalid username or password")
        if (Format.long(user, "is_active") == 0L) error("Account is inactive — contact administrator")
        val hash = Format.str(user, "password_hash")
        if (!Crypto.verifyPassword(password, hash)) {
            val attempts = Format.long(user, "failed_attempts") + 1
            val id = Format.long(user, "id")
            if (attempts >= 5) {
                db.run("UPDATE users SET failed_attempts=?,is_locked=1,locked_until=datetime('now','+15 minutes'),updated_at=datetime('now') WHERE id=?", arrayOf(attempts, id))
            } else {
                db.run("UPDATE users SET failed_attempts=?,updated_at=datetime('now') WHERE id=?", arrayOf(attempts, id))
            }
            error("Invalid username or password")
        }
        val id = Format.long(user, "id")
        db.run("UPDATE users SET last_login_at=datetime('now'),failed_attempts=0,is_locked=0,locked_until=NULL,updated_at=datetime('now') WHERE id=?", arrayOf(id))
        val authUser = AuthUser(
            id = id,
            username = Format.str(user, "username"),
            fullName = Format.str(user, "full_name"),
            role = Format.str(user, "role"),
            isActive = true,
            mustChangePwd = Format.long(user, "must_change_pwd") == 1L,
            initials = Crypto.initials(Format.str(user, "full_name"))
        )
        currentUser = authUser
        audit("LOGIN", "auth", id, "User signed in")
        return authUser
    }

    fun logout() {
        currentUser?.let { audit("LOGOUT", "auth", it.id, "User signed out") }
        currentUser = null
    }

    fun changePassword(userId: Long, newPassword: String) {
        val actor = currentUser ?: error("Authentication is required")
        require(actor.id == userId || actor.role == "Administrator") { "You can only change your own password" }
        val (stored, salt) = Crypto.hashPassword(newPassword)
        db.run("UPDATE users SET password_hash=?,password_salt=?,must_change_pwd=0,failed_attempts=0,is_locked=0,locked_until=NULL,updated_at=datetime('now') WHERE id=?", arrayOf(stored, salt, userId))
        audit("PASSWORD_CHANGE", "users", userId, "Password changed")
    }

    fun verifyAdminPassword(password: String): AuthUser {
        val actor = currentUser ?: error("Authentication is required")
        require(actor.role == "Administrator") { "Administrator permission is required for this operation" }
        require(password.isNotBlank()) { "Administrator password is required" }
        val user = db.one("SELECT id,password_hash,is_active,is_locked FROM users WHERE id=?", arrayOf(actor.id.toString()))
            ?: error("User not found")
        if (Format.long(user, "is_active") == 0L) error("Account is inactive")
        if (Format.long(user, "is_locked") == 1L) error("Account is locked")
        if (!Crypto.verifyPassword(password, Format.str(user, "password_hash"))) error("Incorrect administrator password")
        return actor
    }

    fun audit(action: String, module: String?, entityId: Long?, description: String) {
        val u = currentUser
        try {
            db.run(
                "INSERT INTO audit_log (user_id, username, action, module, entity_id, description) VALUES (?,?,?,?,?,?)",
                arrayOf(u?.id, u?.username ?: "system", action, module, entityId, description)
            )
        } catch (_: Exception) { }
    }
}
