package wgg.siap.screens

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import wgg.siap.R

class LoadingFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_loading, container, false)
        val message = arguments?.getString(ARG_MESSAGE).orEmpty()
        view.findViewById<TextView>(R.id.txtLoadingMessage).text = message
        return view
    }

    companion object {
        private const val ARG_MESSAGE = "message"

        fun newInstance(message: String = ""): LoadingFragment {
            return LoadingFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_MESSAGE, message)
                }
            }
        }
    }
}
