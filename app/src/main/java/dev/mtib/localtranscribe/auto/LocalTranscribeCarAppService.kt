package dev.mtib.localtranscribe.auto

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.validation.HostValidator

/** Entry point for the Android Auto app. Allows all hosts since this is a personal sideload build. */
class LocalTranscribeCarAppService : CarAppService() {
    override fun createHostValidator(): HostValidator =
        HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): Session = object : Session() {
        override fun onCreateScreen(intent: Intent) = TranscribeScreen(carContext)
    }
}
