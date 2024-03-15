package info.bitcoinunlimited.www.wally

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import info.bitcoinunlimited.www.wally.databinding.*

class Welcome : CommonNavActivity()
{
    private lateinit var ui:ActivityWelcomeBinding
    var walletCreated = false
    var enteredIdentity = false

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        ui = ActivityWelcomeBinding.inflate(layoutInflater)
        setContentView(ui.root)
        val app = (getApplication() as WallyApp)
    }

    /*  Since I'm using the fragment nav, this isn't needed but keeping for possible use later
    fun loadFragment(fragment: Fragment)
    {
        // create a FragmentManager
        val fm: FragmentManager = supportFragmentManager
        // create a FragmentTransaction to begin the transaction and replace the Fragment
        val fragmentTransaction: FragmentTransaction = fm.beginTransaction()
        // replace the FrameLayout with new Fragment
        fragmentTransaction.replace(R.id.welcomeStep1, fragment)
        fragmentTransaction.commit() // save the changes
    }
     */
}


/**
class WelcomeFragment1 : Fragment()
{
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View
    {
        return Welcomef1Binding.inflate(inflater).root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?)
    {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<Button>(R.id.button_first).setOnClickListener {
            findNavController().navigate(R.id.action_Welcome1_to_Welcome2)
        }
    }
}
*/