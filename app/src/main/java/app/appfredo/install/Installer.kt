package app.appfredo.install

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import app.appfredo.AppfredoApplication

data class InstallResult(val message: String, val confirmation: Intent? = null, val success: Boolean = false)

class Installer(private val context: Context) {
    fun install(apk: PreparedApk) {
        check(context.packageManager.canRequestPackageInstalls()) { "Allow Appfredo to install apps, then tap Install again." }
        val installer = context.packageManager.packageInstaller
        // Abandon an earlier unfinished Appfredo session before creating a replacement.
        installer.mySessions.filter { it.appPackageName == apk.manifest.packageName }.forEach {
            runCatching { installer.abandonSession(it.sessionId) }
        }
        val parameters = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(apk.manifest.packageName)
            setSize(apk.file.length())
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
        }
        val id = installer.createSession(parameters)
        try {
            installer.openSession(id).use { session ->
                session.openWrite("app.apk", 0, apk.file.length()).use { output ->
                    apk.file.inputStream().use { it.copyTo(output) }
                    session.fsync(output)
                }
                val intent = Intent(context, InstallReceiver::class.java).setAction("app.appfredo.INSTALL_RESULT")
                // PackageInstaller must fill status extras, so this explicit callback is mutable.
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
                val callback = PendingIntent.getBroadcast(context, id, intent, flags)
                session.commit(callback.intentSender)
            }
        } catch (e: Exception) { installer.abandonSession(id); throw e }
    }
}

class InstallReceiver : BroadcastReceiver() {
    @Suppress("DEPRECATION")
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val result = when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> InstallResult("Ready for Android's confirmation",
                intent.getParcelableExtra(Intent.EXTRA_INTENT))
            PackageInstaller.STATUS_SUCCESS -> InstallResult("Installed. At your service.", success = true)
            PackageInstaller.STATUS_FAILURE_ABORTED -> InstallResult("Installation cancelled. Your previous app is unchanged.")
            else -> InstallResult("Android could not install this APK: ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "error $status"}")
        }
        (context.applicationContext as AppfredoApplication).installResult.value = result
    }
}
