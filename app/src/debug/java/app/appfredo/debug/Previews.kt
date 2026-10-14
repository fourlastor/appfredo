package app.appfredo.debug

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import app.appfredo.ui.AppfredoApp
import app.appfredo.ui.StoreActions
import app.appfredo.ui.theme.AppfredoTheme

private val NoActions = StoreActions(
    vet = { null }, add = {}, remove = {}, restore = { _, _ -> }, refresh = {}, prepare = {}, install = {},
    openInstallSettings = {}, showPrompt = {}, dismissTray = {}, canOpen = { false }, open = {}, viewRepository = {},
)

private class Scenes : PreviewParameterProvider<String> {
    override val values = Samples.scenes.keys.asSequence()
}

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ScenePreview(@PreviewParameter(Scenes::class) scene: String) {
    AppfredoTheme { AppfredoApp(Samples.scenes.getValue(scene), permissionNeeded = false, version = "0.1.0", actions = NoActions) }
}

@Preview(name = "Asking to install")
@Composable
private fun PermissionPreview() {
    AppfredoTheme { AppfredoApp(Samples.scenes.getValue("served"), permissionNeeded = true, version = "0.1.0", actions = NoActions) }
}
