package com.music.bitchord.desktop

import com.music.bitchord.desktop.secrets.SecretServiceApi
import org.freedesktop.dbus.DBusPath
import org.freedesktop.dbus.connections.impl.DBusConnection
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder
import org.freedesktop.dbus.types.Variant

/** The platform's own password store, for the credentials this app holds. */
internal object DesktopSecretStore {

    /** Plain-text transport. */
    private const val PLAIN = "plain"

    private const val DEFAULT_COLLECTION = "/org/freedesktop/secrets/aliases/default"

    /** Runs [block] with an open Secret Service session, or returns null. */
    private fun <T> withService(
        block: (DBusConnection, SecretServiceApi.Service, DBusPath) -> T?,
    ): T? = runCatching {
        DBusConnectionBuilder.forSessionBus().withShared(false).build().use { connection ->
            val service = connection.getRemoteObject(
                SecretServiceApi.BUS_NAME,
                SecretServiceApi.SERVICE_PATH,
                SecretServiceApi.Service::class.java,
            )
            val session = service.OpenSession(PLAIN, Variant("")).session
            block(connection, service, session)
        }
    }.getOrElse {
        DesktopTrackLog.log("secret store unavailable: ${it.message}")
        null
    }

    /** The first secret stored under [attributes], as bytes. */
    fun lookup(attributes: Map<String, String>): ByteArray? = if (DesktopPlatform.isMac) {
        lookupMac(attributes)
    } else {
        withService { _, service, session ->
            val found = service.SearchItems(attributes)
            val items = buildList {
                addAll(found.unlocked)
                if (found.locked.isNotEmpty()) {
                    runCatching { service.Unlock(found.locked) }.getOrNull()?.let { addAll(it.unlocked) }
                }
            }
            if (items.isEmpty()) return@withService null
            service.GetSecrets(items, session).values.firstOrNull()?.value
        }
    }

    /** Stores [secret] under [attributes], replacing whatever was there. */
    fun store(label: String, attributes: Map<String, String>, secret: ByteArray): Boolean =
        if (DesktopPlatform.isMac) {
            storeMac(label, attributes, secret)
        } else {
            withService { connection, service, session ->
                val collection = runCatching { service.ReadAlias("default")?.path }
                    .getOrNull()
                    ?.takeIf { it.isNotBlank() && it != "/" }
                    ?: DEFAULT_COLLECTION
                val properties = mapOf<String, Variant<*>>(
                    "org.freedesktop.Secret.Item.Label" to Variant(label),
                    "org.freedesktop.Secret.Item.Attributes" to Variant(attributes, "a{ss}"),
                )
                val item = connection.getRemoteObject(
                    SecretServiceApi.BUS_NAME,
                    collection,
                    SecretServiceApi.Collection::class.java,
                )
                item.CreateItem(
                    properties,
                    SecretServiceApi.Secret(session, ByteArray(0), secret, "text/plain; charset=utf8"),
                    true,
                )
                true
            } ?: false
        }

    /** Forgets everything stored under [attributes]. */
    fun remove(attributes: Map<String, String>): Boolean = if (DesktopPlatform.isMac) {
        removeMac(attributes)
    } else {
        withService { connection, service, _ ->
            val found = service.SearchItems(attributes)
            (found.unlocked + found.locked).forEach { path ->
                runCatching {
                    connection.getRemoteObject(
                        SecretServiceApi.BUS_NAME,
                        path.path,
                        SecretServiceApi.Item::class.java,
                    ).Delete()
                }
            }
            true
        } ?: false
    }

    /** Whether a store is actually reachable, for the settings screen to say so. */
    fun isAvailable(): Boolean = if (DesktopPlatform.isMac) {
        java.io.File("/usr/bin/security").canExecute()
    } else {
        withService { _, _, _ -> true } == true
    }

    private fun macAccountKey(attributes: Map<String, String>): String =
        attributes["account"] ?: attributes.entries.sortedBy { it.key }.joinToString(":") { "${it.key}=${it.value}" }

    private fun lookupMac(attributes: Map<String, String>): ByteArray? {
        val app = attributes["application"]
        val isThirdParty = app != null && app != "bitchord"
        val (service, account) = if (isThirdParty) {
            val s = when (app.lowercase()) {
                "chrome" -> "Chrome Safe Storage"
                "chromium" -> "Chromium Safe Storage"
                "brave" -> "Brave Safe Storage"
                "edge" -> "Microsoft Edge Safe Storage"
                "vivaldi" -> "Vivaldi Safe Storage"
                else -> app
            }
            val a = attributes["account"] ?: when (app.lowercase()) {
                "chrome" -> "Chrome"
                "chromium" -> "Chromium"
                "brave" -> "Brave"
                "edge" -> "Microsoft Edge"
                "vivaldi" -> "Vivaldi"
                else -> ""
            }
            s to a
        } else {
            "com.music.bitchord" to macAccountKey(attributes)
        }
        return runCatching {
            val cmd = mutableListOf("/usr/bin/security", "find-generic-password", "-s", service, "-w")
            if (account.isNotBlank()) {
                cmd.addAll(listOf("-a", account))
            }
            val process = ProcessBuilder(cmd).redirectError(ProcessBuilder.Redirect.DISCARD).start()
            val text = process.inputStream.bufferedReader().readText().trim()
            if (process.waitFor() == 0 && text.isNotBlank()) {
                if (isThirdParty) {
                    text.toByteArray(Charsets.UTF_8)
                } else {
                    runCatching { java.util.Base64.getDecoder().decode(text) }.getOrDefault(text.toByteArray(Charsets.UTF_8))
                }
            } else {
                null
            }
        }.getOrNull()
    }

    private fun storeMac(label: String, attributes: Map<String, String>, secret: ByteArray): Boolean {
        val account = macAccountKey(attributes)
        val encoded = java.util.Base64.getEncoder().encodeToString(secret)
        return runCatching {
            val cmd = listOf(
                "/usr/bin/security", "add-generic-password",
                "-U",
                "-s", "com.music.bitchord",
                "-a", account,
                "-w", encoded,
                "-l", label,
            )
            val process = ProcessBuilder(cmd).redirectError(ProcessBuilder.Redirect.DISCARD).start()
            process.waitFor() == 0
        }.getOrDefault(false)
    }

    private fun removeMac(attributes: Map<String, String>): Boolean {
        val account = macAccountKey(attributes)
        return runCatching {
            val cmd = listOf(
                "/usr/bin/security", "delete-generic-password",
                "-s", "com.music.bitchord",
                "-a", account,
            )
            val process = ProcessBuilder(cmd).redirectError(ProcessBuilder.Redirect.DISCARD).start()
            process.waitFor() == 0
        }.getOrDefault(false)
    }
}
