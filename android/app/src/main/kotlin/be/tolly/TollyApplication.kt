package be.tolly

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import be.tolly.core.data.LogExporter
import javax.inject.Inject

@HiltAndroidApp
class TollyApplication : Application(), Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var logExporter: LogExporter

    override fun onCreate() {
        super.onCreate()
        logExporter.installCrashRecorder()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
}
