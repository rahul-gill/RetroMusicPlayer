package meloplayer.app.fragments.player

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import meloplayer.app.R
import meloplayer.app.SHOW_LYRICS
import meloplayer.app.databinding.FragmentCoverLyricsBinding
import meloplayer.app.extensions.dipToPix
import meloplayer.app.fragments.NowPlayingScreen
import meloplayer.app.fragments.base.AbsMusicServiceFragment
import meloplayer.app.fragments.base.AbsPlayerFragment
import meloplayer.app.fragments.base.goToLyrics
import meloplayer.app.helper.MusicPlayerRemote
import meloplayer.app.helper.MusicProgressViewUpdateHelper
import meloplayer.app.model.lyrics.AbsSynchronizedLyrics
import meloplayer.app.model.lyrics.Lyrics
import meloplayer.app.util.LyricUtil
import meloplayer.app.util.PreferenceUtil
import meloplayer.app.util.color.MediaNotificationProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jaudiotagger.audio.exceptions.CannotReadException
import java.io.File
import java.io.FileNotFoundException

class CoverLyricsFragment : AbsMusicServiceFragment(R.layout.fragment_cover_lyrics),
    MusicProgressViewUpdateHelper.Callback, SharedPreferences.OnSharedPreferenceChangeListener {
    private var progressViewUpdateHelper: MusicProgressViewUpdateHelper? = null
    private var _binding: FragmentCoverLyricsBinding? = null
    private val binding get() = _binding!!

    private val lyricsLayout: FrameLayout get() = binding.playerLyrics
    private val lyricsLine1: TextView get() = binding.playerLyricsLine1
    private val lyricsLine2: TextView get() = binding.playerLyricsLine2

    private var lyrics: Lyrics? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentCoverLyricsBinding.bind(view)
        progressViewUpdateHelper = MusicProgressViewUpdateHelper(this, 500, 1000)
        if (PreferenceUtil.showLyrics) {
            progressViewUpdateHelper?.start()
        }
        // Remove background on Fit theme
        val nps = PreferenceUtil.nowPlayingScreen
        if (nps == NowPlayingScreen.Fit || nps == NowPlayingScreen.Full) {
            binding.root.background = null
        }
        binding.playerLyricsLine2.setOnClickListener {
            goToLyrics(requireActivity())
        }
    }

    fun setColors(color: MediaNotificationProcessor) {
        binding.run {
            playerLyrics.background = null
            playerLyricsLine1.setTextColor(color.primaryTextColor)
            playerLyricsLine1.setShadowLayer(dipToPix(10f), 0f, 0f, color.backgroundColor)
            playerLyricsLine2.setTextColor(color.primaryTextColor)
            playerLyricsLine2.setShadowLayer(dipToPix(10f), 0f, 0f, color.backgroundColor)
        }

    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        if (key == SHOW_LYRICS) {
            if (sharedPreferences.getBoolean(key, false) == true) {
                progressViewUpdateHelper?.start()
                binding.root.isVisible = true
                updateLyrics()
            } else {
                progressViewUpdateHelper?.stop()
                binding.root.isVisible = false
            }
        }
    }

    override fun onPlayingMetaChanged() {
        super.onPlayingMetaChanged()
        if (PreferenceUtil.showLyrics) {
            updateLyrics()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (PreferenceUtil.showLyrics) {
            updateLyrics()
        }
    }

    private fun updateLyrics() {
        lyrics = null
        lifecycleScope.launch(Dispatchers.IO) {
            val song = MusicPlayerRemote.currentSong
            lyrics = try {
                val lrcFile: File? = LyricUtil.getSyncedLyricsFile(song)
                val data: String = LyricUtil.getStringFromLrc(lrcFile)
                Lyrics.parse(song,
                    data.ifEmpty {
                        // Get Embedded Lyrics
                        LyricUtil.getEmbeddedSyncedLyrics(song.data)
                    }
                )
            } catch (err: FileNotFoundException) {
                null
            } catch (e: CannotReadException) {
                null
            }
        }
    }

    override fun onUpdateProgressViews(progress: Int, total: Int) {
        if (_binding == null) return

        if (!isLyricsLayoutVisible()) {
            hideLyricsLayout()
            return
        }

        if (lyrics !is AbsSynchronizedLyrics) return
        val synchronizedLyrics = lyrics as AbsSynchronizedLyrics

        lyricsLayout.isVisible = true
        lyricsLayout.alpha = 1f

        val oldLine = lyricsLine2.text.toString()
        val line = synchronizedLyrics.getLine(progress)

        if (oldLine != line || oldLine.isEmpty()) {
            lyricsLine1.text = oldLine
            lyricsLine2.text = line

            lyricsLine1.isVisible = true
            lyricsLine2.isVisible = true

            lyricsLine2.measure(
                View.MeasureSpec.makeMeasureSpec(
                    lyricsLine2.measuredWidth,
                    View.MeasureSpec.EXACTLY
                ),
                View.MeasureSpec.UNSPECIFIED
            )
            val h: Float = lyricsLine2.measuredHeight.toFloat()

            lyricsLine1.alpha = 1f
            lyricsLine1.translationY = 0f
            lyricsLine1.animate().alpha(0f).translationY(-h).duration =
                AbsPlayerFragment.VISIBILITY_ANIM_DURATION

            lyricsLine2.alpha = 0f
            lyricsLine2.translationY = h
            lyricsLine2.animate().alpha(1f).translationY(0f).duration =
                AbsPlayerFragment.VISIBILITY_ANIM_DURATION
        }
    }

    private fun isLyricsLayoutVisible(): Boolean {
        return lyrics != null && lyrics!!.isSynchronized && lyrics!!.isValid
    }

    private fun hideLyricsLayout() {
        lyricsLayout.animate().alpha(0f).setDuration(AbsPlayerFragment.VISIBILITY_ANIM_DURATION)
            .withEndAction {
                if (_binding == null) return@withEndAction
                lyricsLayout.isVisible = false
                lyricsLine1.text = null
                lyricsLine2.text = null
            }
    }

    override fun onResume() {
        super.onResume()
        PreferenceManager.getDefaultSharedPreferences(requireContext())
            .registerOnSharedPreferenceChangeListener(this)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        PreferenceManager.getDefaultSharedPreferences(requireContext())
            .unregisterOnSharedPreferenceChangeListener(this)
        progressViewUpdateHelper?.stop()
        _binding = null
    }
}