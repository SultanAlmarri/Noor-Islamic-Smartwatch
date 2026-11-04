package com.noor.wear

import android.app.Application
import com.noor.wear.data.ConfigRepository

class NoorWearApp : Application() {
    val config by lazy { ConfigRepository(this) }
}
