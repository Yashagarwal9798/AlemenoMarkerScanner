package com.alemenomarkerscanner.marker

import com.facebook.react.BaseReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.module.model.ReactModuleInfo
import com.facebook.react.module.model.ReactModuleInfoProvider

class MarkerDetectorPackage : BaseReactPackage() {
  override fun getModule(
      name: String,
      reactContext: ReactApplicationContext,
  ): NativeModule? =
      when (name) {
        NativeMarkerDetectorModule.NAME -> NativeMarkerDetectorModule(reactContext)
        else -> null
      }

  override fun getReactModuleInfoProvider(): ReactModuleInfoProvider =
      ReactModuleInfoProvider {
        mapOf(
            NativeMarkerDetectorModule.NAME to
                ReactModuleInfo(
                    NativeMarkerDetectorModule.NAME,
                    NativeMarkerDetectorModule::class.java.name,
                    false,
                    false,
                    false,
                    false,
                )
        )
      }
}
