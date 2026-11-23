package com.example.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build

data class DetectedEmulator(
  val packageName: String,
  val displayName: String,
  val isInstalled: Boolean,
)

object EmulatorLauncher {
  private val knownEmulators =
    listOf(
      "com.epsxe.ePSXe" to "ePSXe for Android",
      "com.github.stenzek.duckstation" to "DuckStation",
      "com.retroarch.aarch64" to "RetroArch (64-bit)",
      "com.retroarch" to "RetroArch",
    )

  fun detectInstalledEmulators(context: Context): List<DetectedEmulator> {
    val pm = context.packageManager
    return knownEmulators.map { (pkg, label) ->
      val installed =
        try {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
          } else {
            @Suppress("DEPRECATION") pm.getPackageInfo(pkg, 0)
          }
          true
        } catch (_: PackageManager.NameNotFoundException) {
          false
        }
      DetectedEmulator(pkg, label, installed)
    }
  }

  /**
   * Attempts to launch the modded PS1 disc URI into ePSXe / DuckStation / RetroArch, or falls back
   * to the Android system app chooser with read URI permission granted.
   *
   * Returns true if a dedicated emulator package was targeted directly, false if system chooser was opened.
   */
  fun openDiscInEmulator(
    context: Context,
    discUri: Uri,
    preferredPackage: String,
  ): Result<Boolean> = runCatching {
    val installed = detectInstalledEmulators(context).filter { it.isInstalled }
    val targetPkg =
      if (preferredPackage != "AUTO" && installed.any { it.packageName == preferredPackage }) {
        preferredPackage
      } else {
        installed.firstOrNull()?.packageName
      }

    val viewIntent =
      Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(discUri, "application/octet-stream")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        if (targetPkg != null) {
          setPackage(targetPkg)
        }
      }

    if (targetPkg != null && viewIntent.resolveActivity(context.packageManager) != null) {
      context.startActivity(viewIntent)
      true
    } else {
      val chooser =
        Intent.createChooser(
            Intent(Intent.ACTION_VIEW).apply {
              setDataAndType(discUri, "*/*")
              addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            "Open PS1 Mod Disc",
          )
          .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
      context.startActivity(chooser)
      false
    }
  }
}
