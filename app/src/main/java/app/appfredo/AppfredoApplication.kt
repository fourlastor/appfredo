package app.appfredo

import android.app.Application
import app.appfredo.install.InstallResult
import kotlinx.coroutines.flow.MutableStateFlow

class AppfredoApplication : Application() {
    val installResult = MutableStateFlow<InstallResult?>(null)
}
