package com.sprintmark.tracktime.audio

import android.content.Context
import android.media.MediaPlayer
import androidx.annotation.RawRes
import com.sprintmark.tracktime.R

class StarterSoundPlayer(
    private val context: Context,
    @RawRes private val setSoundResId: Int,
    @RawRes private val gunSoundResId: Int
) {
    fun playCall() {
        playSound(setSoundResId)
    }

    fun playGun() {
        playSound(gunSoundResId)
    }

    private fun playSound(@RawRes resId: Int) {
        val player = MediaPlayer.create(context, resId) ?: error("音声プレーヤーの生成に失敗しました")
        player.setOnCompletionListener { it.release() }
        player.setOnErrorListener { mp, _, _ ->
            mp.release()
            true
        }
        player.start()
    }

    fun release() = Unit

    companion object {
        val DEFAULT_SET_SOUND_RES = R.raw.set
        val DEFAULT_GUN_SOUND_RES = R.raw.gun
    }
}
