package wgg.siap.screens

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.annotation.RawRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.airbnb.lottie.LottieAnimationView
import wgg.siap.R

class StatusFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_status, container, false)
        val args = arguments ?: return view

        val titleView = view.findViewById<TextView>(R.id.txtTitle)
        val animView = view.findViewById<LottieAnimationView>(R.id.animIcon)
        val messageView = view.findViewById<TextView>(R.id.txtMessage)

        // Titulo
        titleView.text = args.getString(ARG_TITLE).orEmpty()
        titleView.setTextColor(ContextCompat.getColor(requireContext(), args.getInt(ARG_TITLE_COLOR, R.color.screenTextPrimary)))

        // Animacion
        val sizeDp = args.getInt(ARG_ANIM_SIZE_DP, DEFAULT_SIZE_DP)
        if (sizeDp > 0) {
            val sizePx = (sizeDp * resources.displayMetrics.density).toInt()
            animView.layoutParams.width = sizePx
            animView.layoutParams.height = sizePx
        }
        animView.setAnimation(args.getInt(ARG_ANIM_RES, R.raw.load))
        animView.loop(args.getBoolean(ARG_LOOP, false))
        animView.playAnimation()

        // Mensaje
        val message = args.getString(ARG_MESSAGE).orEmpty()
        if (message.isNotEmpty()) {
            messageView.text = message
            messageView.visibility = View.VISIBLE
        } else {
            messageView.visibility = View.GONE
        }

        return view
    }

    companion object {
        private const val ARG_TITLE = "title"
        private const val ARG_TITLE_COLOR = "titleColor"
        private const val ARG_ANIM_RES = "animRes"
        private const val ARG_LOOP = "loop"
        private const val ARG_MESSAGE = "message"
        private const val ARG_ANIM_SIZE_DP = "animSizeDp"
        private const val DEFAULT_SIZE_DP = 0 // 0 = usar dimen del layout

        fun success(title: String, message: String = ""): StatusFragment = create(
            title = title,
            titleColor = R.color.screenSuccess,
            animRes = R.raw.success,
            loop = false,
            message = message
        )

        fun error(title: String, message: String = ""): StatusFragment = create(
            title = title,
            titleColor = R.color.screenError,
            animRes = R.raw.error,
            loop = false,
            message = message
        )

        fun cancel(title: String, message: String = ""): StatusFragment = create(
            title = title,
            titleColor = R.color.screenCancel,
            animRes = R.raw.error,
            loop = false,
            message = message
        )

        fun warning(title: String, message: String = ""): StatusFragment = create(
            title = title,
            titleColor = R.color.screenCancel,
            animRes = R.raw.warning,
            loop = false,
            message = message
        )

        fun tolerance(title: String, message: String = ""): StatusFragment = create(
            title = title,
            titleColor = R.color.screenTime,
            animRes = R.raw.clock,
            loop = true,
            message = message,
            animSizeDp = 200
        )

        private fun create(
            title: String,
            @ColorRes titleColor: Int,
            @RawRes animRes: Int,
            loop: Boolean,
            message: String,
            animSizeDp: Int = DEFAULT_SIZE_DP
        ): StatusFragment {
            return StatusFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_TITLE, title)
                    putInt(ARG_TITLE_COLOR, titleColor)
                    putInt(ARG_ANIM_RES, animRes)
                    putBoolean(ARG_LOOP, loop)
                    putString(ARG_MESSAGE, message)
                    putInt(ARG_ANIM_SIZE_DP, animSizeDp)
                }
            }
        }
    }
}
